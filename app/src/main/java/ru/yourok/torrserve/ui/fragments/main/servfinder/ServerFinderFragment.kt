package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
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
import ru.yourok.torrserve.utils.Accessibility
import ru.yourok.torrserve.utils.Net.isValidPublicIp4
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Enumeration


class ServerFinderFragment : TSFragment() {

    private val hostAdapter = HostAdapter()
    private var ips = ""

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
            vi.findViewById<TextInputEditText>(R.id.etHost)?.setText(it)
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

        //vi.findViewById<TextView>(R.id.tvConnectedHost)?.text = Settings.getHost().removePrefix("http://")
        vi.findViewById<TextInputEditText>(R.id.etHost)?.setText(Settings.getHost().removePrefix("http://"))
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.Default) {
            update()
        }
    }

    override fun onDestroyView() {
        lifecycleScope.launch {
            hideProgress()
        }
        super.onDestroyView()
        lifecycleScope.launch {
            onResult?.invoke(null)
        }
    }

    private fun setHost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var host = view?.findViewById<TextInputEditText>(R.id.etHost)?.text?.toString() ?: return@launch
                var uri = Uri.parse(host)
                // Don't allow current local IP as server address
                uri.host?.let { hst ->
                    val lst = ips.split(",").map { ip -> ip.trim() }
                    if (lst.contains(hst)) {
                        App.toast(R.string.not_support_local_ip, true)
                        return@launch
                    }
                }

                if (uri.scheme == null || !uri.scheme!!.contains("http", true))
                    host = "http://$host"

                uri = Uri.parse(host) // no port, set default
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
                else { // unload local service in case switch to remote
                    if (Api.echo().isNotEmpty() && Accessibility.isEnabledService(App.context))
                        Accessibility.enableService(App.context, false)
                    TorrService.stop()
                    ServerFile().stop() // killall 4 sure
                }

                val lst = Settings.getHosts().toMutableList()
                lst.add(host)
                Settings.setHosts(lst)
                popBackStackFragment()
            } catch (e: Exception) {
                e.message?.let {
                    App.toast(it)
                }
            }
        }
    }

    @SuppressLint("FragmentLiveDataObserve")
    private suspend fun update() = withContext(Dispatchers.Main) {
        view?.let {
            showProgress()
            ips = withContext(Dispatchers.IO) { getLocalIP() }

            val btnFind = view?.findViewById<Button>(R.id.btnFindHosts)

            btnFind?.isEnabled = false
            view?.findViewById<TextView>(R.id.tvCurrentIP)?.text = ips
            hostAdapter.clear()
            // add local
            val localhost = "http://localhost:8090"
            var status = App.context.getString(R.string.local_server)
            if (TorrService.isLocal())
                status += " · ${App.context.getString(R.string.connected_host)}"
            val localVersion: String = withContext(Dispatchers.IO) {
                Api.remoteEcho(localhost).also {
                    if (it.isNotEmpty()) {
                        status += " · ${App.context.getString(R.string.online)}"
                    }
                }
            }
            hostAdapter.add(ServerIp(localhost, localVersion, status))
            // add saved
            Settings.getHosts().forEach { host ->
                status = App.context.getString(R.string.saved_server)
                if (host == Settings.getHost())
                    status = App.context.getString(R.string.connected_host)
                val remoteVersion: String = withContext(Dispatchers.IO) {
                    Api.remoteEcho(host).also {
                        if (it.isNotEmpty()) {
                            status += " · ${App.context.getString(R.string.online)}"
                        }
                    }
                }
                hostAdapter.add(ServerIp(host, remoteVersion, status))
            }
            // find on local network
            viewModel = ViewModelProvider(this@ServerFinderFragment)[ServerFinderViewModel::class.java]
            // java.lang.IllegalStateException: Can't access the Fragment View's LifecycleOwner when getView() is null
            // i.e., before onCreateView() or after onDestroyView()
            (viewModel as ServerFinderViewModel).getStats().observe(this@ServerFinderFragment) {
                btnFind?.text = it
            }
            (viewModel as ServerFinderViewModel).getServers().observe(this@ServerFinderFragment) {
                hostAdapter.add(it)
            }
            (viewModel as ServerFinderViewModel).getOnFinish().observe(this@ServerFinderFragment) {
                if (it) {
                    btnFind?.text = App.context.getString(R.string.find_hosts)
                    btnFind?.isEnabled = true
                    lifecycleScope.launch {
                        this@ServerFinderFragment.hideProgress()
                    }
                }
            }

            (viewModel as ServerFinderViewModel).find()
        }
    }

    private fun getLocalIP(): String {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val ret = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback) // skip loopback
                continue
            if (networkInterface.isPointToPoint) // skip ptp / vpn
                continue
            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val ip = interfaceAddress.address
                if (ip is Inet4Address && !isValidPublicIp4(ip.hostAddress)) {
                    ret.add(interfaceAddress)
                }
            }
        }

        return ret.joinToString(", ") { it.address.hostAddress ?: "" }
    }

}