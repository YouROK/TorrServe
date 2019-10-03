package ru.yourok.torrserve.utils

import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.client.methods.HttpGet
import cz.msebera.android.httpclient.impl.client.HttpClients
import cz.msebera.android.httpclient.util.EntityUtils
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


/**
 * Created by yourok on 07.11.17.
 */

class Http(val url: String) {

    fun read(): String {
        val httpclient = HttpClients.custom().setSslcontext(getSslContext()).build()
        val httpreq = HttpGet(url)
        val response = httpclient.execute(httpreq)

        val status = response.statusLine?.statusCode ?: -1
        if (status == 200) {
            val entity = response.entity ?: return ""
            return EntityUtils.toString(entity)
        } else {
            return ""
        }
    }

    fun getEntity(): HttpEntity? {
        val httpclient = HttpClients.custom().setSslcontext(getSslContext()).build()
        val httpreq = HttpGet(url)
        val response = httpclient.execute(httpreq)

        val status = response.statusLine?.statusCode ?: -1
        if (status == 200) {
            val entity = response.entity ?: return null
            return entity
        } else {
            return null
        }
    }

    private fun getSslContext(): SSLContext? {
        val byPassTrustManagers = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
            }

            override fun checkServerTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })

        var sslContext: SSLContext? = null

        try {
            sslContext = SSLContext.getInstance("TLS")
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        try {
            sslContext?.init(null, byPassTrustManagers, SecureRandom())
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        }

        return sslContext
    }
}