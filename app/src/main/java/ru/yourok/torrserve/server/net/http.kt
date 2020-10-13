package ru.yourok.torrserve.server.net

import android.net.Uri
import cz.msebera.android.httpclient.client.methods.HttpEntityEnclosingRequestBase
import cz.msebera.android.httpclient.client.methods.HttpGet
import cz.msebera.android.httpclient.client.methods.HttpPost
import cz.msebera.android.httpclient.entity.ContentType
import cz.msebera.android.httpclient.entity.StringEntity
import cz.msebera.android.httpclient.entity.mime.HttpMultipartMode
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder
import cz.msebera.android.httpclient.entity.mime.content.FileBody
import cz.msebera.android.httpclient.entity.mime.content.StringBody
import cz.msebera.android.httpclient.impl.client.HttpClients.custom
import cz.msebera.android.httpclient.util.EntityUtils
import org.json.JSONArray
import org.json.JSONObject
import ru.yourok.torrserve.preferences.Preferences
import java.io.IOException
import java.net.URI

object Net {

    fun fixLink(link: String): String {
        try {
            if (link.isNotEmpty()) {
                val url = Uri.parse(link)
                val uri = URI(url.scheme, url.userInfo, url.host, url.port, url.path, url.query, url.fragment)
                return uri.toASCIIString()
            }
        } catch (e: Exception) {
        }
        return link
    }

    fun getHostUrl(path: String): String {
        val url = Preferences.getCurrentHost()
        if (path.isEmpty())
            return url
        if (url.last() == '/')
            return url + path.substring(1)
        else
            return url + path
    }

    fun joinHostUrl(url: String, path: String): String {
        if (url.last() == '/')
            return url + path.substring(1)
        else
            return url + path
    }

    fun Upload(url: String, path: String, save: Boolean): List<String> {
        val file = java.io.File(path)

        val httpclient = custom().build()
        val httppost = HttpPost(url)

        val mpEntity = MultipartEntityBuilder.create()
        mpEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        mpEntity.addPart(file.name, FileBody(file))
        if (!save)
            mpEntity.addPart("DontSave", StringBody("true", ContentType.DEFAULT_TEXT))

        val entity = mpEntity.build()
        httppost.setEntity(entity)
        val response = httpclient.execute(httppost)
        val str = EntityUtils.toString(response.getEntity())
        val arr = JSONArray(str)

        val hashList = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val str = arr.getString(i)
            hashList.add(str)
        }
        return hashList
    }

    fun Post(url: String, req: String): String {
        val httpclient = custom().disableRedirectHandling().build()
        val httpreq = HttpPost(url)
        if (req.isNotEmpty())
            (httpreq as HttpEntityEnclosingRequestBase).setEntity(StringEntity(req, ContentType.APPLICATION_JSON))


        val response = httpclient.execute(httpreq)
        val status = response.statusLine?.statusCode ?: -1
        if (status == 200) {
            val entity = response.entity ?: return ""
            return EntityUtils.toString(entity)
        } else if (status == 302) {
            return ""
        } else {
            val resp = EntityUtils.toString(response.entity)
            resp?.let {
                if (it.isNotEmpty()) {
                    var errMsg = response.statusLine.reasonPhrase
                    try {
                        errMsg = JSONObject(it).getString("Message")
                    } catch (e: Exception) {
                        try {
                            errMsg = JSONObject(it).getString("message")
                        } catch (e: Exception) {
                        }
                    }
                    throw IOException(errMsg)
                }
            }
            throw IOException(response.statusLine.reasonPhrase)
        }
    }

    fun Get(url: String): String {
        val httpclient = custom().disableRedirectHandling().build()

        val httpreq = HttpGet(url)
        val response = httpclient.execute(httpreq)
        val status = response.statusLine?.statusCode ?: -1
        if (status == 200) {
            val entity = response.entity ?: let {
                return ""
            }
            return EntityUtils.toString(entity)
        } else if (status == 302) {
            return ""
        } else {
            val resp = EntityUtils.toString(response.entity)
            resp?.let {
                if (it.isNotEmpty()) {
                    var errMsg = response.statusLine.reasonPhrase
                    try {
                        errMsg = JSONObject(it).getString("Message")
                    } catch (e: Exception) {
                        try {
                            errMsg = JSONObject(it).getString("message")
                        } catch (e: Exception) {
                        }
                    }
                    throw IOException(errMsg)
                }
            }
            throw IOException(response.statusLine.reasonPhrase)
        }
    }
}