package ru.yourok.torrserve.server.models.ffp

data class Format(
    val bit_rate: String,
    val duration: String,
    val filename: String,
    val format_long_name: String,
    val format_name: String,
    val nb_programs: Int,
    val nb_streams: Int,
    val probe_score: Int,
    val size: String,
    val start_time: String,
    val tags: Tags
)