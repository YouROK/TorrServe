package ru.yourok.torrserve.utils

import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.settings.Settings
import java.io.InputStream
import java.nio.charset.Charset


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
        val response = Jsoup.connect(url)
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
}