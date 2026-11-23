package ru.yourok.torrserve.server.api

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Релизы GitHub через Atom-ленту `<repo>/releases.atom`.
 *
 * API `api.github.com` не используется: на части ТВ-сетей провайдер отдаёт из кеша
 * устаревший список релизов, а без токена у API лимит 60 запросов в час на IP.
 * Atom-лента отдаёт актуальные теги и текст релиза и не имеет такого лимита.
 *
 * Клиент и сервер выпускаются в разных репозиториях:
 *  - клиент: github.com/YouROK/TorrServe, теги `Silo.N.Client`, ассет `TorrServe_Silo.N.Client.apk`;
 *  - сервер: github.com/YouROK/TorrServer, теги `Silo.N`, ассет `TorrServer-android-<arch>`.
 *
 * Имена ассетов известны заранее, поэтому ссылки строятся без запроса списка ассетов.
 */
object GithubReleases {

    const val CLIENT_REPO = "YouROK/TorrServe"
    const val SERVER_REPO = "YouROK/TorrServer"
    const val TAG_PREFIX = "Silo."
    const val CLIENT_SUFFIX = ".Client"

    /** Репозиторий, из которого берутся релизы. */
    enum class Repo(val slug: String) {
        CLIENT(CLIENT_REPO),
        SERVER(SERVER_REPO),
    }

    data class Release(
        val tag: String,
        val pageUrl: String,
        val notesHtml: String,
        val publishedAt: Long,
    ) {
        /** true, если это релиз нашего клиента (Silo.N.Client), а не сервера. */
        val isClient: Boolean
            get() = tag.startsWith(TAG_PREFIX) && tag.endsWith(CLIENT_SUFFIX)
    }

    private const val TAG = "SiloUpd"
    private const val TTL_MS = 15 * 60 * 1000L

    /** Базовый адрес релизов; в тестовых сборках подменяется на локальный сервер. */
    private const val BASE = "https://github.com"

    /** Кеш на репозиторий: время получения и список релизов. */
    private val cache = HashMap<Repo, Pair<Long, List<Release>>>()

    fun atomUrl(repo: Repo): String = "$BASE/${repo.slug}/releases.atom"

    /** Прямая ссылка на файл релиза: имя ассета в релизе известно заранее. */
    fun downloadUrl(repo: Repo, tag: String, asset: String): String =
        "$BASE/${repo.slug}/releases/download/$tag/$asset"

    /** Релизы репозитория (из кеша, если он свежий). [force] - обойти кеш в памяти. */
    @Synchronized
    fun releases(repo: Repo, force: Boolean = false): List<Release> {
        val now = System.currentTimeMillis()
        val cached = cache[repo]
        if (!force && cached != null && now - cached.first in 0 until TTL_MS) return cached.second
        val list = parseAtom(repo, request(atomUrl(repo)))
        cache[repo] = now to list
        return list
    }

    /** Последний релиз клиента или null, если клиентских релизов нет. */
    fun latestClient(force: Boolean = false): Release? =
        releases(Repo.CLIENT, force).filter { it.isClient }.maxWithOrNull { a, b ->
            compare(versionOf(a.tag) ?: emptyList(), versionOf(b.tag) ?: emptyList())
        }

    /** Последний релиз сервера Silo или null. */
    fun latestServer(force: Boolean = false): Release? =
        releases(Repo.SERVER, force).filter { it.tag.startsWith(TAG_PREFIX) && !it.isClient }
            .maxWithOrNull { a, b ->
                compare(versionOf(a.tag) ?: emptyList(), versionOf(b.tag) ?: emptyList())
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

    private fun request(url: String): String {
        // Метка времени: на стороне провайдера бывает кеш
        val full = url + (if (url.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis()
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/atom+xml, application/xml")
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

    /** Разбор Atom: по записи на релиз (тег, ссылка на страницу, текст, дата). */
    private fun parseAtom(repo: Repo, xml: String): List<Release> {
        val result = ArrayList<Release>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var tag: String? = null
        var page = ""
        var notes = ""
        var published = 0L
        var inEntry = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        tag = null
                        page = ""
                        notes = ""
                        published = 0L
                    }
                    "title" -> if (inEntry) tag = parser.nextText().trim()
                    "content" -> if (inEntry) notes = parser.nextText()
                    "updated" -> if (inEntry) published = parseDate(parser.nextText())
                    "link" -> if (inEntry) {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if ((rel == null || rel == "alternate") && !href.isNullOrBlank()) page = href
                    }
                }
                XmlPullParser.END_TAG -> if (inEntry && parser.name == "entry") {
                    val entryTag = tag
                    if (!entryTag.isNullOrBlank()) {
                        result += Release(
                            tag = entryTag,
                            pageUrl = page.ifBlank { "$BASE/${repo.slug}/releases/tag/$entryTag" },
                            notesHtml = notes,
                            publishedAt = published,
                        )
                    }
                    inEntry = false
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseDate(value: String): Long = try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.isLenient = false
        format.parse(value.trim())?.time ?: 0L
    } catch (e: Exception) {
        Log.w(TAG, "bad date: $value", e)
        0L
    }
}
