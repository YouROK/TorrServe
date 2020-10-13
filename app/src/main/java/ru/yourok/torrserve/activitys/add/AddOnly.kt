package ru.yourok.torrserve.activitys.add

import android.os.Bundle

class AddOnly : Add() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playIntent?.let {
            it.putExtra("DontPlay", true)
            startActivity(it)
        }
        startPlayActivity()
    }
}