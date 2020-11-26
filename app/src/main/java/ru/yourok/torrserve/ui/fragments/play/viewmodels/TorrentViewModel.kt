package ru.yourok.torrserve.ui.fragments.play.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.TorrentHelper

data class TorrentVMData(val torr: Torrent, val viewed: List<Viewed>)

class TorrentViewModel : ViewModel() {

    private val data: MutableLiveData<TorrentVMData> = MutableLiveData()

    fun loadTorrent(link: String, hash: String, title: String, poster: String, save: Boolean): LiveData<TorrentVMData>? {
        if (hash.isNotEmpty())
            loadHash(hash)
        else if (link.isNotEmpty())
            loadLink(link, title, poster, save)
        else return null
        return data
    }

    private fun loadHash(hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val torr = Api.getTorrent(hash)
            load(torr)
        }
    }

    private fun loadLink(link: String, title: String, poster: String, save: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val torr = Api.addTorrent(link, title, poster, save)
            load(torr)
        }
    }

    private suspend fun load(torr: Torrent) {
        val updViewed = Api.listViewed(torr.hash)
        val updTorr = TorrentHelper.waitFiles(torr.hash)
        withContext(Dispatchers.Main) {
            data.value = TorrentVMData(updTorr ?: torr, updViewed)
        }
    }
}