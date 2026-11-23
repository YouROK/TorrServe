package ru.yourok.torrserve.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import ru.yourok.torrserve.server.api.GithubReleases
import ru.yourok.torrserve.settings.Prefs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Обновление самого приложения: всё берётся из релизов github.com/YouROK/TorrServe.
 *
 * Клиентские релизы помечаются тегом `Silo.N.Client`, в релизе лежат
 * `TorrServe_Silo.N.Client.apk` и `SHA256SUMS.txt`. Список релизов и текст
 * «что нового» приходят из Atom-ленты, ссылки на файлы строятся по имени ассета.
 *
 * Проверка кешируется: автоматическая - не чаще раза в 6 часов
 * (`Prefs.updateCheckedAt`), ручная из настроек - принудительно, но не чаще раза в минуту.
 */
object AppUpdater {

    private const val TAG = "SiloUpd"

    /** Автоматическая проверка не чаще раза в 6 часов. */
    const val AUTO_TTL_MS = 6 * 60 * 60 * 1000L

    /** Защита от частых нажатий «Проверить обновление»: в пределах минуты - из кеша. */
    private const val MANUAL_FLOOR_MS = 60 * 1000L

    /** Найденный новый релиз клиента. */
    data class Update(
        val tag: String,
        val notesHtml: String,
        val apkUrl: String,
        val shaUrl: String,
        val pageUrl: String,
    )

    /** Результат проверки (для авто-проверки и кнопки в настройках). */
    sealed class Result {
        data class Available(val update: Update) : Result()
        object UpToDate : Result()
        object Throttled : Result()
        data class Failed(val error: String) : Result()
    }

    /** Обновление отменено пользователем (закрыл диалог во время скачивания). */
    class Cancelled : Exception("cancelled")

    /** Версия установленного приложения, например "Silo.2.Client". */
    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Последний релиз клиента или null, если клиентских релизов нет. */
    fun latest(force: Boolean = false): GithubReleases.Release? =
        GithubReleases.latestClient(force)

    fun apkUrl(tag: String): String =
        GithubReleases.downloadUrl(GithubReleases.Repo.CLIENT, tag, "TorrServe_$tag.apk")

    fun shaUrl(tag: String): String =
        GithubReleases.downloadUrl(GithubReleases.Repo.CLIENT, tag, "SHA256SUMS.txt")

    fun updateOf(tag: String, notesHtml: String = "", pageUrl: String = ""): Update = Update(
        tag = tag,
        notesHtml = notesHtml,
        apkUrl = apkUrl(tag),
        shaUrl = shaUrl(tag),
        pageUrl = pageUrl.ifBlank { "https://github.com/${GithubReleases.CLIENT_REPO}/releases/tag/$tag" },
    )

    /**
     * Проверка новой версии с кешем: [force] - кнопка «Проверить обновление».
     * Ошибки сети не сбрасывают отметку времени, чтобы не дёргать GitHub на каждом запуске.
     */
    fun check(context: Context, force: Boolean = false): Result {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val last = prefs.updateCheckedAt
        val current = installedVersion(context)

        if (!force && now - last in 0 until AUTO_TTL_MS) return Result.Throttled
        if (force && now - last in 0 until MANUAL_FLOOR_MS) {
            // Проверяли только что: отдаём то, что уже знаем, без запроса
            val tag = prefs.appUpdateLatestTag
            return if (tag.isNotBlank() && GithubReleases.isNewer(tag, current)) {
                Result.Available(updateOf(tag))
            } else {
                Result.UpToDate
            }
        }

        return try {
            // Ручная проверка перечитывает и Atom-ленту: описание релиза могли дописать
            val release = latest(force)
            prefs.updateCheckedAt = now
            if (release == null || !GithubReleases.isNewer(release.tag, current)) {
                prefs.appUpdateLatestTag = release?.tag ?: ""
                Result.UpToDate
            } else {
                prefs.appUpdateLatestTag = release.tag
                Result.Available(
                    updateOf(release.tag, release.notesHtml, release.pageUrl)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "app update check failed", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Скачивает APK релиза в cacheDir/updates и сверяет SHA-256 с SHA256SUMS.txt релиза.
     * [isCancelled] проверяется по ходу загрузки; [onProgress] получает проценты 0..100.
     */
    fun download(
        context: Context,
        update: Update,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "TorrServe_${update.tag}.apk")
        val tmp = File(dir, target.name + ".part")

        // Уже качали (например, не успели выдать разрешение на установку) - сверяем и отдаём
        if (target.exists() && target.length() > 0) {
            if (verify(target, update.shaUrl)) {
                onProgress(100)
                return target
            }
            target.delete()
        }

        val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (isCancelled()) throw Cancelled()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            onProgress(100)
            if (isCancelled()) throw Cancelled()
            if (!verify(tmp, update.shaUrl)) {
                throw IllegalStateException("SHA-256 mismatch")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("cannot move downloaded file")
            return target
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * Запускает системный установщик APK через FileProvider.
     * false - установщика нет или не выдано разрешение на установку из этого источника.
     */
    fun install(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.w(TAG, "no package installer")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // Разрешение «Установка неизвестных приложений» для нашего пакета
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return false
        }
        context.startActivity(intent)
        return true
    }

    /** SHA-256 файла совпадает с релизным SHA256SUMS.txt (или файла нет - не проверяем). */
    private fun verify(file: File, shaUrl: String): Boolean {
        val expected = expectedSha(shaUrl) ?: run {
            Log.w(TAG, "no SHA256SUMS in release, skip verify")
            return true
        }
        val actual = sha256(file)
        val ok = actual.equals(expected, ignoreCase = true)
        if (!ok) Log.e(TAG, "sha mismatch: expected $expected, got $actual")
        return ok
    }

    private fun expectedSha(shaUrl: String): String? = try {
        val conn = (URL(shaUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) {
                null
            } else {
                // Формат sha256sum: "<hash>  TorrServe_Silo.N.Client.apk"
                conn.inputStream.bufferedReader().use { it.readText() }
                    .lineSequence()
                    .firstOrNull { it.trim().isNotEmpty() }
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.takeIf { it.length == 64 }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "SHA256SUMS unavailable: ${e.message}")
        null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
