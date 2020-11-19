package ru.yourok.torrserve

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.server.local.Updater
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.main.MainFragment
import ru.yourok.torrserve.utils.Premissions

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Premissions.requestPermissionWithRationale(this)
        TorrService.start()
        //TODO remove
        Updater.updateFromFile("/sdcard/Download/TorrServer-linux-arm64")

        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
    }
}