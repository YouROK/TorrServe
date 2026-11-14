package ru.yourok.torrserve.server.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Клиент API Silo. Работает через HttpURLConnection (без лишних зависимостей).
 * Все вызовы блокирующие - вызывать из фонового потока.
 */
class SiloApi(private val baseUrl: String) {

    /** true, если сервер отвечает на /api/system/ping. */
    fun ping(): Boolean = try {
        request("GET", "/api/system/ping", null, null).first == 200
    } catch (e: Exception) {
        false
    }

    /** Версия сервера из /api/system/version ("Silo.2") или null. */
    fun version(): String? = try {
        val (code, resp) = request("GET", "/api/system/version", null, null)
        if (code != 200) null else JSONObject(resp).optString("version").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** Вход пользователя. */
    fun login(username: String, password: String): LoginResult {
        val body = JSONObject().put("username", username).put("password", password).toString()
        return try {
            val (code, resp) = request("POST", "/api/auth/login", body, null)
            when (code) {
                200 -> LoginResult.Ok(JSONObject(resp).getString("token"))
                401 -> LoginResult.WrongPassword
                else -> LoginResult.Error("HTTP $code")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "network error")
        }
    }

    /** Список торрентов пользователя (краткие карточки). */
    fun listTorrents(token: String): List<TorrentItem> {
        val (code, resp) = request("GET", "/api/torrents", null, token)
        if (code != 200) throw IllegalStateException("HTTP $code")
        val arr = JSONObject(resp).optJSONArray("torrents") ?: return emptyList()
        return (0 until arr.length()).map { i -> parseItem(arr.getJSONObject(i)) }
    }

    /** Полные данные торрента (статус и файлы). */
    fun getTorrent(token: String, hash: String): TorrentDetails {
        val (code, resp) = request("GET", "/api/torrents/$hash", null, token)
        if (code != 200) throw IllegalStateException(errorOf(resp, code))
        val o = JSONObject(resp)
        val files = o.optJSONArray("file_stats")
        val viewed = o.optJSONArray("viewed_files")
        val viewedSet = (0 until (viewed?.length() ?: 0)).map { viewed!!.getInt(it) }.toSet()
        val list = (0 until (files?.length() ?: 0)).map { i ->
            val f = files!!.getJSONObject(i)
            TorrentFile(
                id = f.optInt("id"),
                name = f.optString("name").ifBlank { f.optString("path") },
                length = f.optLong("length"),
                viewed = viewedSet.contains(f.optInt("id")),
            )
        }
        return TorrentDetails(
            hash = hash,
            title = o.optString("title").ifBlank { o.optString("name") },
            statString = o.optString("stat_string"),
            stat = o.optInt("stat"),
            poster = o.optString("poster"),
            category = o.optString("category"),
            sizeBytes = o.optLong("torrent_size"),
            downloadSpeed = o.optDouble("download_speed", 0.0),
            peers = o.optInt("active_peers"),
            seeders = o.optInt("connected_seeders"),
            preloadedBytes = o.optLong("preloaded_bytes"),
            files = list,
        )
    }



    /** Активирует раздачу в памяти сервера (без этого предзагрузка не работает). */
    fun wakeTorrent(token: String, hash: String): String? {
        val (code, resp) = request("POST", "/api/torrents/$hash/wake", "{}", token)
        return if (code in 200..299) null else errorOf(resp, code)
    }




    /**
     * Проверка, что начало файла реально доступно: запрашиваем первые килобайты потока.
     * Это надёжнее preload по индексу (id файла и индекс в движке могут не совпадать)
     * и одновременно запускает чтение нужных кусков на сервере.
     */
    fun probeStream(token: String, hash: String, fileId: Int, timeoutMs: Int = 2500): Boolean {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL(streamUrl(token, hash, fileId))
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Range", "bytes=0-65535")
            val code = conn.responseCode
            if (code == 200 || code == 206) {
                val buffer = ByteArray(1024)
                val read = conn.inputStream.use { it.read(buffer) }
                read > 0
            } else {
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                // соединение уже закрыто
            }
        }
    }

    /** Состояние кэша раздачи: сколько уже загружено (это реальный показатель предзагрузки). */
    data class CacheState(
        val filled: Long,
        val capacity: Long,
        val pieceLength: Long,
        val pieceCount: Int,
        val ids: Set<Int>,
    )

