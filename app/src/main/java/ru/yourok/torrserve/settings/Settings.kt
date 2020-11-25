package ru.yourok.torrserve.settings

import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.core.remove
import androidx.datastore.preferences.createDataStore
import kotlinx.coroutines.flow.map
import ru.yourok.torrserve.app.App
import java.io.File

object Settings {
    private val dataStore: DataStore<Preferences> = App.context.createDataStore("settings")

    private val CHOOSER_ACTION = preferencesKey<Int>("chooser_action")
    private val BOOTSTART = preferencesKey<Boolean>("boot_start")
    private val ROOT = preferencesKey<Boolean>("root")
    private val HOST = preferencesKey<String>("host")

    fun getChooserAction(): Int {
        return get(CHOOSER_ACTION, 0)
    }

    suspend fun setChooserAction(v: Int) {
        set(CHOOSER_ACTION, v)
    }

    fun isBootStart(): Boolean {
        return get(BOOTSTART, false)
    }

    suspend fun setBootStart(v: Boolean) {
        set(BOOTSTART, v)
    }

    fun isRootStart(): Boolean {
        return get(ROOT, false)
    }

    suspend fun setRootStart(v: Boolean) {
        set(ROOT, v)
    }

    fun getHost(): String {
        return "http://10.0.0.10:8090"
//        return "http://192.168.43.46:8090"
//        return get(HOST, "http://127.0.0.1:8090")
    }

    suspend fun setHost(host: String) {
        set(HOST, host)
    }

    /////////////////////////////////////////////////////////
    fun getTorrPath(): String {
        val state = Environment.getExternalStorageState()
        var filesDir: File?
        if (Environment.MEDIA_MOUNTED == state)
            filesDir = App.context.getExternalFilesDir(null)
        else
            filesDir = App.context.filesDir

        if (filesDir == null)
            filesDir = App.context.cacheDir

        if (filesDir == null)
            filesDir = File("/sdcard/Torrserve")

        if (!filesDir.exists())
            filesDir.mkdirs()
        return filesDir.path
    }
    /////////////////////////////////////////////////////////

    private fun <T> get(name: Preferences.Key<T>, def: T): T {
        var v: T? = null
        dataStore.data.map { preferences ->
            v = preferences[name] ?: def
        }
        return v ?: def
    }

    private suspend fun <T> set(name: Preferences.Key<T>, value: T?) {
        if (value == null)
            dataStore.edit { preferences ->
                preferences.remove(name)
            }
        else
            dataStore.edit { preferences ->
                preferences[name] = value
            }
    }
}
