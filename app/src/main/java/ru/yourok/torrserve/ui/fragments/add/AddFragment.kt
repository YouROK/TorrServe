package ru.yourok.torrserve.ui.fragments.add

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.ui.activities.play.PlayActivity

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
                val link = view?.findViewById<EditText>(R.id.etMagnet)?.text
                val title = view?.findViewById<EditText>(R.id.etTitle)?.text
                val poster = view?.findViewById<EditText>(R.id.etPoster)?.text
                val intent = Intent(this@AddFragment.context, PlayActivity::class.java)

                if (!link.isNullOrBlank()) {
                    intent.data = Uri.parse(link.toString())
                    intent.putExtra("title", title.toString())
                    intent.putExtra("poster", poster.toString())
                    startActivity(intent)
                }
                popBackStackFragment()
            }
            findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
                popBackStackFragment()
            }
        }
    }
}