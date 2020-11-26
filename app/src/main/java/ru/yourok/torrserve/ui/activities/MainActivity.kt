package ru.yourok.torrserve.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.clearStackFragmnet
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.main.torrents.TorrentsFragment
import ru.yourok.torrserve.utils.Premissions

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Premissions.requestPermissionWithRationale(this)
        setContentView(R.layout.main_activity)

        //TODO remove
//        Updater.updateFromFile("/sdcard/Download/TorrServer-linux-arm64")

        TorrService.start()

        if (savedInstanceState == null) {
            clearStackFragmnet()
            TorrentsFragment().show(this, R.id.container)
        }
    }
}