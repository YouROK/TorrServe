package ru.yourok.torrserve.ui.fragments.main.update.apk

class ApkVersions : ArrayList<ApkVersion>()

data class ApkVersion(
    val desc: String,
    val link: String,
    val version: String,
    val versionInt: Int
)