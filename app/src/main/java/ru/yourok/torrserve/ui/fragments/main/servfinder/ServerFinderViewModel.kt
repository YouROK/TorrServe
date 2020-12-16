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
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.nio.charset.Charset
import java.util.*

////////
// stat
// 0 - check
// 1 - found
data class SFStat(val stat: Int, val servIP: ServerIp)

data class ServerIp(val host: String, val version: String) {
    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        return (other as ServerIp).host == host
    }
}

class ServerFinderViewModel : ViewModel() {
    private var isWork = false
    var data: MutableLiveData<SFStat>? = null

    fun start(): LiveData<SFStat> {
        if (data == null) {
            data = MutableLiveData()
        }
        viewModelScope.launch {
            update()
        }
        return data!!
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
            withContext(Dispatchers.Main) {
                data?.value = SFStat(3, ServerIp("", ""))
            }
            isWork = false
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
                try {
                    withContext(Dispatchers.Main) {
                        data?.value = SFStat(0, ServerIp(checkHost, ""))
                    }
                    val conn = Http(Uri.parse("$checkHost/echo"))
                    conn.setTimeout(1000)
                    val version = conn.getInputStream()?.bufferedReader(Charset.defaultCharset())?.readText() ?: ""
                    if (version.isNotEmpty() && version.startsWith("1.2."))
                        withContext(Dispatchers.Main) {
                            data?.value = SFStat(1, ServerIp(checkHost, version))
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
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