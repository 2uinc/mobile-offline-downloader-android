package com.twou.offline.base.downloader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import com.twou.offline.Offline
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineNoSpaceException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineModule
import com.twou.offline.util.*
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.min


abstract class BaseOfflineDownloader(private val mKeyItem: KeyOfflineItem) : BaseDownloader(),
    CoroutineScope {

    protected val handler = Handler(Looper.getMainLooper())

    protected val filesDirPath = OfflineDownloaderUtils.getDirPath(mKeyItem.key)

    private var mOnDownloadListener: OnDownloadListener? = null
    private var mOnDownloadProgressListener: OnDownloadProgressListener? = null
    private var mCurrentCall: Call? = null

    private val mGeneralFilesMap = mutableMapOf<String, ResourceLink>()

    private val mOfflineLoggerInterceptor = Offline.getOfflineLoggerInterceptor()

    private var mProgressAnimator: ValueAnimator? = null
    private var mCurrentFloatProgress = 0f
    private var mCurrentProgress = 0
    private var mAllProgress = 0
    private var isAnimationStarted = false
    private var mAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener {
        val value = (it.animatedValue as? Float) ?: return@AnimatorUpdateListener
        if (isDestroyed.get()) return@AnimatorUpdateListener

        mCurrentFloatProgress = value

        mOnDownloadProgressListener?.onProgressChanged(
            min(mCurrentFloatProgress.toInt(), 99000), 100000
        )
    }
    private var mAnimatorListener = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            if (isAnimationStarted && mCurrentProgress < mAllProgress) {
                isAnimationStarted = false
                val nextProgress = mCurrentProgress + 1
                val time = if (nextProgress == mAllProgress) 50000L else 20000L
                updateProgress(nextProgress, mAllProgress, time)
            }
        }
    }

    override val coroutineContext = Dispatchers.IO

    private val mBgJob = SupervisorJob()
    private val mBgScope = CoroutineScope(context = Dispatchers.IO + mBgJob)

    protected abstract fun startPreparation()
    protected abstract fun checkResourceBeforeSave(
        link: ResourceLink, data: String, threadId: Int
    ): String

    fun prepare(l1: OnDownloadListener, l2: OnDownloadProgressListener) {
        mOnDownloadListener = l1
        mOnDownloadProgressListener = l2
        mCurrentFloatProgress = 0f

        File(filesDirPath).apply {
            if (!exists()) mkdirs()
        }

        if (OfflineConst.IS_PREPARED) updateProgress(6, 10, 60000)
        startPreparation()
    }

    fun addGeneralFile(resourceLink: ResourceLink) {
        mGeneralFilesMap[resourceLink.fileName] = resourceLink
    }

    override fun destroy() {
        super.destroy()

        mOnDownloadProgressListener = null

        mBgJob.cancel()
        mBgJob.cancelChildren()

        try {
            mCurrentCall?.cancel()
        } catch (ignore: Exception) {
        }

        try {
            cancel()
        } catch (ignore: Exception) {
        }
        handler.removeCallbacksAndMessages(null)

        handler.post {
            mProgressAnimator?.removeAllUpdateListeners()
            mProgressAnimator?.cancel()
        }
    }

    protected fun processError(error: Throwable) {
        destroy()
        handler.post {
            if (error is OfflineDownloadException &&
                BaseOfflineUtils.isThereNoFreeSpace(Offline.getContext())
            ) {
                mOnDownloadListener?.onError(OfflineNoSpaceException())

            } else {
                mOnDownloadListener?.onError(error)
            }
        }
    }

    protected fun processWarning(message: String) {
        mOnDownloadListener?.onWarning(message)
    }

    protected fun processDebug(message: String) {
        mOnDownloadListener?.onDebug(message)
    }

    protected fun downloadAllResources(
        linkQueue: ConcurrentLinkedDeque<ResourceLink>,
        progressStatus: (current: Int, all: Int) -> Unit, onFinished: () -> Unit
    ) {
        if (isDestroyed.get()) return

        val allFilesSize = linkQueue.size

        mBgScope.launch main@{
            (0..MAX_THREAD_COUNT).map { i ->
                mBgScope.async job@{
                    while (!isDestroyed.get()) {
                        val resourceLink = linkQueue.poll()
                        if (resourceLink == null) {
                            return@job

                        } else {
                            while (!isDestroyed.get() && !resourceLink.isDownloaded()) {
                                var currentProgress = allFilesSize - linkQueue.size
                                if (currentProgress >= allFilesSize) {
                                    currentProgress = allFilesSize - 1
                                }

                                OfflineLogs.d(TAG, "Downloading ${currentProgress}/$allFilesSize")

                                mBgScope.launch(Dispatchers.Main) {
                                    progressStatus(currentProgress, allFilesSize)
                                }

                                try {
                                    if (resourceLink.isNeedCheckBeforeSave) {
                                        downloadFileWithCheck(resourceLink, i)

                                    } else {
                                        if (resourceLink.url.contains("/cache/")) {
                                            copyFileFromCache(resourceLink)

                                        } else {
                                            downloadFileToLocalStorage(resourceLink, i)
                                        }
                                    }

                                    resourceLink.finishDownload()

                                } catch (e: Exception) {
                                    e.printStackTrace()

                                    delay(4000)
                                    if (!Offline.isConnected() ||
                                        BaseOfflineUtils.isThereNoFreeSpace(Offline.getContext())
                                    ) {
                                        resourceLink.finishDownload()
                                        processError(OfflineDownloadException(e))

                                    } else {
                                        resourceLink.newAttemptToDownload()
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()

            if (isDestroyed.get()) return@main

            withContext(Dispatchers.Main) {
                if (isDestroyed.get()) return@withContext
                progressStatus(allFilesSize, allFilesSize)
            }

            if (!isDestroyed.get()) onFinished()
        }
    }

    @Throws(Exception::class)
    protected fun downloadFileToLocalStorage(resourceLink: ResourceLink, threadId: Int = 1000) {
        OfflineLogs.d(
            TAG,
            mKeyItem.key + ": trying to download file " + resourceLink.url + " | with path " + resourceLink.getFilePath()
        )

        val client = Offline.getClient()
        val requestBuilder = Request.Builder().url(resourceLink.url)

        try {
            val uri = Uri.parse(resourceLink.url)
            val cookies = CookieManager.getInstance().getCookie(uri.scheme + "://" + uri.host)
            requestBuilder.addHeader("Cookie", cookies ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val call = client.newCall(requestBuilder.build())
        mCurrentCall = call
        val response = call.execute()

        response.headers["content-length"]?.let { contentLength ->
            OfflineLogs.d(
                TAG, mKeyItem.key + ": File size is " + contentLength + " for " + resourceLink.url
            )
        }

        if (MimeTypeMap.getFileExtensionFromUrl(resourceLink.fileName).isBlank() ||
            resourceLink.fileName.endsWith(".bin")
        ) {
            var isExtensionFound = false
            response.headers["content-type"]?.let { contentType ->
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
                if (!extension.isNullOrBlank()) {
                    isExtensionFound = true

                    if (!resourceLink.fileName.endsWith(".$extension")) {
                        resourceLink.fileName += ".$extension"
                    }
                }
            }

            if (!isExtensionFound) {
                response.headers["content-disposition"]?.let { disposition ->
                    val values = disposition.split("\"")
                    if (values.size > 1 && values[0].contains("filename")) {
                        resourceLink.fileName = values[1]
                    }
                }
            }
        }

        val body = response.body

        val downloadFile = File(resourceLink.getShortFilePath())
        if (downloadFile.exists()) {
            body?.close()
            return
        }

        val tempFile = File(resourceLink.dirPath + "/$threadId.tmp")
        val outputStream = FileOutputStream(tempFile)

        if (body == null) {
            mOfflineLoggerInterceptor?.onLogMessage(
                mKeyItem, OfflineLoggerType.DOWNLOAD_WARNING, "Body is NULL ${response.code}"
            )
        }
        body?.use { responseBody ->
            val inputStream = responseBody.byteStream()
            val data = ByteArray(1024 * 8)

            var count: Int

            while (inputStream.read(data).also { count = it } != -1) {
                if (isDestroyed.get()) return@use
                outputStream.write(data, 0, count)
            }
        }

        outputStream.flush()
        outputStream.close()

        if (!isDestroyed.get()) {
            tempFile.renameTo(downloadFile)
        }

        OfflineLogs.d(TAG, mKeyItem.key + ": File downloaded for " + resourceLink.url)
    }

    protected fun updateProgress(
        currentProgress: Int, allProgress: Int, animDuration: Long = 1500L
    ) {
        if (isDestroyed.get()) return

        mCurrentProgress = currentProgress
        mAllProgress = allProgress

        val next = currentProgress.toFloat() * 100 / allProgress
        val nextValue = next * 1000f

        if (nextValue < mCurrentFloatProgress) return

        if (mProgressAnimator == null) {
            mProgressAnimator = ValueAnimator.ofFloat(mCurrentFloatProgress, nextValue).apply {
                interpolator = LinearInterpolator()
                duration = animDuration

                addUpdateListener(mAnimatorUpdateListener)
                addUpdateListener { }
                if (OfflineConst.IS_PREPARED) {
                    addListener(mAnimatorListener)
                    addListener(object : AnimatorListenerAdapter() {})
                }
                start()
            }

        } else {
            isAnimationStarted = true
            mProgressAnimator?.removeListener(mAnimatorListener)
            mProgressAnimator?.removeUpdateListener(mAnimatorUpdateListener)
            mProgressAnimator?.end()
            mProgressAnimator?.setFloatValues(mCurrentFloatProgress, nextValue)
            mProgressAnimator?.duration = animDuration
            mProgressAnimator?.addUpdateListener(mAnimatorUpdateListener)
            if (OfflineConst.IS_PREPARED) mProgressAnimator?.addListener(mAnimatorListener)
            mProgressAnimator?.start()
        }
    }

    protected fun setAllDataDownloaded(offlineModule: OfflineModule) {
        launch(Dispatchers.Main) {
            mProgressAnimator?.removeAllUpdateListeners()
            mProgressAnimator?.cancel()
            launch(Dispatchers.Default) {
                mOnDownloadListener?.onDownloaded(offlineModule)
            }
        }
    }

    @Throws(Exception::class)
    private fun downloadFileWithCheck(resourceLink: ResourceLink, threadId: Int) {
        OfflineLogs.d(
            TAG,
            "trying to download file with check " + resourceLink.url + " | with path " + resourceLink.getFilePath()
        )

        if (isGeneralLinkExists(resourceLink)) {
            OfflineLogs.d(TAG, "    General link exists")
            return
        }

        val content = downloadFileContent(resourceLink.url)
        val updatedContent = checkResourceBeforeSave(resourceLink, content, threadId)
        val tempFile = File(resourceLink.dirPath + "/$threadId.tmp")
        tempFile.writeText(updatedContent, Charset.forName("UTF-8"))

        val downloadFile = File(resourceLink.getShortFilePath())
        tempFile.renameTo(downloadFile)
    }

    private fun copyFileFromCache(resourceLink: ResourceLink) {
        val file = File(resourceLink.url)
        val destinationFile = File(resourceLink.getShortFilePath())
        if (destinationFile.exists()) destinationFile.delete()
        file.copyTo(destinationFile)
    }

    private fun isGeneralLinkExists(resourceLink: ResourceLink): Boolean {
        val generalLink = mGeneralFilesMap[resourceLink.fileName]
        if (generalLink != null) {
            resourceLink.dirPath = generalLink.dirPath + Uri.parse(resourceLink.url).encodedPath

            if (File(resourceLink.getShortFilePath()).exists()) return true

            File(resourceLink.dirPath).apply {
                if (!exists()) mkdirs()
            }
        }

        return false
    }

    data class ResourceLink(
        var url: String, var dirPath: String, var fileName: String, var oldUrl: String = "",
        var isNeedCheckBeforeSave: Boolean = false, var attemptCount: Int = 0
    ) {
        fun getFilePath() = "file://$dirPath/$fileName"

        fun getShortFilePath() = "$dirPath/$fileName"

        fun isDownloaded(): Boolean = attemptCount >= MAX_ATTEMPT_COUNT

        fun newAttemptToDownload() {
            attemptCount++
        }

        fun finishDownload() {
            attemptCount = MAX_ATTEMPT_COUNT
        }
    }

    interface OnDownloadListener {

        fun onDownloaded(offlineModule: OfflineModule)
        fun onError(error: Throwable)
        fun onWarning(message: String)
        fun onDebug(message: String)
    }

    interface OnDownloadProgressListener {

        fun onProgressChanged(currentProgress: Int, allProgress: Int)
    }

    companion object {

        private const val TAG = "OfflineDownloader"

        private const val MAX_THREAD_COUNT = 3

        private const val MAX_ATTEMPT_COUNT = 3
    }
}