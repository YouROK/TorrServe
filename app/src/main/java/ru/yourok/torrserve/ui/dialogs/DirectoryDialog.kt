package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.animations.FIO
import ru.yourok.torrserve.app.App
import java.io.File

object DirectoryDialog {

    fun show(context: Context, msg: String, onSelect: (dir: String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        if (msg.isNotEmpty())
            builder.setTitle(msg)

        val directoryAdapter = DirectoryAdapter()
        val vi = LayoutInflater.from(context).inflate(R.layout.directory_chooser_fragment, null)

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
            vi?.findViewById<TextView>(R.id.tvCurrSize)?.text = directoryAdapter.getSize()
            vi?.findViewById<TextView>(R.id.tvCurrDir)?.text = directoryAdapter.getPath()
        }

        vi.findViewById<ImageButton>(R.id.btnUpDir).setOnClickListener {
            directoryAdapter.dirUp()
        }

        vi.findViewById<ImageButton>(R.id.btnCreateDir).setOnClickListener {
            val view1 = vi.findViewById<LinearLayout>(R.id.dirCtrlLayout) ?: return@setOnClickListener
            val view2 = vi.findViewById<LinearLayout>(R.id.dirNameLayout) ?: return@setOnClickListener
            FIO.anim(view1, view2, 300) {
                vi.findViewById<EditText>(R.id.etDirName)?.requestFocus()
            }
        }

        vi.findViewById<ImageButton>(R.id.btnDone).setOnClickListener {
            val view1 = vi.findViewById<LinearLayout>(R.id.dirCtrlLayout) ?: return@setOnClickListener
            val view2 = vi.findViewById<LinearLayout>(R.id.dirNameLayout) ?: return@setOnClickListener
            FIO.anim(view2, view1, 300)

            val dirName = vi.findViewById<EditText>(R.id.etDirName)?.text?.toString() ?: ""
            if (dirName.isNotBlank()) {
                val path = directoryAdapter.getPath()
                if (File(path).canWrite()) {
                    val dir = File(path, dirName)
                    if (dir.exists() || dir.mkdirs()) {
                        directoryAdapter.setPath(dir.path)
                        vi.findViewById<EditText>(R.id.etDirName)?.setText("")
                        return@setOnClickListener
                    }
                }
                App.Toast(context.getString(R.string.permission_deny))
            }
        }

        builder.setView(vi)

        builder.setPositiveButton(R.string.apply) { dialog, _ ->
            val path = directoryAdapter.getPath()
            if (!File(path).canWrite())
                App.Toast(context.getString(R.string.permission_deny))
            onSelect(path)
            dialog.dismiss()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }
}