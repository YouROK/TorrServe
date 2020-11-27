package ru.yourok.torrserve.ui.activities.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.server.api.Api

class StatusViewModel : ViewModel() {
    private var isWork = false
    var data: MutableLiveData<String>? = null

    fun get(): LiveData<String> {
        if (data == null) {
            data = MutableLiveData()
            update()
        }
        return data!!
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
                    val st = Api.echo()
                    val old = data?.value
                    if (old == null || st != old)
                        withContext(Dispatchers.Main) { data?.value = st }
                    delay(1000)
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}