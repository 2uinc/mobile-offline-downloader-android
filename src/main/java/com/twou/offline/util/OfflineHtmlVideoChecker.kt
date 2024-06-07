package com.twou.offline.util

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.twou.offline.Offline
import com.twou.offline.base.downloader.BaseDownloader
import com.twou.offline.base.downloader.BaseHtmlOfflineDownloader
import com.twou.offline.error.OfflineNoSpaceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.nio.charset.Charset

class OfflineHtmlVideoChecker : CoroutineScope {

    private val mHandler = Handler(Looper.getMainLooper())
    private val mBaseDownloader = BaseDownloader()
    private val mVideoLinks = mutableListOf<VideoLink>()
    private var mCurrentLinkPosition = 0
    private var mOnVideoProcessListener: OnVideoProcessListener? = null
    private var mDocument: Document? = null
    private var mResourceSet: Set<String>? = null

    override val coroutineContext = Dispatchers.IO

    fun setResourceSet(resourceSet: Set<String>) {
        mResourceSet = resourceSet
    }

    fun setup(document: Document, l: OnVideoProcessListener) {
        mDocument = document
        mOnVideoProcessListener = l

        document.getElementsByTag("iframe")?.forEach { element ->
            getVideoType(element)?.let { type ->
                OfflineLogs.d(TAG, "found video iFrame with type: $type")

                mVideoLinks.add(VideoLink(type, element))
            }
        }

        document.getElementsByTag("script")?.forEach { element ->
            if (getVideoType(element) == VideoType.WISTIA) {
                val link = element.attr("src")
                if (link.endsWith(".jsonp")) {
                    if (mVideoLinks.find { link.contains(it.element.attr("src")) } != null) {
                        return@forEach
                    }

                    Uri.parse(link).lastPathSegment?.substringBefore(".jsonp")?.let { id ->
                        document.getElementsByTag("span").forEach { spanElement ->
                            if (spanElement.attr("class").contains(id)) {
                                spanElement.attr("src", link.substringBefore(".jsonp"))
                                mVideoLinks.add(VideoLink(VideoType.WISTIA, spanElement))
                                return@let
                            }
                        }
                    }

                } else if (link.contains("e-v1.js", ignoreCase = true)) {
                    element.remove()
                }
            }
        }

        document.getElementsByClass("wistia_embed").forEach { element ->
            mVideoLinks.add(VideoLink(VideoType.WISTIA, element))
        }

        replaceAllFoundedVideosWithPreviewDiv()
    }

