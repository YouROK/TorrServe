package ru.yourok.torrserve.ui.fragments.main.servfinder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Net.isValidPublicIp4
import java.net.*
import java.util.*

data class ServerIp(val host: String, val version: String, val status: String) {
    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        return (other as ServerIp).host == host
    }
}

class ServerFinderViewModel : ViewModel() {
    private var isWork = Any()
    private var stats: MutableLiveData<String>? = null
    private var servers: MutableLiveData<ServerIp>? = null
    private var onFinish: MutableLiveData<Boolean>? = null

    fun getStats(): LiveData<String> {
        if (stats == null)
            stats = MutableLiveData()
        return stats!!
    }

    fun getServers(): MutableLiveData<ServerIp> {
        if (servers == null)
            servers = MutableLiveData()
        return servers!!
    }

    fun getOnFinish(): MutableLiveData<Boolean> {
        if (onFinish == null)
            onFinish = MutableLiveData()
        return onFinish!!
    }

    fun find() {
        update()
    }

    override fun onCleared() {
        isWork = false
        super.onCleared()
    }

    private fun update() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(isWork) {
                if (isWork == true)
                    return@launch
            }
            isWork = true
            withContext(Dispatchers.Main) {
                onFinish?.value = false
            }
            try {
                val ifaces = getIFaces()
                ifaces.forEach { iface ->
                    val ipbytes = iface.address?.hostAddress?.split(".")
                    var ipRange = ""
                    if (ipbytes?.size == 4)
                        ipRange = "${ipbytes[0]}.${ipbytes[1]}.${ipbytes[2]}."
                    if (ipRange.isEmpty())
                        return@forEach
                    iface.address?.hostAddress?.let { findIn(ipRange, it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isWork = false
            withContext(Dispatchers.Main) {
                onFinish?.value = true
            }
        }
    }

    private suspend fun findIn(ipRange: String, local: String) {
        if (isWork != true)
            return
        for (i in 1..254) {
            if (isWork == true) {
                val checkHost = "http://$ipRange$i:8090"

                if ("$ipRange$i" == local)
                    continue
                withContext(Dispatchers.Main) {
                    stats?.value = checkHost.removePrefix("http://")
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val version = Api.remoteEcho(checkHost)
                    var status = if (!Settings.getHosts().contains(checkHost))
                            App.context.getString(R.string.new_server)
                        else ""
                    if (version.isNotEmpty() && (version.startsWith("1.2.") || version.startsWith("MatriX"))) {
//                        status += " · ${App.context.getString(R.string.online)}"
                        withContext(Dispatchers.Main) {
                            servers?.value = ServerIp(checkHost, version, status)
                        }
                    }
                }
            }
        }
    }

    private fun getIFaces(): List<InterfaceAddress> {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val ret = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback) // skip loopbask
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
        return ret
    }

}