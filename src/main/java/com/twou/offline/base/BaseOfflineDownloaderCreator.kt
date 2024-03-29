package com.twou.offline.base

import com.twou.offline.data.IOfflineDownloaderCreator
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineQueueItem

abstract class BaseOfflineDownloaderCreator(val offlineQueueItem: OfflineQueueItem) :
    IOfflineDownloaderCreator {

    private var mError: Throwable? = null
    private var mCurrentProgress = -1
    private var mAllProgress = -1

    override fun getKeyOfflineItem(): KeyOfflineItem {
        return offlineQueueItem.keyItem
    }

    override fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit) {
        mError = null
        mCurrentProgress = -1
        mAllProgress = -1
    }

    override fun isPrepared(): Boolean = true

    override fun setError(error: Throwable?) {
        mError = error
    }

    override fun getError(): Throwable? = mError

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseOfflineDownloaderCreator) return false

        if (getKeyOfflineItem() != other.getKeyOfflineItem()) return false

        return true
    }

    override fun hashCode(): Int {
        return getKeyOfflineItem().hashCode()
    }

    override fun setProgress(currentProgress: Int, allProgress: Int) {
        mCurrentProgress = currentProgress
        mAllProgress = allProgress
    }

    override fun getCurrentProgress(): Int = mCurrentProgress

    override fun getAllProgress(): Int = mAllProgress
}