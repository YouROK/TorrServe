package ru.yourok.torrserve.ui.fragments.main.servsets

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
                it.text = Settings.getHost().removePrefix("http://")
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
                    vi.findViewById<SwitchMaterial>(R.id.cbSaveOnDisk)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbSaveOnDisk)?.visibility = View.GONE
                    vi.findViewById<SwitchMaterial>(R.id.cbRemoveCacheOnDrop)?.visibility = View.GONE
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
                    //vi.findViewById<TextInputLayout>(R.id.lbPreloadCache)?.isHintEnabled = true
                    vi.findViewById<TextInputLayout>(R.id.lbPreloadCache)?.visibility = View.VISIBLE
                    vi.findViewById<TextInputEditText>(R.id.etPreloadCache)?.visibility = View.VISIBLE
                    vi.findViewById<SwitchMaterial>(R.id.cbPreloadBuffer)?.visibility = View.GONE
                    vi.findViewById<TextView>(R.id.lbPreloadBuffer)?.visibility = View.GONE
                }
            }
            if ( // MatriX.105 add DLNA / disable DhtConnectionLimit
                ver.contains("MatriX", true) &&
                verMajor > 104
            ) {
                withContext(Dispatchers.Main) {
                    //vi.findViewById<TextInputLayout>(R.id.tvConnectionsDhtLimit)?.isHintEnabled = false
                    vi.findViewById<TextInputLayout>(R.id.tvConnectionsDhtLimit)?.visibility = View.GONE
                    vi.findViewById<TextInputEditText>(R.id.etConnectionsDhtLimit)?.visibility = View.GONE
                    vi.findViewById<SwitchMaterial>(R.id.cbEnableDLNA)?.visibility = View.VISIBLE
                }
            }
            if ( // MatriX.120 add Rutor search
                ver.contains("MatriX", true) &&
                verMajor > 119
            ) {
                withContext(Dispatchers.Main) {
                    vi.findViewById<SwitchMaterial>(R.id.cbEnableRutorSearch)?.visibility = View.VISIBLE
                    vi.findViewById<TextView>(R.id.tvEnableRutorSearch)?.visibility = View.VISIBLE
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
                            App.toast(R.string.default_sets_applied)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            //e.message?.let { msg -> App.Toast(msg) }
                            App.toast(R.string.error_sending_settings)
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
                    findViewById<TextInputEditText>(R.id.etCacheSize)?.setText((sets.CacheSize / (1024 * 1024)).toString())
                    findViewById<SwitchMaterial>(R.id.cbPreloadBuffer)?.isChecked = sets.PreloadBuffer
                    findViewById<TextInputEditText>(R.id.etPreloadTorrent)?.setText(sets.ReaderReadAHead.toString())
                    findViewById<TextInputEditText>(R.id.etPreloadCache)?.setText(sets.PreloadCache.toString())
                    findViewById<SwitchMaterial>(R.id.cbSaveOnDisk)?.isChecked = sets.UseDisk
                    findViewById<SwitchMaterial>(R.id.cbRemoveCacheOnDrop)?.isChecked = sets.RemoveCacheOnDrop
                    findViewById<Button>(R.id.btnContentPath)?.text = sets.TorrentsSavePath
                    findViewById<Spinner>(R.id.spinnerRetracker)?.setSelection(sets.RetrackersMode)
                    findViewById<TextInputEditText>(R.id.etDisconnectTimeout)?.setText(sets.TorrentDisconnectTimeout.toString())
                    findViewById<SwitchMaterial>(R.id.cbForceEncrypt)?.isChecked = sets.ForceEncrypt
                    findViewById<SwitchMaterial>(R.id.cbEnableDebug)?.isChecked = sets.EnableDebug
                    findViewById<SwitchMaterial>(R.id.cbEnableDLNA)?.isChecked = sets.EnableDLNA
                    findViewById<SwitchMaterial>(R.id.cbEnableRutorSearch)?.isChecked = sets.EnableRutorSearch
                    findViewById<SwitchMaterial>(R.id.cbEnableIPv6)?.isChecked = sets.EnableIPv6
                    findViewById<SwitchMaterial>(R.id.cbDisableTCP)?.isChecked = !sets.DisableTCP
                    findViewById<SwitchMaterial>(R.id.cbDisableUTP)?.isChecked = !sets.DisableUTP
                    findViewById<SwitchMaterial>(R.id.cbDisableUPNP)?.isChecked = !sets.DisableUPNP
                    findViewById<SwitchMaterial>(R.id.cbDisableDHT)?.isChecked = !sets.DisableDHT
                    findViewById<SwitchMaterial>(R.id.cbDisablePEX)?.isChecked = !sets.DisablePEX
                    findViewById<SwitchMaterial>(R.id.cbDisableUpload)?.isChecked = !sets.DisableUpload
                    findViewById<TextInputEditText>(R.id.etDownloadRateLimit)?.setText(sets.DownloadRateLimit.toString())
                    findViewById<TextInputEditText>(R.id.etUploadRateLimit)?.setText(sets.UploadRateLimit.toString())
                    findViewById<TextInputEditText>(R.id.etConnectionsLimit)?.setText(sets.ConnectionsLimit.toString())
                    findViewById<TextInputEditText>(R.id.etConnectionsDhtLimit)?.setText(sets.DhtConnectionLimit.toString())
                    findViewById<TextInputEditText>(R.id.etPeersListenPort)?.setText(sets.PeersListenPort.toString())
                }
                if (BuildConfig.DEBUG)
                    findViewById<SwitchMaterial>(R.id.cbEnableDebug)?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            App.toast(R.string.error_retrieving_settings)
        }
    }

    suspend fun saveSettings() = withContext(Dispatchers.Main) {
        try {
            view?.apply {
                btsets = BTSets(
                    CacheSize = (findViewById<TextInputEditText>(R.id.etCacheSize)?.text?.toString()?.toLong() ?: 96L) * 1024 * 1024,
                    PreloadBuffer = findViewById<SwitchMaterial>(R.id.cbPreloadBuffer)?.isChecked ?: false,
                    ReaderReadAHead = findViewById<TextInputEditText>(R.id.etPreloadTorrent)?.text?.toString()?.toInt() ?: 95,
                    PreloadCache = findViewById<TextInputEditText>(R.id.etPreloadCache)?.text?.toString()?.toInt() ?: 0,
                    UseDisk = findViewById<SwitchMaterial>(R.id.cbSaveOnDisk)?.isChecked ?: false,
                    RemoveCacheOnDrop = findViewById<SwitchMaterial>(R.id.cbRemoveCacheOnDrop)?.isChecked ?: false,
                    TorrentsSavePath = btsets?.TorrentsSavePath ?: "",
                    ForceEncrypt = findViewById<SwitchMaterial>(R.id.cbForceEncrypt)?.isChecked ?: false,
                    RetrackersMode = findViewById<Spinner>(R.id.spinnerRetracker)?.selectedItemPosition ?: 0,
                    TorrentDisconnectTimeout = findViewById<TextInputEditText>(R.id.etDisconnectTimeout)?.text?.toString()?.toInt() ?: 30,
                    EnableDebug = findViewById<SwitchMaterial>(R.id.cbEnableDebug)?.isChecked ?: false,
                    EnableDLNA = findViewById<SwitchMaterial>(R.id.cbEnableDLNA)?.isChecked ?: false,
                    EnableRutorSearch = findViewById<SwitchMaterial>(R.id.cbEnableRutorSearch)?.isChecked ?: false,
                    EnableIPv6 = findViewById<SwitchMaterial>(R.id.cbEnableIPv6)?.isChecked ?: false,
                    DisableTCP = findViewById<SwitchMaterial>(R.id.cbDisableTCP)?.isChecked != true,
                    DisableUTP = findViewById<SwitchMaterial>(R.id.cbDisableUTP)?.isChecked != true,
                    DisableUPNP = findViewById<SwitchMaterial>(R.id.cbDisableUPNP)?.isChecked != true,
                    DisableDHT = findViewById<SwitchMaterial>(R.id.cbDisableDHT)?.isChecked != true,
                    DisablePEX = findViewById<SwitchMaterial>(R.id.cbDisablePEX)?.isChecked != true,
                    DisableUpload = findViewById<SwitchMaterial>(R.id.cbDisableUpload)?.isChecked != true,
                    DownloadRateLimit = findViewById<TextInputEditText>(R.id.etDownloadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    UploadRateLimit = findViewById<TextInputEditText>(R.id.etUploadRateLimit)?.text?.toString()?.toInt() ?: 0,
                    ConnectionsLimit = findViewById<TextInputEditText>(R.id.etConnectionsLimit)?.text?.toString()?.toInt() ?: 23,
                    DhtConnectionLimit = findViewById<TextInputEditText>(R.id.etConnectionsDhtLimit)?.text?.toString()?.toInt() ?: 500,
                    PeersListenPort = findViewById<TextInputEditText>(R.id.etPeersListenPort)?.text?.toString()?.toInt() ?: 0,
                )
                btsets?.let { sets ->
                    withContext(Dispatchers.IO) {
                        saveSettings(sets)
                        App.toast(R.string.done_sending_settings)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            App.toast(R.string.error_sending_settings)
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
        return try {
            Api.getSettings()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}