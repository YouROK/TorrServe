package ru.yourok.torrserve.ui.fragments.main.settings

import android.content.Intent
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
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
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

        findPreference<Preference>("host")?.apply {
            summary = Settings.getHost()
            setOnPreferenceClickListener {
                ServerFinderFragment().show(requireActivity(), R.id.container, true)
                true
            }
        }

        findPreference<Preference>("server_settings")?.setOnPreferenceClickListener {
            ServerSettingsFragment().show(requireActivity(), R.id.container, true)
            true
        }

        findPreference<Preference>("remove_action")?.setOnPreferenceClickListener {
            Settings.setChooserAction(0)
            App.Toast(R.string.reset_def)
            true
        }

        findPreference<ListPreference>("choose_player")?.apply {
            val pList = Players.getList()
            val player = Settings.getPlayer()
            this.entryValues = pList.map { it.first }.toTypedArray()
            this.entries = pList.map {
            //    if (it.first.isNotEmpty() && it.first != "0")
            //        it.second + "\n" + it.first
            //    else
                    it.second
            }.toTypedArray()
            this.value = player
            this.summary = pList.find { it.first == player }?.second ?: player
            setOnPreferenceChangeListener { preference, newValue ->
                Settings.setPlayer(newValue.toString())
                this.summary = (pList.find { it.first == newValue }?.second ?: newValue).toString()
                true
            }
        }

        findPreference<Preference>("show_battery_save")?.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (intent.resolveActivity(requireActivity().packageManager) == null)
                    ps?.removePreference(this)
                setOnPreferenceClickListener {
                    requireActivity().startActivity(intent)
                    true
                }
            } else {
                ps?.removePreference(this)
            }
        }

    }
}