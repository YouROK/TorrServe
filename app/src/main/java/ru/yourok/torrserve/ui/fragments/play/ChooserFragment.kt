package ru.yourok.torrserve.ui.fragments.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.ResultFragment


class ChooserFragment : ResultFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.chooser_fragment, container, false)
        TorrService.start()
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        view?.apply {
            findViewById<CardView>(R.id.cvPlay)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(1)
                }
            }
            findViewById<CardView>(R.id.cvAddPlay)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(2)
                }
            }
            findViewById<CardView>(R.id.cvAdd)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(3)
                }
            }
        }
    }
}