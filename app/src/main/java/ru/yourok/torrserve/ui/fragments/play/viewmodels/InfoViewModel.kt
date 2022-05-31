package ru.yourok.torrserve.ui.fragments.play.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent

data class InfoTorrent(val torrent: Torrent?, val error: String)

class InfoViewModel : ViewModel() {
    private var data: MutableLiveData<InfoTorrent>? = null
    private var lock = Any()
    private var torrent: Torrent? = null

    fun setTorrent(hash: String): LiveData<InfoTorrent> {
        if (data == null)
            data = MutableLiveData()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                torrent = Api.getTorrent(hash)
                withContext(Dispatchers.Main) {
                    data?.value = InfoTorrent(torrent, "")
                }
                update()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    data?.value = InfoTorrent(null, e.message ?: "error on add torrent")
                }
            }
        }

        return data!!
    }

    override fun onCleared() {
        lock = false
        super.onCleared()
    }

    private fun update() {
        if (torrent == null)
            return

        synchronized(lock) {
            if (lock == true)
                return
            lock = true
            viewModelScope.launch(Dispatchers.IO) {
                while (lock == true) {
                    try {
                        torrent?.let {
                            torrent = Api.getTorrent(it.hash)
                            withContext(Dispatchers.Main) {
                                data?.value = InfoTorrent(torrent, "")
                            }
                        }
                        delay(100)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            data?.value = InfoTorrent(null, e.message ?: "error on get info torrent")
                        }
                        delay(1000)
                    }
                }
            }
        }
    }
}