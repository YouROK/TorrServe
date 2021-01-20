package ru.yourok.torrserve.ext

import java.net.URLEncoder

fun String.urlEncode(): String = URLEncoder.encode(this, "utf8")