    private fun replaceAllFoundedVideosWithPreviewDiv() {
        launch {
            var isNeedAddOfflineVideoScript = false

            var videoPosition = mVideoLinks.size

            mDocument?.getElementsByTag(BaseHtmlOfflineDownloader.HtmlLink.VIDEO)
                ?.forEach { element ->
                    val src = if (element.hasAttr(BaseHtmlOfflineDownloader.HtmlLink.SRC)) {
                        element.attr(BaseHtmlOfflineDownloader.HtmlLink.SRC)
                    } else {
                        val sourceElement = element.getElementsByTag("source")?.first()
                        if (sourceElement?.hasAttr(BaseHtmlOfflineDownloader.HtmlLink.SRC) == true) {
                            sourceElement.attr(BaseHtmlOfflineDownloader.HtmlLink.SRC)

                        } else {
                            ""
                        }
                    }
                    if (src.isNotBlank() && !src.contains("youtube") && !src.startsWith("blob:")) {
                        var subtitleUrl = ""
                        val fileName =
                            src.substring(src.lastIndexOf("/") + 1).substringBeforeLast(".")

                        run job@{
                            mResourceSet?.forEach {
                                if (it.contains(".vtt") && it.contains(fileName)) {
                                    subtitleUrl = it
                                    return@job
                                }
                            }
                        }

                        val videoDiv = getVideoHtml("$videoPosition", src, subtitleUrl)
                        var workingElement: Element? = null
                        if (element.hasParent() && element.parent()
                                .hasClass("fluid-width-video-wrapper")
                        ) {
                            workingElement = element.parent()

                        } else if (element.hasParent() && element.parent()
                                .hasAttr("data-vjs-player")
                        ) {
                            if (element.parent().hasParent()) {
                                workingElement = element.parent()

                                run job@{
                                    mDocument?.getElementsByTag(BaseHtmlOfflineDownloader.HtmlLink.SCRIPT)
                                        ?.forEach { scriptElement ->
                                            if (scriptElement.hasAttr(BaseHtmlOfflineDownloader.HtmlLink.SRC) &&
                                                scriptElement.attr(BaseHtmlOfflineDownloader.HtmlLink.SRC)
                                                    .contains("main.")
                                            ) {
                                                scriptElement.remove()
                                                return@job
                                            }
                                        }
                                }

                                element.parent().parent().children().forEach { child ->
                                    if (child.hasAttr("class")) {
                                        if (child.attr("class")
                                                .contains("styles__TranscriptHeader")
                                        ) {
                                            child.remove()

                                        } else if (child.attr("class")
                                                .contains("styles__TranscriptBody")
                                        ) {
                                            child.attr("style", "")
                                            child.getElementsByClass("p3sdk-interactive-transcript-control-bar")
                                                ?.firstOrNull()
                                                ?.attr("style", "display: none;")

                                            child.getElementsByClass("p3sdk-interactive-transcript-content")
                                                ?.firstOrNull()
                                                ?.attr("style", "padding-top: 30px;")

                                            child.getElementsByClass("p3sdk-interactive-transcript-bottom-bar")
                                                ?.firstOrNull()?.remove()
                                        }
                                    }
                                }
                            }

                        } else if (element.hasParent() && element.parent().hasAttr("aria-label")
                            && element.parent().attr("aria-label") == "Video Player"
                        ) {
                            mDocument?.getElementsByClass("oyster-wrapper")?.firstOrNull()?.let {
                                workingElement = it
                                mDocument?.getElementsByClass("oyster-grid-transcript")
                                    ?.firstOrNull()?.let { element ->
                                        element.getElementsByClass("transcripts-container")
                                            ?.firstOrNull()?.removeClass("transcripts-container")
                                        element.getElementById("copy-to-clipboard")?.remove()
                                        mDocument?.getElementById("oyster")?.appendChild(element)
                                        element.attr("style", "padding: 20px;")
                                    }
                            }

                            if (workingElement == null) workingElement = element

                        } else {
                            run job@{
                                element.parents().forEach { parent ->
                                    if (parent.hasClass("video_wrapper") ||
                                        parent.hasClass("wistia_responsive_padding")
                                    ) {
                                        workingElement = parent
                                        return@job
                                    }
                                }
                            }

                            if (workingElement == null) workingElement = element
                        }

                        workingElement?.replaceWith(Jsoup.parse(videoDiv))

                        isNeedAddOfflineVideoScript = true
                        videoPosition++
                    }
                }

            if (mVideoLinks.isNotEmpty() || isNeedAddOfflineVideoScript) {
                mDocument?.head()?.append(OfflineConst.OFFLINE_VIDEO_SCRIPT)
                mDocument?.head()?.append(OfflineConst.PROGRESS_CSS)
            }

            mVideoLinks.forEachIndexed { index, videoLink ->
                val element = videoLink.element
                val videoDiv = getPreviewVideoHtml(index)
                var workingElement: Element? = null
                if (element.hasParent() && element.parent()
                        .hasClass("fluid-width-video-wrapper")
                ) {
                    workingElement = element.parent()

                } else {
                    run job@{
                        element.parents().forEach { parent ->
                            if ((parent.hasClass("video_wrapper") ||
                                        parent.hasClass("wistia_responsive_padding")) &&
                                parent.hasParent()
                            ) {
                                workingElement = parent
                                return@job
                            }
                        }
                    }
                }

                if (workingElement == null) workingElement = element

                videoLink.previewElement = Jsoup.parse(videoDiv)
                workingElement?.replaceWith(videoLink.previewElement)
            }

            launch(Dispatchers.Main) {
                mOnVideoProcessListener?.onVideoLinksReplaced()
            }
        }
    }

