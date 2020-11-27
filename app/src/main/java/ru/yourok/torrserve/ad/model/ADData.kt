package ru.yourok.torrserve.ad.model

data class ADData(
    val expired: String,
    val images: List<Image>
)

data class Image(
    val url: String,
    val wait: Long,
)