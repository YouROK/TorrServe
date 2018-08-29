package ru.yourok.torrserve.server.api

import org.json.JSONArray
import org.json.JSONObject

class JSObject(val js: JSONObject) {
    constructor(js: String) : this(JSONObject(js))
    constructor() : this(JSONObject())

    fun <T> set(name: String, v: T) {
        js.put(name, v)
    }

    fun <T> get(name: String, def: T?): T {
        return try {
            when (def) {
                is String -> js.getString(name) as T
                is Boolean -> js.getBoolean(name) as T
                is Int -> js.getInt(name) as T
                is Long -> js.getLong(name) as T
                is Double -> js.getDouble(name) as T
                is JSONArray -> js.getJSONArray(name) as T
                else -> JSObject(js.getJSONObject(name)) as T
            }
        } catch (e: Exception) {
            if (def == null)
                JSObject(JSONObject()) as T
            else
                def
        }
    }

    fun getString(name: String, def: String): String {
        return try {
            js.getString(name) ?: def
        } catch (e: Exception) {
            def
        }
    }

    fun getBoolean(name: String, def: Boolean): Boolean {
        return try {
            js.getBoolean(name)
        } catch (e: Exception) {
            def
        }
    }

    fun getInt(name: String, def: Int): Int {
        return try {
            js.getInt(name)
        } catch (e: Exception) {
            def
        }
    }

    fun getLong(name: String, def: Long): Long {
        try {
            return js.getLong(name)
        } catch (e: Exception) {
            return def
        }
    }

    fun getDouble(name: String, def: Double): Double {
        return try {
            js.getDouble(name)
        } catch (e: Exception) {
            def
        }
    }

    fun getObject(name: String): JSObject? {
        return try {
            JSObject(js.getJSONObject(name))
        } catch (e: Exception) {
            null
        }
    }

    override fun toString(): String {
        return js.toString(0)
    }
}