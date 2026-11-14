package ru.yourok.torrserve.server.api

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Лента релизов GitHub-репозитория YouROK/TorrServer.
 *
 * В репозитории лежат три вида тегов: MatriX.* (старый клиент), Silo.* (сервер)
 * и Silo.*.Client (наш клиент). Список берётся одним запросом и кешируется на 5 минут:
 * у GitHub без токена лимит 60 запросов в час на IP, а проверка выполняется при
 * открытии главного окна.
 */
object GithubReleases {

    const val REPO = "YouROK/TorrServer"
    const val TAG_PREFIX = "Silo."
    const val CLIENT_SUFFIX = ".Client"

    data class Release(val tag: String, val htmlUrl: String, val assets: Map<String, String>) {
        /** true, если это релиз нашего клиента (Silo.N.Client), а не сервера. */
        val isClient: Boolean
            get() = tag.startsWith(TAG_PREFIX) && tag.endsWith(CLIENT_SUFFIX)
    }

    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=100"

    /** Atom-лента релизов: отдаётся github.com, без лимитов API и без кеша api-хоста. */
    private const val ATOM = "https://github.com/$REPO/releases.atom"
    private const val TTL_MS = 5 * 60 * 1000L

    private var cached: List<Release>? = null
    private var cachedAt = 0L

    /** Все релизы репозитория (из кеша, если он свежий). */
    @Synchronized
    fun all(): List<Release> {
        val now = System.currentTimeMillis()
        val fresh = cached
        if (fresh != null && now - cachedAt < TTL_MS) return fresh

        // На ТВ api.github.com может отдавать устаревший ответ (кеш провайдера),
        // поэтому сначала пробуем Atom-ленту github.com, а к API идём только если не вышло
        val fromAtom = try {
            parseAtom(request(ATOM + "?t=" + System.currentTimeMillis()))
        } catch (e: Exception) {
            emptyList()
        }
        if (fromAtom.isNotEmpty()) {
            cached = fromAtom
            cachedAt = now
            return fromAtom
        }

        val body = request(API + (if (API.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis())
        val array = JSONArray(body)
        val result = ArrayList<Release>(array.length())
        for (i in 0 until array.length()) {
            val release = array.getJSONObject(i)
            val assets = HashMap<String, String>()
            val list = release.optJSONArray("assets")
            if (list != null) {
                for (j in 0 until list.length()) {
                    val asset = list.getJSONObject(j)
                    assets[asset.optString("name")] = asset.optString("browser_download_url")
                }
            }
            result += Release(
                tag = release.optString("tag_name"),
                htmlUrl = release.optString("html_url"),
                assets = assets,
            )
        }
        cached = result
        cachedAt = now
        return result
    }

    /** Версия из тега: "Silo.2" -> [2], "Silo.2.1" -> [2, 1], "Silo.1.Client" -> [1]. */
    fun versionOf(tag: String): List<Int>? {
        var text = tag.removePrefix(TAG_PREFIX)
        if (text.endsWith(CLIENT_SUFFIX)) text = text.removeSuffix(CLIENT_SUFFIX)
        return text.split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
            .takeIf { it.isNotEmpty() }
    }

    /** Сравнение версий по сегментам: >0 если [a] новее [b]. */
    fun compare(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** true, если версия [candidate] новее [installed] (обе в виде тегов Silo.*). */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = versionOf(candidate) ?: return false
        val b = versionOf(installed) ?: return false
        return compare(a, b) > 0
    }

    /** Теги из Atom-ленты релизов (ассетов там нет, только тег и ссылка на страницу). */
    private fun parseAtom(xml: String): List<Release> {
        val result = ArrayList<Release>()
        val regex = Regex("href=\"https://github\\.com/$REPO/releases/tag/([^\"]+)\"")
        regex.findAll(xml).forEach { match ->
            val tag = match.groupValues[1]
            if (result.none { it.tag == tag }) {
                result += Release(tag, "https://github.com/$REPO/releases/tag/$tag", emptyMap())
            }
        }
        return result
    }

    private fun request(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("GitHub HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
