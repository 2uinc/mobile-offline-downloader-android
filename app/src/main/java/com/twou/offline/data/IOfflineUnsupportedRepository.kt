package com.twou.offline.data

interface IOfflineUnsupportedRepository {

    fun isUnsupported(key: String): Boolean

    fun setUnsupported(key: String)

}