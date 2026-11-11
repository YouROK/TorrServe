package ru.yourok.torrserve.utils

import org.conscrypt.Conscrypt
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL factory that accepts every certificate and every hostname.
 * WARNING: disables all TLS security. Use only for debugging / self-signed servers.
 */
class TlsSocketFactory : SSLSocketFactory {
    private val enabledProtocols: Array<String>
    private val delegate: SSLSocketFactory

    constructor(enabledProtocols: Array<String>) {
        this.enabledProtocols = enabledProtocols
        this.delegate = socketFactory
    }

    constructor() {
        this.enabledProtocols = TLS_RESTRICTED
        this.delegate = socketFactory
    }

    constructor(base: SSLSocketFactory) {
        this.enabledProtocols = TLS_RESTRICTED
        this.delegate = base
    }

    override fun getDefaultCipherSuites() = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites() = delegate.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket(): Socket = patch(delegate.createSocket())

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        patch(delegate.createSocket(s, host, port, autoClose))

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(host, port, localHost, localPort))

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(address, port, localAddress, localPort))

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) {
            s.enabledProtocols = enabledProtocols
        }
        return s
    }

    companion object {
        private var conscrypt: Provider? = null
        val TLS_MODERN: Array<String> = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
        val TLS_RESTRICTED: Array<String> = arrayOf("TLSv1.2", "TLSv1.3")

        /** Trust manager that accepts every chain. */
        val TRUST_ALL: X509TrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        @get:Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
        private val socketFactory: SSLSocketFactory
            get() {
                if (conscrypt == null) {
                    conscrypt = Conscrypt.newProvider()
                    Security.insertProviderAt(conscrypt, 1)
                }
                val context = SSLContext.getInstance("TLS", conscrypt)
                context.init(
                    null,
                    arrayOf<TrustManager>(TRUST_ALL),
                    SecureRandom()
                )
                return context.socketFactory
            }
    }
}