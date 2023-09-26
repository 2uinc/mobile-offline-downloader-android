package com.twou.offline.viewmodel

import androidx.lifecycle.ViewModel
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.data.model.DownloadQueueUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DownloadQueueViewModel : ViewModel() {

    private val offlineManager = Offline.getOfflineManager()

    private val _uiState = MutableStateFlow(DownloadQueueUIState(offlineManager.getAllDownloads()))
    val uiState: StateFlow<DownloadQueueUIState> = _uiState.asStateFlow()

    private val downloadListener = object : OfflineManager.OfflineListener() {
        override fun onItemRemoved(key: String) {
            _uiState.update { state ->
                state.copy(items = state.items.filter { items ->
                    items.keyItem.key != key
                })
            }
        }

        override fun onItemDownloaded(key: String) {
            onItemRemoved(key)
        }
    }

    init {
        offlineManager.addListener(downloadListener)
    }

    fun onDestroy() {
        offlineManager.removeListener(downloadListener)
    }
}
