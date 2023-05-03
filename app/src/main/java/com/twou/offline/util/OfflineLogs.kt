package com.twou.offline.util

import android.util.Log
import com.twou.offline.BuildConfig

class OfflineLogs {

    companion object {

        @JvmStatic
        fun e(tag: String, text: String) {
            if (BuildConfig.DEBUG) Log.e(tag, text)
        }

        @JvmStatic
        fun d(tag: String, text: String) {
            if (BuildConfig.DEBUG) Log.d(tag, text)
        }

        @JvmStatic
        fun w(tag: String, text: String) {
            if (BuildConfig.DEBUG) Log.w(tag, text)
        }
    }
}