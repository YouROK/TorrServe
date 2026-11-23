package ru.yourok.torrserve.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Подготовка файла к воспроизведению: активирует раздачу на сервере и загружает
 * начало файла, показывая реальный прогресс.
 *
 * Почему не только `POST /files/:id/preload`: для действия «Играть» торрент
 * добавляется с `save_to_db=false`, и сервер отвечает
 * `400 torrent not found in your library`, а `preloaded_bytes` всегда 0
 * (проверено на 192.168.1.11:8090). Поэтому если серверная предзагрузка
 * недоступна или не двигается, прогресс берём из собственного чтения потока:
 * сервер отдаёт игроку те же байты, то есть это и есть реальная предзагрузка.
 */
class Preloader(
    context: Context,
    private val api: SiloApi,
    private val token: String,
    private val hash: String,
    private val file: SiloApi.TorrentFile,
    private val listener: Listener,
) {

    interface Listener {
        /** Этап без числового прогресса (шкала «бежит»). */
        fun onStage(text: String)

        /** Числовой прогресс: 0..100, строка статуса и подробности под шкалой. */
        fun onProgress(percent: Int, status: String, detail: String)
    }

    private val context: Context = context.applicationContext
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false
    @Volatile private var openNow = false
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        runCatching { connection?.disconnect() }
    }

    fun openNow() {
        openNow = true
        runCatching { connection?.disconnect() }
    }

    /** Запускает подготовку; [onReady] вызывается в главном потоке, если не отменено. */
    fun start(onReady: () -> Unit) {
        thread {
            runCatching { prepare() }
                .onFailure { e ->
                    ui.post {
                        if (!cancelled && !openNow) {
                            listener.onStage(context.getString(R.string.prep_error, e.message ?: ""))
                        }
                    }
                }
            ui.post { if (!cancelled) onReady() }
        }
    }

    // ------------------------------------------------------------------ этапы

    private fun prepare() {
        stage(R.string.prep_activating)
        runCatching { api.wakeTorrent(token, hash) }

        // Ждём, пока сервер поднимет сессию (4 - закрыт, 5 - только в базе)
        var details = waitActive()
        if (details == null) {
            stage(R.string.prep_activate_failed)
        }

        val target = preloadTarget()
        val deadline = System.currentTimeMillis() + MAX_WAIT_MS
        // Прогресс всегда считаем по реально пришедшим байтам потока.
        // Серверный /preload для «Играть» (save_to_db=false) отвечает
        // «torrent not found in your library», а preloaded_bytes у части раздач
        // остаётся нулём - шкала тогда стоит на 0% (жалоба пользователя).
        streamPreload(target, deadline, details)
    }

    /** Ожидание активной сессии: возвращает последние данные или null по таймауту. */
    private fun waitActive(): SiloApi.TorrentDetails? {
        var last: SiloApi.TorrentDetails? = null
        val deadline = System.currentTimeMillis() + ACTIVATE_WAIT_MS
        while (!cancelled && !openNow && System.currentTimeMillis() < deadline) {
            val d = runCatching { api.getTorrent(token, hash) }.getOrNull()
            if (d != null) {
                last = d
                if (d.stat !in 4..5) return d
            }
            Thread.sleep(POLL_MS)
        }
        return last
    }

    /** Сколько сервер предзагружает (preload_size); 0 - предзагрузка отключена. */
    private fun preloadTarget(): Long {
        val configured = runCatching { api.preloadSize(token) }.getOrDefault(0L)
        val size = if (configured > 0) configured else FALLBACK_PRELOAD_BYTES
        val length = file.length
        return if (length > 0) minOf(size, length) else size
    }

    /**
     * Чтение начала файла через поток. Сервер параллельно качает эти куски с пиров,
     * байты попадают в его кэш - поэтому игроку потом отдаётся то же самое.
     */
    private fun streamPreload(
        target: Long,
        deadline: Long,
        initial: SiloApi.TorrentDetails?,
    ) {
        stage(R.string.prep_reading_stream)
        var read = 0L
        var speed = 0.0
        var details = initial
        var speedTime = System.currentTimeMillis()
        var speedBytes = 0L
        var nextPeerPoll = 0L

        while (read < target && !cancelled && !openNow &&
            System.currentTimeMillis() < deadline
        ) {
            val remaining = target - read
            val conn = openStream(read, remaining)
            if (conn == null) {
                if (read == 0L) {
                    // Совсем нет соединения: показываем состояние и ждём/отмену
                    stage(R.string.prep_no_data)
                }
                Thread.sleep(POLL_MS)
                continue
            }
            connection = conn
            var streamStalled = false
            try {
                val input = conn.inputStream
                val buffer = ByteArray(CHUNK)
                while (read < target && !cancelled && !openNow) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    read += n
                    speedBytes += n

                    val now = System.currentTimeMillis()
                    if (now - speedTime >= 1000) {
                        speed = speedBytes * 1000.0 / (now - speedTime)
                        speedTime = now
                        speedBytes = 0
                    }
                    // Пиры/сиды - из статуса сервера, не чаще раза в 1.5 с
                    if (now >= nextPeerPoll) {
                        nextPeerPoll = now + PEER_POLL_MS
                        details = runCatching { api.getTorrent(token, hash) }.getOrNull() ?: details
                    }
                    report(
                        percentOf(read, target),
                        read,
                        target,
                        details,
                        speed,
                    )
                }
            } catch (e: Exception) {
                streamStalled = read < target
                if (streamStalled) {
                    // Нет данных: показываем понятное состояние, повторяем чтение с места
                    stage(if (read == 0L) R.string.prep_no_data else R.string.prep_stalled)
                    Thread.sleep(POLL_MS)
                }
            } finally {
                runCatching { connection?.disconnect() }
                connection = null
            }
        }
    }

    /** Соединение с потоком с нужного места (Range). */
    private fun openStream(from: Long, count: Long): HttpURLConnection? = try {
        val end = from + count - 1
        val conn = (URL(api.streamUrl(token, hash, file.id)).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Range", "bytes=$from-$end")
        }
        conn.connect()
        conn
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- отчёты

    private fun report(
        percent: Int,
        loaded: Long,
        target: Long,
        details: SiloApi.TorrentDetails?,
        speed: Double = details?.downloadSpeed ?: 0.0,
    ) {
        val peers = context.getString(
            R.string.card_seeders_peers,
            details?.seeders ?: 0,
            details?.peers ?: 0,
        )
        val status = context.getString(
            R.string.prep_status,
            humanSize(loaded),
            humanSize(target),
            peers,
        )
        val detail = buildString {
            append(context.getString(R.string.prep_file_size, humanSize(file.length)))
            if (speed > 0) {
                append(" · ")
                append(
                    context.getString(
                        R.string.prep_speed,
                        String.format(Locale.ROOT, "%.1f", speed / 1024 / 1024),
                    )
                )
            }
        }
        ui.post {
            if (!cancelled && !openNow) listener.onProgress(percent, status, detail)
        }
    }

    private fun stage(res: Int) = stage(context.getString(res))

    private fun stage(text: String) {
        ui.post { if (!cancelled && !openNow) listener.onStage(text) }
    }

    private fun percentOf(loaded: Long, target: Long): Int =
        if (target <= 0) 100 else ((loaded * 100) / target).toInt().coerceIn(0, 100)

    companion object {
        private const val FALLBACK_PRELOAD_BYTES = 16L * 1024 * 1024
        private const val POLL_MS = 600L
        private const val PEER_POLL_MS = 1500L
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 15000
        private const val ACTIVATE_WAIT_MS = 20_000L
        private const val MAX_WAIT_MS = 60_000L
        private const val CHUNK = 64 * 1024

        fun humanSize(bytes: Long): String {
            if (bytes <= 0) return "0 Б"
            val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
            var value = bytes.toDouble()
            var index = 0
            while (value >= 1024 && index < units.size - 1) {
                value /= 1024
                index++
            }
            return String.format(Locale.ROOT, "%.1f %s", value, units[index])
        }
    }
}
