package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.utils.Http
import java.net.*
import java.nio.charset.Charset
import java.util.*

data class ServerIp(val host: String, val version: String) {
    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        return (other as ServerIp).host == host
    }
}

class ServerFinderViewModel : ViewModel() {
    private var isWork = false
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
                if (isWork)
                    return@launch
            }
            isWork = true
            withContext(Dispatchers.Main) {
                onFinish?.value = false
            }
            try {
                val ifaces = getIFaces()
                ifaces.forEach { iface ->
                    val ipbytes = iface.address.hostAddress.split(".")
                    var ipRange = ""
                    if (ipbytes.size == 4)
                        ipRange = "${ipbytes[0]}.${ipbytes[1]}.${ipbytes[2]}."
                    if (ipRange.isEmpty())
                        return@forEach
                    findIn(ipRange, iface.address.hostAddress)
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
        if (!isWork)
            return
        for (i in 1..254) {
            if (isWork) {
                val checkHost = "http://$ipRange$i:8090"

                if ("$ipRange$i" == local)
                    continue
                withContext(Dispatchers.Main) {
                    stats?.value = checkHost
                }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val conn = Http(Uri.parse("$checkHost/echo"))
                        conn.setTimeout(1000)
                        conn.connect()
                        conn.getInputStream()?.apply {
                            val version = bufferedReader(Charset.defaultCharset())?.readText() ?: ""
                            if (version.isNotEmpty() && (version.startsWith("1.2.") || version.startsWith("MatriX")))
                                withContext(Dispatchers.Main) {
                                    servers?.value = ServerIp(checkHost, version)
                                }

                            close()
                        }
                    } catch (e: Exception) {
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
            if (networkInterface.isLoopback())
                continue
            for (interfaceAddress in networkInterface.getInterfaceAddresses()) {
                val ip = interfaceAddress.getAddress()
                if (ip is Inet4Address) {
                    ret.add(interfaceAddress)
                }
            }
        }
        return ret
    }

}