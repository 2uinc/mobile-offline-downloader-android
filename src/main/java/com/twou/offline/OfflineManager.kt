package com.twou.offline

import com.twou.offline.base.BaseOfflineDownloaderCreator
import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.data.IOfflineDownloaderCreator
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.error.OfflineNoSpaceException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.OfflineModule
import com.twou.offline.item.OfflineQueueItem
import com.twou.offline.item.QueueState
import com.twou.offline.util.BaseOfflineUtils
import com.twou.offline.util.OfflineDownloaderUtils
import com.twou.offline.util.OfflineLoggerType
import io.paperdb.Paper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList

class OfflineManager internal constructor() : CoroutineScope {

    private val mOfflineRepository = Offline.getOfflineRepository()
    private val mOfflineUnsupportedRepository = Offline.getOfflineUnsupportedRepository()
    private val mOfflineLoggerInterceptor = Offline.getOfflineLoggerInterceptor()

    private val mCreatorList = LinkedList<BaseOfflineDownloaderCreator>()

    private var mCurrentState = STATE_IDLE

    private val mListenerSet = mutableSetOf<OfflineListener>()

    override val coroutineContext = Dispatchers.Main

    init {
        Offline.addNetworkListener(object : IOfflineNetworkChangedListener {
            override fun onChanged(isConnected: Boolean) {
                if (isConnected) {
                    resumeAll(QueueState.NETWORK_ERROR)

                } else {
                    if (mCurrentState == STATE_DOWNLOADING) {
                        pauseAll(QueueState.NETWORK_ERROR)

                    } else {
                        mCreatorList.forEach { creator ->
                            if (creator.offlineQueueItem.queueState != QueueState.UNSUPPORTED_ERROR) {
                                creator.offlineQueueItem.queueState = QueueState.NETWORK_ERROR
                            }
                        }
                    }
                }
            }
        })
    }

    fun addOfflineDownloaderCreator(creator: BaseOfflineDownloaderCreator) {
        if (mOfflineUnsupportedRepository.isUnsupported(creator.getKeyOfflineItem().key)) return
        if (mCreatorList.contains(creator)) return
        mCreatorList.add(creator)

        setItemAdded(creator.getKeyOfflineItem().key)

        creator.prepareOfflineDownloader { error ->
            creator.setError(error)
            saveQueue()

            error?.let {
                mOfflineLoggerInterceptor?.onLogMessage(
                    creator.getKeyOfflineItem(), OfflineLoggerType.PREPARE, error.message ?: "",
                )

                if (error is OfflineUnsupportedException) {
                    mOfflineUnsupportedRepository.setUnsupported(creator.getKeyOfflineItem().key)
                    creator.offlineQueueItem.queueState = QueueState.UNSUPPORTED_ERROR

                } else {
                    creator.offlineQueueItem.queueState = QueueState.SERVER_ERROR
                }
            }

            launch {
                error?.let { setItemError(creator.getKeyOfflineItem().key, error) }

                if (creator.offlineQueueItem.queueState == QueueState.PREPARING) {
                    creator.offlineQueueItem.queueState = QueueState.PREPARED
                    setItemPrepared(creator.getKeyOfflineItem().key)
                }

                updateOfflineManagerState()
            }
        }
    }

    fun getCurrentState() = mCurrentState

    // Call this function on the first launch to restore Queue
    fun start() {
        if (mCurrentState != STATE_IDLE) return

        restoreQueue()
    }

    fun pause(key: String) {
        run job@{
            mCreatorList.forEach { creator ->
                if (creator.getKeyOfflineItem().key == key) {
                    creator.destroy()
                    creator.offlineQueueItem.queueState = QueueState.PAUSED

                    saveQueue()
                    setItemPaused(creator.getKeyOfflineItem().key)
                    return@job
                }
            }
        }

        updateOfflineManagerState()
    }

    fun resume(key: String) {
        if (!Offline.isConnected()) return

        run job@{
            mCreatorList.forEach { creator ->
                if (creator.getKeyOfflineItem().key == key) {
                    creator.offlineQueueItem.queueState = QueueState.PREPARED
                    saveQueue()
                    setItemResumed(key)

                    updateOfflineManagerState()
                    return@job
                }
            }
        }
    }

