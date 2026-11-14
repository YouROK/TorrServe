package ru.yourok.torrserve.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Prefs

/**
 * Первый экран: анимация и решение, куда идти дальше.
 * - Есть сохранённый сервер -> подключаемся (локальный поднимаем сервисом).
 * - Нет сервера -> онбординг (скачивание сервера добавим позже).
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        prefs = Prefs(this)

        val logo = findViewById<ImageView>(R.id.splash_logo)
        ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = ANIM_MS
            interpolator = DecelerateInterpolator()
            start()
        }

        logo.postDelayed({ next() }, ANIM_MS + DELAY_MS)
    }

    private fun next() {
        val target = if (prefs.serverUrl.isBlank() || !prefs.onboardingDone) {
            OnboardingActivity::class.java
        } else {
            MainActivity::class.java
        }

        // Локальный сервер поднимаем сервисом до перехода
        if (prefs.isLocalServer && prefs.serverUrl.isNotBlank()) {
            TorrService.start(this)
        }

        startActivity(Intent(this, target))
        finish()
    }

    companion object {
        private const val ANIM_MS = 600L
        private const val DELAY_MS = 800L
    }
}
