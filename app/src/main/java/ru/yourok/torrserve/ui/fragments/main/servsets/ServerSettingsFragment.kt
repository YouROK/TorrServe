package ru.yourok.torrserve.ui.fragments.main.servsets

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.dialogs.DirectoryDialog
import ru.yourok.torrserve.ui.fragments.TSFragment

class ServerSettingsFragment : TSFragment() {

    private var btsets: BTSets? = null
    private var loaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch {
            showProgress()
        }

        val vi = inflater.inflate(R.layout.server_settings_fragment, container, false)

        vi.findViewById<TextView>(R.id.tvServerAddr).let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                it.visibility = View.INVISIBLE
            else {
                it.visibility = View.VISIBLE
                it.text = Settings.getHost()
            }
        }
        vi.findViewById<Button>(R.id.btnContentPath)?.let {
            it.setOnClickListener { _ ->
                DirectoryDialog.show(context ?: return@setOnClickListener, "") { path ->
                    it.text = path
                    btsets?.TorrentsSavePath = path
                    lifecycleScope.launch {
                        updateUI()
                    }
                }
            }
            it.isEnabled = TorrService.isLocal()
        }
        // hide disk cache options for older server versions
        lifecycleScope.launch(Dispatchers.IO) {
            val ver = Api.echo()
            val numbers = Regex("[0-9]+").findAll(ver)
                .map(MatchResult::value)
                .toList()
            val verMajor = numbers.firstOrNull()?.toIntOrNull() ?: 0
            //val verMinor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
            if ( // MatriX.94 is 1st disk cache release
                ver.contains("MatriX", true) &&
                verMajor < 94
            ) {
                withContext(Dispatchers.Main) {
                    vi.findViewById<CheckBox>(R.id.cbSaveOnDisk)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbSaveOnDisk)?.visibility = View.GONE
                    vi.findViewById<CheckBox>(R.id.cbRemoveCacheOnDrop)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbRemoveCacheOnDrop)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbContentPath)?.visibility = View.GONE
                    vi.findViewById<Button>(R.id.btnContentPath)?.visibility = View.GONE
                }
            }
            if ( // MatriX.101 add PreloadCache
                ver.contains("MatriX", true) &&
                verMajor > 100
            ) {
                withContext(Dispatchers.Main) {
                    vi.findViewById<TextView>(R.id.lbPreloadCache)?.visibility = View.VISIBLE
                    vi.findViewById<EditText>(R.id.etPreloadCache)?.visibility = View.VISIBLE
                    vi.findViewById<CheckBox>(R.id.cbPreloadBuffer)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbPreloadBuffer)?.visibility = View.GONE
                }
            }
        }

        vi.findViewById<Button>(R.id.btnApply)?.setOnClickListener {
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

        val adpRetracker = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.retracker_mode))
        adpRetracker.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vi.findViewById<Spinner>(R.id.spinnerRetracker)?.adapter = adpRetracker

        vi.findViewById<Button>(R.id.btnDefaultSets)?.let {
            it.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        Api.defSettings()
                        withContext(Dispatchers.Main) {
                            App.Toast(R.string.default_sets_applied)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            //e.message?.let { msg -> App.Toast(msg) }
                            App.Toast(R.string.error_sending_settings)
                        }
                    }
                    popBackStackFragment()
                }
            }
        }

        return vi
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { load() }
    }

    private suspend fun load() = withContext(Dispatchers.Main) {
        if (!loaded) {
            showProgress()
            withContext(Dispatchers.IO) { btsets = loadSettings() }
            updateUI()
            hideProgress()
            loaded = true
        }
    }

    private suspend fun updateUI() = withContext(Dispatchers.Main) {
        try {
            view?.apply {
                btsets?.let { sets ->
                    findViewById<EditText>(R.id.etCacheSize)?.setText((sets.CacheSize / (1024 * 1024)).toString())
                    findViewById<CheckBox>(R.id.cbPreloadBuffer)?.isChecked = sets.PreloadBuffer
                    findViewById<EditText>(R.id.etPreloadTorrent)?.setText(sets.ReaderReadAHead.toString())
                    findViewById<EditText>(R.id.etPreloadCache)?.setText(sets.PreloadCache.toString())
                    findViewById<CheckBox>(R.id.cbSaveOnDisk)?.isChecked = sets.UseDisk
                    findViewById<CheckBox>(R.id.cbRemoveCacheOnDrop)?.isChecked = sets.RemoveCacheOnDrop
                    findViewById<Button>(R.id.btnContentPath)?.text = sets.TorrentsSavePath
                    findViewById<Spinner>(R.id.spinnerRetracker)?.setSelection(sets.RetrackersMode)
                    findViewById<EditText>(R.id.etDisconnectTimeout)?.setText(sets.TorrentDisconnectTimeout.toString())
                    findViewById<CheckBox>(R.id.cbForceEncrypt)?.isChecked = sets.ForceEncrypt
                    findViewById<CheckBox>(R.id.cbEnableDebug)?.isChecked = sets.EnableDebug
                    findViewById<CheckBox>(R.id.cbEnableIPv6)?.isChecked = sets.EnableIPv6
                    findViewById<CheckBox>(R.id.cbDisableTCP)?.isChecked = !sets.DisableTCP
                    findViewById<CheckBox>(R.id.cbDisableUTP)?.isChecked = !sets.DisableUTP
                    findViewById<CheckBox>(R.id.cbDisableUPNP)?.isChecked = !sets.DisableUPNP
                    findViewById<CheckBox>(R.id.cbDisableDHT)?.isChecked = !sets.DisableDHT
                    findViewById<CheckBox>(R.id.cbDisablePEX)?.isChecked = !sets.DisablePEX
                    findViewById<CheckBox>(R.id.cbDisableUpload)?.isChecked = !sets.DisableUpload
                    findViewById<EditText>(R.id.etDownloadRateLimit)?.setText(sets.DownloadRateLimit.toString())
                    findViewById<EditText>(R.id.etUploadRateLimit)?.setText(sets.UploadRateLimit.toString())
                    findViewById<EditText>(R.id.etConnectionsLimit)?.setText(sets.ConnectionsLimit.toString())
                    findViewById<EditText>(R.id.etConnectionsDhtLimit)?.setText(sets.DhtConnectionLimit.toString())
                    findViewById<EditText>(R.id.etPeersListenPort)?.setText(sets.PeersListenPort.toString())
                }
                if (BuildConfig.DEBUG)
                    findViewById<CheckBox>(R.id.cbEnableDebug)?.visibility = View.VISIBLE
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
                    CacheSize = (findViewById<EditText>(R.id.etCacheSize)?.text?.toString()?.toLong() ?: 96L) * 1024 * 1024,
                    PreloadBuffer = findViewById<CheckBox>(R.id.cbPreloadBuffer)?.isChecked ?: false,
                    ReaderReadAHead = findViewById<EditText>(R.id.etPreloadTorrent)?.text?.toString()?.toInt() ?: 95,
                    PreloadCache = findViewById<EditText>(R.id.etPreloadCache)?.text?.toString()?.toInt() ?: 0,
                    UseDisk = findViewById<CheckBox>(R.id.cbSaveOnDisk)?.isChecked ?: false,
                    RemoveCacheOnDrop = findViewById<CheckBox>(R.id.cbRemoveCacheOnDrop)?.isChecked ?: false,
                    TorrentsSavePath = btsets?.TorrentsSavePath ?: "",
                    ForceEncrypt = findViewById<CheckBox>(R.id.cbForceEncrypt)?.isChecked ?: false,
                    RetrackersMode = findViewById<Spinner>(R.id.spinnerRetracker)?.selectedItemPosition ?: 0,
                    TorrentDisconnectTimeout = findViewById<EditText>(R.id.etDisconnectTimeout)?.text?.toString()?.toInt() ?: 30,
                    EnableDebug = findViewById<CheckBox>(R.id.cbEnableDebug)?.isChecked ?: false,
                    EnableIPv6 = findViewById<CheckBox>(R.id.cbEnableIPv6)?.isChecked ?: false,
                    DisableTCP = findViewById<CheckBox>(R.id.cbDisableTCP)?.isChecked != true,
                    DisableUTP = findViewById<CheckBox>(R.id.cbDisableUTP)?.isChecked != true,
                    DisableUPNP = findViewById<CheckBox>(R.id.cbDisableUPNP)?.isChecked != true,
                    DisableDHT = findViewById<CheckBox>(R.id.cbDisableDHT)?.isChecked != true,
                    DisablePEX = findViewById<CheckBox>(R.id.cbDisablePEX)?.isChecked != true,
                    DisableUpload = findViewById<CheckBox>(R.id.cbDisableUpload)?.isChecked != true,
                    DownloadRateLimit = findViewById<EditText>(R.id.etDownloadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    UploadRateLimit = findViewById<EditText>(R.id.etUploadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    ConnectionsLimit = findViewById<EditText>(R.id.etConnectionsLimit)?.text?.toString()?.toInt() ?: 23,
                    DhtConnectionLimit = findViewById<EditText>(R.id.etConnectionsDhtLimit)?.text?.toString()?.toInt() ?: 500,
                    PeersListenPort = findViewById<EditText>(R.id.etPeersListenPort)?.text?.toString()?.toInt() ?: 0,
                )
                btsets?.let { sets ->
                    withContext(Dispatchers.IO) {
                        saveSettings(sets)
                        App.Toast(R.string.done_sending_settings)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            App.Toast(R.string.error_sending_settings)
        }
    }

    private fun saveSettings(sets: BTSets) {
        try {
            Api.setSettings(sets)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSettings(): BTSets? {
        try {
            return Api.getSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}