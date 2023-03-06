package ru.yourok.torrserve.server.models.torrent

data class Torrent(
    var title: String,
    var poster: String,
    var data: String,
    var timestamp: Long,
    var name: String,
    var hash: String,
    var stat: Int,
    var stat_string: String,
    var loaded_size: Long,
    var torrent_size: Long,
    var preloaded_bytes: Long,
    var preload_size: Long,
    var download_speed: Double,
    var upload_speed: Double,
    var total_peers: Int,
    var pending_peers: Int,
    var active_peers: Int,
    var connected_seeders: Int,
    var half_open_peers: Int,
    var bytes_written: Long,
    var bytes_written_data: Long,
    var bytes_read: Long,
    var bytes_read_data: Long,
    var bytes_read_useful_data: Long,
    var chunks_written: Long,
    var chunks_read: Long,
    var chunks_read_useful: Long,
    var chunks_read_wasted: Long,
    var pieces_dirtied_good: Long,
    var pieces_dirtied_bad: Long,
    var duration_seconds: Double?,
    var bit_rate: String?,

    var file_stats: List<FileStat>?
)

data class FileStat(
    var id: Int,
    var path: String,
    var length: Long
)