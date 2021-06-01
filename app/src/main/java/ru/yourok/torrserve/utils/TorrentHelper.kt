package ru.yourok.torrserve.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.ext.urlEncode
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import java.io.File
import java.util.*

object TorrentHelper {
    const val TorrentSTAdded = 0
    const val TorrentSTGettingInfo = 1
    const val TorrentSTPreload = 2
    const val TorrentSTWorking = 3
    const val TorrentSTClosed = 4
    const val TorrentInDB = 5

    fun getPlayableFiles(torr: Torrent): List<FileStat> {
        if (torr.file_stats.isNullOrEmpty())
            return emptyList()

        val files = torr.file_stats
        val retList = mutableListOf<FileStat>()

        files?.forEach {
            val path = it.path
            if (Mime.getMimeType(path) != "*/*") {
                val size = it.length
                if (File(path).extension.lowercase(Locale.getDefault()) == "m2ts") {
                    if (size > 1073741824L)
                        retList.add(it)
                } else
                    retList.add(it)
            } else if (path.lowercase(Locale.getDefault()).contains("bdmv/index.bdmv")) {
                retList.add(it)
            }
        }
        return retList
    }

    fun waitFiles(hash: String): Torrent? {
        var count = 0
        while (true) {
            try {
                val torr = Api.getTorrent(hash)
                if (torr.file_stats != null) {
                    if ((torr.file_stats?.size ?: 0) > 0 || count > 59)
                        return torr
                    else
                        count++
                }
                Thread.sleep(1000)
            } catch (e: Exception) {
                Thread.sleep(1000)
            }
        }
    }

    fun getTorrentMagnet(torr: Torrent): String {
        return "magnet:?xt=urn:btih:${torr.hash}&dn=${torr.title.urlEncode()}"
    }

    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        val file = findFile(torr, index)
        val name = file?.let { File(it.path).name } ?: torr.title
        return Net.getHostUrl("/stream/${name.urlEncode()}?link=${torr.hash}&index=${index}&play")
    }

    fun getFileLink(torr: Torrent, file: FileStat?): String {
        val name = file?.let { File(it.path).name } ?: torr.title
        return Net.getHostUrl("/stream/${name.urlEncode()}?link=${torr.hash}&index=${file?.id}&play")
    }

    fun getTorrentPreloadLink(torr: Torrent, index: Int): String {
        return Net.getHostUrl("/stream/${torr.title.urlEncode()}?link=${torr.hash}&index=${index}&preload")
    }

    suspend fun preloadTorrent(torr: Torrent, index: Int) = withContext(Dispatchers.IO) {
        try {
            val link = getTorrentPreloadLink(torr, index)
            Net.getAuth(link)
        } catch (e: Exception) {
        }
    }

    fun findFile(torrent: Torrent, index: Int): FileStat? {
        torrent.file_stats?.forEach {
            if (it.id == index)
                return it
        }
        return null
    }

    fun findIndex(torrent: Torrent, file: FileStat): Int {
        torrent.file_stats?.forEachIndexed { index, fileStat ->
            if (fileStat.id == file.id)
                return index
        }
        return -1
    }
}