package com.twou.offline.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.twou.offline.Offline
import com.twou.offline.item.BaseOfflineItem
import com.twou.offline.item.OfflineModule
import org.jsoup.nodes.Element

class BaseOfflineUtils {

    companion object {

        @JvmStatic
        fun isMimeTypeSupported(mimeType: String?): Boolean {
            if (mimeType == null) return false

            return mimeType.startsWith("application/") || mimeType.startsWith("audio/")
                    || mimeType.startsWith("video/") || mimeType.startsWith("image/")
                    || mimeType.startsWith("text/plain")
        }

        @JvmStatic
        fun getHtmlErrorOverlay(element: Element): String {
            return Offline.getHtmlErrorOverlay().replace("#OUTER_HTML#", element.outerHtml())
        }

        @JvmStatic
        fun isThereNoFreeSpace(context: Context): Boolean {
            val file = context.getExternalFilesDir(null) ?: return true
            val freeSpace = file.freeSpace
            OfflineLogs.d("BaseOfflineUtils", "free space is $freeSpace")
            return freeSpace <= 104857600
        }

        @JvmStatic
        fun getParentElementByClass(className: String, element: Element?): Element? {
            val parentElement = element?.parent() ?: return null
            return if (parentElement.hasClass(className)) {
                parentElement

            } else {
                getParentElementByClass(className, parentElement)
            }
        }

        @JvmStatic
        fun getParentElementById(id: String, element: Element?): Element? {
            val parentElement = element?.parent() ?: return null
            return if (parentElement.id() == id) {
                parentElement

            } else {
                getParentElementById(id, parentElement)
            }
        }

        @JvmStatic
        fun convertOfflineModuleToBase(offlineModule: OfflineModule): BaseOfflineItem {
            return Gson().fromJson(offlineModule.value, BaseOfflineItem::class.java)
        }

        @JvmStatic
        fun isOnline(context: Context): Boolean {
            var result = false
            val capabilities = getNetworkCapabilities(context)

            capabilities?.run {
                when {
                    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        result = true
                    }

                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        result = true
                    }

                    hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                        result = true
                    }
                }
            }
            return result
        }

        @JvmStatic
        private fun getNetworkCapabilities(context: Context): NetworkCapabilities? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            return cm?.getNetworkCapabilities(cm.activeNetwork)
        }
    }
}