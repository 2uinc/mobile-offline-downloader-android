package com.twou.offline.error

class OfflineUnsupportedException(e: Throwable? = null, message: String? = null) :
    Exception(message, e)