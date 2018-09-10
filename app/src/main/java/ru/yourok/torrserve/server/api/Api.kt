package ru.yourok.torrserve.server.api

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.net.Net.Get
import ru.yourok.torrserve.server.net.Net.Post
import ru.yourok.torrserve.server.net.Net.Upload
import ru.yourok.torrserve.server.net.Net.fixLink
import ru.yourok.torrserve.server.net.Net.getHostUrl
import ru.yourok.torrserve.server.net.Net.joinHostUrl
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Api {

    fun torrentAdd(link: String, title: String, info: String, save: Boolean): String {
        if (link.startsWith("magnet:", true))
            return torrentAddLink(fixLink(link), title, info, save)
        else if (
                link.startsWith("http:", true) ||
                link.startsWith("https:", true))
            return torrentAddLink(link, title, info, save)
        else
            return torrentUploadFile(link, save)
    }

    fun torrentAddLink(link: String, title: String, info: String, save: Boolean): String {
        var rlink = link
        if (link.startsWith("magnet:", true))
            rlink = fixLink(link)

        val url = getHostUrl("/torrent/add")
        val js = JSONObject()
        js.put("Link", rlink)
        if (title.isNotEmpty())
            js.put("Title", title)
        if (info.isNotEmpty())
            js.put("Info", info)
        js.put("DontSave", !save)
        val req = js.toString(0)
        return Post(url, req)
    }

    fun torrentUploadFile(path: String, save: Boolean): String {
        val url = getHostUrl("/torrent/upload")
        var link = path
        var isRemove = false
        if (link.startsWith("content://", true)) {
            val outputDir = App.getContext().getCacheDir()
            val outputFile = File.createTempFile("tmp", ".torr", outputDir)
            val fd = App.getContext().contentResolver.openFileDescriptor(Uri.parse(link), "r")
            val inStream = FileInputStream(fd.fileDescriptor)
            val outStream = FileOutputStream(outputFile)
            val inChannel = inStream.getChannel()
            val outChannel = outStream.getChannel()
            inChannel.transferTo(0, inChannel.size(), outChannel)
            inStream.close()
            outStream.close()
            link = outputFile.path
            isRemove = true
        }

        if (link.startsWith("file://", true))
            link = Uri.parse(link).path

        val hashes = Upload(url, link, save)
        if (isRemove)
            File(link).delete()
        return hashes[0]
    }

    fun torrentGet(hash: String): JSObject {
        val url = getHostUrl("/torrent/get")
        val js = JSONObject()
        js.put("Hash", hash)
        val req = js.toString(0)
        return JSObject(JSONObject(Post(url, req)))
    }

    fun torrentList(): List<JSObject> {
        val url = getHostUrl("/torrent/list")
        val torjs = JSONArray(Post(url, ""))
        val list = mutableListOf<JSObject>()
        for (i in 0 until torjs.length())
            list.add(JSObject(torjs.getJSONObject(i)))
        return list.toList()
    }

    fun torrentRemove(hash: String) {
        val url = getHostUrl("/torrent/rem")
        val js = JSONObject()
        js.put("Hash", hash)
        val req = js.toString(0)
        Post(url, req)
    }

    fun torrentStat(hash: String): JSObject {
        val url = getHostUrl("/torrent/stat")
        val js = JSONObject()
        js.put("Hash", hash)
        val req = js.toString(0)
        return JSObject(JSONObject(Post(url, req)))
    }

    fun torrentDrop(hash: String) {
        val url = getHostUrl("/torrent/drop")
        val js = JSONObject()
        js.put("Hash", hash)
        val req = js.toString(0)
        Post(url, req)
    }

    fun torrentPreload(preloadLink: String) {
        val url = getHostUrl(preloadLink)
        Get(url)
    }

    fun torrentRestart() {
        val url = getHostUrl("/torrent/restart")
        Get(url)
    }

    fun serverEcho(): String {
        try {
            val url = getHostUrl("/echo")
            return Get(url)
        } catch (e: Exception) {
            return ""
        }
    }

    fun serverCheck(host: String): String {
        try {
            val url = joinHostUrl(host, "/echo")
            return Get(url)
        } catch (e: Exception) {
            return ""
        }
    }

    fun serverReadSettings(): JSObject {
        val url = getHostUrl("/settings/read")
        return JSObject(JSONObject(Post(url, "")))
    }

    fun serverWriteSettings(sets: JSObject) {
        val url = getHostUrl("/settings/write")
        Post(url, sets.toString())
    }

    fun serverShutdown() {
        val url = getHostUrl("/shutdown")
        try {
            Post(url, "")
        } catch (e: Exception) {
        }
    }

    fun serverIsLocal(): Boolean {
        val host = getHostUrl("")
        return host.commonPrefixWith("http://localhost", true) == "http://localhost"
    }

    fun searchMovies(params: List<String>): JSONArray {
        var url = getHostUrl("/search/movie?")
        url += params.joinToString("&")
        return JSONArray(Get(fixLink(url)))
    }

    fun getMovie(id: String): JSObject {
        var url = getHostUrl("/search/movie/$id")
        return JSObject(Get(fixLink(url)))
    }

    fun searchShows(params: List<String>): JSONArray {
        var url = getHostUrl("/search/show?")
        url += params.joinToString("&")
        return JSONArray(Get(fixLink(url)))
    }

    fun getShow(id: String): JSObject {
        var url = getHostUrl("/search/show/$id")
        return JSObject(Get(fixLink(url)))
    }

    fun searchTorrent(query: String, filter: List<String>): JSONArray {
        var url = getHostUrl("/search/torrent")
        url += "?query=$query"
        if (filter.isNotEmpty())
            url += "&ft=" + filter.joinToString("&ft=")
        return JSONArray(Get(fixLink(url)))
    }

    fun searchConfig(): JSONObject {
        var url = getHostUrl("/search/config?type=config")
        val cfg = JSONObject(Get(url))
        url = getHostUrl("/search/config?type=genres")
        val genres = JSONObject(Get(url))
        val js = JSONObject()
        js.put("Config", cfg)
        js.put("Genres", genres)
        return js
    }
}