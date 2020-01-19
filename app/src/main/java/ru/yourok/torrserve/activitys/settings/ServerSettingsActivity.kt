package ru.yourok.torrserve.activitys.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_server_settings.*
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import kotlin.concurrent.thread

class ServerSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_settings)

        buttonOk.setOnClickListener {
            saveSettings()
            finish()
        }

        buttonCancel.setOnClickListener {
            finish()
        }

        btnServerAddr.requestFocus()
        btnServerAddr.setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }

        val adpEnc = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.encryption_mode))
        adpEnc.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEncryption.setAdapter(adpEnc)

        val adpRetracker = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.retracker_mode))
        adpRetracker.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRetracker.setAdapter(adpRetracker)

        loadSettings()
        checkServer()
    }

    fun loadSettings() {
        thread {
            try {
                Handler(Looper.getMainLooper()).post {
                    btnServerAddr.setText(Preferences.getCurrentHost())
                }
                val sets = Api.serverReadSettings()
                Handler(Looper.getMainLooper()).post {
                    editTextCacheSize.setText((sets.getLong("CacheSize", 0) / (1024 * 1024)).toString())
                    editTextPreloadBufferSize.setText((sets.getLong("PreloadBufferSize", 0) / (1024 * 1024)).toString())
                    spinnerRetracker.setSelection(sets.getInt("RetrackersMode", 0))

                    checkBoxDisableTCP.isChecked = sets.getBoolean("DisableTCP", false)
                    checkBoxDisableUTP.isChecked = sets.getBoolean("DisableUTP", false)
                    checkBoxDisableUPNP.isChecked = sets.getBoolean("DisableUPNP", false)
                    checkBoxDisableDHT.isChecked = sets.getBoolean("DisableDHT", false)
                    checkBoxDisableUpload.isChecked = sets.getBoolean("DisableUpload", false)

                    spinnerEncryption.setSelection(sets.getInt("Encryption", 0))
                    editTextConnectionsLimit.setText(sets.getInt("ConnectionsLimit", 0).toString())
                    editTextConnectionsDhtLimit.setText(sets.getInt("DhtConnectionLimit", 500).toString())
                    editTextDownloadRateLimit.setText(sets.getInt("DownloadRateLimit", 0).toString())
                    editTextUploadRateLimit.setText(sets.getInt("UploadRateLimit", 0).toString())
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, R.string.error_retrieving_settings, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveSettings() {
        thread {
            try {
                val sets = Api.serverReadSettings()

                sets.set("CacheSize", editTextCacheSize.text.toString().toLong() * (1024 * 1024))
                sets.set("PreloadBufferSize", editTextPreloadBufferSize.text.toString().toLong() * (1024 * 1024))
                sets.set("RetrackersMode", spinnerRetracker.selectedItemPosition)
                sets.set("DisableTCP", checkBoxDisableTCP.isChecked)
                sets.set("DisableUTP", checkBoxDisableUTP.isChecked)
                sets.set("DisableUPNP", checkBoxDisableUPNP.isChecked)
                sets.set("DisableDHT", checkBoxDisableDHT.isChecked)
                sets.set("DisableUpload", checkBoxDisableUpload.isChecked)
                sets.set("Encryption", spinnerEncryption.selectedItemPosition)
                sets.set("DownloadRateLimit", editTextDownloadRateLimit.text.toString().toInt())
                sets.set("UploadRateLimit", editTextUploadRateLimit.text.toString().toInt())
                sets.set("ConnectionsLimit", editTextConnectionsLimit.text.toString().toInt())
                sets.set("DhtConnectionLimit", editTextConnectionsDhtLimit.text.toString().toInt())

                thread {
                    try {
                        Api.serverWriteSettings(sets)
                        Api.torrentRestart()
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            App.Toast(getString(R.string.error_sending_settings))
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(App.getContext(), R.string.error_sending_settings, Toast.LENGTH_SHORT).show()
                }
            }
        }.join()
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        checkServer()
    }

    override fun onPause() {
        super.onPause()
        isCheck = false
    }

    var isCheck = false
    fun checkServer() {
        synchronized(isCheck) {
            if (isCheck)
                return
            isCheck = true
        }
        thread {
            while (isCheck) {
                val echo = Api.serverEcho()
                runOnUiThread {
                    if (echo.isNotEmpty())
                        tvVersion.text = ("Server version: " + echo)
                    else
                        tvVersion.setText(R.string.server_not_responding)
                }
                Thread.sleep(1000)
            }
        }
    }
}
