package com.twou.offline.activity

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.R
import com.twou.offline.adapter.DownloadQueueAdapter
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.databinding.ActivityDownloadQueueBinding
import com.twou.offline.item.QueueState
import com.twou.offline.viewmodel.DownloadQueueViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DownloadQueueActivity : AppCompatActivity() {

    private var mDownloadQueueAdapter: DownloadQueueAdapter? = null
    private var mOfflineListener: OfflineManager.OfflineListener? = null
    private var mIOfflineNetworkChangedListener: IOfflineNetworkChangedListener? = null
    private var mCurrentOfflineState = -1
    private var isNetworkConnected = true
    private lateinit var binding: ActivityDownloadQueueBinding

    private val mOfflineManager = Offline.getOfflineManager()

    private val mViewModel: DownloadQueueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityDownloadQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        initUI()
        mViewModel.uiState.onEach {
            if (it.items.isEmpty()) {
                finish()
                return@onEach
            }
            mDownloadQueueAdapter?.setData(it.items)
            updatePauseResumeState()
        }.launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        mOfflineListener?.let { mOfflineManager.removeListener(it) }
        mIOfflineNetworkChangedListener?.let { Offline.removeNetworkListener(it) }
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun initUI() {
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        DividerItemDecoration(this, DividerItemDecoration.VERTICAL).also { decoration ->
            ContextCompat.getDrawable(this, R.drawable.vertical_space_2dp)?.let {
                decoration.setDrawable(it)
                binding.recyclerView.addItemDecoration(decoration)
            }
        }
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        mDownloadQueueAdapter = DownloadQueueAdapter(
            mContext = this,
            mCallback = { callback ->
                when (callback) {
                    is DownloadQueueAdapter.DownloadQueueCallback.OnRemoveItemCallback -> {
                        mOfflineManager.remove(callback.itemKey)
                    }
                }
            }
        )
        mDownloadQueueAdapter?.setHasStableIds(true)
        binding.recyclerView.adapter = mDownloadQueueAdapter

        binding.resumePauseTextView.setOnClickListener {
            if (!isNetworkConnected) return@setOnClickListener

            when (mCurrentOfflineState) {
                OfflineManager.STATE_DOWNLOADING -> {
                    mOfflineManager.pauseAll()
                }

                OfflineManager.STATE_PAUSED -> {
                    mOfflineManager.resumeAll()
                }
            }
        }

        mOfflineListener = object : OfflineManager.OfflineListener() {
            override fun onStateChanged(state: Int) {
                mCurrentOfflineState = state
                updatePauseResumeState()
            }

            override fun onItemRemoved(key: String) {
                updatePauseResumeState()
            }

            override fun onItemDownloaded(key: String) {
                updatePauseResumeState()
            }
        }
        mOfflineListener?.let { mOfflineManager.addListener(it) }

        mIOfflineNetworkChangedListener = object : IOfflineNetworkChangedListener {
            override fun onChanged(isConnected: Boolean) {
                isNetworkConnected = isConnected
                updatePauseResumeState()
            }
        }
        mIOfflineNetworkChangedListener?.let { Offline.addNetworkListener(it) }
    }

    private fun updatePauseResumeState() {
        when (mCurrentOfflineState) {
            OfflineManager.STATE_DOWNLOADING -> {
                binding.resumePauseTextView.setText(R.string.offline_download_pause_all)
                binding.resumePauseTextView.visibility =
                    if (isNetworkConnected) View.VISIBLE else View.GONE
            }

            OfflineManager.STATE_PAUSED -> {
                val hasPausedContent = mDownloadQueueAdapter?.getItems()
                    ?.find { it.queueState != QueueState.UNSUPPORTED_ERROR } != null

                if (hasPausedContent) {
                    binding.resumePauseTextView.setText(R.string.offline_download_resume_all)
                    binding.resumePauseTextView.visibility =
                        if (isNetworkConnected) View.VISIBLE else View.GONE

                } else {
                    binding.resumePauseTextView.visibility = View.GONE
                }
            }

            else -> {
                binding.resumePauseTextView.visibility = View.GONE
            }
        }
    }
}