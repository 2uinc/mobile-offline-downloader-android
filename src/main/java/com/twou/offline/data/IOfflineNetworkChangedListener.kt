package com.twou.offline.data

interface IOfflineNetworkChangedListener {

    fun onChanged(isConnected: Boolean)
}