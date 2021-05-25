package ru.yourok.torrserve.ui.fragments.main.servsets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.ui.fragments.TSFragment
import java.io.File

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

        vi?.findViewById<TextView>(R.id.tvCurrSize)?.text = directoryAdapter.getSize()
        vi?.findViewById<TextView>(R.id.tvCurrDir)?.text = directoryAdapter.getPath()

        directoryAdapter.onClick = {
            updateUI()
        }

        vi.findViewById<ImageButton>(R.id.btnUpDir).setOnClickListener {
            directoryAdapter.dirUp()
        }

        vi.findViewById<Button>(R.id.btnApply)?.setOnClickListener {
            val path = directoryAdapter.getPath()
            if (!File(path).canWrite())
                App.Toast(getString(R.string.permission_deny))
            lifecycleScope.launch {
                onResult?.invoke(path)
            }
            popBackStackFragment()
        }

        vi.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            popBackStackFragment()
        }

        return vi
    }

    private fun updateUI() {
        view?.findViewById<TextView>(R.id.tvCurrSize)?.text = directoryAdapter.getSize()
        view?.findViewById<TextView>(R.id.tvCurrDir)?.text = directoryAdapter.getPath()
    }
}