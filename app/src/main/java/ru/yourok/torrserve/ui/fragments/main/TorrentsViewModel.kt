package ru.yourok.torrserve.ui.fragments.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.services.TorrService

data class TorrentState(val status: String, val torrents: List<Torrent>)

class TorrentsViewModel : ViewModel() {
    private var isWork = false
    var data: MutableLiveData<TorrentState>? = null

    fun getData(): LiveData<TorrentState> {
        if (data == null) {
            data = MutableLiveData()
            update()
        }
        return data!!
    }

    override fun onCleared() {
        isWork = false
        super.onCleared()
    }

    private fun update() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(isWork) {
                if (isWork)
                    return@launch
            }
            isWork = true
            while (isWork) {
                try {
                    var diff = false
                    var ver = Api.echo()

                    if (ver.isNullOrEmpty()) {
                        if (TorrService.isLocal() && !ServerFile().exists())
                            ver = App.context.getString(R.string.server_not_exists)
                        else
                            ver = App.context.getString(R.string.server_not_responding)
                    }

                    var list = emptyList<Torrent>()

                    if (ver != data?.value?.status)
                        diff = true

                    if (ver.isNotEmpty()) {
                        list = Api.listTorrent()
                        if (!diff) {
                            val oldList = data?.value?.torrents ?: emptyList()
                            if (list.size != oldList.size)
                                diff = true
                            else
                                for (i in list.indices)
                                    if (list[i] != oldList[i]) {
                                        diff = true
                                        break
                                    }
                        }
                    }

                    if (diff)
                        withContext(Dispatchers.Main) { data?.value = TorrentState(ver, list) }
                    delay(1000)
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}