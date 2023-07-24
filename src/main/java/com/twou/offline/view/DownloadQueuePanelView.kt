package com.twou.offline.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.R
import com.twou.offline.activity.DownloadQueueActivity
import com.twou.offline.base.BaseOfflineDownloaderCreator
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.databinding.ViewDownloadQueuePanelBinding

class DownloadQueuePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private var mIOfflineNetworkChangedListener: IOfflineNetworkChangedListener? = null
    private var isConnected = true
    private var isDownloadInProgress = false
    private val binding =
        ViewDownloadQueuePanelBinding.inflate(LayoutInflater.from(context), this, true)

    private val mOfflineManager = Offline.getOfflineManager()

    private val mOfflineListener = object : OfflineManager.OfflineListener() {
        override fun onStateChanged(state: Int) {
            when (state) {
                OfflineManager.STATE_IDLE -> {
                    isDownloadInProgress = false
                    binding.downloadQueuePanelLayout.visibility =
                        if (isConnected) View.GONE else View.VISIBLE
                }

                OfflineManager.STATE_PAUSED -> {
                    isDownloadInProgress = true

                    updateCurrentState()
                }

                OfflineManager.STATE_DOWNLOADING -> {
                    isDownloadInProgress = true

                    updateCurrentKey()
                }
            }
        }

        override fun onItemStartedDownload(key: String) {
            updateCurrentKey()
        }

        override fun onItemDownloaded(key: String) {
            updateCurrentKey()
        }

        override fun onItemError(key: String, error: Throwable) {
            updateCurrentKey()
        }

        override fun onItemPaused(key: String) {
            updateCurrentKey()
        }

        override fun onItemRemoved(key: String) {
            updateCurrentKey()
        }

        override fun onItemResumed(key: String) {
            updateCurrentKey()
        }
    }

    init {
        if (!isInEditMode) init()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode) return

        mOfflineManager.addListener(mOfflineListener)
        mIOfflineNetworkChangedListener?.let { Offline.addNetworkListener(it) }
    }

    override fun onDetachedFromWindow() {
        mOfflineManager.removeListener(mOfflineListener)
        mIOfflineNetworkChangedListener?.let { Offline.removeNetworkListener(it) }
        super.onDetachedFromWindow()
    }

    private fun init() {
        binding.downloadQueuePanelLayout.visibility = View.GONE
        binding.downloadQueuePanelLayout.setOnClickListener {
            if (!isDownloadInProgress) return@setOnClickListener
            context.startActivity(Intent(context, DownloadQueueActivity::class.java))
        }

        mIOfflineNetworkChangedListener = object : IOfflineNetworkChangedListener {
            override fun onChanged(isConnected: Boolean) {
                updateForInternetState(isConnected)
            }
        }
    }

    private fun updateCurrentState() {
        if (mOfflineManager.getCurrentState() == OfflineManager.STATE_PAUSED) {
            binding.queuePanelTextView.setText(R.string.offline_download_state_paused)

            binding.downloadQueuePanelLeftContainerLayout.visibility = View.GONE
            binding.downloadQueuePanelLayout.visibility = View.VISIBLE

        } else if (mOfflineManager.getCurrentState() == OfflineManager.STATE_IDLE) {
            binding.downloadQueuePanelLayout.visibility =
                if (isConnected) View.GONE else View.VISIBLE

        } else {
            val contentName =
                (mOfflineManager.getCurrentDownloaderCreator() as? BaseOfflineDownloaderCreator)
                    ?.offlineQueueItem?.keyItem?.title ?: ""
            binding.queuePanelTextView.text = contentName

            binding.downloadQueuePanelLeftContainerLayout.visibility = View.VISIBLE
            binding.downloadQueuePanelLayout.visibility = View.VISIBLE
        }
    }

    private fun updateForInternetState(isConnected: Boolean) {
        this.isConnected = isConnected

        if (isConnected) {
            binding.queuePanelTextView.visibility = View.VISIBLE
            binding.noInternetQueuePanelTextView.visibility = View.GONE
            binding.downloadQueuePanelLayout.visibility =
                if (isDownloadInProgress) View.VISIBLE else View.GONE

        } else {
            binding.queuePanelTextView.visibility = View.GONE
            binding.noInternetQueuePanelTextView.visibility = View.VISIBLE
            binding.downloadQueuePanelLayout.visibility = View.VISIBLE
        }
    }

    private fun updateCurrentKey() {
        updateCurrentState()
        mOfflineManager.getCurrentDownloaderCreator()?.let { creator ->
            binding.downloadQueuePanelItemView.setKeyItem(creator.getKeyOfflineItem())
        }
    }
}
