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
import androidx.lifecycle.ViewModelProvider
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
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.*

class ServerFinderFragment : TSFragment() {

    private val hostAdapter = HostAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.server_finder_fragment, container, false)

        vi.findViewById<RecyclerView>(R.id.rvHosts)?.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = hostAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
        }

        vi.findViewById<Button>(R.id.btnFindHosts)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Default) {
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
        lifecycleScope.launch(Dispatchers.Default) {
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
        view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = false
        showProgress()
        view?.findViewById<TextView>(R.id.tvCurrentHost)?.text = getLocalIP()
        hostAdapter.clear()
        // add local
        hostAdapter.add(ServerIp("http://127.0.0.1:8090", App.context.getString(R.string.local_server)))
        // add saved
        Settings.getHosts().forEach {
            hostAdapter.add(ServerIp(it, "${App.context.getString(R.string.saved_server)}"))
        }
        // find all
        viewModel = ViewModelProvider(this@ServerFinderFragment).get(ServerFinderViewModel::class.java)
        (viewModel as ServerFinderViewModel).start().observe(this@ServerFinderFragment) {
            if (it.stat == 0) {
                view?.findViewById<TextView>(R.id.tvFindHosts)?.text = it.servIP.host
            } else if (it.stat == 1) {
                hostAdapter.add(it.servIP)
            } else {
                view?.findViewById<TextView>(R.id.tvFindHosts)?.text = ""
                view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = true
                lifecycleScope.launch {
                    this@ServerFinderFragment.hideProgress()
                }
            }
        }
    }

    private fun getLocalIP(): String {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val ret = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback())
                continue
            for (interfaceAddress in networkInterface.getInterfaceAddresses()) {
                val ip = interfaceAddress.getAddress()
                if (ip is Inet4Address) {
                    ret.add(interfaceAddress)
                }
            }
        }
        return ret.map { it.address.hostAddress }.joinToString(", ")
    }
}