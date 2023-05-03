package com.twou.offline.util

interface OfflineNetworkChangedListener {

    fun onChanged(isConnected: Boolean)
}