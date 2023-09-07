package com.twou.offline.util

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.twou.offline.Offline

open class OfflineDelayWebViewClient(
    private val mOnDelayListener: OfflineDelayListener, private val isNeedDelay: Boolean = true,
    private val finishDelayTime: Long = 15000, private val loadResourceDelayTime: Long = 15000
) : WebViewClient() {

    private var isClientWorking = true
    private var mCurrentUrl = ""

    private val mHandler = Handler(Looper.getMainLooper())
    private val mRunnable = Runnable {
        if (!isClientWorking) return@Runnable
        isClientWorking = false

        mOnDelayListener.onPagePrepared()
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        mCurrentUrl = url ?: ""

        isClientWorking = true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        if (!isClientWorking) return

        mHandler.removeCallbacksAndMessages(null)
        mHandler.removeCallbacks(mRunnable)
        mHandler.postDelayed(mRunnable, if (isNeedDelay) finishDelayTime else 0)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)

        if (Offline.isNeedIgnoreUrl(url ?: "")) return

        resetDelay()
    }

    override fun shouldInterceptRequest(
        view: WebView?, request: WebResourceRequest?
    ): WebResourceResponse? {

        resetDelay()

        return super.shouldInterceptRequest(view, request)
    }

    private fun resetDelay() {
        if (isNeedDelay) {
            if (!isClientWorking) return

            mHandler.removeCallbacksAndMessages(null)
            mHandler.removeCallbacks(mRunnable)
            mHandler.postDelayed(mRunnable, loadResourceDelayTime)
        }
    }

    interface OfflineDelayListener {

        fun onPagePrepared()
    }
}