package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.adapters.HostAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.TSFragment

class ServerFinderFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch {
            showProgress()
        }

        val vi = inflater.inflate(R.layout.server_finder_fragment, container, false)

        vi.findViewById<RecyclerView>(R.id.rvHosts)?.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = HostAdapter {
                vi.findViewById<EditText>(R.id.etHost)?.setText(it)
                setHost()
            }
            addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
        }

        vi.findViewById<Button>(R.id.btnFindHosts)?.setOnClickListener {
            lifecycleScope.launch {
                update()
            }
        }

        vi.findViewById<Button>(R.id.buttonCancel)?.setOnClickListener {
            popBackStackFragment()
        }

        vi.findViewById<Button>(R.id.buttonOk)?.setOnClickListener {
            setHost()
        }

        vi.findViewById<TextView>(R.id.tvConnectedHost)?.text = Settings.getHost()
        vi.findViewById<EditText>(R.id.etHost)?.setText(Settings.getHost())
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        lifecycleScope.launch {
            update()
        }
    }

    private fun setHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var host = view?.findViewById<EditText>(R.id.etHost)?.text?.toString() ?: return@launch
                val uri = Uri.parse(host)

                if (uri.scheme == null)
                    host = "http://$host"

                if (uri.port == -1)
                    host += ":8090"

                Settings.setHost(host)

                if (ServerFile().exists() && (host.toLowerCase().contains("localhost") || host.toLowerCase().contains("127.0.0.1")))
                    TorrService.start()

                val lst = Settings.getHosts().toMutableList()
                lst.add(host)
                if (lst.size > 10)
                    lst.removeAt(0)
                Settings.setHosts(lst)

                popBackStackFragment()
            } catch (e: Exception) {
                e.message?.let {
                    App.Toast(it)
                }
            }
        }
    }

    private suspend fun update() = withContext(Dispatchers.Main) {
        showProgress()
        view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = false
        val currHosts = (view?.findViewById<RecyclerView>(R.id.rvHosts)?.adapter as HostAdapter?)?.update {
            lifecycleScope.launch(Dispatchers.Main) {
                hideProgress()
                view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = true
            }
        }

        view?.findViewById<TextView>(R.id.tvCurrentHost)?.text = currHosts?.joinToString(", ") ?: return@withContext
    }
}