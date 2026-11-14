package ru.yourok.torrserve.ui

import android.content.Context
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session

/**
 * Вход в локальный сервер.
 *
 * Локальный сервер, запущенный из приложения, требует пароль владельца
 * (`auth.owner_password`). Пароль хранится в настройках клиента, поэтому клиент
 * может войти сам, без окна логина. Если пароль пустой - сервер без пароля.
 */
object LocalAuth {

    /** Возвращает токен для работы с текущим сервером (может быть пустым). */
    fun ensureSession(context: Context, prefs: Prefs): String {
        val existing = prefs.activeSession()
        if (existing != null) return existing.token
        if (!prefs.isLocalServer) return ""
        val password = prefs.ownerPassword
        // Сервер без пароля: токен не нужен
        if (password.isEmpty()) return ""
        return when (val result = SiloApi(prefs.serverUrl).login("owner", password)) {
            is SiloApi.LoginResult.Ok -> {
                prefs.saveSession(Session(id = OWNER_ID, username = OWNER_ID, token = result.token))
                prefs.activeUserId = OWNER_ID
                prefs.ownerToken = result.token
                result.token
            }
            else -> ""
        }
    }

    /** Перезапуск локального сервера после смены пароля владельца (только сервер, не приложение). */
    fun restartService(context: Context) {
        ru.yourok.torrserve.server.local.TorrService.restart(context)
    }

    const val OWNER_ID = "owner"
}
