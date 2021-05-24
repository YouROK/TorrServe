package ru.yourok.torrserve.ui.fragments.main.servsets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.fragments.TSFragment

class DirectoryChooserFragment : TSFragment() {

    private var directoryAdapter = DirectoryAdapter()

    fun show(activity: FragmentActivity?, onChoose: (String) -> Unit) {
        this.onResult = {
            if (it is String)
                onChoose(it)
        }
        show(activity, R.id.container, true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.directory_chooser_fragment, container, false)

        vi.findViewById<RecyclerView>(R.id.rvListDir)?.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = directoryAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
            directoryAdapter.setPath("/sdcard/")
        }

        directoryAdapter.onClick = {
            directoryAdapter.setPath(it)
        }

        vi.findViewById<ImageButton>(R.id.btnUpDir).setOnClickListener {

        }

        return vi
    }
}