    fun pauseAll(newItemState: Int = QueueState.PAUSED) {
        if (mCurrentState != STATE_DOWNLOADING) return

        mCreatorList.forEach { creator ->
            if (creator.offlineQueueItem.queueState == QueueState.PREPARING ||
                creator.offlineQueueItem.queueState == QueueState.PREPARED ||
                creator.offlineQueueItem.queueState == QueueState.DOWNLOADING ||
                creator.offlineQueueItem.queueState == QueueState.SERVER_ERROR
            ) {
                creator.destroy()
                creator.offlineQueueItem.queueState = newItemState
            }
        }

        saveQueue()
        setNewState(STATE_PAUSED)
    }

    fun resumeAll(currentState: Int = QueueState.PAUSED) {
        if (mCurrentState != STATE_PAUSED) return

        mCreatorList.forEach { creator ->
            if ((currentState == QueueState.PAUSED &&
                        creator.offlineQueueItem.queueState != QueueState.UNSUPPORTED_ERROR) ||
                creator.offlineQueueItem.queueState == currentState
            ) {
                creator.offlineQueueItem.queueState = QueueState.PREPARED
            }
        }

        saveQueue()
        updateOfflineManagerState()
    }

    fun getAllDownloads(): List<OfflineQueueItem> {
        val items = mutableListOf<OfflineQueueItem>()

        mCreatorList.forEach { creator ->
            val item = creator.offlineQueueItem
            if (item.keyItem.title.isNotEmpty()) {
                items.add(item)
            }
        }

        return items
    }

    fun addListener(l: OfflineListener) {
        mListenerSet.add(l)

        l.onStateChanged(mCurrentState)
    }

    fun removeListener(l: OfflineListener) {
        mListenerSet.remove(l)
    }

    fun remove(keys: List<String>) {
        keys.forEach { key ->
            val each = mCreatorList.iterator()
            run job@{
                while (each.hasNext()) {
                    each.next().let {
                        if (it.getKeyOfflineItem().key == key) {
                            it.destroy()
                            each.remove()
                            return@job
                        }
                    }
                }
            }

            mOfflineRepository.getOfflineModule(key)?.let { item ->
                File(OfflineDownloaderUtils.getDirPath(key)).deleteRecursively()
                mOfflineRepository.removeOfflineModule(item)
            }
        }

        saveQueue()
        launch {
            setItemsRemoved(keys)
            updateOfflineManagerState()
        }
    }

    fun remove(key: String) {
        val each = mCreatorList.iterator()
        run job@{
            while (each.hasNext()) {
                each.next().let {
                    if (it.getKeyOfflineItem().key == key) {
                        it.destroy()
                        each.remove()
                        return@job
                    }
                }
            }
        }

        mOfflineRepository.getOfflineModule(key)?.let { item ->
            File(OfflineDownloaderUtils.getDirPath(key)).deleteRecursively()
            mOfflineRepository.removeOfflineModule(item)
        }

        saveQueue()
        launch {
            setItemRemoved(key)
            updateOfflineManagerState()
        }
    }

    fun removeAllActive() {
        val each = mCreatorList.iterator()
        while (each.hasNext()) {
            each.next().let {
                mOfflineRepository.getOfflineModule(it.getKeyOfflineItem().key)?.let { item ->
                    File(OfflineDownloaderUtils.getDirPath(it.getKeyOfflineItem().key)).deleteRecursively()
                    mOfflineRepository.removeOfflineModule(item)
                }

                it.destroy()
                each.remove()
            }
        }


        saveQueue()
        setRemovedAllActive()
        updateOfflineManagerState()
    }

    fun getCurrentDownloaderCreator(): IOfflineDownloaderCreator? {
        mCreatorList.forEach {
            if (it.offlineQueueItem.queueState == QueueState.DOWNLOADING) {
                return it
            }
        }

        return null
    }

    fun getDownloaderCreator(key: String): IOfflineDownloaderCreator? {
        mCreatorList.forEach {
            if (it.getKeyOfflineItem().key == key) {
                return it
            }
        }

        return null
    }

    private fun saveQueue() {
        val items = mutableListOf<OfflineQueueItem>()

        mCreatorList.forEach { creator -> items.add(creator.offlineQueueItem) }

        Paper.book().write("offline_queue", items)
    }

