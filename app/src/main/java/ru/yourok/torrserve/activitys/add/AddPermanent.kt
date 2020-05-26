package ru.yourok.torrserve.activitys.add

import android.os.Bundle


class AddPermanent : Add() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playIntent?.let {
            startActivity(it)
        }
        finish()
    }
}