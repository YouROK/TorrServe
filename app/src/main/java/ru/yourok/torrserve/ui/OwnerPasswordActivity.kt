package ru.yourok.torrserve.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session
import kotlin.concurrent.thread

/**
 * Многопользовательский режим: задаём пароль owner, перезапускаем сервер
 * с этим паролем, затем входим под owner и сохраняем его токен.
 */
class OwnerPasswordActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var passwordInput: EditText
    private lateinit var confirmInput: EditText
    private lateinit var errorText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var submit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_password)
        prefs = Prefs(this)

        passwordInput = findViewById(R.id.owner_password)
        confirmInput = findViewById(R.id.owner_password_confirm)
        errorText = findViewById(R.id.owner_error)
        progress = findViewById(R.id.owner_progress)
        submit = findViewById(R.id.owner_submit)

        submit.setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val pass = passwordInput.text.toString()
        val confirm = confirmInput.text.toString()

        if (pass.isEmpty()) {
            showError(getString(R.string.owner_err_empty))
            return
        }
        if (pass != confirm) {
            showError(getString(R.string.owner_err_mismatch))
            return
        }

        setBusy(true)
        // Сохраняем пароль и перезапускаем локальный сервер с ним
        prefs.ownerPassword = pass
        TorrService.start(this)

        thread {
            val api = SiloApi(prefs.serverUrl)
            // Ждём, пока сервер поднимется с новым конфигом
            val ready = waitForServer(api)
            if (!ready) {
                ui.post { setBusy(false); showError(getString(R.string.owner_err_server)) }
                return@thread
            }
            when (val res = api.login("owner", pass)) {
                is SiloApi.LoginResult.Ok -> {
                    prefs.ownerToken = res.token
                    prefs.saveSession(Session("owner", "owner", res.token))
                    prefs.activeUserId = "owner"
                    prefs.multiUser = true
                    prefs.markOnboardingDone()
                    ui.post { openMain() }
                }
                SiloApi.LoginResult.WrongPassword -> ui.post {
                    setBusy(false); showError(getString(R.string.owner_err_login))
                }
                is SiloApi.LoginResult.Error -> ui.post {
                    setBusy(false); showError(getString(R.string.owner_err_server))
                }
            }
        }
    }

    private fun waitForServer(api: SiloApi): Boolean {
        repeat(WAIT_TRIES) {
            if (api.ping()) return true
            Thread.sleep(WAIT_STEP_MS)
        }
        return false
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        submit.isEnabled = !busy
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = android.view.View.VISIBLE
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val WAIT_TRIES = 30
        private const val WAIT_STEP_MS = 500L
    }
}
