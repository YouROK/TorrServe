package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
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
import ru.yourok.torrserve.settings.Settings.isDisableAccessibilityOnServerSwitchOn
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.Accessibility
import ru.yourok.torrserve.utils.Net.isValidPublicIp4
import java.net.Inet4Address
import java.net.NetworkInterface


class ServerFinderFragment : TSFragment() {

    private val hostAdapter = HostAdapter()
    private var localIPs = ""
    private var selectedServer: ServerIp? = null

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
            selectedServer = it
            vi.findViewById<TextInputEditText>(R.id.etHost)?.setText(it.host)
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

        vi.findViewById<TextInputEditText>(R.id.etHost)?.setText(Settings.getHost().removePrefix("http://"))
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ServerFinderViewModel::class.java]
        val vm = viewModel as ServerFinderViewModel

        vm.getStats().observe(viewLifecycleOwner) { text ->
            view.findViewById<Button>(R.id.btnFindHosts)?.text = text
        }
        vm.getServers().observe(viewLifecycleOwner) {
            hostAdapter.add(it)
        }
        vm.getOnFinish().observe(viewLifecycleOwner) { finished ->
            if (finished) {
                view.findViewById<Button>(R.id.btnFindHosts)?.apply {
                    text = App.context.getString(R.string.find_hosts)
                    isEnabled = true
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    hideProgress()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Default) { update() }
    }

    override fun onStop() {
        lifecycleScope.launch(Dispatchers.Main) {
            hideProgress()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            onResult?.invoke(null)
        }
    }

    override fun onDestroy() {
        if (isViewModelInitialized()) {
            (viewModel as ServerFinderViewModel).stopDiscovery()
        }
        super.onDestroy()
    }

    private fun setHost() {
        if (isViewModelInitialized()) {
            (viewModel as ServerFinderViewModel).stopDiscovery()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var host = view?.findViewById<TextInputEditText>(R.id.etHost)?.text?.toString() ?: return@launch
                var uri = host.toUri()
                // Don't allow current local IP as server address
                uri.host?.let { hst ->
                    val lst = localIPs.split(",").map { ip -> ip.trim() }
                    if (lst.contains(hst)) {
                        App.toast(R.string.not_support_local_ip, true)
                        return@launch
                    }
                }

                if (uri.scheme == null || !uri.scheme!!.contains("http", true))
                    host = "http://$host"

                uri = host.toUri() // no port, set default
                if (uri.port == -1)
                    host += ":8090"

                val oldHost = Settings.getHost()
                val oldAuth = Settings.getServerAuth()
                Settings.setHost(host)
                selectedServer?.takeIf { server -> server.isMdns && server.host == host }?.let { server ->
                    Settings.set("server_auth", server.auth)
                }
                if (Api.echo().startsWith("1.1.")) {
                    App.toast(R.string.not_support_old_server, true)
                    if (!TorrService.isLocal()) {
                        Settings.setHost(oldHost)
                        Settings.set("server_auth", oldAuth)
                        return@launch
                    }
                }

                if (ServerFile().exists() && TorrService.isLocal())
                    TorrService.start()
                else { // unload local service in case switch to remote
                    if (Api.echo().isNotEmpty() && Accessibility.isEnabledService(App.context)) {
                        if (isDisableAccessibilityOnServerSwitchOn()) {
                            Accessibility.enableService(App.context, false)
                        }
                        TorrService.stop()
                        ServerFile().stop() // killall 4 sure
                    }
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

    private suspend fun update() = withContext(Dispatchers.Main) {
        val v = view ?: return@withContext
        showProgress()
        localIPs = withContext(Dispatchers.IO) { getLocalIP() }
        v.findViewById<Button>(R.id.btnFindHosts)?.isEnabled = false
        v.findViewById<TextView>(R.id.tvCurrentIP)?.text = localIPs
        hostAdapter.clear()

        // add local
        val localhost = "http://localhost:8090"
        var status = App.context.getString(R.string.local_server)
        if (TorrService.isLocal())
            status += " · ${App.context.getString(R.string.connected_host)}"
        val localVersion: String = withContext(Dispatchers.IO) {
            Api.remoteEcho(localhost)
        }
        hostAdapter.add(ServerIp(localhost, localVersion, status))
        // add saved servers
        Settings.getHosts().forEach { host ->
            status = App.context.getString(R.string.saved_server)
            if (host == Settings.getHost()) {
                val name = Settings.getDiscoveredServerName(host)
                status = listOfNotNull(name, App.context.getString(R.string.connected_host)).joinToString(" · ")
            }
            val remoteVersion: String = withContext(Dispatchers.IO) {
                Api.remoteEcho(host)
            }
            hostAdapter.add(ServerIp(host, remoteVersion, status))
        }
        // scan network
        (viewModel as ServerFinderViewModel).find()
    }

    private fun getLocalIP(): String {
        var hasVpn = false
        val ret = mutableListOf<String>()

        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (ni.isLoopback) continue // skip loopback

            val name = ni.name
            if (name.startsWith("tun") || name.startsWith("tap") ||
                name.startsWith("wg") || name.startsWith("utun")
            ) {
                hasVpn = true
                continue // don't collect VPN addresses
            }

            if (ni.isPointToPoint) continue // skip other ptp

            ni.interfaceAddresses
                ?.mapNotNull { it.address as? Inet4Address }
                ?.filterNot { isValidPublicIp4(it.hostAddress) }
                ?.mapTo(ret) { it.hostAddress }
        }

        if (hasVpn) ret.add("VPN")
        return ret.joinToString(", ")
    }
}
