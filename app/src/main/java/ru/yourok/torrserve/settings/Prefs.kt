package ru.yourok.torrserve.settings

import android.content.Context

/** Вошедший на устройстве пользователь. */
data class Session(val id: String, val username: String, val token: String)

/**
 * Настройки клиента (обычные SharedPreferences, без шифрования - по решению владельца).
 * Сессии хранятся отдельно для каждого адреса сервера: у локального и удалённого серверов
 * свои токены, поэтому при переключении сервера вход выполняется заново.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Адрес сервера Silo, к которому подключаемся. Пусто - ещё не настроено. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    /** true - сервер локальный (127.0.0.1), им управляет TorrService. */
    val isLocalServer: Boolean
        get() = serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost")


    /** true - режим многопользовательский (у owner задан пароль). */
    var multiUser: Boolean
        get() = prefs.getBoolean(KEY_MULTI_USER, false)
        set(value) = prefs.edit().putBoolean(KEY_MULTI_USER, value).apply()

    /**
     * Пароль owner, который записывается в config.yaml локального сервера (auth.owner_password).
     * Пусто - одиночный режим, сервер открыт.
     */
    var ownerPassword: String
        get() = prefs.getString(KEY_OWNER_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OWNER_PASSWORD, value).apply()

    /** Токен owner, полученный при входе (для совместимости с первым запуском). */
    var ownerToken: String
        get() = prefs.getString(KEY_OWNER_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OWNER_TOKEN, value).apply()

    /** Ключи сессий привязаны к адресу сервера. */
    private fun sessionsKey(): String = "$KEY_SESSIONS|$serverUrl"
    private fun activeUserKey(): String = "$KEY_ACTIVE_USER|$serverUrl"

    /** Все пользователи, вошедшие на этом устройстве для текущего сервера. */
    fun getSessions(): List<Session> {
        val raw = prefs.getString(sessionsKey(), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Session(o.getString("id"), o.getString("username"), o.getString("token"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Сохраняет или обновляет сессию пользователя (по id). */
    fun saveSession(session: Session) {
        val list = getSessions().filterNot { it.id == session.id } + session
        writeSessions(list)
    }

    /** Удаляет сессию пользователя (выход). Если она была активной - сбрасываем активного. */
    fun removeSession(id: String) {
        writeSessions(getSessions().filterNot { it.id == id })
        if (activeUserId == id) activeUserId = ""
    }

    private fun writeSessions(list: List<Session>) {
        val arr = org.json.JSONArray()
        list.forEach { s ->
            arr.put(org.json.JSONObject()
                .put("id", s.id).put("username", s.username).put("token", s.token))
        }
        prefs.edit().putString(sessionsKey(), arr.toString()).apply()
    }

    /** id пользователя, которому идут операции по умолчанию (magnet и т.п.). Пусто - выбрать. */
    var activeUserId: String
        get() = prefs.getString(activeUserKey(), "") ?: ""
        set(value) = prefs.edit().putString(activeUserKey(), value).apply()

    /** Активная сессия или null. */
    fun activeSession(): Session? {
        val list = getSessions()
        return list.firstOrNull { it.id == activeUserId } ?: list.firstOrNull()?.also {
            activeUserId = it.id
        }
    }

    /**
     * Токен активной сессии для текущего сервера.
     * Пустая строка - сервер без пароля (одиночный режим): запросы идут без авторизации.
     */
    val activeToken: String
        get() = activeSession()?.token ?: ""


    /** Плеер по умолчанию (пусто - спрашивать каждый раз). */
    var playerPkg: String
        get() = prefs.getString(KEY_PLAYER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER, value).apply()

    /** Передавать ли выбранному плееру название раздачи. */
    fun playerSendsTitle(pkg: String): Boolean =
        prefs.getBoolean("player_title|$pkg", true)

    fun setPlayerSendsTitle(pkg: String, value: Boolean) =
        prefs.edit().putBoolean("player_title|$pkg", value).apply()

    /** Передавать ли выбранному плееру постер. */
    fun playerSendsPoster(pkg: String): Boolean =
        prefs.getBoolean("player_poster|$pkg", false)

    fun setPlayerSendsPoster(pkg: String, value: Boolean) =
        prefs.edit().putBoolean("player_poster|$pkg", value).apply()


    /** Сортировка списка торрентов: 0 - дата, 1 - название, 2 - размер, 3 - скорость. */
    var sortMode: Int
        get() = prefs.getInt(KEY_SORT, 0)
        set(value) = prefs.edit().putInt(KEY_SORT, value).apply()


    /** Запомненное действие для magnet-ссылок: 0 - спрашивать, 1 - добавить, 2 - проиграть, 3 - добавить и проиграть. */
    var magnetAction: Int
        get() = prefs.getInt(KEY_MAGNET_ACTION, 0)
        set(value) = prefs.edit().putInt(KEY_MAGNET_ACTION, value).apply()

    /** Использовать запомненное действие без окна. */
    var magnetRemember: Boolean
        get() = prefs.getBoolean(KEY_MAGNET_REMEMBER, false)
        set(value) = prefs.edit().putBoolean(KEY_MAGNET_REMEMBER, value).apply()


    /** Последний открытый файл раздачи (id), чтобы возвращать на него фокус. */
    fun lastFile(hash: String): Int = prefs.getInt("last_file|$hash", -1)

    fun setLastFile(hash: String, fileId: Int) =
        prefs.edit().putInt("last_file|$hash", fileId).apply()

    /** Онбординг пройден (выбран режим работы). */
    val onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun markOnboardingDone() = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    companion object {
        private const val NAME = "silo_client"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_MULTI_USER = "multi_user"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_OWNER_PASSWORD = "owner_password"
        private const val KEY_OWNER_TOKEN = "owner_token"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_ACTIVE_USER = "active_user"
        private const val KEY_PLAYER = "player_pkg"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_MAGNET_ACTION = "magnet_action"
        private const val KEY_MAGNET_REMEMBER = "magnet_remember"

        /** Адрес локального сервера по умолчанию. */
        const val LOCAL_URL = "http://127.0.0.1:8090"
    }
}
