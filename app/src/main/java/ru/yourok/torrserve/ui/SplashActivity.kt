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
 * Первый экран: короткая анимация логотипа, затем сразу главное окно.
 * Локальный сервер поднимается сервисом до перехода.
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
        // Приветственного экрана больше нет: сразу главное окно.
        // На чистой установке по умолчанию выставляем адрес локального сервера.
        if (prefs.serverUrl.isBlank()) prefs.serverUrl = Prefs.LOCAL_URL

        // Локальный сервер поднимаем сервисом до перехода
        if (prefs.isLocalServer) TorrService.start(this)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val ANIM_MS = 600L
        private const val DELAY_MS = 800L
    }
}
