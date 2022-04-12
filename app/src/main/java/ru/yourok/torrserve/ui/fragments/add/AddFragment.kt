package ru.yourok.torrserve.ui.fragments.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.ui.fragments.TSFragment

class AddFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.add_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            findViewById<Button>(R.id.btnOK)?.setOnClickListener {
                val link = view.findViewById<EditText>(R.id.etMagnet)?.text?.toString() ?: ""
                val title = view.findViewById<EditText>(R.id.etTitle)?.text?.toString() ?: ""
                val poster = view.findViewById<EditText>(R.id.etPoster)?.text?.toString() ?: ""

                if (link.isNotBlank())
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            addTorrent("", link, title, poster, "", true)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            App.toast(e.message ?: getString(R.string.error_retrieve_data))
                        }
                    }
                popBackStackFragment()
            }
            findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
                popBackStackFragment()
            }
        }
    }
}