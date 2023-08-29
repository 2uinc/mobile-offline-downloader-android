package com.twou.offline.base.downloader

import com.google.gson.Gson
import com.twou.offline.item.BaseOfflineItem
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineModule
import com.twou.offline.util.OfflineDownloaderUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentLinkedDeque

abstract class BaseHtmlOnePageDownloader(private val mKeyItem: KeyOfflineItem) :
    BaseVideoHtmlOfflineDownloader(mKeyItem) {

    private val mLinkFilesMap = mutableMapOf<String, HtmlLink>()

    protected fun finishPreparation(document: Document) {
        clear()

        if (isDestroyed.get()) return

        val internalResourceSet = resourceSet.toSet()
        launch(Dispatchers.Default) {
            document.getElementsByTag(HtmlLink.SCRIPT)
                ?.forEach { element ->
                    val src = element.attr(HtmlLink.SRC)
                    if (src.contains("main-es") || src.contains("/mathjax/")) {
                        element.remove()
                    }
                }

            internalResourceSet.forEach { url ->
                createHtmlLinkFrom(url, "")?.let { addLink(it) }
            }

            createHtmlLinksFrom(document).forEach { addLink(it) }

            val resourceLinkQueue = ConcurrentLinkedDeque<ResourceLink>()
            mLinkFilesMap.values.forEach { resourceLinkQueue.add(it.resourceLink) }

            downloadAllResources(resourceLinkQueue,
                { currentProgress, allProgress ->
                    updateProgress(currentProgress, allProgress)

                }, {
                    val updatedDocument = replaceLinksInDocument(document, mLinkFilesMap.values)

                    saveHtmlToFile(
                        OfflineDownloaderUtils.getStartPagePath(mKeyItem.key),
                        OfflineDownloaderUtils.clearHtml(updatedDocument.html())
                    )

                    val value = Gson().toJson(BaseOfflineItem(mKeyItem.key))
                    setAllDataDownloaded(OfflineModule(mKeyItem.key, value))
                })
        }
    }

    private fun addLink(htmlLink: HtmlLink) {
        if (!mLinkFilesMap[htmlLink.resourceLink.url]?.attr.isNullOrEmpty()) return

        mLinkFilesMap[htmlLink.resourceLink.url] = htmlLink
    }
}