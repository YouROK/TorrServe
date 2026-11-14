package ru.yourok.torrserve.server.local

import android.content.Context
import android.os.Build
import ru.yourok.torrserve.server.api.GithubReleases
import ru.yourok.torrserve.server.api.ReleaseFeed
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивание бинарника сервера Silo с GitHub Releases проекта YouROK/TorrServer.
 *
 * В релизах репозитория лежат и клиентские сборки MatriX / Silo.*.Client, и серверные
 * Silo.*. Нам нужны только теги `Silo.*` без суффикса `.Client` — берём последний
 * по номеру версии и качаем ассет под архитектуру устройства
 * (`TorrServer-android-<arch>`). Файл кладётся в filesDir как `silo`.
 */
object ServerDownloader {

    /** Суффикс файла по архитектуре устройства. */
    fun archSuffix(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm7"
            "x86_64" -> "amd64"
            "x86" -> "386"
            else -> "arm7"
        }
    }

    /** Имя файла в релизе для текущей архитектуры. */
    fun remoteName(): String = "TorrServer-android-${archSuffix()}"

    /** true, если версия [candidate] новее установленной [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean =
        GithubReleases.isNewer(candidate, installed)

    /** Релиз сервера (не клиент). */
    private fun isServer(release: GithubReleases.Release): Boolean =
        release.tag.startsWith(GithubReleases.TAG_PREFIX) && !release.isClient

    /** Последний релиз сервера Silo (MatriX.* и *.Client игнорируются). */
    fun latest(): GithubReleases.Release {
        val releases = GithubReleases.all().filter(::isServer)
        return releases.maxWithOrNull { a, b ->
            GithubReleases.compare(
                GithubReleases.versionOf(a.tag) ?: emptyList(),
                GithubReleases.versionOf(b.tag) ?: emptyList(),
            )
        } ?: throw IllegalStateException("на GitHub нет релизов Silo")
    }

    /**
     * Скачивает бинарник последнего релиза Silo в filesDir/silo, ставит права на исполнение.
     * [onProgress] получает проценты 0..100. Возвращает скачанный релиз.
     */
    fun download(context: Context, onProgress: (Int) -> Unit = {}): GithubReleases.Release {
        // Основной источник версии и ссылки - JSON автора (не кешируется провайдером),
        // запасной - лента GitHub
        val feed = ReleaseFeed.server()
        val release = latest()
        val tag = feed?.version ?: release.tag
        val url = feed?.links?.get("android-${archSuffix()}")
            ?: release.assets[remoteName()]
            ?: "https://github.com/${GithubReleases.REPO}/releases/download/$tag/${remoteName()}"
        val target = File(context.filesDir, ServerFile.BIN_NAME)
        val tmp = File(context.filesDir, "${ServerFile.BIN_NAME}.part")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("cannot move downloaded file")
            if (!target.setExecutable(true, true)) throw IllegalStateException("cannot set exec permission")
            onProgress(100)
            return feed?.let { GithubReleases.Release(it.version, "", emptyMap()) } ?: release
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
