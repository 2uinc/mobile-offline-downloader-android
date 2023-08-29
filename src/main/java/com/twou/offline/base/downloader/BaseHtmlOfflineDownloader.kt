package com.twou.offline.base.downloader

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import com.twou.offline.Offline
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.nio.charset.Charset
import java.util.*

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
abstract class BaseHtmlOfflineDownloader(keyItem: KeyOfflineItem) : BaseOfflineDownloader(keyItem) {

    protected val resourceSet: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    private var mHtmlListener: HtmlListener? = null

    private var mWebView: WebView? = null

    private val mSupportedTags = setOf(
        HtmlLink.A, HtmlLink.IMG, HtmlLink.LINK, HtmlLink.SCRIPT, HtmlLink.VIDEO, HtmlLink.TRACK,
        HtmlLink.SOURCE
    )
    private val mSupportedClasses = setOf("offline-video-player")

    protected fun initWebView() {
        mWebView = createWebView()
    }

    open fun createWebView(): WebView {
        val webView = WebView(Offline.getContext())

        val webSettings: WebSettings = webView.settings
        webSettings.builtInZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        webSettings.useWideViewPort = true
        webSettings.setSupportZoom(true)
        webSettings.displayZoomControls = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowContentAccess = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccessFromFileURLs = true

        webView.setBackgroundColor(Color.TRANSPARENT)

        addWebViewClient(webView)

        return webView
    }

