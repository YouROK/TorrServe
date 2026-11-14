package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import kotlin.concurrent.thread

/**
 * Окно подготовки файла перед запуском плеера: активирует раздачу в памяти сервера,
 * запускает предзагрузку и показывает реальный прогресс.
 */
object PreloadDialog {

    /** Запасной размер предзагрузки, если сервер не ответил. */
    private const val FALLBACK_PRELOAD_BYTES = 16L * 1024 * 1024
    private const val POLL_MS = 600L
    private const val PROBE_TIMEOUT_MS = 3000
    private const val MAX_WAIT_MS = 60_000L
    private const val ACTIVATE_WAIT_MS = 20_000L
    private const val NO_PEERS_HINT_MS = 8_000L
    /** Через сколько пробуем запасной вариант (проверка потока), если предзагрузка не идёт. */
    private const val FALLBACK_PROBE_MS = 10_000L

    private val ui = Handler(Looper.getMainLooper())

    /**
     * Готовит [file] и вызывает [onReady] для запуска плеера.
     * [onProgress]: 0..100 для внешнего прогресса (может быть null).
     */
    fun prepare(
        context: Context,
        api: SiloApi,
        token: String,
        hash: String,
        file: SiloApi.TorrentFile,
        onProgress: ((Int) -> Unit)? = null,
        onReady: () -> Unit,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_preload, null)
        val title = view.findViewById<TextView>(R.id.prep_title)
        val status = view.findViewById<TextView>(R.id.prep_status)
        val progress = view.findViewById<ProgressBar>(R.id.prep_progress)
        val percent = view.findViewById<TextView>(R.id.prep_percent)

        title.text = file.name
        progress.isIndeterminate = true

        var cancelled = false
        var openNow = false
        // Доп. информация в окне: размер файла и скорость
        var infoSuffix = ""
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            // Можно не ждать: открыть плеер с тем, что уже загружено
            .setPositiveButton(R.string.prep_open_now) { _, _ -> openNow = true }
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }
            .setCancelable(true)
            .create()
        dialog.setOnDismissListener { cancelled = true }
        dialog.show()

        thread {
            fun say(text: String) = ui.post {
                if (dialog.isShowing) status.text = text
            }

            fun setPercent(p: Int) = ui.post {
                if (!dialog.isShowing) return@post
                progress.isIndeterminate = false
                progress.progress = p
                percent.text = "$p%$infoSuffix"
                onProgress?.invoke(p)
            }

            // 1. Раздача должна быть активна в памяти, иначе предзагрузка - пустышка
            say(context.getString(R.string.prep_activating))
            runCatching { api.wakeTorrent(token, hash) }
            val activateDeadline = System.currentTimeMillis() + ACTIVATE_WAIT_MS
            var active = false
            while (System.currentTimeMillis() < activateDeadline) {
                if (cancelled) return@thread
                val d = runCatching { api.getTorrent(token, hash) }.getOrNull()
                // 4 = закрыт, 5 = только в базе: ждём, пока сервер поднимет сессию
                if (d != null && d.stat != 4 && d.stat != 5) {
                    active = true
                    break
                }
                Thread.sleep(POLL_MS)
            }
            if (!active) say(context.getString(R.string.prep_activate_failed))

            // Сколько сервер предзагружает (настройка preload_size, обычно 16 МБ)
            val preloadTarget = runCatching { api.preloadSize(token) }
                .getOrDefault(0L)
                .let { if (it > 0) it else FALLBACK_PRELOAD_BYTES }

            // 2. Предзагрузка. Сервер инициализирует список файлов не сразу после wake,
            //    поэтому первый вызов может вернуть "file index out of bounds" - повторяем.
            var preloadStarted = false
            var lastPercent = -1
            var shownNoPeers = false
            val started = System.currentTimeMillis()
            val deadline = started + MAX_WAIT_MS
            var iteration = 0

            while (System.currentTimeMillis() < deadline) {
                if (cancelled) return@thread
                if (openNow) break

                val details = runCatching { api.getTorrent(token, hash) }.getOrNull()

                // Вызываем ровно один раз; повторяем только если сервер ещё не готов
                // (иначе повторный вызов перезапускает предзагрузку и сбрасывает прогресс)
                if (!preloadStarted) {
                    val err = runCatching { api.preloadFile(token, hash, file.id) }.getOrNull()
                    if (err == null) preloadStarted = true
                }

                if (details != null) {
                    // preloaded_bytes = заполненность кэша предзагрузки (как в веб-JSON)
                    val target = minOf(
                        preloadTarget.coerceAtLeast(1),
                        file.length.coerceAtLeast(1),
                    )
                    val preloaded = details.preloadedBytes.coerceIn(0, target)
                    val p = ((preloaded * 100) / target).toInt().coerceIn(0, 100)
                    if (p != lastPercent) {
                        lastPercent = p
                        setPercent(p)
                    }
                    val speed = details.downloadSpeed
                    infoSuffix = buildString {
                        append(" · ")
                        append(context.getString(R.string.prep_file_size, humanSize(file.length)))
                        if (speed > 0) {
                            append(" · ")
                            append(String.format(java.util.Locale.ROOT, "%.1f МБ/с", speed / 1024 / 1024))
                        }
                    }
                    say(
                        context.getString(
                            R.string.prep_status,
                            humanSize(preloaded),
                            humanSize(target),
                            context.getString(
                                R.string.card_seeders_peers,
                                details.seeders,
                                details.peers,
                            ),
                        )
                    )
                    val peers = details.seeders + details.peers
                    if (peers == 0 && !shownNoPeers &&
                        System.currentTimeMillis() - started > NO_PEERS_HINT_MS
                    ) {
                        shownNoPeers = true
                        say(context.getString(R.string.prep_no_peers))
                    }
                    if (preloaded >= target) break

                    // Страховка: если сервер не наращивает предзагрузку, но поток уже отдаёт
                    // данные - не держим пользователя, запускаем воспроизведение.
                    if (preloaded == 0L && System.currentTimeMillis() - started > FALLBACK_PROBE_MS) {
                        if (api.probeStream(token, hash, file.id, PROBE_TIMEOUT_MS)) break
                    }
                }
                iteration++
                Thread.sleep(POLL_MS)
            }

            ui.post {
                if (dialog.isShowing) dialog.dismiss()
                onReady()
            }
        }
    }

    /** Размер в человекочитаемом виде (как в списке торрентов). */
    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 Б"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.size - 1) {
            value /= 1024
            index++
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[index])
    }
}
