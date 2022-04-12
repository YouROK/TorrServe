package ru.yourok.torrserve.ui.fragments.donate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.TSFragment
import java.util.*

class DonateFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.donate_fragment, container, false)
        Settings.setLastViewDonate(System.currentTimeMillis() + 12 * 60 * 60 * 1000)

        vi.findViewById<Button>(R.id.btnYandex)?.setOnClickListener {
            val link = "https://money.yandex.ru/to/410013733697114/200"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivitySafely(intent)
            Settings.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
        }

        vi.findViewById<Button>(R.id.btnPayPal)?.setOnClickListener {
            val link = "https://www.paypal.me/yourok/5usd"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivitySafely(intent)
            Settings.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
        }

        vi.findViewById<Button>(R.id.btnTelegram)?.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse("https://t.me/torrserve")
            startActivitySafely(intent)
        }

        vi.findViewById<ImageView>(R.id.ivTelegram)?.apply {
            alpha = 0.6f
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus)
                    alpha = 1.0f
                else
                    alpha = 0.6f
            }
            setOnClickListener {
                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.data = Uri.parse("https://t.me/torrserve")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivitySafely(intent)
            }
        }
        return vi
    }

    fun startActivitySafely(intent: Intent): Boolean {
        try {
            if (intent.resolveActivity(App.appContext().packageManager) != null) {
                App.appContext().startActivity(intent)
                return true
            }
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
        // Maybe will be added a Toast to notify user
        return false
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
                        DonateFragment().show(activity, R.id.container, true)
                    }
                    .show()
            }, 5000)
            Handler(Looper.getMainLooper()).postDelayed({
                if (snackbar.isShown) {
                    snackbar.dismiss()
                    Settings.setLastViewDonate(System.currentTimeMillis() + 3 * 60 * 60 * 1000)
                }
                showDonate = false
            }, 10000)
        }
    }
}