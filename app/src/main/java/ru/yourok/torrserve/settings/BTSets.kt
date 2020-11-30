package ru.yourok.torrserve.settings

data class BTSets(
    var CacheSize: Long,
    var PreloadBufferSize: Long,
    var SaveOnDisk: Boolean,
    var ContentPath: String,
    var RetrackersMode: Int,
    var TorrentDisconnectTimeout: Int,
    var EnableDebug: Boolean,
    var EnableIPv6: Boolean,
    var DisableTCP: Boolean,
    var DisableUTP: Boolean,
    var DisableUPNP: Boolean,
    var DisableDHT: Boolean,
    var DisableUpload: Boolean,
    var DownloadRateLimit: Int,
    var UploadRateLimit: Int,
    var ConnectionsLimit: Int,
    var DhtConnectionLimit: Int,
    var PeersListenPort: Int,
)

