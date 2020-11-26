package ru.yourok.torrserve.ui.fragments.main.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.commitFragment

class SettingsFragment : PreferenceFragmentCompat() {

    fun show(activity: FragmentActivity, id: Int) {
        activity.commitFragment {
            replace(id, this@SettingsFragment)
            addToBackStack("Settings")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)
    }
}