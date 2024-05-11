package ru.yourok.torrserve.settings

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import java.io.File

object Settings {
    val showFab: Boolean
        get() = get("show_fab", true)

    val showSortFab: Boolean
        get() = get("show_sort_fab", false)

    val sortTorrByTitle: Boolean
        get() = get("sort_torrents", false)

    fun getServerAuth(): String = get("server_auth", "").trim()
    fun useLocalAuth(): Boolean = get("local_auth", false)
    fun getHosts(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
        val ret = prefs.getStringSet("saved_hosts", mutableSetOf())
        return ret?.toList() ?: emptyList()
    }

    fun setHosts(hosts: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
        prefs.edit().putStringSet("saved_hosts", hosts.toMutableSet()).apply()
    }

    fun getLastViewDonate() = get("last_view_donate", 0L)
    fun setLastViewDonate(v: Long) = set("last_view_donate", v)

    fun getPlayer(): String = get(App.context.getString(R.string.player_pref_key), "")
    fun setPlayer(v: String) = set(App.context.getString(R.string.player_pref_key), v)

    fun getChooserAction(): Int = get("chooser_action", 0)
    fun setChooserAction(v: Int) = set("chooser_action", v)

    fun isAccessibilityOn(): Boolean = get("switch_accessibility", false)
    fun isBootStart(): Boolean = get("boot_start", false)
    fun isRootStart(): Boolean = get("root_start", false)

    fun showBanner(): Boolean = get("show_banner", true)
    fun setShowBanner(v: Boolean) = set("show_banner", v)

    fun showCover(): Boolean = get("show_cover", true)
    fun getTheme(): String = get("theme", "auto")
    fun setTheme(v: String) = set("theme", v)

    fun getHost(): String = get("host", "http://localhost:8090")
    fun setHost(host: String) {
        var hst = host
        if (hst.isEmpty())
            hst = "http://localhost:8090"
        val url = Uri.parse(hst)
        if (url.scheme.isNullOrBlank())
            hst = "http://$hst"
        if (url.port == -1)
            hst = "$hst:8090"

        set("host", hst)
    }

    fun getTorrPath(): String {
        var filesDir: File?
        filesDir = App.context.getExternalFilesDir(null)

        if (filesDir?.canWrite() != true || Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            if (BuildConfig.DEBUG) Log.d("*****", "Can't write to $filesDir or SDK>33")
            filesDir = null
        }

        if (filesDir == null) {
            filesDir = App.context.filesDir
            if (BuildConfig.DEBUG) Log.d("*****", "Use $filesDir for settings path")
        }
        if (filesDir == null)
            filesDir = File(Environment.getExternalStorageDirectory().path, "TorrServe")

        if (!filesDir.exists())
            filesDir.mkdirs()

        return filesDir.path
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String, def: T): T {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
            if (prefs.all.containsKey(name))
                return prefs.all[name] as T
            return def
        } catch (e: Exception) {
            return def
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun set(name: String, value: Any?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
        when (value) {
            is String -> prefs.edit().putString(name, value).apply()
            is Boolean -> prefs.edit().putBoolean(name, value).apply()
            is Float -> prefs.edit().putFloat(name, value).apply()
            is Int -> prefs.edit().putInt(name, value).apply()
            is Long -> prefs.edit().putLong(name, value).apply()
            is MutableSet<*>? -> prefs.edit().putStringSet(name, value as MutableSet<String>?).apply()
            else -> prefs.edit().putString(name, value.toString()).apply()
        }
    }
}
