package ru.yourok.torrserve.utils

import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.util.zip.GZIPInputStream


/**
 * Created by yourok on 07.11.17.
 */

class Http(url: Uri) {
    private var currUrl: String = url.toString()
    private var isConn: Boolean = false
    private var connection: HttpURLConnection? = null
    private var errMsg: String = ""
    private var inputStream: InputStream? = null

    fun connect() {
        connect(0)
    }

    fun connect(pos: Long): Long {
        var responseCode: Int
        var redirCount = 0
        do {
            val url = URL(currUrl)
            connection = url.openConnection() as HttpURLConnection
            connection!!.connectTimeout = 30000
            connection!!.readTimeout = 15000
            connection!!.setRequestMethod("GET")
            connection!!.setDoInput(true)

            connection!!.setRequestProperty("UserAgent", "DWL/1.1.0 (Linux; Android;)")
            connection!!.setRequestProperty("Accept", "*/*")
            connection!!.setRequestProperty("Accept-Encoding", "gzip")
            if (pos > 0)
                connection!!.setRequestProperty("Range", "bytes=$pos-")

            connection!!.connect()

            responseCode = connection!!.getResponseCode()
            val redirected = responseCode == HTTP_MOVED_PERM || responseCode == HTTP_MOVED_TEMP || responseCode == HTTP_SEE_OTHER
            if (redirected) {
                currUrl = connection!!.getHeaderField("Location")
                connection!!.disconnect()
                redirCount++
            }
            if (redirCount > 5) {
                throw IOException("Error connect to: " + currUrl + " too many redirects")
            }
        } while (redirected)


        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw IOException("Error connect to: " + currUrl + " " + connection!!.responseMessage)
        }
        isConn = true
        if ((connection!!.getHeaderField("Accept-Ranges")?.toLowerCase() ?: "") == "none")
            return -1
        return getSize()
    }

    fun isConnected(): Boolean {
        return isConn
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
        } catch (e: Exception) {
        }

        cl = connection!!.getHeaderField("Content-Length")
        try {
            if (!cl.isNullOrEmpty()) {
                return cl.toLong()
            }
        } catch (e: Exception) {
        }

        return 0
    }

    fun getUrl(): String {
        return currUrl
    }

    fun getInputStream(): InputStream? {
        if (inputStream == null && connection != null) {
            if ("gzip".equals(connection?.getContentEncoding()))
                inputStream = GZIPInputStream(connection!!.getInputStream())
            else
                inputStream = connection!!.getInputStream()
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
        } catch (e: Exception) {
        }
        connection?.disconnect()
        isConn = false
    }
}