package ru.yourok.torrserve.utils

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
    fun getHostUrl(path: String): String {
        val url = Settings.getHost()
        if (path.isEmpty())
            return url

        if (url.last() == '/')
            return url + path.substring(1)
        else
            return url + path
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
//            .timeout(5000)
            .method(Connection.Method.POST)

        val auth = getAuthB64()
        if (auth.isNotEmpty())
            conn.header("Authorization", auth)

        val response = conn.execute()

        val status = response.statusCode()
        if (status == 200) {
            return response.body()
        } else if (status == 302) {
            return ""
        } else {
            throw Exception(response.statusMessage())
        }
    }

    fun getAuth(url: String): String {
        val conn = Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .timeout(2000)

        val auth = getAuthB64()
        if (auth.isNotEmpty())
            conn.header("Authorization", auth)

        val response = conn.execute()

        val status = response.statusCode()
        if (status == 200) {
            return response.body()
        } else if (status == 302) {
            return ""
        } else {
            throw Exception(response.statusMessage())
        }
    }

    fun get(url: String): String {
        val link = Uri.parse(url)
        if (link.scheme.equals("https", true)) {
            val trustAllHostnames: HostnameVerifier = object : HostnameVerifier {
                override fun verify(hostname: String?, session: SSLSession?): Boolean {
                    return true // Just allow them all
                }
            }
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        }
        val response = Jsoup.connect(url)
            .sslSocketFactory(insecureTlsSocketFactory())
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .timeout(2000)
            .execute()

        val status = response.statusCode()
        if (status == 200) {
            return response.body()
        } else if (status == 302) {
            return ""
        } else {
            throw Exception(response.statusMessage())
        }
    }

    // https://stackoverflow.com/questions/26649389/how-to-disable-sslv3-in-android-for-httpsurlconnection
    private fun insecureTlsSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            }

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
            val noSSLv3Factory: SSLSocketFactory = TlsOnlySocketFactory(sslContext.socketFactory)
            return noSSLv3Factory
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