package ru.yourok.torrserve.ui.fragments.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.fragments.fragManager

class AddFragment : Fragment() {

    companion object {
        fun newInstance() = AddFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.add_fragment, container, false)
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        view?.apply {
            findViewById<Button>(R.id.btnOK)?.setOnClickListener {
                //TODO add torrent
                fragManager.popBackStack()
            }
            findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
                fragManager.popBackStack()
            }
        }
    }
}