    fun processVideoLinks() {
        if (mDocument == null) throw IllegalStateException("Document should not be NULL")

        if (mCurrentLinkPosition >= mVideoLinks.size) {
            mCurrentLinkPosition = 0
            mDocument = null

            mOnVideoProcessListener?.onVideoLoaded(mVideoLinks)
            return
        }

        launch {
            val videoLink = getCurrentVideoLink()

            try {
                when (videoLink.type) {
                    VideoType.HAP_YAK -> processHapYakHtml(videoLink)
                    VideoType.FROST -> processFrostVideo(videoLink)
                    VideoType.WISTIA -> processWistiaVideo(videoLink)
                    VideoType.VIMEO -> processVimeoVideo(videoLink)
                    VideoType.OBJECTS_IFRAME -> processObjectIframeVideo(videoLink)
                }

            } catch (e: Exception) {
                e.printStackTrace()

                delay(4000)
                if (!Offline.isConnected()) {
                    mOnVideoProcessListener?.onError(
                        IllegalStateException("Internet connection must be turned on")
                    )

                } else if (BaseOfflineUtils.isThereNoFreeSpace(Offline.getContext())) {
                    mOnVideoProcessListener?.onError(OfflineNoSpaceException())

                } else if (videoLink.attemptCount < MAX_ATTEMPT_COUNT) {
                    videoLink.attemptCount++

                } else {
                    videoLink.previewElement?.replaceWith(videoLink.element)
                    videoLink.error = e

                    mCurrentLinkPosition++
                }

                mHandler.post { processVideoLinks() }
            }
        }
    }

    private fun getVideoType(element: Element): VideoType? {
        val link = element.attr("src")

        if (link.contains("www.hapyak.com/embed")) return VideoType.HAP_YAK

        if (link.contains("/frost_widgets/videolist/")) {
            val dataPath = element.attr("data-path")
            if (dataPath.isNotBlank()) return VideoType.FROST
        }

        if (link.contains("wistia.")) return VideoType.WISTIA

        if (link.contains("player.vimeo.com/video")) return VideoType.VIMEO

        if (link.contains("media_objects_iframe")) return VideoType.OBJECTS_IFRAME
        return null
    }

    private fun getCurrentVideoLink() = mVideoLinks[mCurrentLinkPosition]

    private fun processHapYakHtml(videoLink: VideoLink) {
        var link = videoLink.element.attr("src")
        if (link.startsWith("//")) link = "https:$link"
        val html = mBaseDownloader.downloadFileContent(link)

        var firstSub = html.substringAfter("\"source\": \"")
        val source = firstSub.substringBefore("\"")
        firstSub = html.substringAfter("\"source_id\": \"")
        val sourceId = firstSub.substringBefore("\"")

        OfflineLogs.d(TAG, "    HapYak: found source with id: $sourceId and with type: $source")

        if (source == "vimeo") {
            val manifestUrl = "https://player.vimeo.com/video/$sourceId"
            OfflineLogs.d(TAG, "    HapYak: This is Vimeo, not HapYak. Url is : $manifestUrl")
            processVimeoVideo(videoLink, manifestUrl)

        } else {
            val manifestUrl = "https://fast.wistia.net/embed/medias/$sourceId.json"
            val data = mBaseDownloader.downloadFileContent(manifestUrl)

            val wistiaItem = Gson().fromJson(data, WistiaItem::class.java)
            wistiaItem.media.assets.filter { it.codec != null }
                .minByOrNull { it.size }?.url?.let { url ->
                    OfflineLogs.d(TAG, "    HapYak: video url is: $url")

                    val subtitleUrl = getWistiaSubtitleUrl(wistiaItem.media.hashedId)
                    replaceVideoElement(url, subtitleUrl)
                }

            mHandler.post {
                mCurrentLinkPosition++
                processVideoLinks()
            }
        }
    }

    private fun processFrostVideo(videoLink: VideoLink) {
        val dataPath = videoLink.element.attr("data-path")

        OfflineLogs.d(TAG, "    Frost: data-path is: $dataPath")

        val content =
            mBaseDownloader.downloadFileContent(dataPath).replaceFirst("var widget_data = ", "")
        val widgetItem = Gson().fromJson(content, VideoWidgetItem::class.java)

        val videos = StringBuilder()
        var position = 0

        widgetItem.widgetData.options.forEach { option ->
            val videoUrl = option.imgDetails.mediaPath

            OfflineLogs.d(TAG, "    Frost: video url is: $videoUrl")

            val videoDiv = getVideoHtml("${mCurrentLinkPosition}_${position}", videoUrl)
            videos.append(videoDiv)

            position++
        }

        getCurrentVideoLink().previewElement?.replaceWith(Jsoup.parse(videos.toString()))
        getCurrentVideoLink().videoHtml = videos.toString()

        mHandler.post {
            mCurrentLinkPosition++
            processVideoLinks()
        }
    }

