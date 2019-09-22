package ru.yourok.torrserve.activitys.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_app_settings.*
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.dialog.DialogList
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.utils.Player
import ru.yourok.torrserve.utils.Players

class AppSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        btnServerSettings.setOnClickListener {
            startActivity(Intent(this, ServerSettingsActivity::class.java))
        }

        btnStartServerOnBoot.setOnClickListener {
            val list = listOf(getString(R.string.yes), getString(R.string.no))
            DialogList.show(this, getString(R.string.start_server_on_boot), list, false) { select, i ->
                Preferences.setAutoStart(i[0] == 0)
                updateStats()
            }
        }

        btnSelectPlayer.setOnClickListener {
            val plist = Players.getList()
            plist.add(0, Player(getString(R.string.default_player), "0"))
            plist.add(1, Player(getString(R.string.choose_player), "1"))
            plist.add(2, Player(getString(R.string.inner_player), "2"))

            val list = plist.map { it.Name }

            DialogList.show(this, getString(R.string.select_player), list, false) { select, i ->
                val player = plist[i[0]]
                Preferences.setPlayer(player.Package)
                updateStats()
            }
        }

        btnDisableAD.setOnClickListener {
            var v = Preferences.isDisableAD()
            v = !v
            Preferences.disableAD(v)
            updateStats()
        }

        textViewVersion.text = ("YouROK " + getText(R.string.app_name) + " ${BuildConfig.VERSION_NAME}")
        updateStats()
    }

    private fun updateStats() {
        Handler(Looper.getMainLooper()).post {
            if (Preferences.isAutoStart())
                tvStatStartBoot.setText(R.string.start_server_on_boot)
            else
                tvStatStartBoot.setText(R.string.dont_start_server_on_boot)

            val plist = Players.getList()
            plist.add(0, Player(getString(R.string.default_player), "0"))
            plist.add(1, Player(getString(R.string.choose_player), "1"))
            plist.add(2, Player(getString(R.string.inner_player), "2"))

            var player = getString(R.string.default_player)
            val currPlayer = Preferences.getPlayer()

            val pnames = plist.filter { it.Package == currPlayer }
            if (pnames.isNotEmpty())
                player = pnames[0].Name

            tvStatPlayer.text = player

            if (Preferences.isDisableAD())
                tvStatAD.text = getText(R.string.ad_disabled)
            else
                tvStatAD.text = getText(R.string.ad_enabled)

        }
    }
}
