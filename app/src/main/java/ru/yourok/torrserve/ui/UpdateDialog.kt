package ru.yourok.torrserve.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import ru.yourok.torrserve.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Диалог обновления приложения: версии, «что нового» из текста релиза, прогресс
 * скачивания и запуск установщика APK.
 *
 * Скачивание идёт в фоне (как в старом клиенте), но файл кладётся в cacheDir
 * приложения и передаётся установщику через FileProvider. Закрытие диалога
 * во время загрузки отменяет её и удаляет недокачанный файл.
 */
object UpdateDialog {

    fun show(activity: Activity, update: AppUpdater.Update) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        val versions = view.findViewById<TextView>(R.id.update_versions)
        val notesHeader = view.findViewById<View>(R.id.update_notes_header)
        val notesScroll = view.findViewById<View>(R.id.update_notes_scroll)
        val notes = view.findViewById<TextView>(R.id.update_notes)
        val progress = view.findViewById<ProgressBar>(R.id.update_progress)
        val percent = view.findViewById<TextView>(R.id.update_percent)
        val nowButton = view.findViewById<TextView>(R.id.update_now)
        val laterButton = view.findViewById<TextView>(R.id.update_later)

        versions.text = activity.getString(
            R.string.app_update_message,
            update.tag,
            AppUpdater.installedVersion(activity),
        )

        val notesHtml = update.notesHtml.trim()
        val hasNotes = notesHtml.isNotEmpty() && !notesHtml.equals("No content.", ignoreCase = true)
        notesHeader.visibility = if (hasNotes) View.VISIBLE else View.GONE
        notesScroll.visibility = if (hasNotes) View.VISIBLE else View.GONE
        if (hasNotes) {
            notes.text = HtmlCompat.fromHtml(notesHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.app_update_title)
            .setView(view)
            .create()

        // Отмена по закрытию диалога и защита от двойного запуска
        val cancelled = AtomicBoolean(false)
        val running = AtomicBoolean(false)
        dialog.setOnDismissListener { cancelled.set(true) }

        fun setDownloading(active: Boolean) {
            progress.visibility = if (active) View.VISIBLE else View.GONE
            percent.visibility = if (active) View.VISIBLE else View.GONE
            // Кнопку не прячем, иначе диалог «прыгает»: гасим её и оставляем «Отмену»
            nowButton.isEnabled = !active
            nowButton.alpha = if (active) 0.4f else 1f
            laterButton.text = activity.getString(
                if (active) R.string.app_update_cancel else R.string.app_update_later
            )
        }

        fun toast(text: String) {
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
        }

        fun startDownload() {
            if (!running.compareAndSet(false, true)) return
            setDownloading(true)
            percent.text = activity.getString(R.string.app_update_downloading, 0)
            thread(name = "app-update") {
                val file = try {
                    AppUpdater.download(
                        context = activity.applicationContext,
                        update = update,
                        isCancelled = { cancelled.get() },
                        onProgress = { value ->
                            activity.runOnUiThread {
                                if (!cancelled.get()) {
                                    progress.progress = value
                                    percent.text = activity.getString(R.string.app_update_downloading, value)
                                }
                            }
                        },
                    )
                } catch (e: AppUpdater.Cancelled) {
                    activity.runOnUiThread { running.set(false) }
                    return@thread
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        running.set(false)
                        if (!cancelled.get()) {
                            setDownloading(false)
                            toast(activity.getString(R.string.app_update_error, e.message ?: ""))
                        }
                    }
                    return@thread
                }

                activity.runOnUiThread {
                    running.set(false)
                    if (cancelled.get()) return@runOnUiThread
                    percent.text = activity.getString(R.string.app_update_verifying)
                    if (AppUpdater.install(activity, file)) {
                        cancelled.set(true)
                        dialog.dismiss()
                    } else {
                        setDownloading(false)
                        toast(activity.getString(R.string.app_update_install_permission))
                    }
                }
            }
        }

        nowButton.setOnClickListener { startDownload() }
        laterButton.setOnClickListener {
            if (running.get()) {
                cancelled.set(true)
                running.set(false)
                setDownloading(false)
            } else {
                dialog.dismiss()
            }
        }

        dialog.show()

        // Короткий текст релиза не должен оставлять пустую область высотой 200dp:
        // высота списка подгоняется под содержимое, но не больше 200dp
        if (hasNotes) {
            notesScroll.post {
                val max = (200 * activity.resources.displayMetrics.density).toInt()
                val content = notes.height + notes.paddingTop + notes.paddingBottom
                if (content in 1..max) {
                    notesScroll.layoutParams = notesScroll.layoutParams.apply { height = content }
                    notesScroll.requestLayout()
                }
            }
        }
    }
}