    private fun processWistiaVideo(videoLink: VideoLink) {
        var link = videoLink.element.attr("src")

        OfflineLogs.d(TAG, "    Wistia: loading iFrame: $link")

        if (link.startsWith("//")) link = "https:$link"
        val html = mBaseDownloader.downloadFileContent(link)

        var firstSub = html.substringAfter("iframeInit(")
        var data = firstSub.substringBefore(", {});")

        val wistiaMediaItem = try {
            Gson().fromJson(data, WistiaMediaItem::class.java)
        } catch (e: Exception) {
            e.printStackTrace()

            firstSub = html.substringAfter("W.embed(")
            data = firstSub.substringBefore(", embedOptions);")

            Gson().fromJson(data, WistiaMediaItem::class.java)
        }

        if (wistiaMediaItem.type.startsWith("audio", true)) {
            wistiaMediaItem.assets.filter { it.type.contains("audio", true) }
                .minByOrNull { it.size }?.url?.let { url ->
                    OfflineLogs.d(TAG, "    Wistia: audio url is: $url")

                    replaceVideoElement(url)
                }

        } else {
            wistiaMediaItem.assets.filter { it.codec != null }
                .minByOrNull { it.size }?.url?.let { url ->
                    OfflineLogs.d(TAG, "    Wistia: video url is: $url")

                    val subtitleUrl = getWistiaSubtitleUrl(wistiaMediaItem.hashedId)
                    replaceVideoElement(url, subtitleUrl)
                }
        }

        mHandler.post {
            mCurrentLinkPosition++
            processVideoLinks()
        }
    }

