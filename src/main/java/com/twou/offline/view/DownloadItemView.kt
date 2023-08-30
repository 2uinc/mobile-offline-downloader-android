package com.twou.offline.view

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.twou.offline.*
import com.twou.offline.base.BaseOfflineDownloaderCreator
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.databinding.ViewDownloadItemBinding
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineQueueItem
import com.twou.offline.item.QueueState

class DownloadItemView : FrameLayout {

    private var mCurrentKeyItem: KeyOfflineItem? = null
    private var mCurrentState = -1
    private var isVisibilityOnAction = false
    private var isWithRemoveAbility = false
    private var mDialog: AlertDialog? = null
    private var isNetworkConnected = true
    private var onDownloadedListener: () -> Unit = {}
    private val binding =
        ViewDownloadItemBinding.inflate(LayoutInflater.from(context), this, true)

    private val mOfflineRepository = Offline.getOfflineRepository()
    private val mOfflineUnsupportedRepository = Offline.getOfflineUnsupportedRepository()
    private val mOfflineManager = Offline.getOfflineManager()

    private val mDownloadListener = object : OfflineManager.OfflineListener() {
        override fun onStateChanged(state: Int) {
            checkCurrentItemDownloadState()
        }

        override fun onProgressChanged(key: String, currentProgress: Int, allProgress: Int) {
            if (key == mCurrentKeyItem?.key) {
                setState(STATE_DOWNLOADING, currentProgress, allProgress)
            }
        }

        override fun onItemAdded(key: String) {
            if (key == mCurrentKeyItem?.key) setState(STATE_PREPARING)
        }

        override fun onItemRemoved(key: String) {
            if (key == mCurrentKeyItem?.key) setState(STATE_IDLE)
        }

        override fun onItemStartedDownload(key: String) {
            if (key == mCurrentKeyItem?.key) setState(STATE_DOWNLOADING)
        }

        override fun onItemDownloaded(key: String) {
            if (key == mCurrentKeyItem?.key) {
                setState(STATE_DOWNLOADED)
                onDownloadedListener.invoke()
            }
        }

        override fun onItemPaused(key: String) {
            if (key == mCurrentKeyItem?.key) setState(STATE_PAUSED)
        }

        override fun onItemResumed(key: String) {
            if (key == mCurrentKeyItem?.key) checkCurrentItemDownloadState()
        }
    }

    private val mOfflineNetworkChangedListener = object : IOfflineNetworkChangedListener {
        override fun onChanged(isConnected: Boolean) {
            isNetworkConnected = isConnected
            checkCurrentItemDownloadState()
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null, 0)

    init {
        if (!isInEditMode) init()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode) return

        mOfflineManager.addListener(mDownloadListener)
        Offline.addNetworkListener(mOfflineNetworkChangedListener)

        checkCurrentItemDownloadState()
    }

    override fun onDetachedFromWindow() {
        mOfflineManager.removeListener(mDownloadListener)
        Offline.removeNetworkListener(mOfflineNetworkChangedListener)
        super.onDetachedFromWindow()
    }

    fun setKeyItem(keyItem: KeyOfflineItem) {
        mCurrentKeyItem = keyItem
        checkCurrentItemDownloadState()
    }

    fun setOnDownloadedListener(listener: () -> Unit) {
        onDownloadedListener = listener
    }

    fun setVisibilityOnAction() {
        isVisibilityOnAction = true
    }

    fun setWithRemoveAbility() {
        isWithRemoveAbility = true
    }

    fun setViewColor(color: Int) {
        binding.downloadStatusImageView.setColorFilter(color)
        binding.progressStatusImageView.setColorFilter(color)
        binding.downloadInfinityProgressBar.indeterminateTintList = ColorStateList.valueOf(color)
        binding.downloadProgressBar.progressTintList = ColorStateList.valueOf(color)
        binding.downloadPercentTextView.setTextColor(color)
    }

