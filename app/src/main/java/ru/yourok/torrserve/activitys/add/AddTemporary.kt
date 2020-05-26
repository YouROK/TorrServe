package ru.yourok.torrserve.activitys.add

import android.os.Bundle


class AddTemporary : Add() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playIntent?.let {
            it.putExtra("DontSave", true)
            startActivity(it)
        }
        finish()
    }
}