package ru.yourok.torrserve.activitys.updater

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_updater.*
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.dialog.DialogList
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.serverloader.Updater
import kotlin.concurrent.thread

class UpdaterActivity : AppCompatActivity() {

    val testVersion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updater)

        checkVersion()

        findViewById<Button>(R.id.download_apk_button).setOnClickListener {
            thread {
                try {
                    Updater.checkRemoteApk()
                    val link = Updater.getJson(false, testVersion).getString("Link")
                    if (link.isNotEmpty()) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(browserIntent)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        textViewApkUpdate.text = e.message ?: "Error check version"
                    }
                }
            }
        }

        findViewById<Button>(R.id.update_remotly_button).setOnClickListener {
            thread {
                updateRemotly()
            }
        }

        findViewById<Button>(R.id.update_local_button).setOnClickListener {
            thread {
                updateLocaly()
            }
        }

        findViewById<Button>(R.id.delete_server_button).setOnClickListener {
            ServerFile.deleteServer()
            checkVersion()
        }

        findViewById<ImageButton>(R.id.buttonCheckUpdate).setOnClickListener {
            checkVersion()
        }
    }

    fun checkVersion() {
        Handler(Looper.getMainLooper()).post {
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = true
            textViewArch.text = ("Arch: ${Updater.getArch()}")
            textViewApkVersion.text = ("Android ${getString(R.string.version)}: ${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}")
        }
        thread {

            val tha = thread {
                try {
                    Updater.checkRemoteApk()
                    Handler(Looper.getMainLooper()).post {
                        try {
                            textViewApkUpdate.text = ("Android ${getString(R.string.update)} :" + Updater.getJson(false, testVersion).getString("Version"))
                        } catch (e: Exception) {
                            textViewApkUpdate.text = e.message ?: "Error get version"
                        }
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        textViewApkUpdate.text = e.message ?: "Error check version"
                    }
                }
            }

            val thl = thread {
                try {
                    Updater.checkLocalVersion()
                    Handler(Looper.getMainLooper()).post {
                        textViewServerVersion.text = ("Server ${getString(R.string.version)} :" + Updater.getCurrVersion())
                        if (Api.serverEcho().isNotEmpty())
                            findViewById<TextView>(R.id.update_info).setText(R.string.stat_server_is_running)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        textViewServerVersion.text = e.message ?: "error check local server"
                    }
                }
            }

            val thr = thread {
                try {
                    Updater.checkRemoteServer(testVersion)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            textViewServerUpdate.text = ("Server ${getString(R.string.update)} :" + Updater.getJson(true, testVersion).getString("Version"))
                        } catch (e: Exception) {
                            textViewServerUpdate.text = e.message ?: "Error get version"
                        }
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        textViewServerUpdate.text = e.message ?: "Error check version"
                    }
                }
            }
            tha.join()
            thl.join()
            thr.join()
            Handler(Looper.getMainLooper()).post {
                findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            }
        }
    }

    fun updateRemotly() {
        Handler(Looper.getMainLooper()).post {
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = false
            findViewById<TextView>(R.id.update_info).setText(R.string.downloading)
        }
        try {
            Updater.updateServerRemote(testVersion) { prc ->
                Handler(Looper.getMainLooper()).post {
                    findViewById<ProgressBar>(R.id.progress_bar).progress = prc
                }
            }
            Handler(Looper.getMainLooper()).post {
                findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = true
            }
            Thread.sleep(1000)
            checkVersion()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                val msg = "Error download server: " + (e.message ?: "")
                findViewById<TextView>(R.id.update_info).setText(msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            }
        }
    }

    fun updateLocaly() {
        val dw = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dw.listFiles { file ->
            if (file.isFile)
                file.name.contains("TorrServer", true)
            else
                false
        }

        Handler(Looper.getMainLooper()).post {
            if (files == null || files.isEmpty()) {
                Toast.makeText(this, R.string.warn_no_localy_updates, Toast.LENGTH_SHORT).show()

                val msg = getString(R.string.warn_no_localy_updates) + ": ${dw.name}/TorrServer-android-${Updater.getArch()}"
                findViewById<TextView>(R.id.update_info).setText(msg)
                return@post
            }

            DialogList.show(this, "", files.map { it.name }, false) { selList: List<String>, selInt: List<Int> ->
                Handler(Looper.getMainLooper()).post {
                    findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
                    findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = true
                }
                val file = files[selInt[0]]
                try {
                    Updater.updateServerLocal(file.path)
                    Thread.sleep(1000)
                    checkVersion()
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        val msg = "Error copy server:" + (e.message ?: "")
                        findViewById<TextView>(R.id.update_info).setText(msg)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
                    }
                }
            }
        }
    }
}
