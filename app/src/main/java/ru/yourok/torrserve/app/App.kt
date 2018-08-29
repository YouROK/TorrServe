package ru.yourok.torrserve.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import ru.yourok.torrserve.R


class App : Application() {
    companion object {
        private lateinit var contextApp: Context

        fun getContext(): Context {
            return contextApp
        }

        fun Toast(txt: String) {
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(contextApp, txt, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        contextApp = applicationContext
        ACR.get(this)
                .setEmailAddresses("8yourok8@gmail.com")
                .setEmailSubject(getString(R.string.app_name) + " Crash Report")
                .start()
    }
}
