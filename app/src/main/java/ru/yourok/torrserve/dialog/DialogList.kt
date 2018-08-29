package ru.yourok.torrserve.dialog

import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import kotlin.concurrent.thread


object DialogList {
    fun show(context: Context, msg: String, list: List<String>, multiSelect: Boolean, onSelect: (select: List<String>, i: List<Int>) -> Unit) {
        val builder = AlertDialog.Builder(context)
        if (msg.isNotEmpty())
            builder.setTitle(msg)

        if (!multiSelect) {
            builder.setItems(list.toTypedArray(), object : DialogInterface.OnClickListener {
                override fun onClick(p0: DialogInterface?, p1: Int) {
                    thread {
                        onSelect(listOf(list[p1]), listOf(p1))
                    }
                    p0?.dismiss()
                }
            })
        } else {
            val retBool = BooleanArray(list.size)
            builder.setMultiChoiceItems(list.toTypedArray(), retBool, object : DialogInterface.OnMultiChoiceClickListener {
                override fun onClick(p0: DialogInterface?, p1: Int, p2: Boolean) {
                    retBool[p1] = p2
                }
            })

            builder.setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {
                override fun onClick(p0: DialogInterface?, p1: Int) {
                    thread {
                        val retStr = mutableListOf<String>()
                        val retInt = mutableListOf<Int>()
                        for (i in 0 until retBool.size)
                            if (retBool[i]) {
                                retStr.add(list[i])
                                retInt.add(i)
                            }
                        onSelect(retStr, retInt)
                    }
                    p0?.dismiss()
                }
            })
        }
        val dialog = builder.create()
        dialog.show()
    }
}