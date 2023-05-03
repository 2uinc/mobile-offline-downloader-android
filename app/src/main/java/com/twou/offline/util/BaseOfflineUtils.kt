package com.twou.offline.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.twou.offline.Offline
import com.twou.offline.R
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
            val message =
                Offline.getContext().getString(R.string.download_content_not_available)

            return """
                <div class="offline-error-container">
                    <div class="offline-error-overlay">
                        <div>
                            <p>$message</p>
                        </div>
                    </div>
                    ${element.outerHtml()}
                    <div class="offline-error-note">
                        <p>$message</p>
                    </div>
                </div>
                """.trimIndent()
        }

        @JvmStatic
        fun getHtmlErrorScript(): String = """
            function updatePlaceholders() {
                var overlays = Array.from(document.getElementsByClassName("offline-error-overlay"));
                var notes = Array.from(document.getElementsByClassName("offline-error-note"));
                var tapOverlays = Array.from(document.getElementsByClassName("campus-document-overlay-container"));
                
                var isOnline = window.offline.isOnline();
                if (isOnline) {
                    overlays.forEach(function (element) {
                        element.style.display = "none";
                    });
                    notes.forEach(function (element) {
                        element.style.display = "flex";
                    });
                } else {
                    overlays.forEach(function (element) {
                        element.style.display = "block";
                    });
                    notes.forEach(function (element) {
                        element.style.display = "none";
                    });
                    tapOverlays.forEach(function (element) {
                        element.style.display = "none";
                    });
                }
            }

            window.addEventListener("load", function (event) {
                updatePlaceholders();
            });

            document.addEventListener('DOMContentLoaded', function (event) {
                updatePlaceholders();
            });
        """.trimIndent()

        @JvmStatic
        fun getHtmlErrorCss(): String = """
            .offline-error-container {
                 position: relative;
                 display: block;
                 overflow: hidden;
                 min-height: 80px;
            }

            .offline-error-container iframe {
                height: calc(100vw / (16/9));
                border: 0;
            }

            .offline-error-container audio {
                width: 100%;
            }

            .offline-error-container .offline-error-note {
                display: flex;
                justify-content: center;
                text-align: center;
                width: 100%;
                border-radius: 10px;
                border: 2px solid #e5146fff;
                margin-top: 20px;
            }
            
            #page-mod-video-view .custom-notes .offline-error-note p,
            .offline-error-container .offline-error-note p {
                padding: 10px !important;
                margin: 0px !important;
                margin-bottom: 0px !important;
                margin-top: 0px !important;
            }

            .offline-error-overlay {
                 position: absolute;
                 background-color: #000000;
                 width: 100%;
                 height: 100%;
                 top: 0px;
                 left: 0px;
                 z-index: 400;
                 text-align: center;
            }

            .offline-error-overlay div {
                 width: 100%;
                 height: 100%;
                 display: flex;
                 align-items: center;
                 justify-content: center;
            }

            .offline-error-overlay div p {
                 color: white;
                 font-size: 17px;
                 font-family: system-ui;
                 font-weight: 500;
                 background-color: .black;
                 padding: 15px 32px;
                 margin: 0;
                 border-radius: 10px;
                 display: inline-block;
            }
            """.trimIndent()

        @JvmStatic
        fun isThereNoFreeSpace(context: Context): Boolean {
            val file = context.getExternalFilesDir(null) ?: return true
            val freeSpace = file.freeSpace
            OfflineLogs.d("BaseOfflineUtils", "free space is $freeSpace")
            return freeSpace <= 104857600
        }

        @JvmStatic
        fun getParentElement(className: String, element: Element?): Element? {
            val parentElement = element?.parent() ?: return null
            return if (parentElement.hasClass(className)) {
                parentElement

            } else {
                getParentElement(className, parentElement)
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