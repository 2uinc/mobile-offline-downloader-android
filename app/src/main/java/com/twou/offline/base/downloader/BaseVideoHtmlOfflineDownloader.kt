package com.twou.offline.base.downloader

import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.BaseOfflineUtils
import com.twou.offline.util.OfflineHtmlVideoChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class BaseVideoHtmlOfflineDownloader(keyItem: KeyOfflineItem) :
    BaseHtmlOfflineDownloader(keyItem) {

    protected fun getAllVideosAndDownload(
        document: Document, l: OfflineHtmlVideoChecker.OnVideoProcessListener,
        contentUrl: String = ""
    ) {
        OfflineHtmlVideoChecker().apply {
            setResourceSet(resourceSet)
            setup(document, object : OfflineHtmlVideoChecker.OnVideoProcessListener() {
                override fun onVideoLinksReplaced() {
                    if (isDestroyed.get()) return

                    processVideoLinks()
                    l.onVideoLinksReplaced()
                }

                override fun onVideoLoaded(videoLinks: List<OfflineHtmlVideoChecker.VideoLink>) {
                    if (isDestroyed.get()) return

                    replaceUnusedIframesWithPlaceholder(document, videoLinks, l, contentUrl)
                }

                override fun onError(e: Exception) {
                    l.onError(e)
                }

                override fun onWarning(message: String) {
                    l.onWarning(message)
                }
            })
        }
    }

    private fun replaceUnusedIframesWithPlaceholder(
        document: Document, videoLinks: List<OfflineHtmlVideoChecker.VideoLink>,
        l: OfflineHtmlVideoChecker.OnVideoProcessListener, contentUrl: String
    ) {
        launch {
            var isNeedAddScripts = false

            document.getElementsByTag("script").forEach { element ->
                element.attr("src")?.let {
                    if (it.startsWith("//")) {
                        element.attr("src", "https:$it")
                    }
                }
            }

            val classes = listOf("pb_feed", "exco", "playbuzz", "campus-document-overlay-container")
            classes.forEach {
                document.getElementsByClass(it)?.forEach job@{ element ->
                    if (it == "campus-document-overlay-container" &&
                        element.hasAttr("is_downloadable") &&
                        element.attr("is_downloadable") == "true"
                    ) {
                        return@job
                    }

                    isNeedAddScripts = true
                    element.replaceWith(Jsoup.parse(BaseOfflineUtils.getHtmlErrorOverlay(element)))
                }
            }

            val logText = mutableListOf<String>()
            document.getElementsByTag("iframe")?.forEach { element ->
                isNeedAddScripts = true
                val replaceElement =
                    BaseOfflineUtils.getParentElement("fluid-width-video-wrapper", element)
                        ?: element
                replaceElement.replaceWith(Jsoup.parse(BaseOfflineUtils.getHtmlErrorOverlay(element)))
                val srcUrl = element.attr("src") ?: ""
                logText.add("• video content in iframe: $srcUrl")
            }
            logText.takeIf { it.isNotEmpty() }?.let {
                l.onWarning("\n▧ Downloading Errors:\n${it.joinToString("\n")}")
            }

            if (isNeedAddScripts) {
                document.head().appendElement("style").html(BaseOfflineUtils.getHtmlErrorCss())
                document.head().appendElement("script")
                    .append(BaseOfflineUtils.getHtmlErrorScript())
            }

            launch(Dispatchers.Main) {
                l.onVideoLoaded(videoLinks)
            }
        }
    }
}