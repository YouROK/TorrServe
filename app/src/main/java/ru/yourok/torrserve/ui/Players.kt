package ru.yourok.torrserve.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs
import java.util.Locale

/**
 * Плееры для воспроизведения потока - как в старой версии клиента:
 * список установленных приложений, плеер по умолчанию и настройки на каждый плеер.
 */
object Players {

    data class App(val pkg: String, val label: String, val icon: Drawable?)

    /** Пакеты, которые умеют открывать видео, но неудобны как плеер (проводники, галереи). */
    private val BLACKLIST = hashSetOf(
        "com.android.gallery3d",
        "com.android.tv.frameworkpackagestubs",
        "com.google.android.tv.frameworkpackagestubs",
        "com.estrongs.android.pop",
        "com.ghisler.android.totalcommander",
        "com.mixplorer.silver",
        "com.mixplorer",
        "com.opera.browser",
        "com.android.chrome",
        "nextapp.fx",
        "pl.solidexplorer2",
        "org.droidtv.contentexplorer",
        "com.droidlogic.imageplayer",
    )

    /** MIME по расширению файла: часть плееров от него зависит. */
    fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "aac", "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "video/*"
    }

    /** Установленные плееры, которые могут открыть такой поток. */
    fun installed(context: Context, url: String, mime: String): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime)
        val resolved = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }
        return resolved
            .filter { !BLACKLIST.contains(it.activityInfo.packageName.lowercase(Locale.ROOT)) }
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun label(context: Context, pkg: String): String {
        if (pkg.isBlank()) return context.getString(R.string.player_ask)
        return try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /**
     * Открывает поток. [pkg] - null/пусто: выбрать плеер по умолчанию из настроек,
     * а если он не задан - спросить пользователя.
     */
    fun play(
        context: Context,
        url: String,
        mime: String,
        title: String,
        poster: String,
        pkg: String? = null,
        forceAsk: Boolean = false,
        playlist: List<Pair<String, String>>? = null,
        startIndex: Int = 0,
    ) {
        val prefs = Prefs(context)
        val target = pkg ?: if (!forceAsk) prefs.playerPkg else ""
        if (target.isBlank()) {
            PlayersDialog.show(context, url, mime, title, poster, playlist = playlist, startIndex = startIndex)
            return
        }
        launch(context, url, mime, title, poster, target, playlist, startIndex)
    }

    /** Запуск конкретного плеера с учётом его персональных настроек. */
    fun launch(
        context: Context,
        url: String,
        mime: String,
        title: String,
        poster: String,
        pkg: String,
        playlist: List<Pair<String, String>>? = null,
        startIndex: Int = 0,
    ) {
        val prefs = Prefs(context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(pkg)
        }
        // MX Player открывается только через свой экран
        if (pkg.contains("com.mxtech.videoplayer")) {
            intent.component = ComponentName(pkg, "$pkg.ActivityScreen")
        }
        if (prefs.playerSendsTitle(pkg) && title.isNotBlank()) intent.putExtra("title", title)
        if (prefs.playerSendsPoster(pkg) && poster.isNotBlank()) intent.putExtra("poster", poster)

        // Vimu: передаём весь список файлов раздачи (как в старой версии клиента)
        if (pkg.contains("gtvbox") && !playlist.isNullOrEmpty() && playlist.size > 1) {
            intent.setDataAndType(Uri.parse(url), "application/vnd.gtvbox.filelist")
            intent.putStringArrayListExtra("asusfilelist", ArrayList(playlist.map { it.first }))
            intent.putStringArrayListExtra("asusnamelist", ArrayList(playlist.map { it.second }))
            intent.putExtra("startindex", startIndex)
            intent.putExtra("forcename", title)
            intent.putExtra("forcedirect", true)
            intent.putExtra("forceresume", true)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.torrent_no_player, Toast.LENGTH_LONG).show()
        }
    }
}
