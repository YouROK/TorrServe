package ru.yourok.torrserve.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session
import kotlin.concurrent.thread

/**
 * Вход пользователя Silo (логин + пароль). Сохраняет сессию на устройстве и делает пользователя активным.
 * Используется для добавления второго и последующих пользователей.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar
    private lateinit var submit: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        prefs = Prefs(this)

        username = findViewById(R.id.login_user)
        password = findViewById(R.id.login_password)
        error = findViewById(R.id.login_error)
        progress = findViewById(R.id.login_progress)
        submit = findViewById(R.id.login_submit)
        findViewById<TextView>(R.id.login_cancel).setOnClickListener { finish() }

        submit.setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty()) {
            showError(getString(R.string.login_err_user))
            return
        }
        setBusy(true)
        thread {
            when (val res = SiloApi(prefs.serverUrl).login(user, pass)) {
                is SiloApi.LoginResult.Ok -> {
                    // Для owner id фиксированный; для остальных используем логин как id
                    val id = if (user == "owner") "owner" else user
                    prefs.saveSession(Session(id, user, res.token))
                    prefs.activeUserId = id
                    ui.post { finishOk() }
                }
                SiloApi.LoginResult.WrongPassword -> ui.post {
                    setBusy(false); showError(getString(R.string.login_err_wrong))
                }
                is SiloApi.LoginResult.Error -> ui.post {
                    setBusy(false); showError(getString(R.string.login_err_server))
                }
            }
        }
    }

    private fun finishOk() {
        // Возвращаемся туда, откуда пришли (список пользователей или главный экран)
        setResult(RESULT_OK)
        finish()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        submit.isEnabled = !busy
    }

    private fun showError(msg: String) {
        error.text = msg
        error.visibility = View.VISIBLE
    }

    companion object {
        fun intent(context: android.content.Context) = Intent(context, LoginActivity::class.java)
    }
}
