package ru.yourok.torrserve.ui.fragments.main.torrents

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

class TorrentsViewModel : ViewModel() {
    private var isWork = Any()
    private var isUpdate = true
    var data: MutableLiveData<List<Torrent>>? = null

    fun getData(): LiveData<List<Torrent>> {
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

    fun setUpdate(update: Boolean) {
        isUpdate = update
    }

    private fun update() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(isWork) {
                if (isWork == true)
                    return@launch
            }
            isWork = true
            while (isWork == true) {
                try {
                    if (!isUpdate) {
                        delay(1000)
                        continue
                    }
                    val list = Api.listTorrent()
                    val oldList = data?.value
                    if (oldList == null || list != oldList)
                        withContext(Dispatchers.Main) { data?.value = list }
                    delay(1000)
                } catch (e: Exception) {
                    delay(2000)
                    withContext(Dispatchers.Main) { data?.value = emptyList() }
                }
            }
        }
    }
}