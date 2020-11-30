package ru.yourok.torrserve.ui.fragments.donate

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.TSFragment

class Donate : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.info_fragment, container, false)
        Settings.setLastViewDonate(System.currentTimeMillis() + 60 * 60 * 1000)
        return vi
    }

}

object DonateMessage {
    private var showDonate = false

    fun showDonate(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            synchronized(showDonate) {
                val last: Long = Settings.getLastViewDonate()
                if (System.currentTimeMillis() < last || showDonate)
                    return@launch
                showDonate = true
            }

            val snackbar = Snackbar.make(activity.findViewById(R.id.container), R.string.donate, Snackbar.LENGTH_INDEFINITE)
            Handler(Looper.getMainLooper()).postDelayed({
                snackbar
                    .setAction(android.R.string.ok) {
                        Donate().show(activity, R.id.container)
                    }
                    .show()
            }, 5000)
            Handler(Looper.getMainLooper()).postDelayed({
                if (snackbar.isShown) {
                    snackbar.dismiss()
                    Settings.setLastViewDonate(System.currentTimeMillis() + 5 * 60 * 1000)
                }
                showDonate = false
            }, 15000)
        }
    }
}