package ru.yourok.torrserve.ui.fragments.play

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.TorrentHelper

data class InfoTorrent(val torrent: Torrent?, val error: String)

class InfoViewModel : ViewModel() {
    private var data: MutableLiveData<InfoTorrent>? = null
    private var stop = true
    private var torrent: Torrent? = null

    fun addTorrent(link: String, title: String, poster: String): LiveData<InfoTorrent> {
        if (data == null)
            data = MutableLiveData()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                torrent = Api.addTorrent(link, title, poster)
                data?.value = InfoTorrent(torrent, "")
                update()
            } catch (e: Exception) {
                e.printStackTrace()
                data?.value = InfoTorrent(null, e?.message ?: "error on add torrent")
            }
        }

        return data!!
    }

    fun preloadTorrent(index: Int) {
        torrent?.let {
            val link = TorrentHelper.getTorrentPlayPreloadLink(it, index)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    Jsoup.connect(link)
                        .method(Connection.Method.GET)
                        .execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                    data?.value = InfoTorrent(null, e?.message ?: "error on preload torrent")
                }
            }
            update()
        }
    }

    override fun onCleared() {
        stop = true
        super.onCleared()
    }

    private fun update() {
        if (torrent == null)
            return

        synchronized(stop) {
            if (!stop)
                return
            stop = false
            viewModelScope.launch(Dispatchers.IO) {
                while (!stop) {
                    try {
                        torrent?.let {
                            torrent = Api.getTorrent(it.hash)
                            data?.value = InfoTorrent(torrent, "")
                        }
                        delay(100)
                    } catch (e: Exception) {
                        data?.value = InfoTorrent(null, e.message ?: "error on get info torrent")
                        delay(1000)
                    }
                }
            }
        }
    }
}