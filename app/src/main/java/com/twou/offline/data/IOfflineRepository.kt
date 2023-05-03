package com.twou.offline.data

import com.twou.offline.item.OfflineModule

interface IOfflineRepository {

    fun getOfflineModule(key: String): OfflineModule?

    fun addOfflineModule(offlineModule: OfflineModule)

    fun removeOfflineModule(offlineModule: OfflineModule)

    fun getAllModules(): List<OfflineModule>
}