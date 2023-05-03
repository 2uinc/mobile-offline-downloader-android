package com.twou.offline.base.repository

import com.twou.offline.data.IOfflineRepository
import com.twou.offline.item.OfflineModule
import io.paperdb.Paper

class BaseOfflineRepository : IOfflineRepository {

    private val mOfflineModules = mutableMapOf<String, OfflineModule>()

    private var isLoaded = false

    override fun getOfflineModule(key: String): OfflineModule? {
        if (!isLoaded) loadModules()

        return mOfflineModules[key]
    }

    override fun addOfflineModule(offlineModule: OfflineModule) {
        if (!isLoaded) loadModules()

        mOfflineModules[offlineModule.key] = offlineModule
        saveModules()
    }

    override fun removeOfflineModule(offlineModule: OfflineModule) {
        if (!isLoaded) loadModules()

        mOfflineModules.remove(offlineModule.key)
        saveModules()
    }

    override fun getAllModules(): List<OfflineModule> {
        if (!isLoaded) loadModules()

        return mOfflineModules.values.toList()
    }

    private fun loadModules() {
        try {
            Paper.book().read<Set<OfflineModule>>("offline_modules")?.forEach {
                mOfflineModules[it.key] = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isLoaded = true
    }

    private fun saveModules() {
        Paper.book().write("offline_modules", mOfflineModules.values.toSet())
    }
}