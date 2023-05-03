package com.twou.offline.data

import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.item.KeyOfflineItem

interface IOfflineDownloaderCreator {

    fun getKeyOfflineItem(): KeyOfflineItem

    fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit)

    fun isPrepared(): Boolean

    fun createOfflineDownloader(unit: (downloader: BaseOfflineDownloader?, error: Throwable?) -> Unit)

    fun destroy()

    fun setError(error: Throwable?)

    fun getError(): Throwable?

    fun setProgress(currentProgress: Int, allProgress: Int)

    fun getCurrentProgress(): Int

    fun getAllProgress(): Int
}