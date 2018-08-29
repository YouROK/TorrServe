package ru.yourok.torrserve.dialog

import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import ru.yourok.torrserve.R
import kotlin.concurrent.thread


object DialogInputList {

    fun show(context: Context, msg: String, listStr: List<String>, onEnter: (txt: String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        if (msg.isNotEmpty())
            builder.setTitle(msg)

        val vi = LayoutInflater.from(context).inflate(R.layout.dialog_input_list, null, false)
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, listStr)
        val list = vi.findViewById<ListView>(R.id.dialog_list)
        val edit = vi.findViewById<EditText>(R.id.dialog_edit)
        list.setAdapter(adapter)

        builder.setView(vi)
        builder.setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {
            override fun onClick(p0: DialogInterface?, p1: Int) {
                thread {
                    onEnter(edit.text.toString().trim())
                }
                p0?.dismiss()
            }
        })
        val dialog = builder.create()
        list.setOnItemClickListener { adapterView, view, i, l ->
            edit.setText(adapter.getItem(i).toString())
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        edit.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                true
            }
            false
        })
        dialog.show()
    }
}