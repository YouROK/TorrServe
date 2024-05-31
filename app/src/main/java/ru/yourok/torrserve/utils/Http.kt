package ru.yourok.torrserve.utils

import android.net.Uri
import android.os.Build
import info.guardianproject.netcipher.NetCipher
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_MOVED_PERM
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_PARTIAL
import java.net.HttpURLConnection.HTTP_SEE_OTHER
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection


/**
 * Created by yourok on 07.11.17.
 */

class Http(url: Uri) {
    private var currUrl: String = url.toString()
    private var isConn: Boolean = false
    private var connection: HttpURLConnection? = null
    private var errMsg: String = ""
    private var inputStream: InputStream? = null
    private var auth: String = ""

    private var timeout = 30000

    fun connect() {
        connect(0)
    }

    private fun connect(pos: Long): Long {

        var responseCode: Int
        var redirCount = 0
        do {
            if (!currUrl.contains("://"))
                currUrl = currUrl.replace(":/", "://")

            val url = URL(currUrl)

            connection = if (currUrl.startsWith("https"))
                NetCipher.getHttpsURLConnection(url)
                    .also {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            val trustAllHostnames = HostnameVerifier { _, _ ->
                                true // Just allow them all
                            }
                            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
                            HttpsURLConnection.setDefaultSSLSocketFactory(Net.insecureTlsSocketFactory())
                        }
                    }
            else
                NetCipher.getHttpURLConnection(url)
            connection!!.connectTimeout = timeout
            connection!!.readTimeout = 15000
            connection!!.requestMethod = "GET"
            connection!!.doInput = true

            connection!!.setRequestProperty("UserAgent", "DWL/1.1.0 (Linux; Android;)")
            connection!!.setRequestProperty("Accept", "*/*")
            connection!!.setRequestProperty("Accept-Encoding", "gzip")
            if (pos > 0)
                connection!!.setRequestProperty("Range", "bytes=$pos-")

            if (auth.isNotBlank())
                connection!!.setRequestProperty("Authorization", auth)

            connection!!.connect()

            responseCode = connection!!.responseCode
            var redirected =
                responseCode == HTTP_MOVED_PERM || responseCode == HTTP_MOVED_TEMP || responseCode == HTTP_SEE_OTHER
            if (redirected) {
                currUrl = connection!!.getHeaderField("Location")
                connection!!.disconnect()
                redirCount++
            }

            if (responseCode == 429) {
                var retry = connection!!.getHeaderField("Retry-After")
                if (retry.isNullOrEmpty() || retry == "0")
                    retry = "1"
                redirCount++
                redirected = true
                Thread.sleep(retry.toLong() * 1000L)
            }

            if (redirCount > 5) {
                throw IOException("Error connect to: $currUrl too many redirects")
            }
        } while (redirected)


        if (responseCode != HTTP_OK && responseCode != HTTP_PARTIAL) {
            throw IOException("Error connect to: " + currUrl + " " + connection!!.responseMessage)
        }
        isConn = true
        if (connection!!.getHeaderField("Accept-Ranges")?.lowercase(Locale.getDefault()) == "none")
            return -1
        return getSize()
    }

    fun setTimeout(timeout: Int) {
        this.timeout = timeout
    }

    fun isConnected(): Boolean {
        return isConn
    }

    fun setAuth(auth: String) {
        this.auth = auth
    }

    fun getSize(): Long {
        if (!isConn)
            return 0

        var cl = connection!!.getHeaderField("Content-Range")
        try {
            if (!cl.isNullOrEmpty()) {
                val cr = cl.split("/")
                if (cr.isNotEmpty())
                    cl = cr.last()
                return cl.toLong()
            }
        } catch (_: Exception) {
        }

        cl = connection!!.getHeaderField("Content-Length")
        try {
            if (!cl.isNullOrEmpty()) {
                return cl.toLong()
            }
        } catch (_: Exception) {
        }

        return 0
    }

    fun getUrl(): String {
        return currUrl
    }

    fun getInputStream(): InputStream? {
        if (inputStream == null && connection != null) {
            inputStream = if ("gzip" == connection?.contentEncoding)
                GZIPInputStream(connection!!.inputStream)
            else
                connection!!.inputStream
        }

        return inputStream
    }

    fun read(b: ByteArray): Int {
        if (!isConn or (getInputStream() == null))
            throw IOException("connect before read")
        var sz = getInputStream()!!.read(b)
        var size = sz
        while (sz > 0 && sz < b.size / 2) {
            try {
                sz = getInputStream()!!.read(b, size, b.size - size)
                if (sz > 0)
                    size += sz
                else
                    break
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        return size
    }

    fun getErrorMessage(): String {
        return errMsg
    }

    fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        connection?.disconnect()
        isConn = false
    }
}