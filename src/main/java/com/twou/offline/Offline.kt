package com.twou.offline

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.twou.offline.base.BaseOfflineDownloaderCreator
import com.twou.offline.base.repository.BaseOfflineRepository
import com.twou.offline.base.repository.BaseOfflineUnsupportedRepository
import com.twou.offline.data.IOfflineLoggerInterceptor
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.data.IOfflineRepository
import com.twou.offline.data.IOfflineUnsupportedRepository
import com.twou.offline.item.OfflineQueueItem
import com.twou.offline.util.BaseOfflineUtils
import com.twou.offline.util.OfflineConst
import io.paperdb.Paper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Offline private constructor(
    private val mContext: Context, private val mClient: OkHttpClient,
    private val mBaseUrl: String, private val mOfflineRepository: IOfflineRepository,
    private val mOfflineUnsupportedRepository: IOfflineUnsupportedRepository,
    private val mOfflineLoggerInterceptor: IOfflineLoggerInterceptor?,
    private val mHtmlErrorOverlay: String, private val mHtmlErrorScript: String,
    private val mHtmlErrorCSS: String
) {

    internal var isConnected = false

    private val mNetworkChangedSet = mutableSetOf<IOfflineNetworkChangedListener>()
    private val mNetworkHandler = Handler(Looper.getMainLooper())
    private val mCheckHandler = Handler(Looper.getMainLooper())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            updateCurrentConnectivityState()
        }

        override fun onLost(network: Network) {
            super.onLost(network)

            updateCurrentConnectivityState()
        }
    }

    init {
        isConnected = BaseOfflineUtils.isOnline(mContext)

        if (!isConnected) startConnectionCheck()

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val connectivityManager =
            mContext.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun addNetworkListener(l: IOfflineNetworkChangedListener) {
        mNetworkChangedSet.add(l)

        l.onChanged(isConnected)
    }

    fun removeNetworkListener(l: IOfflineNetworkChangedListener) {
        mNetworkChangedSet.remove(l)
    }

    private fun updateCurrentConnectivityState() {
        mNetworkHandler.removeCallbacksAndMessages(null)
        mNetworkHandler.postDelayed(
            { notifyNetworkChanged(BaseOfflineUtils.isOnline(mContext)) }, 2000
        )
    }

    private fun notifyNetworkChanged(isOnline: Boolean) {
        if (isOnline != isConnected) {
            isConnected = isOnline

            mNetworkChangedSet.forEach { it.onChanged(isConnected) }

            if (!isConnected) startConnectionCheck()
        }
    }

    private fun startConnectionCheck() {
        mCheckHandler.postDelayed({
            try {
                val isOnline = BaseOfflineUtils.isOnline(mContext)
                if (isOnline) {
                    notifyNetworkChanged(true)

                } else {
                    startConnectionCheck()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 5000)
    }

    class Builder {

        private var mClient: OkHttpClient? = null
        private var mBaseUrl = ""
        private var mOfflineRepository: IOfflineRepository? = null
        private var mOfflineUnsupportedRepository: IOfflineUnsupportedRepository? = null
        private var mOfflineLoggerInterceptor: IOfflineLoggerInterceptor? = null
        private var mHtmlErrorOverlay: String? = null
        private var mHtmlErrorScript: String? = null
        private var mHtmlErrorCSS: String? = null

        fun setClient(client: OkHttpClient): Builder {
            mClient = client
            return this
        }

        fun setBaseUrl(baseUrl: String): Builder {
            mBaseUrl = baseUrl
            return this
        }

        fun setOfflineRepository(repository: IOfflineRepository): Builder {
            mOfflineRepository = repository
            return this
        }

        fun setOfflineUnsupportedRepository(repository: IOfflineUnsupportedRepository): Builder {
            mOfflineUnsupportedRepository = repository
            return this
        }

        fun setOfflineLoggerInterceptor(interceptor: IOfflineLoggerInterceptor): Builder {
            mOfflineLoggerInterceptor = interceptor
            return this
        }

        fun setHtmlErrorOverlay(html: String): Builder {
            mHtmlErrorOverlay = html
            return this
        }

        fun setHtmlErrorScript(script: String): Builder {
            mHtmlErrorScript = script
            return this
        }

        fun setHtmlErrorCSS(css: String): Builder {
            mHtmlErrorCSS = css
            return this
        }

        internal fun build(context: Context): Offline {
            val client = if (mClient == null) {
                OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build()
            } else mClient

            val offlineRepository =
                if (mOfflineRepository == null) BaseOfflineRepository() else mOfflineRepository
            val unsupportedRepository =
                if (mOfflineUnsupportedRepository == null) BaseOfflineUnsupportedRepository() else
                    mOfflineUnsupportedRepository

            val message = context.getString(R.string.offline_download_content_not_available)

            return Offline(
                context, client!!, mBaseUrl, offlineRepository!!, unsupportedRepository!!,
                mOfflineLoggerInterceptor,
                mHtmlErrorOverlay ?: OfflineConst.HTML_ERROR_OVERLAY.replace("#MESSAGE#", message),
                mHtmlErrorScript ?: OfflineConst.HTML_ERROR_SCRIPT,
                mHtmlErrorCSS ?: OfflineConst.HTML_ERROR_CSS
            )
        }
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var instance: Offline? = null

        private var mOfflineManager: OfflineManager? = null

        private var mCreatorUnit:
                ((queueItem: OfflineQueueItem) -> BaseOfflineDownloaderCreator)? = null

        private var mOfflineModeIgnoreUrls: Set<String>? = null

        fun init(
            context: Context, builder: Builder? = null,
            creatorUnit: (queueItem: OfflineQueueItem) -> BaseOfflineDownloaderCreator
        ) {
            instance = builder?.build(context.applicationContext)
                ?: Builder().build(context.applicationContext)
            mCreatorUnit = creatorUnit
            mOfflineManager = OfflineManager()
            Paper.init(context)
        }

        fun getOfflineRepository(): IOfflineRepository {
            checkInstance()
            return instance?.mOfflineRepository!!
        }

        fun getOfflineUnsupportedRepository(): IOfflineUnsupportedRepository {
            checkInstance()
            return instance?.mOfflineUnsupportedRepository!!
        }

        fun getOfflineLoggerInterceptor(): IOfflineLoggerInterceptor? {
            checkInstance()
            return instance?.mOfflineLoggerInterceptor
        }

        fun getOfflineManager(): OfflineManager {
            checkInstance()
            return mOfflineManager!!
        }

        fun getCreatorUnit(): (queueItem: OfflineQueueItem) -> BaseOfflineDownloaderCreator {
            checkInstance()
            return mCreatorUnit!!
        }

        fun setOfflineModeIgnoreUrls(urls: Set<String>?) {
            mOfflineModeIgnoreUrls = urls
        }

        fun isNeedIgnoreUrl(url: String): Boolean {
            checkInstance()
            mOfflineModeIgnoreUrls?.forEach {
                if (url.contains(it)) return true
            }

            return false
        }

        fun getHtmlErrorOverlay(): String {
            checkInstance()
            return instance?.mHtmlErrorOverlay ?: ""
        }

        fun getHtmlErrorScript(): String {
            checkInstance()
            return instance?.mHtmlErrorScript ?: ""
        }

        fun getHtmlErrorCSS(): String {
            checkInstance()
            return instance?.mHtmlErrorCSS ?: ""
        }

        internal fun getClient(): OkHttpClient {
            checkInstance()
            return instance?.mClient!!
        }

        internal fun getBaseUrl(): String {
            checkInstance()
            return instance?.mBaseUrl ?: ""
        }

        internal fun getContext(): Context {
            checkInstance()
            return instance?.mContext!!
        }

        internal fun addNetworkListener(l: IOfflineNetworkChangedListener) {
            checkInstance()
            instance?.addNetworkListener(l)
        }

        internal fun removeNetworkListener(l: IOfflineNetworkChangedListener) {
            checkInstance()
            instance?.removeNetworkListener(l)
        }

        internal fun isConnected(): Boolean {
            checkInstance()
            return instance?.isConnected!!
        }

        private fun checkInstance() {
            if (instance == null || mCreatorUnit == null)
                throw IllegalStateException("You need to call init() first")
        }
    }
}