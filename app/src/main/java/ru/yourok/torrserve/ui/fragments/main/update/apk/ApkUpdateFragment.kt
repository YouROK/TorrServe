package ru.yourok.torrserve.ui.fragments.main.update.apk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.ui.fragments.TSFragment

class ApkUpdateFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch { showProgress() }
        val vi = inflater.inflate(R.layout.apk_update_fragment, container, false)
        vi.findViewById<TextView>(R.id.tvCurrentVersion)?.text = getString(R.string.current_version) + ": " + BuildConfig.VERSION_NAME

        vi.findViewById<Button>(R.id.btnUpdate)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                UpdaterApk.installNewVersion {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            vi.findViewById<ProgressBar>(R.id.pbUpdate).setProgress(it, true)
                        else
                            vi.findViewById<ProgressBar>(R.id.pbUpdate).setProgress(it)
                    }
                }
            }
        }

        vi?.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            popBackStackFragment()
        }
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            if (!UpdaterApk.check()) withContext(Dispatchers.Main) {
                App.Toast(R.string.not_found_new_app_update, true)
                hideProgress()
                popBackStackFragment()
            }
            val newVer = UpdaterApk.getVersion()
            val overview = UpdaterApk.getOverview()
            withContext(Dispatchers.Main) {
                view?.findViewById<TextView>(R.id.tvNewVersion)?.text = getString(R.string.new_version) + ": " + newVer
                view?.findViewById<TextView>(R.id.tvOverview)?.text = overview
            }
            hideProgress()
        }
    }
}