package com.twou.offline.error

class OfflineDownloadException(e: Throwable? = null, message: String? = null) :
    Exception(message, e)