package com.twou.offline.item

data class OfflineQueueItem(val keyItem: KeyOfflineItem, var queueState: Int = QueueState.PREPARING)

object QueueState {

    const val PREPARING = 0
    const val PREPARED = 1
    const val DOWNLOADING = 2
    const val PAUSED = 3

    // When no Internet connection
    const val NETWORK_ERROR = 10

    // When unable to download a file (server issue)
    const val SERVER_ERROR = 11

    // When unsupported content
    const val UNSUPPORTED_ERROR = 12

    // When no free space available
    const val NO_SPACE_ERROR = 13
}
