package ru.yourok.torrserve.ui.fragments.main.servsets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.ui.fragments.TSFragment

class ServerSettingsFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch {
            showProgress()
        }

        val vi = inflater.inflate(R.layout.server_settings_fragment, container, false)

        vi.findViewById<Button>(R.id.btnOk)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                showProgress()
                saveSettings()
                hideProgress()
                withContext(Dispatchers.Main) { popBackStackFragment() }
            }
        }

        vi.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            popBackStackFragment()
        }

        val adpRetracker = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.retracker_mode))
        adpRetracker.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vi.findViewById<Spinner>(R.id.spinnerRetracker)?.setAdapter(adpRetracker)

        return vi
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { load() }
    }

    private var btsets: BTSets? = null

    private suspend fun load() = withContext(Dispatchers.Main) {
        showProgress()
        viewModel = ViewModelProvider(this@ServerSettingsFragment).get(ServSetsViewModel::class.java)
        val data = (viewModel as ServSetsViewModel).loadSettings()
        data.observe(this@ServerSettingsFragment) {
            btsets = it
            lifecycleScope.launch {
                updateUI()
                hideProgress()
            }
        }
    }

    private suspend fun updateUI() = withContext(Dispatchers.Main) {
        try {
            view?.apply {
                btsets?.let { sets ->
                    findViewById<EditText>(R.id.etCacheSize)?.setText((sets.CacheSize / (1024 * 1024)).toString())
                    findViewById<EditText>(R.id.etPreloadBufferSize)?.setText((sets.PreloadBufferSize / (1024 * 1024)).toString())
                    findViewById<CheckBox>(R.id.cbSaveOnDisk)?.isChecked = sets.SaveOnDisk
                    findViewById<EditText>(R.id.etContentPath)?.setText(sets.ContentPath)
                    findViewById<Spinner>(R.id.spinnerRetracker)?.setSelection(sets.RetrackersMode)
                    findViewById<EditText>(R.id.etDisconnectTimeout)?.setText(sets.TorrentDisconnectTimeout.toString())
                    findViewById<CheckBox>(R.id.cbEnableIPv6)?.isChecked = sets.EnableIPv6
                    findViewById<CheckBox>(R.id.cbDisableTCP)?.isChecked = sets.DisableTCP
                    findViewById<CheckBox>(R.id.cbDisableUTP)?.isChecked = sets.DisableUTP
                    findViewById<CheckBox>(R.id.cbDisableUPNP)?.isChecked = sets.DisableUPNP
                    findViewById<CheckBox>(R.id.cbDisableDHT)?.isChecked = sets.DisableDHT
                    findViewById<CheckBox>(R.id.cbDisableUpload)?.isChecked = sets.DisableUpload
                    findViewById<EditText>(R.id.etDownloadRateLimit)?.setText(sets.DownloadRateLimit.toString())
                    findViewById<EditText>(R.id.etUploadRateLimit)?.setText(sets.UploadRateLimit.toString())
                    findViewById<EditText>(R.id.etConnectionsLimit)?.setText(sets.ConnectionsLimit.toString())
                    findViewById<EditText>(R.id.etConnectionsDhtLimit)?.setText(sets.DhtConnectionLimit.toString())
                    findViewById<EditText>(R.id.etPeersListenPort)?.setText(sets.PeersListenPort.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            App.Toast(R.string.error_retrieving_settings)
        }
    }

    suspend fun saveSettings() = withContext(Dispatchers.Main) {
        try {
            view?.apply {
                btsets = BTSets(
                    findViewById<EditText>(R.id.etCacheSize)?.text?.toString()?.toLong() ?: 200L * 1024 * 1024,
                    findViewById<EditText>(R.id.etPreloadBufferSize)?.text?.toString()?.toLong() ?: 40L * 1024 * 1024,
                    findViewById<CheckBox>(R.id.cbSaveOnDisk)?.isChecked ?: false,
                    findViewById<EditText>(R.id.etContentPath)?.text?.toString() ?: "",
                    findViewById<Spinner>(R.id.spinnerRetracker)?.selectedItemPosition ?: 0,
                    findViewById<EditText>(R.id.etDisconnectTimeout)?.text?.toString()?.toInt() ?: 30,
                    false,
                    findViewById<CheckBox>(R.id.cbEnableIPv6)?.isChecked ?: false,
                    findViewById<CheckBox>(R.id.cbDisableTCP)?.isChecked ?: false,
                    findViewById<CheckBox>(R.id.cbDisableUTP)?.isChecked ?: true,
                    findViewById<CheckBox>(R.id.cbDisableUPNP)?.isChecked ?: false,
                    findViewById<CheckBox>(R.id.cbDisableDHT)?.isChecked ?: false,
                    findViewById<CheckBox>(R.id.cbDisableUpload)?.isChecked ?: false,
                    findViewById<EditText>(R.id.etDownloadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    findViewById<EditText>(R.id.etUploadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    findViewById<EditText>(R.id.etConnectionsLimit)?.text?.toString()?.toInt() ?: 20,
                    findViewById<EditText>(R.id.etConnectionsDhtLimit)?.text?.toString()?.toInt() ?: 500,
                    findViewById<EditText>(R.id.etPeersListenPort)?.text?.toString()?.toInt() ?: 0,
                )
                btsets?.let { sets ->
                    withContext(Dispatchers.IO) {
                        Api.setSettings(sets)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            App.Toast(R.string.error_sending_settings)
        }
    }
}