package ru.yourok.torrserve.settings

data class BTSets(
    // Cache
    var CacheSize: Long,
    var PreloadBuffer: Boolean,
    var PreloadCache: Int,
    var ReaderReadAHead: Int,
    // Storage
    var UseDisk: Boolean,
    var TorrentsSavePath: String,
    var RemoveCacheOnDrop: Boolean,
    // Torrent
    var ForceEncrypt: Boolean,
    var RetrackersMode: Int,
    var TorrentDisconnectTimeout: Int,
    var EnableDebug: Boolean,
    var ResponsiveMode: Boolean,
    // DLNA
    var EnableDLNA: Boolean,
    var FriendlyName: String,
    // Rutor search
    var EnableRutorSearch: Boolean,
    // Torznab search is configured in the server web UI; null is skipped by Gson, so saving never overwrites it
    var EnableTorznabSearch: Boolean? = null,
    // BT Config
    var EnableIPv6: Boolean,
    var DisableTCP: Boolean,
    var DisableUTP: Boolean,
    var DisableUPNP: Boolean,
    var DisableDHT: Boolean,
    var DisablePEX: Boolean,
    var DisableUpload: Boolean,
    var DownloadRateLimit: Int,
    var UploadRateLimit: Int,
    var ConnectionsLimit: Int,
    var DhtConnectionLimit: Int,
    var PeersListenPort: Int,
    // Proxy
    var EnableProxy: Boolean,
    var ProxyHosts: List<String>?,
    // LPD
    var EnableLPD: Boolean,
    var LPDIPv6: Boolean,
    // Bonjour
    var EnableBonjour: Boolean,
    // FS
    var ShowFSActiveTorr: Boolean,
    // Viewed
    var TrackTimecode: Boolean,
)
