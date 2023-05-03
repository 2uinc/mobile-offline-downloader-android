package com.twou.offline.base.repository

import com.twou.offline.data.IOfflineUnsupportedRepository
import io.paperdb.Paper

class BaseOfflineUnsupportedRepository : IOfflineUnsupportedRepository {

    private val mOfflineUnsupportedSet = mutableSetOf<String>()

    private var isLoaded = false

    override fun isUnsupported(key: String): Boolean {
        if (!isLoaded) loadKeys()

        return mOfflineUnsupportedSet.contains(key)
    }

    override fun setUnsupported(key: String) {
        if (!isLoaded) loadKeys()

        mOfflineUnsupportedSet.add(key)

        saveKeys()
    }

    private fun loadKeys() {
        try {
            Paper.book().read<Set<String>>("offline_unsupported_module_keys")?.let {
                mOfflineUnsupportedSet.addAll(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isLoaded = true
    }

    private fun saveKeys() {
        Paper.book().write("offline_unsupported_module_keys", mOfflineUnsupportedSet)
    }
}