    private fun restoreQueue() {
        try {
            Paper.book().read<List<OfflineQueueItem>>("offline_queue")?.forEach {
                if (Offline.isConnected() && it.queueState == QueueState.NETWORK_ERROR) {
                    it.queueState = QueueState.PREPARING

                } else if (it.queueState == QueueState.PREPARED ||
                    it.queueState == QueueState.DOWNLOADING
                ) {
                    it.queueState = QueueState.PREPARING
                }

                addOfflineDownloaderCreator(Offline.getCreatorUnit().invoke(it))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startDownloading(creator: BaseOfflineDownloaderCreator) {
        creator.offlineQueueItem.queueState = QueueState.DOWNLOADING
        creator.setProgress(-1, -1)

        if (BaseOfflineUtils.isThereNoFreeSpace(Offline.getContext())) {
            creator.offlineQueueItem.queueState = QueueState.NO_SPACE_ERROR
            saveQueue()

            setItemError(creator.getKeyOfflineItem().key, OfflineNoSpaceException())
            updateOfflineManagerState()
            mOfflineLoggerInterceptor?.onLogMessage(
                creator.getKeyOfflineItem(), OfflineLoggerType.COMMON, "No free space"
            )
            return
        }

        setItemStartedDownload(creator.getKeyOfflineItem().key)

        creator.createOfflineDownloader { offlineDownloader, error ->
            error?.let {
                mOfflineLoggerInterceptor?.onLogMessage(
                    creator.getKeyOfflineItem(), OfflineLoggerType.PREPARE, error.message ?: ""
                )

                creator.setError(it)
                if (error is OfflineUnsupportedException) {
                    mOfflineUnsupportedRepository.setUnsupported(creator.getKeyOfflineItem().key)
                    creator.offlineQueueItem.queueState = QueueState.UNSUPPORTED_ERROR

                } else if (creator.offlineQueueItem.queueState != QueueState.NETWORK_ERROR) {
                    creator.offlineQueueItem.queueState = QueueState.SERVER_ERROR
                }
                saveQueue()

                launch {
                    setItemError(creator.getKeyOfflineItem().key, error)
                    updateOfflineManagerState()
                }
                return@createOfflineDownloader
            }

            offlineDownloader?.prepare(object : BaseOfflineDownloader.OnDownloadListener {
                override fun onDownloaded(offlineModule: OfflineModule) {
                    mCreatorList.remove(creator)
                    saveQueue()
                    saveOfflineModule(offlineModule)

                    launch {
                        setItemDownloaded(creator.getKeyOfflineItem().key)
                        updateOfflineManagerState()
                    }
                }

                override fun onError(error: Throwable) {
                    error.printStackTrace()
                    mOfflineLoggerInterceptor?.onLogMessage(
                        creator.getKeyOfflineItem(), OfflineLoggerType.DOWNLOAD_ERROR,
                        error.message ?: ""
                    )

                    if (creator.offlineQueueItem.queueState == QueueState.DOWNLOADING) {
                        creator.setError(error)
                        if (error is OfflineUnsupportedException) {
                            mOfflineUnsupportedRepository.setUnsupported(creator.getKeyOfflineItem().key)
                            creator.offlineQueueItem.queueState = QueueState.UNSUPPORTED_ERROR

                        } else if (creator.offlineQueueItem.queueState != QueueState.NETWORK_ERROR) {
                            creator.offlineQueueItem.queueState = QueueState.SERVER_ERROR
                        }
                        saveQueue()

                        launch {
                            setItemError(creator.getKeyOfflineItem().key, error)
                            updateOfflineManagerState()
                        }
                    }
                }

                override fun onWarning(message: String) {
                    mOfflineLoggerInterceptor?.onLogMessage(
                        creator.getKeyOfflineItem(), OfflineLoggerType.DOWNLOAD_WARNING, message
                    )
                }

            }, object : BaseOfflineDownloader.OnDownloadProgressListener {
                override fun onProgressChanged(currentProgress: Int, allProgress: Int) {
                    creator.setProgress(
                        if (currentProgress >= allProgress) allProgress else currentProgress,
                        allProgress
                    )

                    setNewProgress(creator.getKeyOfflineItem().key, currentProgress, allProgress)
                }
            })
        }
    }

    private fun saveOfflineModule(offlineModule: OfflineModule) {
        mOfflineRepository.addOfflineModule(offlineModule)
    }

    private fun updateOfflineManagerState() {
        if (mCreatorList.isEmpty()) {
            setNewState(STATE_IDLE)
            return
        }

        var downloadsCount = 0
        mCreatorList.forEach { creator ->
            if (creator.offlineQueueItem.queueState == QueueState.DOWNLOADING) downloadsCount++
        }

        if (downloadsCount >= DOWNLOADS_COUNT) {
            setNewState(STATE_DOWNLOADING)
            return

        } else {
            mCreatorList.forEach { creator ->
                if (creator.offlineQueueItem.queueState == QueueState.PREPARED) {
                    downloadsCount++
                    setNewState(STATE_DOWNLOADING)
                    startDownloading(creator)

                    if (downloadsCount >= DOWNLOADS_COUNT) return
                }
            }
        }

        if (downloadsCount == 0) {
            setNewState(STATE_PAUSED)
        }
    }

    private fun setNewState(state: Int) {
        if (state == mCurrentState) return

        mCurrentState = state

        launch { mListenerSet.forEach { it.onStateChanged(state) } }
    }

    private fun setNewProgress(key: String, currentProgress: Int, allProgress: Int) {
        launch {
            mListenerSet.forEach { it.onProgressChanged(key, currentProgress, allProgress) }
        }
    }

    private fun setItemAdded(key: String) {
        mListenerSet.forEach { it.onItemAdded(key) }
    }

    private fun setItemPrepared(key: String) {
        mListenerSet.forEach { it.onItemPrepared(key) }
    }

    private fun setItemRemoved(key: String) {
        mListenerSet.forEach { it.onItemRemoved(key) }
    }

    private fun setItemsRemoved(keys: List<String>) {
        mListenerSet.forEach { it.onItemsRemoved(keys) }
    }

    private fun setRemovedAllActive() {
        mListenerSet.forEach { it.onRemovedAllActive() }
    }

    private fun setItemStartedDownload(key: String) {
        mListenerSet.forEach { it.onItemStartedDownload(key) }
    }

    private fun setItemDownloaded(key: String) {
        mListenerSet.forEach { it.onItemDownloaded(key) }
    }

    private fun setItemError(key: String, error: Throwable) {
        mListenerSet.forEach { it.onItemError(key, error) }
    }

    private fun setItemPaused(key: String) {
        mListenerSet.forEach { it.onItemPaused(key) }
    }

    private fun setItemResumed(key: String) {
        mListenerSet.forEach { it.onItemResumed(key) }
    }

    open class OfflineListener : IOfflineListener {
        override fun onStateChanged(state: Int) {
        }

        override fun onProgressChanged(key: String, currentProgress: Int, allProgress: Int) {
        }

        override fun onItemAdded(key: String) {
        }

        override fun onItemPrepared(key: String) {
        }

        override fun onItemRemoved(key: String) {
        }

        override fun onItemsRemoved(keys: List<String>) {
        }

        override fun onRemovedAllActive() {
        }

        override fun onItemStartedDownload(key: String) {
        }

        override fun onItemDownloaded(key: String) {
        }

        override fun onItemError(key: String, error: Throwable) {
        }

        override fun onItemPaused(key: String) {
        }

        override fun onItemResumed(key: String) {
        }
    }

    interface IOfflineListener {

        fun onStateChanged(state: Int)

        fun onProgressChanged(key: String, currentProgress: Int, allProgress: Int)

        fun onItemAdded(key: String)

        fun onItemPrepared(key: String)

        fun onItemRemoved(key: String)

        fun onItemsRemoved(keys: List<String>)

        fun onRemovedAllActive()

        fun onItemStartedDownload(key: String)

        fun onItemDownloaded(key: String)

        fun onItemError(key: String, error: Throwable)

        fun onItemPaused(key: String)

        fun onItemResumed(key: String)
    }

    companion object {

        const val STATE_IDLE = 0
        const val STATE_DOWNLOADING = 1
        const val STATE_PAUSED = 2

        const val DOWNLOADS_COUNT = 3
    }
}