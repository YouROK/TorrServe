package ru.yourok.torrserve.ui.activities.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.clearStackFragmnet
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.main.settings.SettingsFragment
import ru.yourok.torrserve.ui.fragments.main.update.server.ServerUpdateFragment
import ru.yourok.torrserve.utils.Premissions
import ru.yourok.torrserve.utils.ThemeUtil


class SettingsActivity : AppCompatActivity() {

    private val themeUtil = ThemeUtil()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Premissions.requestPermissionWithRationale(this)
        themeUtil.onCreate(this)
        setContentView(R.layout.settings_activity)

        lifecycleScope.launch(Dispatchers.IO) {
            TorrService.start()
            if (TorrService.wait(5)) {
                if (TorrService.isLocal()) {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            ServerUpdateFragment().show(this@SettingsActivity, R.id.container, true)
                            App.Toast(R.string.need_update_server, true)
                        }
                    }
                } else {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            App.Toast(R.string.not_support_old_server, true)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    ServerUpdateFragment().show(this@SettingsActivity, R.id.container, true)
                    App.Toast(R.string.need_update_server, true)
                }
            }
        }

        if (savedInstanceState == null) {
            clearStackFragmnet()
            SettingsFragment().apply {
                show(this@SettingsActivity, R.id.container)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (supportFragmentManager.getBackStackEntryCount() == 0) {
//            TorrService.stop()
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        themeUtil.onResume(this)
    }

}