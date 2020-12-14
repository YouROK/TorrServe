package ru.yourok.torrserve.ui.fragments.play.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent

class TorrentViewModel : ViewModel() {
    private val data: MutableLiveData<Torrent> = MutableLiveData()

    fun loadTorrent(link: String, hash: String, title: String, poster: String, save: Boolean): LiveData<Torrent>? {
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
            withContext(Dispatchers.Main) {
                data.value = torr
            }
        }
    }

    private fun loadLink(link: String, title: String, poster: String, save: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val torr = Api.addTorrent(link, title, poster, save)
                withContext(Dispatchers.Main) {
                    data.value = torr
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    //TODO message
                    App.Toast(e.message ?: return@withContext)
                }
            }
        }
    }
}