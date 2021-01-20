package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog

object DialogList {
    fun show(context: Context, msg: String, list: List<String>, onSelect: (value: String, select: Int) -> Unit) {
        val builder = AlertDialog.Builder(context)
        if (msg.isNotEmpty())
            builder.setTitle(msg)

        builder.setItems(list.toTypedArray()) { dlg, sel ->
            onSelect(list[sel], sel)
            dlg?.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }
}