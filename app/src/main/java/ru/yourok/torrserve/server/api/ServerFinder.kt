package ru.yourok.torrserve.server.api

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Поиск серверов Silo в локальной сети: перебор адресов подсети и проверка /api/system/ping.
 * Сервер отдаёт версию, поэтому в списке видно, что найдено.
 */
object ServerFinder {

    data class Found(val url: String, val address: String, val version: String)

    const val DEFAULT_PORT = 8090

    /** Адреса /24 подсетей, в которых есть наш сетевой интерфейс. */
    private fun localSubnets(): List<String> {
        val result = mutableListOf<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr !is Inet4Address) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("127.")) continue
                    val prefix = host.substringBeforeLast('.')
                    if (prefix.isNotEmpty() && !result.contains(prefix)) result += prefix
                }
            }
        } catch (e: Exception) {
            // без сетевых интерфейсов список останется пустым
        }
        return result
    }

    /**
     * Сканирует сеть. [onProgress] - доля выполненного 0..1, [onFound] - найденный сервер.
     * Блокирующий вызов: запускать в фоновом потоке, останавливать через [isActive].
     */
    fun scan(
        port: Int = DEFAULT_PORT,
        isActive: () -> Boolean,
        onProgress: (Int) -> Unit,
        onFound: (Found) -> Unit,
    ) {
        val subnets = localSubnets()
        if (subnets.isEmpty()) return
        val hosts = subnets.flatMap { prefix -> (1..254).map { "$prefix.$it" } }.distinct()
        val total = hosts.size
        val done = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(32)
        try {
            hosts.forEach { host ->
                pool.execute {
                    if (!isActive()) return@execute
                    val found = probe(host, port)
                    if (found != null) onFound(found)
                    onProgress(done.incrementAndGet() * 100 / total)
                }
            }
        } finally {
            pool.shutdown()
            // ждём завершения задач, но не дольше разумного
            pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun probe(host: String, port: Int): Found? {
        val base = "http://$host:$port"
        return try {
            if (!httpOk("$base/api/system/ping", 400)) return null
            val version = httpText("$base/api/system/version")
            val v = Regex("\"version\"\\s*:\\s*\"([^\"]*)\"").find(version)?.groupValues?.get(1).orEmpty()
            Found(base, host, v.ifBlank { "Silo" })
        } catch (e: Exception) {
            null
        }
    }

    private fun httpOk(url: String, timeoutMs: Int): Boolean = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
        }
        try {
            conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    private fun httpText(url: String): String = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 1500
            readTimeout = 1500
        }
        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        ""
    }
}
