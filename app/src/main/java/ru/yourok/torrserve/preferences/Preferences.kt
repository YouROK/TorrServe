package ru.yourok.torrserve.preferences

import android.preference.PreferenceManager
import ru.yourok.torrserve.app.App

/**
 * Created by yourok on 20.02.18.
 */


object Preferences {

    fun isExecRootServer(): Boolean {
        return get("ExecRootServer", false) as Boolean
    }

    fun execRootServer(v: Boolean) {
        set("ExecRootServer", v)
    }

    fun isDisableAD(): Boolean {
        return get("DisableAD", false) as Boolean
    }

    fun disableAD(v: Boolean) {
        set("DisableAD", v)
    }

    fun getPlayer(): String {
        return get("Player", "1") as String
    }

    fun setPlayer(pkg: String) {
        set("Player", pkg)
    }

    fun isAutoStart(): Boolean {
        return get("AutoStart", false) as Boolean
    }

    fun setAutoStart(v: Boolean) {
        set("AutoStart", v)
    }

    fun getCurrentHost(): String {
        return get("CurrentHost", "http://localhost:8090") as String
    }

    fun setCurrentHost(addr: String) {
        set("CurrentHost", addr)
    }

    fun getHosts(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
        val ret = prefs.getStringSet("AutoCompleteHost", mutableSetOf())
        if (ret.isEmpty())
            ret.add("http://localhost:8090")
        return ret.toList()
    }

    fun setHosts(hosts: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
        prefs.edit().putStringSet("AutoCompleteHost", hosts.toMutableSet()).apply()
    }

    fun getLastViewDonate(): Long {
        return get("LastViewDonate", 0L) as Long
    }

    fun setLastViewDonate(l: Long) {
        set("LastViewDonate", l)
    }

    private fun get(name: String, def: Any): Any? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
        if (prefs.all.containsKey(name))
            return prefs.all[name]
        return def
    }

    private fun set(name: String, value: Any?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
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