    fun cacheState(token: String, hash: String): CacheState? = try {
        val (code, resp) = request("GET", "/api/torrents/$hash/cache", null, token)
        if (code != 200) null else {
            val o = JSONObject(resp)
            val idsArr = o.optJSONArray("ids")
            val ids = HashSet<Int>()
            if (idsArr != null) for (i in 0 until idsArr.length()) ids.add(idsArr.getInt(i))
            CacheState(
                filled = o.optLong("filled"),
                capacity = o.optLong("capacity"),
                pieceLength = o.optLong("piece_length"),
                pieceCount = o.optInt("piece_count"),
                ids = ids,
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Размер предзагрузки из настроек сервера (байты). 0 - предзагрузка отключена. */
    fun preloadSize(token: String): Long = try {
        val (code, resp) = request("GET", "/api/system/torrent-config", null, token)
        if (code == 200) JSONObject(resp).optLong("preload_size", 0L) else 0L
    } catch (e: Exception) {
        0L
    }

    /** Предзагрузка файла: сервер начинает тянуть данные до запуска плеера. */
    fun preloadFile(token: String, hash: String, fileId: Int): String? {
        val (code, resp) = request("POST", "/api/torrents/$hash/files/$fileId/preload", "{}", token)
        return if (code in 200..299) null else errorOf(resp, code)
    }

    /** Удалить торрент у пользователя. Возвращает null при успехе, иначе текст ошибки. */
    fun dropTorrent(token: String, hash: String): String? {
        val (code, resp) = request("DELETE", "/api/torrents/$hash", null, token)
        return if (code in 200..299) null else errorOf(resp, code)
    }

    /** Отметить файл просмотренным/непросмотренным. */
    fun setFileViewed(token: String, hash: String, fileId: Int, viewed: Boolean): String? {
        val body = JSONObject().put("viewed", viewed).toString()
        val (code, resp) = request("POST", "/api/torrents/$hash/files/$fileId/viewed", body, token)
        return if (code in 200..299) null else errorOf(resp, code)
    }


    /** Отметить все файлы торрента просмотренными. */
    fun markAllViewed(token: String, hash: String): String? {
        val (code, resp) = request("POST", "/api/torrents/$hash/viewed", "{}", token)
        return if (code in 200..299) null else errorOf(resp, code)
    }

    /**
     * Добавляет торрент по magnet-ссылке / info-hash / torrs:// от имени пользователя.
     * Возвращает null при успехе, иначе текст ошибки.
     */
    fun addTorrent(token: String, link: String): String? {
        val body = JSONObject().put("link", link).toString()
        return try {
            val (code, resp) = request("POST", "/api/torrents", body, token)
            if (code in 200..299) null else errorOf(resp, code)
        } catch (e: Exception) {
            e.message ?: "network error"
        }
    }


    /** Добавление с сохранением в базу или только для воспроизведения. Возвращает (hash, ошибка). */
    fun addTorrentEx(
        token: String,
        link: String,
        saveToDb: Boolean,
        title: String = "",
        poster: String = "",
        category: String = "",
    ): Pair<String?, String?> {
        val body = JSONObject()
            .put("link", link)
            .put("save_to_db", saveToDb)
            .apply {
                if (title.isNotBlank()) put("title", title)
                if (poster.isNotBlank()) put("poster", poster)
                if (category.isNotBlank()) put("category", category)
            }
            .toString()
        return try {
            val (code, resp) = request("POST", "/api/torrents", body, token)
            if (code in 200..299) {
                val hash = JSONObject(resp).optString("hash").ifBlank { null }
                hash to null
            } else {
                null to errorOf(resp, code)
            }
        } catch (e: Exception) {
            null to (e.message ?: "network error")
        }
    }

    /**
     * URL потока файла для внешнего плеера. Токен передаётся в query (плееры не умеют заголовки).
     */
    fun streamUrl(token: String, hash: String, fileId: Int): String {
        val t = URLEncoder.encode(token, "UTF-8")
        return "$baseUrl/api/stream/$hash/$fileId?token=$t"
    }

    /** Все сохранённые торренты удалить нельзя без прав; здесь только разбор карточки. */
    private fun parseItem(o: JSONObject) = TorrentItem(
        title = o.optString("title").ifBlank { o.optString("name") },
        hash = o.optString("hash"),
        statString = o.optString("stat_string"),
        sizeBytes = o.optLong("torrent_size"),
        peers = o.optInt("active_peers"),
        poster = o.optString("poster"),
        stat = o.optInt("stat"),
        seeders = o.optInt("connected_seeders"),
        downloadSpeed = o.optDouble("download_speed", 0.0),
        category = o.optString("category"),
        timestamp = o.optLong("timestamp"),
        torrs = o.optString("torrs"),
    )

    private fun errorOf(resp: String, code: Int): String =
        runCatching { JSONObject(resp).optString("error") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "HTTP $code"

    /** Меню плагинов с учётом прав пользователя (сервер сам фильтрует по rank). */
    fun uiMenu(token: String): List<MenuItem> {
        val (code, resp) = request("GET", "/api/ui/menu", null, token)
        if (code != 200) return emptyList()
        val arr = JSONObject(resp).optJSONArray("menu") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MenuItem(
                pluginId = o.optString("plugin_id"),
                pluginName = o.optString("plugin_name"),
                title = o.optString("title"),
                href = o.optString("href"),
                icon = o.optString("icon"),
                rank = o.optInt("rank"),
            )
        }
    }

    /**
     * Поток изменений библиотеки (SSE): сервер присылает только то, что изменилось.
     * sync - полный список, card - одна карточка, remove - торрент исчез.
     * Блокирующий вызов: держать в фоновом потоке, прерывать через [isActive] = false.
     */
    fun events(
        token: String,
        isActive: () -> Boolean,
        onSync: (List<TorrentItem>) -> Unit,
        onCard: (TorrentItem) -> Unit,
        onRemove: (String) -> Unit,
    ) {
        while (isActive()) {
            try {
                val conn = (URL("$baseUrl/api/events").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 0 // поток живёт, пока его не закроют
                    setRequestProperty("Accept", "text/event-stream")
                    if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                try {
                    conn.inputStream.bufferedReader().use { reader ->
                        var event = ""
                        val data = StringBuilder()
                        while (isActive()) {
                            val line = reader.readLine() ?: break
                            when {
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) dispatch(event, data.toString(), onSync, onCard, onRemove)
                                    event = ""
                                    data.clear()
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // сервер перезапустился/сеть пропала - переподключаемся ниже
            }
            if (!isActive()) return
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun dispatch(
        event: String,
        data: String,
        onSync: (List<TorrentItem>) -> Unit,
        onCard: (TorrentItem) -> Unit,
        onRemove: (String) -> Unit,
    ) {
        try {
            when (event) {
                "sync" -> {
                    val arr = JSONArray(data)
                    onSync((0 until arr.length()).map { parseCard(arr.getJSONObject(it)) })
                }
                "card" -> onCard(parseCard(JSONObject(data)))
                "remove" -> onRemove(JSONObject(data).optString("hash"))
            }
        } catch (e: Exception) {
            // некорректное событие пропускаем
        }
    }

    /** Карточка библиотеки из SSE (LibraryCard на сервере). */
    private fun parseCard(o: JSONObject) = TorrentItem(
        title = o.optString("title"),
        hash = o.optString("hash"),
        statString = "",
        sizeBytes = o.optLong("size"),
        peers = o.optInt("peers"),
        poster = o.optString("poster"),
        stat = o.optInt("stat"),
        seeders = o.optInt("seeders"),
        downloadSpeed = o.optDouble("download_speed", 0.0),
        category = o.optString("category"),
        timestamp = o.optLong("timestamp"),
        torrs = o.optString("torrs"),
    )

    data class MenuItem(
        val pluginId: String,
        val pluginName: String,
        val title: String,
        val href: String,
        val icon: String,
        val rank: Int,
    )

    private fun request(method: String, path: String, body: String?, token: String?): Pair<Int, String> {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    data class TorrentItem(
        val title: String,
        val hash: String,
        val statString: String,
        val sizeBytes: Long,
        val peers: Int,
        val poster: String,
        val stat: Int,
        val seeders: Int = 0,
        val downloadSpeed: Double = 0.0,
        val category: String = "",
        val timestamp: Long = 0,
        val torrs: String = "",
    )

    data class TorrentDetails(
        val hash: String,
        val title: String,
        val statString: String,
        val stat: Int,
        val poster: String,
        val category: String,
        val sizeBytes: Long,
        val downloadSpeed: Double,
        val peers: Int,
        val seeders: Int,
        val preloadedBytes: Long,
        val files: List<TorrentFile>,
    )

    data class TorrentFile(val id: Int, val name: String, val length: Long, val viewed: Boolean)

    sealed class LoginResult {
        data class Ok(val token: String) : LoginResult()
        object WrongPassword : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    companion object {
        private const val TIMEOUT_MS = 8000
    }
}
