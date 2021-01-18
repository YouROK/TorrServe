package ru.yourok.torrserve.utils

import android.net.Uri
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.settings.Settings
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder


object Net {

    fun fixLink(link: String): String {
        try {
            if (link.isNotEmpty()) {
                val url = Uri.parse(link)
                val uri = URI(
                    url.scheme,
                    url.userInfo,
                    url.host,
                    url.port,
                    url.path,
                    url.query,
                    url.fragment
                )
                return uri.toASCIIString()
            }
        } catch (e: Exception) {
        }
        return link
    }

    fun getHostUrl(path: String): String {
        val url = Settings.getHost()
        if (path.isEmpty())
            return url

        if (url.last() == '/')
            return url + path.substring(1)
        else
            return url + path
    }

    fun joinHostUrl(url: String, path: String): String {
        if (url.last() == '/')
            return url + URLEncoder.encode(path.substring(1), "utf8")
        else
            return url + URLEncoder.encode(path, "utf8")
    }

    fun upload(url: String, title: String, poster: String, file: InputStream, save: Boolean): String {
        val req = Jsoup.connect(url)
            .data("file1", "filename", file)
            .method(Connection.Method.POST)

        if (save)
            req.data("save", "true")
        req.data("title", title)
        req.data("poster", poster)
        val resp = req.execute()
        return resp.body()
    }

    fun post(url: String, req: String): String {
        val response = Jsoup.connect(url)
            .requestBody(req)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .method(Connection.Method.POST)
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