    protected fun addWebViewClient(webView: WebView) {
        webView.webViewClient = object : OfflineDelayWebViewClient(object : OfflineDelayListener {
            override fun onPagePrepared() {
                handler.post { webView.loadUrl(OfflineConst.PRINT_HTML) }
            }
        }) {
            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)

                url?.let { resourceSet.add(it) }
            }

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.let { resourceSet.add(it.toString()) }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.addJavascriptInterface(JavaScriptInterface(), "android")
    }

    override fun checkResourceBeforeSave(link: ResourceLink, data: String, threadId: Int): String {
        if (!link.fileName.endsWith(".css")) return data

        var css = data

        OfflineLogs.d(TAG, "Trying to find links inside CSS")

        val resourceLinks = mutableSetOf<ResourceLink>()

        val uri = Uri.parse(link.url)
        val baseUrl = uri.scheme + "://" + uri.host
        "url\\(([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?\\)".toRegex().findAll(data)
            .forEach { result ->
                if (result.groups.size > 1) {
                    result.groups[1]?.let { group ->
                        val value = group.value

                        OfflineLogs.d(TAG, "    Found link: $value")

                        val url = when {
                            value.startsWith("//") -> "https:$value"
                            value.startsWith("/") -> baseUrl + value
                            value.startsWith("..") -> "${link.url}/$value"
                            !value.startsWith("http") -> "$baseUrl/$value"
                            else -> value
                        }

                        val fileName = OfflineDownloaderUtils.getUrlFileName(url)
                        val resourceLink = ResourceLink(url, link.dirPath, fileName, value)
                        resourceLinks.add(resourceLink)
                    }
                }
            }

        run job@{
            resourceLinks.forEach { resourceLink ->
                if (isDestroyed.get()) return@job
                downloadFileToLocalStorage(resourceLink, threadId)

                css = css.replace(resourceLink.oldUrl, resourceLink.getFilePath())
            }
        }

        return css
    }

    override fun destroy() {
        mHtmlListener = null
        clear()
        super.destroy()
    }

    protected fun getWebView(l: HtmlListener? = null): WebView? {
        mHtmlListener = l
        return mWebView
    }

    protected fun createHtmlLinksFrom(
        document: Document, folderName: String = "", baseUrl: String = ""
    ): List<HtmlLink> {
        val links = mutableListOf<HtmlLink>()

        mSupportedTags.forEach { tag ->
            document.getElementsByTag(tag).forEach { element ->
                var isNeedAddLink = true
                var fileName = ""

                if (tag == HtmlLink.A) {
                    isNeedAddLink = false

                    if (!element.hasAttr("data-offline") && element.hasAttr(HtmlLink.HREF)) {
                        val url = OfflineDownloaderUtils.getUrlFileName(element.attr(HtmlLink.HREF))
                        try {
                            var extension =
                                MimeTypeMap.getFileExtensionFromUrl(Uri.parse(url).encodedPath)
                            if (extension.isEmpty()) {
                                Uri.parse(url).encodedPath?.substringAfter(".")?.let {
                                    if (it.isNotEmpty()) extension = it
                                }
                            }
                            var mimeType =
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

                            if (mimeType.isNullOrEmpty()) {
                                val fileUrl =
                                    if (element.hasAttr("title")) element.attr("title") else element.text()
                                fileName = OfflineDownloaderUtils.getUrlFileName(fileUrl)
                                extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
                                mimeType =
                                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                            }

                            if (BaseOfflineUtils.isMimeTypeSupported(mimeType)) isNeedAddLink = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (isNeedAddLink) {
                    links.addAll(
                        createHtmlLinksFrom(
                            element, fileName = fileName, folderName = folderName,
                            baseUrl = baseUrl
                        )
                    )
                }
            }
        }

        mSupportedClasses.forEach { clazz ->
            document.getElementsByClass(clazz).forEach { element ->
                links.addAll(
                    createHtmlLinksFrom(element, folderName = folderName, baseUrl = baseUrl)
                )
            }
        }

        return links
    }

    protected fun createHtmlLinkFrom(
        linkUrl: String, attr: String, fileName: String = "", folderName: String = "",
        oldLink: String = "", extension: String = "", baseUrl: String = ""
    ): HtmlLink? {
        var oldUrl = oldLink
        var url = linkUrl.trim()

        if (!url.startsWith("http") && !url.contains("/cache/")) {
            run block@{
                resourceSet.forEach { resUrl ->
                    if (resUrl.contains(url)) {
                        oldUrl = url
                        url = resUrl
                        return@block
                    }
                }
            }

            if (oldUrl.isEmpty() && baseUrl.isNotEmpty() && !url.startsWith("file://")) {
                oldUrl = url
                url = baseUrl + url
            }

            if (oldUrl.isEmpty() || !url.startsWith("http")) {
                return null
            }
        }

        var name = fileName.ifEmpty { OfflineDownloaderUtils.getUrlFileName(url) }
        if (extension.isNotEmpty() && !name.endsWith(extension)) name += extension

        val folder = if (folderName.isBlank()) "" else "$folderName/"
        val dirPath = "$filesDirPath/$folder"

        return HtmlLink(ResourceLink(url, dirPath, name, oldUrl), attr)
    }

    protected fun clear() {
        handler.post {
            if (mWebView?.parent != null) {
                mWebView?.parent?.let { parent ->
                    (parent as ViewGroup).removeView(mWebView)
                }
            }
            mWebView?.destroy()
        }
    }

    protected fun replaceLinksInDocument(
        document: Document, links: Collection<HtmlLink>
    ): Document {

        links.forEach { htmlLink ->
            val oldLink = htmlLink.resourceLink.oldUrl.ifEmpty { htmlLink.resourceLink.url }

            replaceLinkInDocument(
                document, HtmlLink.HREF, oldLink, htmlLink.resourceLink.getFilePath()
            )
            replaceLinkInDocument(
                document, HtmlLink.SRC, oldLink, htmlLink.resourceLink.getFilePath()
            )
            replaceLinkInDocument(
                document, HtmlLink.SUBTITLE_URL, oldLink, htmlLink.resourceLink.getFilePath()
            )
        }

        document.getElementsByClass("video_wrapper").forEach { wrapperElement ->
            wrapperElement.getElementsByTag("video").first()?.let { video ->
                video.attr("controls", "")
                video.attr("class", "video-js vjs-big-play-centered")

                wrapperElement.replaceWith(video)
            }
        }

        return document
    }

    protected fun saveHtmlToFile(filePath: String, html: String) {
        File(filePath).writeText(html, Charset.forName("UTF-8"))
    }

    private fun createHtmlLinksFrom(
        element: Element, fileName: String = "", folderName: String = "", baseUrl: String = ""
    ): List<HtmlLink> {
        val links = mutableListOf<HtmlLink>()

        if (element.hasAttr(HtmlLink.HREF)) {
            var extension = ""
            if (element.hasAttr("type") && element.attr("type") == "text/css") {
                extension = ".css"
            }

            createHtmlLinkFrom(
                element.attr(HtmlLink.HREF), HtmlLink.HREF, fileName = fileName,
                folderName = folderName, extension = extension, baseUrl = baseUrl
            )?.let { htmlLink ->
                if (extension.isNotEmpty()) htmlLink.resourceLink.isNeedCheckBeforeSave = true
                links.add(htmlLink)
            }
        }

        if (element.hasAttr(HtmlLink.SRC)) {
            createHtmlLinkFrom(
                element.attr(HtmlLink.SRC), HtmlLink.SRC, fileName = fileName,
                folderName = folderName, baseUrl = baseUrl
            )?.let { htmlLink ->
                links.add(htmlLink)
            }
        }

        if (element.hasAttr(HtmlLink.SUBTITLE_URL)) {
            createHtmlLinkFrom(
                element.attr(HtmlLink.SUBTITLE_URL), HtmlLink.SUBTITLE_URL, fileName = fileName,
                folderName = folderName, baseUrl = baseUrl
            )?.let { htmlLink ->
                links.add(htmlLink)
            }
        }

        if (element.hasAttr(HtmlLink.POSTER)) {
            createHtmlLinkFrom(
                element.attr(HtmlLink.POSTER), HtmlLink.POSTER, fileName = fileName,
                folderName = folderName, baseUrl = baseUrl
            )?.let { htmlLink ->
                links.add(htmlLink)
            }
        }

        return links
    }

    private fun replaceLinkInDocument(
        document: Document, attr: String, oldLink: String, newLink: String
    ): Boolean {
        var isLinkChanged = false

        document.getElementsByAttributeValue(attr, oldLink)
            .forEach { element ->
                isLinkChanged = true
                if (!element.hasAttr("data-offline")) element.attr(attr, newLink)
            }

        return isLinkChanged
    }

    data class HtmlLink(
        val resourceLink: ResourceLink, val attr: String
    ) {

        companion object {

            const val HREF = "href"
            const val SRC = "src"
            const val POSTER = "poster"
            const val SUBTITLE_URL = "subtitle_url"

            const val SCRIPT = "script"
            const val LINK = "link"
            const val VIDEO = "video"
            const val OBJECT = "object"
            const val IMG = "img"
            const val HEAD = "head"
            const val A = "a"
            const val TRACK = "track"
            const val SOURCE = "source"
        }
    }

    interface HtmlListener {
        fun onHtmlLoaded(html: String)
    }

    private inner class JavaScriptInterface {

        @Suppress("UNUSED_PARAMETER", "unused")
        @JavascriptInterface
        fun print(data: String, url: String) {
            mHtmlListener?.onHtmlLoaded(data)
        }
    }

    companion object {

        private const val TAG = "HtmlOfflineDownloader"
    }
}