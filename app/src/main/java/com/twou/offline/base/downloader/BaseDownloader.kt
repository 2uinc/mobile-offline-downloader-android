package com.twou.offline.base.downloader

import android.net.Uri
import android.webkit.CookieManager
import com.twou.offline.Offline
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

open class BaseDownloader {

    protected var isDestroyed = AtomicBoolean(false)

    private var mCurrentCall: Call? = null

    fun downloadFileContent(url: String, headers: Map<String, String>? = null): String {
        val client = Offline.getClient()

        val requestBuilder = Request.Builder().url(url)
        try {
            val uri = Uri.parse(url)
            val cookies = CookieManager.getInstance().getCookie(uri.scheme + "://" + uri.host)
            requestBuilder.addHeader("Cookie", cookies ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        headers?.entries?.forEach { requestBuilder.addHeader(it.key, it.value) }

        val call = client.newCall(requestBuilder.build())
        mCurrentCall = call
        val response = call.execute()
        return response.body?.string() ?: ""
    }

    fun readFileContent(path: String): String {
        return File(path).readText(Charset.forName("UTF-8"))
    }

    open fun destroy() {
        isDestroyed.set(true)
        try {
            mCurrentCall?.cancel()
        } catch (ignore: Exception) {
        }
    }
}