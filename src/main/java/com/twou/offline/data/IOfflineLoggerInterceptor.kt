package com.twou.offline.data

import com.twou.offline.util.OfflineLoggerType
import com.twou.offline.item.KeyOfflineItem

interface IOfflineLoggerInterceptor {

    fun onLogMessage(keyItem: KeyOfflineItem?, type: OfflineLoggerType, message: String)
}
