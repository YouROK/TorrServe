package ru.yourok.torrserve.ui.fragments.main.update.server

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.dialogs.DialogList
import ru.yourok.torrserve.ui.fragments.TSFragment
import java.util.*
import kotlin.concurrent.timerTask

class ServerUpdateFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.server_update_fragment, container, false)
        vi.findViewById<Button>(R.id.btnUpdate)?.also { btn ->
            btn.setOnClickListener {
                btn.isEnabled = false
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        showProgress()
                        Settings.setHost("") // revert to local server
                        UpdaterServer.updateFromNet {
                            lifecycleScope.launch(Dispatchers.Main) {
                                showProgress(it)
                            }
                        }
                        delay(1000)
                        updateUI()
                        hideProgress()
                        withContext(Dispatchers.Main) {
                            btn.isEnabled = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            App.toast(App.context.getString(R.string.warn_error_download_server) + ": " + e.message)
                        }
                    }
                }
            }
        }

        vi.findViewById<Button>(R.id.btnUpdateDownload).setOnClickListener {
            installFromDownload()
        }

        vi.findViewById<Button>(R.id.btnDeleteServer).setOnClickListener {
            lifecycleScope.launch {
                showProgress()
                TorrService.stop()
                ServerFile().stop()
                ServerFile().delete()
                updateUI()
                hideProgress()
            }
            clickOnMenu()
        }

        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Thread.sleep(500)
        updateUI()
    }

    private fun updateUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                view?.findViewById<TextView>(R.id.tvArch)?.text = UpdaterServer.getArch()
                view?.findViewById<TextView>(R.id.tvLocalVersion)?.text = UpdaterServer.getLocalVersion()
            }
            UpdaterServer.check()
            val ver = UpdaterServer.getRemoteVersion()
            withContext(Dispatchers.Main) {
                view?.findViewById<TextView>(R.id.tvRemoteVersion)?.text = ver
            }
        }
    }

    private var countClick = 0
    private var timer: Timer? = null

    private fun clickOnMenu() {
        if (timer != null)
            timer?.cancel()
        timer = Timer()
        timer?.schedule(timerTask {
            countClick = 0
        }, 3000)

        countClick++
        if (countClick > 4)
            view?.findViewById<Button>(R.id.btnUpdateDownload)?.visibility = View.VISIBLE
    }

    private fun installFromDownload() {
        val dw = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dw.listFiles { file ->
            if (file.isFile)
                file.name.contains("TorrServer", true)
            else
                false
        }

        if (files.isNullOrEmpty()) {
            App.toast(R.string.warn_no_localy_updates)
            val msg = getString(R.string.warn_no_localy_updates) + ": ${dw.name}/TorrServer-android-${UpdaterServer.getArch()}"
            view?.findViewById<TextView>(R.id.tvUpdateInfo)?.text = msg
            return
        }

        DialogList.show(context ?: return, "", files.map { it.name }) { _: String, pos: Int ->
            lifecycleScope.launch(Dispatchers.IO) {
                val file = files[pos]
                showProgress()
                try {
                    Settings.setHost("") // revert to local server
                    UpdaterServer.updateFromFile(file.path)
                    delay(1000)
                    updateUI()
                    hideProgress()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val msg = "Error copy server:" + (e.message ?: "")
                        view?.findViewById<TextView>(R.id.tvUpdateInfo)?.text = msg
                        App.toast(msg)
                    }
                    hideProgress()
                }
            }
        }
    }

}