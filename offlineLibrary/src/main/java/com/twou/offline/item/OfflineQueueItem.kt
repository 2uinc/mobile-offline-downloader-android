package com.twou.offline.item

data class OfflineQueueItem(val keyItem: KeyOfflineItem, var queueState: Int = QueueState.PREPARING)

object QueueState {

    const val PREPARING = -2
    const val DOWNLOADING = -1
    const val PAUSED = 0

    // When no Internet connection
    const val NETWORK_ERROR = 1

    // When unable to download a file (server issue)
    const val SERVER_ERROR = 2

    // When unsupported content
    const val UNSUPPORTED_ERROR = 3

    // When no free space available
    const val NO_SPACE_ERROR = 4
}