    private fun getWistiaSubtitleUrl(wistiaId: String): String {
        try {
            val subtitleWistiaUrl =
                "https://fast.wistia.net/embed/captions/${wistiaId}.json"
            val subtitleContent =
                mBaseDownloader.downloadFileContent(subtitleWistiaUrl)

            val subtitleItem =
                Gson().fromJson(subtitleContent, WistiaSubtitleItem::class.java)

            val lines = subtitleItem.captions.firstOrNull()?.hash?.lines
            if (lines.isNullOrEmpty()) return ""

            val subtitleVttBuilder = StringBuilder("WEBVTT\n\n")
            lines.forEach { line ->
                subtitleVttBuilder.append(if (line.start < 10) "0${line.start}" else "${line.start}")
                subtitleVttBuilder.append(" --> ")

                subtitleVttBuilder.append(if (line.end < 10) "0${line.end}" else "${line.end}")
                subtitleVttBuilder.appendLine()

                subtitleVttBuilder.append(line.text.joinToString("\n"))
                subtitleVttBuilder.appendLine()
                subtitleVttBuilder.appendLine()
            }

            val subtitleDir = File(OfflineDownloaderUtils.getDirPath("cache"))
            if (!subtitleDir.exists()) subtitleDir.mkdirs()
            val subtitleFile =
                File(subtitleDir, "/wistia_sub_${wistiaId}.vtt")
            subtitleFile.writeText(
                subtitleVttBuilder.toString(), Charset.forName("UTF-8")
            )
            return subtitleFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    private fun processVimeoVideo(videoLink: VideoLink, src: String? = null) {
        var link = src ?: videoLink.element.attr("src")

        OfflineLogs.d(TAG, "    Vimeo: loading iFrame: $link")

        if (link.startsWith("//")) link = "https:$link"
        val html = mBaseDownloader.downloadFileContent(
            link, mapOf("Referer" to Offline.getBaseUrl())
        )

        val firstSub = if (html.contains("var config = {"))
            html.substringAfter("var config = {") else
            html.substringAfter("playerConfig = {")
        val data = "{" + firstSub.substringBefore("};") + "}"

        if (data.contains("PrivacyError")) {
            OfflineLogs.d(TAG, "    Vimeo: Privacy Error")
            replacePrivacyErrorElement()

        } else {
            val vimeoItem = try {
                Gson().fromJson(data, VimeoItem::class.java)
            } catch (e: Exception) {
                e.printStackTrace()

                Gson().fromJson(
                    data.substringBefore("</script>").substringBeforeLast("}}") + "}}",
                    VimeoItem::class.java
                )
            }

            vimeoItem?.let {
                vimeoItem.request.files.progressive.minByOrNull {
                    minOf(it.height, it.width)
                }?.url?.let { url ->
                    OfflineLogs.d(TAG, "    Vimeo: video url is: $url")

                    var subtitleUrl = vimeoItem.request.textTracks?.getOrNull(0)?.url ?: ""
                    if (subtitleUrl.isNotBlank() && subtitleUrl.startsWith("/")) {
                        subtitleUrl = vimeoItem.playerUrl + subtitleUrl
                        if (!subtitleUrl.startsWith("http")) {
                            subtitleUrl = "https://$subtitleUrl"
                        }
                    }

                    replaceVideoElement(url, subtitleUrl)
                }
            }
        }

        mHandler.post {
            mCurrentLinkPosition++
            processVideoLinks()
        }
    }

    private fun processObjectIframeVideo(videoLink: VideoLink) {
        val link = videoLink.element.attr("src")

        val html = mBaseDownloader.downloadFileContent(
            link, mapOf("Referer" to Offline.getBaseUrl())
        )

        val firstSub = html.substringAfter("ENV = {")
        val data = "{" + firstSub.substringBefore("};") + "}"

        val iframeItem = Gson().fromJson(data, ObjectsIframeItem::class.java)
        if (iframeItem.mediaObject.mediaType.contains("video")) {
            iframeItem.mediaObject.mediaSources.minByOrNull { it.size.toLong() }?.url?.let { url ->
                OfflineLogs.d(TAG, "    Object Iframe: video url is: $url")

                replaceVideoElement(url)
            }
        }

        mHandler.post {
            mCurrentLinkPosition++
            processVideoLinks()
        }
    }

    private fun replaceVideoElement(url: String, subtitleUrl: String = "") {
        val videoDiv = getVideoHtml("$mCurrentLinkPosition", url, subtitleUrl)
        val videoElement = Jsoup.parse(videoDiv)

        getCurrentVideoLink().previewElement?.replaceWith(videoElement)
        getCurrentVideoLink().videoHtml = videoDiv
    }

    private fun getPreviewVideoHtml(id: Int): String {
        return """
            <div class="offline-video-player-preview" id="preview_video_$id">
                <div style="height: 100px;">
                    <div class="offline-video-preview-loader">
                        <svg class="offline-video-preview-circular">
                            <circle class="offline-video-preview-path" cx="50" cy="50" r="20" fill="none" stroke-width="5" stroke-miterlimit="10"></circle>
                        </svg>
                    </div>
                </div>
            </div> 
        """.trimIndent()
    }

    private fun getVideoHtml(id: String, url: String, subtitleUrl: String = ""): String {
        return """
            <div class='offline-video-player' id='video_$id' src='$url' subtitle_url='$subtitleUrl'></div>
        """.trimIndent()
    }

    private fun replacePrivacyErrorElement() {
        val errorDiv =
            "<div style='height: 100px;'>" +
                    "<div class='offline-video-privacy-error'>" +
                    "<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' version='1.1' width='50' height='50' viewBox='0 0 256 256' xml:space='preserve'>" +
                    "<defs>" +
                    "</defs>" +
                    "<g style='stroke: none; stroke-width: 0; stroke-dasharray: none; stroke-linecap: butt; stroke-linejoin: miter; stroke-miterlimit: 10; fill: #d62d20; fill-rule: nonzero; opacity: 1;' transform='translate(1.4065934065934016 1.4065934065934016) scale(2.81 2.81)'>" +
                    "<path d='M 85.429 85.078 H 4.571 c -1.832 0 -3.471 -0.947 -4.387 -2.533 c -0.916 -1.586 -0.916 -3.479 0 -5.065 L 40.613 7.455 C 41.529 5.869 43.169 4.922 45 4.922 c 0 0 0 0 0 0 c 1.832 0 3.471 0.947 4.386 2.533 l 40.429 70.025 c 0.916 1.586 0.916 3.479 0.001 5.065 C 88.901 84.131 87.261 85.078 85.429 85.078 z M 45 7.922 c -0.747 0 -1.416 0.386 -1.79 1.033 L 2.782 78.979 c -0.373 0.646 -0.373 1.419 0 2.065 c 0.374 0.647 1.042 1.033 1.789 1.033 h 80.858 c 0.747 0 1.416 -0.387 1.789 -1.033 s 0.373 -1.419 0 -2.065 L 46.789 8.955 C 46.416 8.308 45.747 7.922 45 7.922 L 45 7.922 z M 45 75.325 c -4.105 0 -7.446 -3.34 -7.446 -7.445 s 3.34 -7.445 7.446 -7.445 s 7.445 3.34 7.445 7.445 S 49.106 75.325 45 75.325 z M 45 63.435 c -2.451 0 -4.446 1.994 -4.446 4.445 s 1.995 4.445 4.446 4.445 s 4.445 -1.994 4.445 -4.445 S 47.451 63.435 45 63.435 z M 45 57.146 c -3.794 0 -6.882 -3.087 -6.882 -6.882 V 34.121 c 0 -3.794 3.087 -6.882 6.882 -6.882 c 3.794 0 6.881 3.087 6.881 6.882 v 16.144 C 51.881 54.06 48.794 57.146 45 57.146 z M 45 30.239 c -2.141 0 -3.882 1.741 -3.882 3.882 v 16.144 c 0 2.141 1.741 3.882 3.882 3.882 c 2.14 0 3.881 -1.741 3.881 -3.882 V 34.121 C 48.881 31.98 47.14 30.239 45 30.239 z' style='stroke: none; stroke-width: 1; stroke-dasharray: none; stroke-linecap: butt; stroke-linejoin: miter; stroke-miterlimit: 10; fill: rgb(0,0,0); fill-rule: nonzero; opacity: 1;' transform=' matrix(1 0 0 1 0 0) ' stroke-linecap='round'></path>" +
                    "</g>" +
                    "</svg> " +
                    "</div> " +
                    "</div>"

        val videoElement = Jsoup.parse(errorDiv)

        getCurrentVideoLink().previewElement?.replaceWith(videoElement)
        getCurrentVideoLink().videoHtml = errorDiv
    }

    enum class VideoType { HAP_YAK, FROST, WISTIA, VIMEO, OBJECTS_IFRAME }

    open class VideoLink(
        val type: VideoType, var element: Element, var previewElement: Element? = null,
        var videoHtml: String = "", var error: Exception? = null, var attemptCount: Int = 0
    )

    // HapYak Data
    data class WistiaItem(val media: WistiaMediaItem)

    data class WistiaMediaItem(
        val assets: List<WistiaAssetItem>, val type: String, val hashedId: String
    )

    data class WistiaAssetItem(
        val type: String, val size: Long, val url: String, val codec: String?
    )

    data class WistiaSubtitleItem(val captions: List<WistiaSubtitleCaptionItem>)

    data class WistiaSubtitleCaptionItem(val hash: WistiaSubtitleHashItem)

    data class WistiaSubtitleHashItem(val lines: List<WistiaSubtitleLineItem>)

    data class WistiaSubtitleLineItem(val start: Double, val end: Double, val text: List<String>)

    // Frost data
    data class VideoWidgetItem(val widgetData: VideoWidgetDataItem)

    data class VideoWidgetDataItem(val options: List<VideoWidgetOptionItem>)

    data class VideoWidgetOptionItem(
        val labelName: String, val imgDetails: VideoWidgetImageDetailsItem
    )

    data class VideoWidgetImageDetailsItem(val mediaPath: String)

    // Vimeo data
    data class VimeoItem(
        val request: VimeoRequestItem, @SerializedName("player_url") val playerUrl: String
    )

    data class VimeoRequestItem(
        val files: VimeoFilesItem,
        @SerializedName("text_tracks") val textTracks: List<VimeoTextTrackItem>?
    )

    data class VimeoFilesItem(val progressive: List<VimeoProgressiveItem>)

    data class VimeoProgressiveItem(
        val width: Int, val height: Int, val mine: String, val url: String
    )

    data class VimeoTextTrackItem(val url: String)

    // Objects Iframe data
    data class ObjectsIframeItem(@SerializedName("media_object") val mediaObject: MediaObjectItem)

    data class MediaObjectItem(
        @SerializedName("media_type") val mediaType: String,
        @SerializedName("media_sources") val mediaSources: List<MediaSource>
    )

    data class MediaSource(
        val size: String, val url: String, @SerializedName("content_type") val contentType: String
    )

    open class OnVideoProcessListener {
        open fun onVideoLinksReplaced() {}
        open fun onVideoLoaded(videoLinks: List<VideoLink>) {}
        open fun onError(e: Exception) {}
        open fun onWarning(message: String) {}
    }

    companion object {

        const val TAG = "VideoHtmlOfflineDownloader"

        private const val MAX_ATTEMPT_COUNT = 3
    }
}