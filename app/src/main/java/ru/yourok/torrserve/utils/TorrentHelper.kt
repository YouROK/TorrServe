package ru.yourok.torrserve.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.ext.urlEncode
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import java.io.File

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
                if (File(path).extension.toLowerCase() == "m2ts") {
                    if (size > 1073741824L)
                        retList.add(it)
                } else
                    retList.add(it)
            } else if (path.toLowerCase().contains("bdmv/index.bdmv")) {
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

    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        return Net.getHostUrl("/stream/${torr.title.urlEncode()}?link=${torr.hash}&index=${index}&play")
    }

    fun getTorrentPlayPreloadLink(torr: Torrent, index: Int): String {
        return Net.getHostUrl("/stream/${torr.title.urlEncode()}?link=${torr.hash}&index=${index}&play&preload")
    }

    suspend fun preloadTorrent(torr: Torrent, index: Int) = withContext(Dispatchers.IO) {
        val link = getTorrentPlayPreloadLink(torr, index)
        Jsoup.connect(link)
            .method(Connection.Method.GET)
            .ignoreContentType(true)
            .timeout(0)
            .execute()
    }

}