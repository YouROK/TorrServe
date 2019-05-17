package ru.yourok.torrserve.activitys.splash

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.services.ServerService
import kotlin.concurrent.thread


class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val infoLabel = findViewById<TextView>(R.id.textViewInfo)
        infoLabel.text = BuildConfig.VERSION_NAME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            infoLabel.alpha = 0f
            infoLabel.animate()
                    .alpha(1f).setDuration(500)
                    .withEndAction {
                        infoLabel.animate().alpha(0f)
                                .withEndAction {
                                    infoLabel.setText(R.string.connecting_to_server)
                                    infoLabel.scaleX = 0f
                                    infoLabel.alpha = 1f
                                    infoLabel.animate().scaleX(1f).setDuration(1000)
                                            .start()
                                }.start()
                    }.start()
        }

        thread {
            val start = System.currentTimeMillis()

            if (Api.serverIsLocal() && ServerFile.serverExists())
                ServerService.start()

            ServerService.wait(10)

            val end = System.currentTimeMillis()
            if (end - start < 2000)
                Thread.sleep(2000)

            finish()
            overridePendingTransition(0, R.anim.splash_fade_out)
        }
    }
}
