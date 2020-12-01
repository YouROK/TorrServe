package ru.yourok.torrserve.ui.fragments.main.update.apk

class Versions : ArrayList<Version>()

data class Version(
    val desc: String,
    val link: String,
    val version: String,
    val versionInt: Int
)