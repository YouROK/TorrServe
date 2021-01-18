package ru.yourok.torrserve.server.api

import com.google.gson.Gson
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.utils.Net
import java.io.InputStream

object Api {
    class ApiException(msg: String, val code: Int) : Exception(msg)

    /// Server
    fun echo(): String {
        try {
            val host = Net.getHostUrl("/echo")
            return Net.get(host)
        } catch (e: Exception) {
            println(e.message)
            return ""
        }
    }

    fun shutdown(): String {
        try {
            val host = Net.getHostUrl("/shutdown")
            return Net.get(host)
        } catch (e: Exception) {
            println(e.message)
            return ""
        }
    }

    /// Torrents
    fun addTorrent(link: String, title: String, poster: String, save: Boolean): Torrent {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("add", link = link, title = title, poster = poster, save_to_db = save).toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, Torrent::class.java)
    }

    fun getTorrent(hash: String): Torrent {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("get", hash).toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, Torrent::class.java)
    }

    fun remTorrent(hash: String) {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("rem", hash).toString()
        postJson(host, req)
    }

    fun listTorrent(): List<Torrent> {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("list").toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, Array<Torrent>::class.java).toList()
    }

    fun dropTorrent(hash: String) {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("drop", hash).toString()
        postJson(host, req)
    }

    fun uploadTorrent(file: InputStream, title: String, poster: String, save: Boolean) {
        val host = Net.getHostUrl("/torrent/upload")
        Net.upload(host, title, poster, file, save)
    }

    // Settings
    fun getSettings(): BTSets {
        val host = Net.getHostUrl("/settings")
        val req = Request("get").toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, BTSets::class.java)
    }

    fun setSettings(sets: BTSets) {
        val host = Net.getHostUrl("/settings")
        val req = SettingsReq("set", sets).toString()
        postJson(host, req)
    }

    // Viewed
    fun listViewed(hash: String): List<Viewed> {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("list", hash).toString()
        val resp = postJson(host, req)
        if (resp.isNullOrBlank())
            return emptyList()
        return Gson().fromJson(resp, Array<Viewed>::class.java).toList()
    }

    fun setViewed(hash: String, index: Int) {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("set", hash, index).toString()
        postJson(host, req)
    }

    fun remViewed(hash: String) {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("rem", hash).toString()
        postJson(host, req)
    }

    private fun postJson(url: String, json: String): String {
        return Net.post(url, json)
    }
}