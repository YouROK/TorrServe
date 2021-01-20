package ru.yourok.torrserve.ui.fragments.main.servsets

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.settings.BTSets

class ServSetsViewModel : ViewModel() {
    var data: MutableLiveData<BTSets>? = null

    fun loadSettings(): LiveData<BTSets> {
        if (data == null) {
            data = MutableLiveData()
            load()
        }
        return data!!
    }

    fun saveSettings(sets: BTSets) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Api.setSettings(sets)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sets = Api.getSettings()
                withContext(Dispatchers.Main) { data?.value = sets }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}