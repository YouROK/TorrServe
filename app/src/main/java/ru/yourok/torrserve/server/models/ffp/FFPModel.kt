package ru.yourok.torrserve.server.models.ffp

data class FFPModel(
    val format: Format,
    val streams: List<Stream>
)