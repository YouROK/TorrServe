package ru.yourok.torrserve.ui.fragments.main.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.ADManager
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.ext.commitFragment
import ru.yourok.torrserve.ext.getLastFragment
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
import ru.yourok.torrserve.ui.fragments.main.servsets.ServerSettingsFragment
import ru.yourok.torrserve.ui.fragments.main.update.apk.ApkUpdateFragment
import ru.yourok.torrserve.ui.fragments.main.update.apk.UpdaterApk
import ru.yourok.torrserve.utils.AccessibilityUtils


class SettingsFragment : PreferenceFragmentCompat() {
    // https://stackoverflow.com/questions/27750901/how-to-manage-dividers-in-a-preferencefragment/55981453#55981453
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return CustomPreferenceAdapter(preferenceScreen)
    }

    @SuppressLint("RestrictedApi")
    internal class CustomPreferenceAdapter @SuppressLint("RestrictedApi")
    constructor(preferenceGroup: PreferenceGroup) : PreferenceGroupAdapter(preferenceGroup) {
        override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val currentPreference = getItem(position)
            if (currentPreference is Preference) {
                holder.itemView.background = ContextCompat.getDrawable(App.context, R.drawable.action_selector)
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    holder.itemView.backgroundTintList = ColorStateList.valueOf(ThemeUtil.getColorFromAttr(holder.itemView.context, R.attr.colorAccent))
//                    holder.itemView.backgroundTintMode = PorterDuff.Mode.MULTIPLY
//                }
            }
        }
    }

    fun show(activity: FragmentActivity, id: Int) {
        if (activity.getLastFragment()?.javaClass?.name == this.javaClass.name)
            return
        activity.commitFragment {
            replace(id, this@SettingsFragment)
            addToBackStack("Settings")
        }
    }

    // PreferenceFragment class
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = listView // This holds the PreferenceScreen's items
        rv?.setPadding(0, 0, 0, 56) // (left, top, right, bottom)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)
        val ps = findPreference<PreferenceScreen>("prefs")

        val bannerPref = findPreference<Preference>("show_banner")
        lifecycleScope.launch(Dispatchers.IO) {
            if (ADManager.expired())
                bannerPref?.let { ps?.removePreference(it) }
        }

        findPreference<Preference>("host")?.apply {
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

        findPreference<ListPreference>(getString(R.string.player_pref_key))?.apply {
            val pList = Players.getList()
            val player = Settings.getPlayer()
            this.entryValues = pList.map { it.first }.toTypedArray()
            this.entries = pList.map { it.second }.toTypedArray()
            this.value = player
            this.summary = pList.find { it.first == player }?.second ?: player
            setOnPreferenceChangeListener { _, newValue ->
                Settings.setPlayer(newValue.toString())
                this.summary = (pList.find { it.first == newValue }?.second ?: newValue).toString()
                true
            }
        }

        findPreference<ListPreference>("app_theme")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                Settings.setTheme(newValue.toString())
                requireActivity().recreate()
                true
            }
        }

        findPreference<Preference>("show_battery_save")?.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val cmp = intent.resolveActivity(requireActivity().packageManager)
                if (cmp == null)
                    ps?.removePreference(this)
                else {
                    setOnPreferenceClickListener {
                        if (Utils.isGoogleTV()) {
                            if (AccessibilityUtils.isPackageInstalled(context, "com.android.settings")) {
                                intent.`package` = "com.android.settings"
                                requireActivity().startActivity(intent)
                            } else {
                                val tvintent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                requireActivity().startActivity(tvintent)
                                App.Toast(R.string.show_battery_save_tv, true)
                            }
                        } else
                            requireActivity().startActivity(intent)
                        true
                    }
                }
            } else {
                ps?.removePreference(this)
            }
        }

        findPreference<SwitchPreferenceCompat>("switch_accessibility")?.apply {
            setOnPreferenceClickListener {
                val enable = AccessibilityUtils.isEnabledService(App.context)
                AccessibilityUtils.enableService(App.context, !enable)
                this.isChecked = AccessibilityUtils.isEnabledService(App.context)
                if (this.isChecked)
                    findPreference<SwitchPreferenceCompat>("boot_start")?.isChecked = true
                true
            }
        }

        findPreference<Preference>("version")?.apply {
            this.summary = BuildConfig.VERSION_NAME
            setOnPreferenceClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (UpdaterApk.check())
                        withContext(Dispatchers.Main) {
                            ApkUpdateFragment().show(requireActivity(), R.id.container, true)
                        }
                    else {
                        withContext(Dispatchers.Main) {
                            App.Toast(R.string.not_found_new_app_update, true)
                        }
                    }
                }
                true
            }
        }

    }

    override fun onResume() {
        super.onResume()

        findPreference<Preference>("host")?.apply {
            summary = Settings.getHost()
        }

        findPreference<SwitchPreferenceCompat>("root_start")?.apply {
            val isRootAvail = Shell.rootAccess()
            this.isEnabled = isRootAvail
            if (!isRootAvail) {
                this.isChecked = false
            }
        }

        findPreference<SwitchPreferenceCompat>("switch_accessibility")?.apply {
            this.isChecked = AccessibilityUtils.isEnabledService(App.context)
            if (this.isChecked)
                findPreference<SwitchPreferenceCompat>("boot_start")?.isChecked = true
        }
    }

}