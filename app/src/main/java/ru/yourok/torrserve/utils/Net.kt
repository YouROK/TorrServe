package ru.yourok.torrserve.utils

import android.annotation.SuppressLint
import android.net.Uri
import info.guardianproject.netcipher.client.TlsOnlySocketFactory
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.settings.Settings
import java.io.InputStream
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

object Net {
    private const val timeout = 30000 // in ms
    fun getHostUrl(path: String): String {
        val url = Settings.getHost()
        if (path.isEmpty())
            return url

        return if (url.last() == '/')
            url + path.substring(1)
        else
            url + path
    }

    private fun getAuthB64(): String {
        val auth = Settings.getServerAuth()
        if (auth.isNotEmpty())
            return "Basic " + android.util.Base64.encode(auth.toByteArray(), android.util.Base64.NO_WRAP).toString(Charset.defaultCharset())
        return ""
    }

    fun uploadAuth(url: String, title: String, poster: String, data: String, file: InputStream, save: Boolean): String {
        val req = Jsoup.connect(url)
            .data("file1", "filename", file)
            .ignoreContentType(true)
            .method(Connection.Method.POST)

        if (save)
            req.data("save", "true")
        req.data("title", title)
        req.data("poster", poster)
        req.data("data", data)

        val auth = getAuthB64()
        if (auth.isNotEmpty())
            req.header("Authorization", auth)

        val resp = req.execute()
        return resp.body()
    }

    fun postAuth(url: String, req: String): String {
        val conn = Jsoup.connect(url)
            .requestBody(req)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .method(Connection.Method.POST)

        val auth = getAuthB64()
        if (auth.isNotEmpty())
            conn.header("Authorization", auth)

        val response = conn.execute()

        return when (response.statusCode()) {
            200 -> {
                response.body()
            }

            302 -> {
                ""
            }

            else -> {
                throw Exception(response.statusMessage())
            }
        }
    }

    fun getAuth(url: String): String {
        val conn = Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .timeout(timeout)

        val auth = getAuthB64()
        if (auth.isNotEmpty())
            conn.header("Authorization", auth)

        val response = conn.execute()
        return when (response.statusCode()) {
            200 -> {
                response.body()
            }

            302 -> {
                ""
            }

            else -> {
                throw Exception(response.statusMessage())
            }
        }
    }

    fun get(url: String): String {
        val link = Uri.parse(url)
        if (link.scheme.equals("https", true)) {
            val trustAllHostnames = HostnameVerifier { _, _ ->
                true // Just allow them all
            }
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        }
        val response = Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .sslSocketFactory(insecureTlsSocketFactory())
            .timeout(timeout)
            .execute()

        return when (response.statusCode()) {
            200 -> {
                response.body()
            }

            302 -> {
                ""
            }

            else -> {
                throw Exception(response.statusMessage())
            }
        }
    }

    // https://stackoverflow.com/questions/26649389/how-to-disable-sslv3-in-android-for-httpsurlconnection
    fun insecureTlsSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        })

        try {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            return TlsOnlySocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            when (e) {
                is RuntimeException, is KeyManagementException -> {
                    throw RuntimeException("Failed to create a SSL socket factory", e)
                }

                else -> throw e
            }
        }
    }
}