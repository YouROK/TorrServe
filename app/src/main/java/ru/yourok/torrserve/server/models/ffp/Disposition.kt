package ru.yourok.torrserve.server.models.ffp

data class Disposition(
    val attached_pic: Int,
    val clean_effects: Int,
    val comment: Int,
    val default: Int,
    val dub: Int,
    val forced: Int,
    val hearing_impaired: Int,
    val karaoke: Int,
    val lyrics: Int,
    val original: Int,
    val visual_impaired: Int
)