package ru.yourok.torrserve.server.local

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Скачивание ffprobe под Android.
 *
 * В старом клиенте (UpdaterServer.downloadFFProbe) брались статические сборки
 * ffbinaries-prebuilt v4.4.1 — они запускаются на Android, но Silo требует ffmpeg >= 6.0,
 * поэтому 4.4.1 он отвергает. Здесь тот же источник (статические сборки johnvansickle),
 * но свежий: ffmpeg-static b6.1.1 (внутри ffprobe 7.0.2-static) в gzip.
 * Сервер ищет `ffprobe` рядом с собой (filesDir), имя фиксировано: ffprobe.
 */
object FfprobeDownloader {

    const val NAME = "ffprobe"

    /** Статические сборки под Linux ARM/ARM64/x86 (внутри ffprobe 7.0.2-static). */
    private const val BASE = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1"

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

    fun url(): String = when (archSuffix()) {
        "arm7" -> "$BASE/ffprobe-linux-arm.gz"
        "arm64" -> "$BASE/ffprobe-linux-arm64.gz"
        "386" -> "$BASE/ffprobe-linux-ia32.gz"
        else -> "$BASE/ffprobe-linux-x64.gz"
    }

    fun file(context: Context): File = File(context.filesDir, NAME)

    fun isInstalled(context: Context): Boolean = file(context).exists()

    /** Строка версии установленного ffprobe или null, если он не запускается. */
    fun version(context: Context): String? = run(file(context), "-version")
        ?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** Первая строка вывода или null, если бинарник не запустился. */
    fun run(binary: File, vararg args: String): String? = try {
        val process = ProcessBuilder(listOf(binary.absolutePath) + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        output.takeIf { output.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    /**
     * Скачивает zip с ffprobe, распаковывает бинарник в filesDir, ставит права
     * на исполнение и проверяет запуском. [onProgress] получает проценты 0..100.
     */
    fun download(context: Context, onProgress: (Int) -> Unit = {}) {
        val target = file(context)
        val zip = File(context.cacheDir, "ffprobe.gz")
        try {
            val conn = (URL(url()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(zip).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (target.exists()) target.delete()
            GZIPInputStream(zip.inputStream().buffered()).use { gz ->
                FileOutputStream(target).use { out -> gz.copyTo(out) }
            }
            if (!target.setExecutable(true, true)) throw IllegalStateException("cannot set exec permission")
            onProgress(100)

            if (version(context) == null) throw IllegalStateException("ffprobe не запускается на этом устройстве")
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            if (zip.exists()) zip.delete()
        }
    }
}
