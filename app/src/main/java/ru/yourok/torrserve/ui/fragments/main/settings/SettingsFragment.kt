package ru.yourok.torrserve.ui.fragments.main.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.ADManager
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.commitFragment
import ru.yourok.torrserve.ext.getLastFragment
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.main.servsets.ServerSettingsFragment

class SettingsFragment : PreferenceFragmentCompat() {

    fun show(activity: FragmentActivity, id: Int) {
        if (activity.getLastFragment()?.javaClass?.name == this.javaClass.name)
            return
        activity.commitFragment {
            replace(id, this@SettingsFragment)
            addToBackStack("Settings")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)
        val ps = findPreference<PreferenceScreen>("prefs")

        val bannerPref = findPreference<SwitchPreferenceCompat>("show_banner")
        lifecycleScope.launch(Dispatchers.IO) {
            if (ADManager.expired())
                ps?.removePreference(bannerPref)
        }

        findPreference<EditTextPreference>("host")?.apply {
            summary = Settings.getHost()
            text = Settings.getHost()
            setOnPreferenceChangeListener { preference, newValue ->
                //TODO сделать окно поиска
                val host = newValue.toString()
                Settings.setHost(host)
                summary = Settings.getHost()
                text = Settings.getHost()

                true
            }
            setOnBindEditTextListener { }
        }

        findPreference<Preference>("server_settings")?.setOnPreferenceClickListener {
            ServerSettingsFragment().show(requireActivity(), R.id.container, true)
            true
        }

        findPreference<Preference>("remove_action")?.setOnPreferenceClickListener {
            Settings.setChooserAction(0)
            App.Toast(R.string.make_as_def)
            true
        }

        val choosePlayerPref = findPreference<ListPreference>("choose_player")
        choosePlayerPref?.apply {
            val pList = Players.getList()
            val player = Settings.getPlayer()
            this.entryValues = pList.map { it.first }.toTypedArray()
            this.entries = pList.map {
                if (it.first.isNotEmpty() && it.first != "0")
                    it.second + " - " + it.first
                else
                    it.second
            }.toTypedArray()
            this.summary = pList.find { it.first == player }?.second ?: player
        }
    }
}