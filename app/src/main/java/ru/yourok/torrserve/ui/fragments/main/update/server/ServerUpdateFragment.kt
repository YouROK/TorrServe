package ru.yourok.torrserve.ui.fragments.main.update.server

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.fragments.TSFragment

class ServerUpdateFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch { showProgress() }
        val vi = inflater.inflate(R.layout.server_update_fragment, container, false)
        vi.findViewById<TextView>(R.id.tvArch).text = UpdaterServer.getArch()
        vi.findViewById<TextView>(R.id.tvLocalVersion).text = UpdaterServer.getLocalVersion()

        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            
            hideProgress()
        }
    }
}