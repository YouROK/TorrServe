package ru.yourok.torrserve.server.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Версии релизов из JSON у автора (старая схема releases1).
 *
 * На некоторых ТВ-сетях github.com/api.github.com отдают устаревший ответ из кеша
 * провайдера, а raw.githubusercontent.com — нет. Поэтому основной источник версий —
 * эти файлы, а GitHub-лента остаётся запасным вариантом.
 *
 * server_release.json:
 *   { "version": "Silo.2", "links": { "android-arm7": "https://...", ... } }
 *
 * apk_release.json:
 *   [ { "version": "Silo.1.Client", "versionInt": 1, "link": "https://...apk", "desc": "..." } ]
 */
object ReleaseFeed {

    private const val BASE = "https://raw.githubusercontent.com/yourok-0001/releases1/master/torr"

    data class Server(val version: String, val links: Map<String, String>)

    data class App(val version: String, val versionInt: Int, val link: String, val desc: String)

    /** Последний релиз сервера Silo или null, если файла нет/там не Silo. */
    fun server(): Server? = try {
        val json = JSONObject(request("$BASE/server_release.json"))
        val version = json.optString("version")
        if (!version.startsWith(GithubReleases.TAG_PREFIX)) {
            null
        } else {
            val links = HashMap<String, String>()
            val obj = json.optJSONObject("links")
            if (obj != null) {
                obj.keys().forEach { key -> links[key] = obj.optString(key) }
            }
            Server(version, links)
        }
    } catch (e: Exception) {
        null
    }

    /** Последний релиз клиента Silo.*.Client или null. */
    fun app(): App? = try {
        val array = JSONArray(request("$BASE/apk_release.json"))
        val items = ArrayList<App>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val version = o.optString("version")
            if (!version.startsWith(GithubReleases.TAG_PREFIX)) continue
            items += App(
                version = version,
                versionInt = o.optInt("versionInt", 0),
                link = o.optString("link"),
                desc = o.optString("desc"),
            )
        }
        items.maxWithOrNull { a, b ->
            if (a.versionInt != b.versionInt) a.versionInt.compareTo(b.versionInt)
            else GithubReleases.compare(
                GithubReleases.versionOf(a.version) ?: emptyList(),
                GithubReleases.versionOf(b.version) ?: emptyList(),
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun request(url: String): String {
        // Метка времени: на стороне провайдера бывает кеш
        val full = url + (if (url.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis()
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
