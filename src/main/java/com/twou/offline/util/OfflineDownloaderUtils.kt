package com.twou.offline.util

import com.twou.offline.Offline
import java.util.*

object OfflineDownloaderUtils {

    fun getDirPath(key: String): String {
        return "" + Offline.getContext().getExternalFilesDir(null) + "/offline_mode/${key}"
    }

    fun getGeneralDirPath(): String {
        return getDirPath("general")
    }

    fun getStartPagePath(key: String): String {
        val dirPath = getDirPath(key)
        return if (dirPath.last() == '/') "${dirPath}index.html" else "$dirPath/index.html"
    }

    fun getUrlFileName(url: String): String {
        var fileName = url.substring(url.lastIndexOf("/") + 1)

        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"))
        }

        fileName = fileName.replace("?", "").replace(":", "")
            .replace("%", "")

        if (isIncorrectFilename(fileName)) {
            fileName = renameFilePathWith(fileName, "")
        }

        return fileName
    }

    fun isIncorrectFilename(filename: String): Boolean {
        val filenameParts = filename.split(".".toRegex()).toTypedArray()
        return filenameParts.size == 1 || getExtension(filename).length > 5 || filename.length > 255
    }

    fun renameFilePathWith(source: String, ext: String): String {
        if (!source.contains("/")) {
            return Date().time.toString() + ext
        }
        var result = source.substring(0, source.lastIndexOf('/'))
        result += "/" + Date().time.toString() + ext
        return result
    }

    fun clearHtml(html: String): String {
        return html.replace("&lt;#root&gt;", "")
            .replace("<#root>", "")
            .replace("&lt;/#root&gt;", "")
            .replace("</#root>", "")
    }

    fun getUrlWithoutQuery(url: String): String {
        return url.replaceFirst("\\?.*$", "")
    }

    private fun getExtension(filename: String): String {
        var result = ""
        if (filename.contains(".")) {
            result = filename.substring(filename.lastIndexOf(".") + 1)
        }
        return result
    }
}