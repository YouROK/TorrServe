package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService
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

        hostAdapter.onClick = {
            vi.findViewById<EditText>(R.id.etHost)?.setText(it)
        }

        vi.findViewById<Button>(R.id.btnFindHosts)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Default) {
                update()
            }
        }

        vi.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            popBackStackFragment()
        }

        vi.findViewById<Button>(R.id.btnApply)?.setOnClickListener {
            setHost()
        }

        vi.findViewById<TextView>(R.id.tvConnectedHost)?.text = Settings.getHost()
        vi.findViewById<EditText>(R.id.etHost)?.setText(Settings.getHost())
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.Default) {
            update()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            hideProgress()
        }
    }

    private fun setHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var host = view?.findViewById<EditText>(R.id.etHost)?.text?.toString() ?: return@launch
                var uri = Uri.parse(host)

                if (uri.scheme == null)
                    host = "http://$host"

                uri = Uri.parse(host) // no port with empty scheme
                if (uri.port == -1)
                    host += ":8090"

                val oldHost = Settings.getHost()
                Settings.setHost(host)

                if (Api.echo().startsWith("1.1.")) {
                    App.toast(R.string.not_support_old_server, true)
                    if (!TorrService.isLocal()) {
                        Settings.setHost(oldHost)
                        return@launch
                    }
                }

                if (ServerFile().exists() && TorrService.isLocal())
                    TorrService.start()
                else
                    TorrService.stop()

                val lst = Settings.getHosts().toMutableList()
                lst.add(host)
//                if (lst.size > 10)
//                    lst.removeAt(0)
                Settings.setHosts(lst)
                popBackStackFragment()
            } catch (e: Exception) {
                e.message?.let {
                    App.toast(it)
                }
            }
        }
    }

    private suspend fun update() = withContext(Dispatchers.Main) {
        view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = false
        showProgress()
        view?.findViewById<TextView>(R.id.tvCurrentIP)?.text = getLocalIP()
        hostAdapter.clear()
        // add local
        val host = "http://127.0.0.1:8090"
        var version = App.context.getString(R.string.local_server)
        withContext(Dispatchers.IO) {
            val v = Api.remoteEcho(host)
            if (v.isNotEmpty()) {
                version += " · $v"
            }
        }
        hostAdapter.add(ServerIp(host, version))
        // add saved
        Settings.getHosts().forEach {
            version = App.context.getString(R.string.saved_server)
            withContext(Dispatchers.IO) {
                val v = Api.remoteEcho(it)
                if (v.isNotEmpty()) {
                    version += " · $v"
                }
            }
            hostAdapter.add(ServerIp(it, version))
        }
        // find all
        viewModel = ViewModelProvider(this@ServerFinderFragment)[ServerFinderViewModel::class.java]
        (viewModel as ServerFinderViewModel).getStats().observe(viewLifecycleOwner) {
            view?.findViewById<TextView>(R.id.tvFindHostsPrefix)?.visibility = View.VISIBLE
            view?.findViewById<TextView>(R.id.tvFindHosts)?.text = it
        }
        (viewModel as ServerFinderViewModel).getServers().observe(viewLifecycleOwner) {
            hostAdapter.add(it)
        }
        (viewModel as ServerFinderViewModel).getOnFinish().observe(viewLifecycleOwner) {
            if (it) {
                view?.findViewById<TextView>(R.id.tvFindHostsPrefix)?.visibility = View.INVISIBLE
                view?.findViewById<TextView>(R.id.tvFindHosts)?.text = ""
                view?.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = true
                lifecycleScope.launch {
                    this@ServerFinderFragment.hideProgress()
                }
            }
        }
        (viewModel as ServerFinderViewModel).find()
    }

    private fun getLocalIP(): String {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val ret = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback)
                continue
            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val ip = interfaceAddress.address
                if (ip is Inet4Address) {
                    ret.add(interfaceAddress)
                }
            }
        }
        return ret.joinToString(", ") { it.address.hostAddress }
    }
}