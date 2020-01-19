package ru.yourok.torrserve.server.finder

import ru.yourok.torrserve.utils.Http
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


data class ServerIp(val host: String, val version: String)

class FinderServer {
    private var localIpLost = listOf<InterfaceAddress>()
    private val lock = Any()
    private var isFind = false

    init {
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        val lstIp = mutableListOf<InterfaceAddress>()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback())
                continue
            for (interfaceAddress in networkInterface.getInterfaceAddresses()) {
                val ip = interfaceAddress.getAddress()
                if (ip is Inet4Address) {
                    lstIp.add(interfaceAddress)
                }
            }
        }
        localIpLost = lstIp
    }

    fun getLocalIPs(): List<InterfaceAddress> {
        return localIpLost
    }

    fun findServers(iface: InterfaceAddress, onFind: (ServerIp) -> Unit) {
        if (!iface.address.hostAddress.startsWith("192.168.") &&
                !iface.address.hostAddress.startsWith("72.16.") &&
                !iface.address.hostAddress.startsWith("10.0.")) {
            return
        }

        synchronized(lock) {
            if (isFind)
                return
            isFind = true
        }

        val ipbytes = iface.address.hostAddress.split(".")
        var ipFind = ""
        if (ipbytes.size == 4)
            ipFind = "${ipbytes[0]}.${ipbytes[1]}.${ipbytes[2]}."
        if (ipFind.isEmpty())
            return

        val pool = Executors.newFixedThreadPool(254)
        val lk = Any()
        var count = 1
        for (i in 1..254) {
            pool.submit {
                var checkHost = ""
                var index = 0
                synchronized(lk) {
                    index = count
                    count++
                }
                checkHost = "http://$ipFind$index:8090"

                if (ipFind + index == iface.address.hostAddress)
                    checkHost = "http://127.0.0.1:8090"
                try {
                    val version = Http("$checkHost/echo").readTimeout(5000)
                    if (version.isNotEmpty())
                        onFind(ServerIp(checkHost, version))
                } catch (e: Exception) {
                }
            }
            Thread.sleep(1)
        }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.MINUTES)
        isFind = false
    }
}