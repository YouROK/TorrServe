package ru.yourok.torrserve.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat.startActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.preferences.Preferences
import java.util.*
import kotlin.concurrent.thread


object Donate {
    private fun isShowDonate(): Boolean {
        return true
    }

    fun donateDialog(context: Context) {
        AlertDialog.Builder(context)
                .setTitle(R.string.donate)
                .setMessage(R.string.donate_msg)
                .setPositiveButton(R.string.paypal) { _, _ ->
                    val cur = Currency.getInstance(Locale.getDefault())
                    val mon = cur.toString()
                    val link = "https://www.paypal.me/yourok/0$mon"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(context, browserIntent, null)
                    Preferences.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
                }
                .setNegativeButton(R.string.yandex_money) { _, _ ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://money.yandex.ru/to/410013733697114/100"))
                    browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(context, browserIntent, null)
                    Preferences.setLastViewDonate(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
                }.show()
    }

    @Volatile
    private var showDonate = false

    fun showDonate(activity: Activity) {
        thread {
            synchronized(showDonate) {
                if (!isShowDonate())
                    return@thread
                val last: Long = Preferences.getLastViewDonate()
                if (System.currentTimeMillis() < last || showDonate)
                    return@thread
                showDonate = true
            }

            val snackbar = Snackbar.make(activity.findViewById(R.id.content), R.string.donate, Snackbar.LENGTH_INDEFINITE)
            Handler(Looper.getMainLooper()).postDelayed({
                snackbar
                        .setAction(android.R.string.ok) {
                            Preferences.setLastViewDonate(System.currentTimeMillis())
                            donateDialog(activity)
                            Preferences.setLastViewDonate(System.currentTimeMillis() + 5 * 60 * 1000)
                        }
                        .show()
            }, 5000)
            Handler(Looper.getMainLooper()).postDelayed({
                if (snackbar.isShown) {
                    snackbar.dismiss()
                    Preferences.setLastViewDonate(System.currentTimeMillis() + 5 * 60 * 1000)
                }
                showDonate = false
            }, 15000)
        }
    }
}