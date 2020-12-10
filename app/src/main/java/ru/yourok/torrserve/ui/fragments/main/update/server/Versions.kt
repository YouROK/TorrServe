package ru.yourok.torrserve.ui.fragments.main.update.server

class Versions : ArrayList<Version>()

data class Version(
    val version: String,
    val links: Map<String, String>
)