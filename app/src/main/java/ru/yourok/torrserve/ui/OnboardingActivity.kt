package ru.yourok.torrserve.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs

/**
 * Чистый запуск: выбор режима работы сервера.
 * Одиночный - сразу на главный экран.
 * Многопользовательский - далее экран пароля owner (следующий шаг).
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        val prefs = Prefs(this)

        if (prefs.serverUrl.isBlank()) prefs.serverUrl = Prefs.LOCAL_URL

        findViewById<Button>(R.id.btn_single).setOnClickListener {
            prefs.multiUser = false
            prefs.markOnboardingDone()
            openMain()
        }

        findViewById<Button>(R.id.btn_multi).setOnClickListener {
            // Многопользовательский: сначала пароль owner, затем вход под ним
            startActivity(Intent(this, OwnerPasswordActivity::class.java))
            finish()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
