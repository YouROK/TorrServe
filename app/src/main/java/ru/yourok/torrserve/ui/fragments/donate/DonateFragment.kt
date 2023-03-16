package ru.yourok.torrserve.ui.fragments.donate

import android.annotation.SuppressLint
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.Format
import ru.yourok.torrserve.utils.ThemeUtil

class DonateFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.donate_fragment, container, false)
        Settings.setLastViewDonate(System.currentTimeMillis() + 12 * 60 * 60 * 1000)

        vi.findViewById<Button>(R.id.btnBoosty)?.setOnClickListener {
            val link = "https://boosty.to/yourok"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivitySafely(intent)
            Settings.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
        }

        vi.findViewById<Button>(R.id.btnYandex)?.setOnClickListener {
            val link = "https://yoomoney.ru/to/410013733697114"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivitySafely(intent)
            Settings.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
        }

//        vi.findViewById<Button>(R.id.btnPayPal)?.setOnClickListener {
//            val link = "https://www.paypal.me/yourok/5usd"
//            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
//            intent.addCategory(Intent.CATEGORY_BROWSABLE)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//            startActivitySafely(intent)
//            Settings.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
//        }

        vi.findViewById<Button>(R.id.btnTelegram)?.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse("https://t.me/torrserve")
            startActivitySafely(intent)
            Settings.setLastViewDonate(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000)
        }

        vi.findViewById<ImageView>(R.id.ivTelegram)?.apply {
            alpha = 0.6f
            setOnFocusChangeListener { _, hasFocus ->
                alpha = if (hasFocus)
                    1.0f
                else
                    0.6f
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

    private fun startActivitySafely(intent: Intent): Boolean {
        try {
            if (intent.resolveActivity(App.context.packageManager) != null) {
                App.context.startActivity(intent)
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
    private var showDonate = Any()

    @SuppressLint("RestrictedApi")
    fun showDonate(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            synchronized(showDonate) {
                val last: Long = Settings.getLastViewDonate()
                if (System.currentTimeMillis() < last || showDonate == true)
                    return@launch
                showDonate = true
            }

            val snackbar = Snackbar.make(activity.findViewById(R.id.container), R.string.donate, Snackbar.LENGTH_INDEFINITE)
            Handler(Looper.getMainLooper()).postDelayed({
                val snackbarLayout: Snackbar.SnackbarLayout? = snackbar.view as Snackbar.SnackbarLayout?
                val themedContext = ContextThemeWrapper(App.context, ThemeUtil.selectedTheme)
                var bg = R.drawable.snackbar
                var tc = R.color.black
                if (ThemeUtil.selectedTheme == R.style.Theme_TorrServe_Light) {
                    bg = R.drawable.snackbar_dark
                    tc = R.color.tv_white
                }
                snackbarLayout?.background = AppCompatResources.getDrawable(App.context, bg)
                val textView = snackbarLayout?.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView?
                textView?.textSize = 18.0f
                textView?.setTextColor(ContextCompat.getColor(App.context, tc))
                val img = ContextCompat.getDrawable(themedContext, R.drawable.ts_round)
                val padding = Format.dp2px(10f)
                val imgSize = textView?.lineHeight ?: (padding * 2)
                img?.setBounds(0, 0, imgSize + Format.dp2px(8f), imgSize + Format.dp2px(8f))
                textView?.setCompoundDrawables(img, null, null, null)
                textView?.compoundDrawablePadding = padding
                val layoutParams = snackbarLayout?.layoutParams as ViewGroup.MarginLayoutParams
                val pad = Format.dp2px(32.0f)
                layoutParams.setMargins(pad, pad, pad, pad)
                snackbarLayout.layoutParams = layoutParams
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