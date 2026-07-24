package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Net.isValidPublicIp4
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Enumeration

data class ServerIp(
    val host: String,
    val version: String,
    val status: String,
    val auth: String = "",
    val isMdns: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is ServerIp && other.host == host
}

class ServerFinderViewModel : ViewModel() {
    companion object {
        /** DNS-SD service type advertised by TorrServer instances. */
        const val MDNS_SERVICE = "_torrserver._tcp"
    }

    @Volatile
    private var isWork = false
    private var discoveryJob: Job? = null
    private val workLock = Any()
    private var stats: MutableLiveData<String>? = null
    private var servers: MutableLiveData<ServerIp>? = null
    private var onFinish: MutableLiveData<Boolean>? = null
    private val nsdLock = Any()
    private val resolvingServices = Collections.synchronizedSet(mutableSetOf<String>())
    private val discoveredHosts = Collections.synchronizedSet(mutableSetOf<String>())
    private val mdnsServers = Collections.synchronizedMap(mutableMapOf<String, ServerIp>())
    private val pendingServices = ArrayDeque<NsdServiceInfo>()
    private var isResolvingService = false
    private val nsdManager by lazy {
        App.context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun getStats(): LiveData<String> = (stats ?: MutableLiveData<String>().also { stats = it })

    fun getServers(): LiveData<ServerIp> = (servers ?: MutableLiveData<ServerIp>().also { servers = it })

    fun getOnFinish(): LiveData<Boolean> = (onFinish ?: MutableLiveData<Boolean>().also { onFinish = it })

    fun find() {
        synchronized(workLock) {
            if (isWork) return
            isWork = true
        }
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            onFinish?.postValue(false)
            resolvingServices.clear()
            try {
                startMdnsDiscovery()
                getIFaces().forEach { iface -> scanInterface(iface) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                synchronized(workLock) { isWork = false }
                onFinish?.postValue(true)
            }
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    /** Stops the persistent mDNS session when the server-finder page is left. */
    fun stopDiscovery() {
        synchronized(workLock) { isWork = false }
        discoveryJob?.cancel()
        discoveryJob = null
        stopMdnsDiscovery()
    }

    fun getMdnsServers(): List<ServerIp> = synchronized(mdnsServers) { mdnsServers.values.toList() }

    private suspend fun scanInterface(iface: InterfaceAddress) {
        val local = iface.address?.hostAddress ?: return
        val bytes = local.split('.')
        if (bytes.size != 4) return
        val ipRange = "${bytes[0]}.${bytes[1]}.${bytes[2]}."

        coroutineScope {
            val requests = Semaphore(32)
            (1..254).map { number ->
                async(Dispatchers.IO) {
                    if (!isWork || "$ipRange$number" == local) return@async
                    requests.withPermit {
                        if (!isWork) return@withPermit
                        val host = "http://$ipRange$number:8090"
                        stats?.postValue(host.removePrefix("http://"))
                        val version = Api.remoteEcho(host)
                        if (version.isNotEmpty() && (version.startsWith("1.2.") || version.startsWith("MatriX"))) {
                            publishServer(host, version)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun startMdnsDiscovery() {
        synchronized(nsdLock) {
            if (discoveryListener != null) return
            pendingServices.clear()
            isResolvingService = false
            discoveredHosts.clear()
            mdnsServers.clear()
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    // Android may add a trailing dot to the returned service type, so the
                    // active discovery request is the reliable filter here.
                    if (!resolvingServices.add(service.serviceName)) return
                    synchronized(nsdLock) {
                        pendingServices.addLast(service)
                    }
                    resolveNextService()
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    resolvingServices.remove(service.serviceName)
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopMdnsDiscovery()
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            discoveryListener = listener
            try {
                nsdManager.discoverServices(MDNS_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: IllegalArgumentException) {
                discoveryListener = null
            }
        }
    }

    private fun stopMdnsDiscovery() {
        val listener = synchronized(nsdLock) {
            pendingServices.clear()
            isResolvingService = false
            mdnsServers.clear()
            discoveryListener.also { discoveryListener = null }
        } ?: return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Discovery may already have failed or been stopped by the framework.
        }
    }

    /**
     * On API 16-33, NsdManager's deprecated resolve API supports one in-flight request.
     * Queue services so a busy resolver does not discard another discovered TorrServer.
     */
    private fun resolveNextService() {
        val service = synchronized(nsdLock) {
            if (isResolvingService) return
            generateSequence { if (pendingServices.isEmpty()) null else pendingServices.removeFirst() }
                .firstOrNull { resolvingServices.contains(it.serviceName) }
                ?.also { isResolvingService = true }
        } ?: return

        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolvingServices.remove(serviceInfo.serviceName)
                    onServiceResolutionFinished()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (!resolvingServices.contains(serviceInfo.serviceName)) {
                        onServiceResolutionFinished()
                        return
                    }
                    serviceInfo.resolveUrl()?.let { url ->
                        val auth = serviceInfo.resolveAuth()
                        viewModelScope.launch(Dispatchers.IO) {
                            publishMdnsServer(url, Api.remoteEcho(url, auth), auth, serviceInfo.serviceName)
                        }
                    }
                    onServiceResolutionFinished()
                }
            })
        } catch (_: IllegalArgumentException) {
            resolvingServices.remove(service.serviceName)
            onServiceResolutionFinished()
        }
    }

    private fun onServiceResolutionFinished() {
        synchronized(nsdLock) { isResolvingService = false }
        resolveNextService()
    }

    private fun publishServer(host: String, version: String) {
        if (!discoveredHosts.add(host)) return
        val status = if (Settings.getHosts().contains(host)) "" else App.context.getString(R.string.new_server)
        servers?.postValue(ServerIp(host, version, status))
    }

    private fun publishMdnsServer(host: String, version: String, auth: String, serviceName: String) {
        val status = if (host == Settings.getHost()) {
            "$serviceName · ${App.context.getString(R.string.connected_host)}"
        } else {
            serviceName
        }
        Settings.setDiscoveredServerName(host, serviceName)
        synchronized(mdnsServers) {
            mdnsServers[host] = ServerIp(host, version, status, auth, isMdns = true)
        }
        if (discoveredHosts.add(host)) {
            servers?.postValue(ServerIp(host, version, status, auth, isMdns = true))
        }
    }

    private fun NsdServiceInfo.resolveUrl(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            attributes["url"]?.decodeToString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val address = host?.hostAddress ?: return null
        val resolvedHost = if (address.contains(':')) "[$address]" else address
        return "http://$resolvedHost:$port"
    }

    private fun NsdServiceInfo.resolveAuth(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return ""
        val user = attributes["user"]?.decodeToString() ?: return ""
        val password = attributes["password"]?.decodeToString() ?: return ""
        return "$user:$password"
    }

    private fun getIFaces(): List<InterfaceAddress> {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val ret = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || networkInterface.isPointToPoint) continue
            networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                val ip = interfaceAddress.address
                if (ip is Inet4Address && !isValidPublicIp4(ip.hostAddress)) ret.add(interfaceAddress)
            }
        }
        return ret
    }
}
