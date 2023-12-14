package ru.yourok.torrserve.ui.activities.main

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.clearStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
import ru.yourok.torrserve.ui.fragments.main.settings.SettingsFragment
import ru.yourok.torrserve.ui.fragments.main.update.server.ServerUpdateFragment
import ru.yourok.torrserve.utils.Permission
import ru.yourok.torrserve.utils.ThemeUtil

/* Activity to show settings from Accessibility */
class SettingsActivity : AppCompatActivity() {

    private val themeUtil = ThemeUtil()

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (BuildConfig.DEBUG) Log.d("SettingsActivity", "handleOnBackPressed()")
            if (supportFragmentManager.backStackEntryCount > 1)
                supportFragmentManager.popBackStack()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                finishAndRemoveTask()
            else
                finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Permission.requestPermissionWithRationale(this, Permission.writePermission)
        themeUtil.onCreate(this)
        setContentView(R.layout.settings_activity)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        lifecycleScope.launch(Dispatchers.IO) {
            TorrService.start()
            if (TorrService.wait(10)) {
                if (TorrService.isLocal()) {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            ServerUpdateFragment().show(this@SettingsActivity, R.id.container, true)
                            App.toast(R.string.need_update_server, true)
                        }
                    }
                } else {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            App.toast(R.string.not_support_old_server, true)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (TorrService.isLocal())
                        ServerUpdateFragment().show(this@SettingsActivity, R.id.container, true)
                    else
                        ServerFinderFragment().show(this@SettingsActivity, R.id.container, true)
                    App.toast(R.string.need_install_server, true)
                }
            }
        }

        if (savedInstanceState == null) {
            clearStackFragment()
            SettingsFragment().apply {
                show(this@SettingsActivity, R.id.container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        themeUtil.onResume(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeUtil.onConfigurationChanged(this, newConfig)
    }

}