    private fun init() {
        setState(STATE_IDLE)

        setOnClickListener {
            when (mCurrentState) {
                STATE_IDLE -> {
                    if (isNetworkConnected) {
                        mCurrentKeyItem?.let { keyItem ->
                            mOfflineManager.addOfflineDownloaderCreator(
                                Offline.getCreatorUnit().invoke(OfflineQueueItem(keyItem))
                            )
                        }
                    }
                }

                STATE_PREPARING -> {
                    mCurrentKeyItem?.let { keyItem ->
                        if (keyItem.title.isNotEmpty()) {
                            mOfflineManager.pause(keyItem.key)
                        }
                    }
                }

                STATE_PREPARED, STATE_DOWNLOADING -> {
                    mOfflineManager.pause(mCurrentKeyItem?.key ?: "")
                }

                STATE_PAUSED -> {
                    if (isNetworkConnected) {
                        mOfflineManager.resume(mCurrentKeyItem?.key ?: "")

                    } else {
                        Toast.makeText(
                            context, R.string.offline_download_network_should_be_online,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                STATE_NETWORK_ERROR -> {
                    showErrorDialog(context.getString(R.string.offline_download_network_error))
                }

                STATE_SERVER_ERROR -> {
                    showErrorDialogWithRetry(context.getString(R.string.offline_download_server_error))
                }

                STATE_UNSUPPORTED_ERROR -> {
                    showErrorDialog(context.getString(R.string.offline_download_unsupported_error))
                }

                STATE_NO_SPACE -> {
                    showErrorDialogWithRetry(context.getString(R.string.offline_download_no_space_error))
                }

                STATE_DOWNLOADED -> {
                    if (isWithRemoveAbility) showRemoveDialog()
                }
            }
        }
    }

    private fun checkCurrentItemDownloadState() {
        if (mCurrentKeyItem == null) return

        when {
            mOfflineUnsupportedRepository.isUnsupported(mCurrentKeyItem?.key ?: "") -> {
                setState(STATE_UNSUPPORTED_ERROR)
            }

            mOfflineRepository.getOfflineModule(mCurrentKeyItem?.key ?: "") != null -> {
                setState(STATE_DOWNLOADED)
            }

            else -> {
                val downloaderCreator =
                    mOfflineManager.getDownloaderCreator(mCurrentKeyItem?.key ?: "")
                if (downloaderCreator == null) {
                    setState(STATE_IDLE)

                } else {
                    if (!Offline.isConnected()) {
                        setState(STATE_NETWORK_ERROR)
                        return
                    }

                    when ((downloaderCreator as BaseOfflineDownloaderCreator).offlineQueueItem.queueState) {
                        QueueState.PREPARED -> {
                            setState(STATE_PREPARED)
                        }

                        QueueState.DOWNLOADING -> {
                            if (downloaderCreator.getCurrentProgress() <= 0) {
                                setState(STATE_DOWNLOADING)

                            } else {
                                setState(
                                    STATE_DOWNLOADING, downloaderCreator.getCurrentProgress(),
                                    downloaderCreator.getAllProgress()
                                )
                            }
                        }

                        QueueState.PAUSED -> setState(STATE_PAUSED)

                        QueueState.NETWORK_ERROR -> setState(STATE_NETWORK_ERROR)

                        QueueState.SERVER_ERROR -> setState(STATE_SERVER_ERROR)

                        QueueState.UNSUPPORTED_ERROR -> setState(STATE_UNSUPPORTED_ERROR)

                        QueueState.NO_SPACE_ERROR -> setState(STATE_NO_SPACE)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setState(state: Int, currentProgress: Int = -1, allProgress: Int = -1) =
        with(binding) {
            mCurrentState = state

            when (state) {
                STATE_DOWNLOADING -> {
                    if (isVisibilityOnAction) visibility = View.VISIBLE

                    downloadStatusImageView.visibility = View.GONE
                    progressStatusImageView.setImageResource(R.drawable.ic_offline_round_pause)
                    progressStatusImageView.visibility = View.VISIBLE

                    if (currentProgress != -1 && allProgress != -1) {
                        downloadProgressBar.max = allProgress
                        downloadProgressBar.progress = currentProgress

                        val progress = currentProgress.toFloat() * 100 / allProgress
                        downloadPercentTextView.text = "${progress.toInt()}%"

                        downloadInfinityProgressBar.visibility = View.INVISIBLE
                        downloadProgressBar.visibility = View.VISIBLE
                        downloadPercentTextView.visibility = View.VISIBLE

                    } else {
                        downloadInfinityProgressBar.visibility = View.VISIBLE
                        downloadProgressBar.visibility = View.GONE
                        downloadPercentTextView.visibility = View.GONE
                    }
                }

                STATE_IDLE, STATE_PREPARING, STATE_PREPARED, STATE_DOWNLOADED, STATE_PAUSED,
                STATE_NETWORK_ERROR, STATE_SERVER_ERROR, STATE_UNSUPPORTED_ERROR, STATE_NO_SPACE -> {
                    if (isVisibilityOnAction) {
                        visibility =
                            if (state == STATE_IDLE) View.GONE else View.VISIBLE
                    }

                    downloadInfinityProgressBar.visibility = View.INVISIBLE
                    downloadProgressBar.visibility = View.GONE
                    downloadPercentTextView.visibility = View.GONE
                    downloadStatusImageView.setImageResource(
                        when (state) {
                            STATE_IDLE -> R.drawable.ic_offline_round_download
                            STATE_PREPARING, STATE_PREPARED -> R.drawable.ic_offline_cloud_clock
                            STATE_DOWNLOADED -> {
                                if (isWithRemoveAbility) R.drawable.ic_offline_round_delete else
                                    R.drawable.ic_offline_outline_cloud_download
                            }

                            STATE_PAUSED -> R.drawable.ic_offline_round_play
                            STATE_NETWORK_ERROR -> R.drawable.ic_offline_baseline_signal_cellular_off
                            STATE_NO_SPACE, STATE_SERVER_ERROR -> R.drawable.ic_offline_round_error_outline
                            STATE_UNSUPPORTED_ERROR -> R.drawable.ic_offline_round_cloud_off
                            else -> R.drawable.ic_offline_round_error_outline
                        }
                    )
                    downloadStatusImageView.visibility = View.VISIBLE

                    if (state == STATE_PAUSED) {
                        progressStatusImageView.setImageResource(R.drawable.ic_offline_round_play)
                        progressStatusImageView.visibility = View.VISIBLE

                    } else {
                        progressStatusImageView.visibility = View.GONE
                    }
                }
            }

            if (!isNetworkConnected && mCurrentState == STATE_IDLE) {
                downloadContainerLayout.alpha = 0.5f

            } else {
                downloadContainerLayout.alpha = 1f
            }
        }

    private fun showErrorDialog(message: String) {
        mDialog?.dismiss()
        mDialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.offline_dialog_ok) { _, _ -> }
            .setMessage(message)
            .show()
    }

    private fun showErrorDialogWithRetry(message: String) {
        mDialog?.dismiss()
        mDialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.offline_dialog_retry) { _, _ ->
                mOfflineManager.resume(mCurrentKeyItem?.key ?: "")
            }
            .setNegativeButton(R.string.offline_dialog_cancel) { _, _ -> }
            .setMessage(message)
            .show()
    }

    private fun showRemoveDialog() {
        mDialog?.dismiss()
        mDialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.offline_dialog_remove) { _, _ ->
                mOfflineManager.remove(mCurrentKeyItem?.key ?: "")
            }
            .setNegativeButton(R.string.offline_dialog_cancel) { _, _ -> }
            .setMessage(R.string.offline_download_remove_content)
            .show()
    }

    companion object {

        const val STATE_IDLE = 1
        const val STATE_PREPARING = 2
        const val STATE_PREPARED = 10
        const val STATE_DOWNLOADING = 3
        const val STATE_DOWNLOADED = 4
        const val STATE_PAUSED = 5
        const val STATE_NETWORK_ERROR = 6
        const val STATE_SERVER_ERROR = 7
        const val STATE_UNSUPPORTED_ERROR = 8
        const val STATE_NO_SPACE = 9
    }
}