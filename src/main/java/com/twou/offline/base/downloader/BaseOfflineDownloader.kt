package com.twou.offline.base.downloader

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import com.twou.offline.Offline
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineNoSpaceException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineModule
import com.twou.offline.util.BaseOfflineUtils
import com.twou.offline.util.OfflineDownloaderUtils
import com.twou.offline.util.OfflineLoggerType
import com.twou.offline.util.OfflineLogs
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors

private val mBgDispatcher =
    Executors.newFixedThreadPool(15).asCoroutineDispatcher()

abstract class BaseOfflineDownloader(private val mKeyItem: KeyOfflineItem) : BaseDownloader(),
    CoroutineScope {

    protected val handler = Handler(Looper.getMainLooper())

    protected val filesDirPath = OfflineDownloaderUtils.getDirPath(mKeyItem.key)

    private var mOnDownloadListener: OnDownloadListener? = null
    private var mCurrentCall: Call? = null

    private val mGeneralFilesMap = mutableMapOf<String, ResourceLink>()

    private val mOfflineLoggerInterceptor = Offline.getOfflineLoggerInterceptor()

    private var mCurrentProgress = 0f

    override val coroutineContext = Dispatchers.IO

    private val mBgJob = SupervisorJob()
    private val mBgScope = CoroutineScope(context = Dispatchers.IO + mBgJob)

    protected abstract fun startPreparation()
    protected abstract fun checkResourceBeforeSave(
        link: ResourceLink, data: String, threadId: Int
    ): String

    fun prepare(l: OnDownloadListener) {
        mOnDownloadListener = l
        mCurrentProgress = 0f

        File(filesDirPath).apply {
            if (!exists()) mkdirs()
        }

        startPreparation()
    }

    fun addGeneralFile(resourceLink: ResourceLink) {
        mGeneralFilesMap[resourceLink.fileName] = resourceLink
    }

    override fun destroy() {
        super.destroy()

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
    }

    protected fun processError(error: Throwable) {
        destroy()
        mBgScope.launch(Dispatchers.Main) {
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

    protected fun downloadAllResources(
        linkQueue: ConcurrentLinkedDeque<ResourceLink>,
        progressStatus: (current: Int, all: Int) -> Unit, onFinished: () -> Unit
    ) {
        if (isDestroyed.get()) return

        val allFilesSize = linkQueue.size

        mBgScope.launch(Dispatchers.Default) main@{
            (0..MAX_THREAD_COUNT).map { i ->
                mBgScope.async(mBgDispatcher) job@{
                    while (!isDestroyed.get()) {
                        val resourceLink = linkQueue.poll()
                        if (resourceLink == null) {
                            return@job

                        } else {
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
                            } catch (e: Exception) {
                                e.printStackTrace()

                                delay(4000)
                                if (!Offline.isConnected()) processError(e)
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
            "trying to download file " + resourceLink.url + " | with path " + resourceLink.getFilePath()
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
        body?.let { responseBody ->
            BufferedInputStream(responseBody.byteStream()).use { inputStream ->
                val data = ByteArray(1024)

                var count: Int

                while (inputStream.read(data).also { count = it } != -1) {
                    if (isDestroyed.get()) return@use
                    outputStream.write(data, 0, count)
                }
            }

            responseBody.close()
        }

        outputStream.flush()
        outputStream.close()

        if (!isDestroyed.get()) {
            tempFile.renameTo(downloadFile)
        }
    }

    protected fun updateProgress(currentProgress: Int, allProgress: Int) {
        if (isDestroyed.get()) return

        mOnDownloadListener?.onProgressChanged(currentProgress, allProgress)
    }

    protected fun setAllDataDownloaded(offlineModule: OfflineModule) {
        launch(Dispatchers.Default) {
            mOnDownloadListener?.onDownloaded(offlineModule)
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
        var isNeedCheckBeforeSave: Boolean = false
    ) {
        fun getFilePath() = "file://$dirPath/$fileName"

        fun getShortFilePath() = "$dirPath/$fileName"
    }

    interface OnDownloadListener {

        fun onDownloaded(offlineModule: OfflineModule)
        fun onError(error: Throwable)
        fun onProgressChanged(currentProgress: Int, allProgress: Int)
        fun onWarning(message: String)
    }

    companion object {

        private const val TAG = "OfflineDownloader"

        private const val MAX_THREAD_COUNT = 3
    }
}