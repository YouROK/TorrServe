package ru.yourok.torrserve.ui.fragments.main.update.server

class ServVersions : ArrayList<ServVersion>()

data class ServVersion(
    val version: String,
    val links: Map<String, String>
)