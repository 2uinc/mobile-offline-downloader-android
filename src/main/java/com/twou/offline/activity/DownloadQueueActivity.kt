package com.twou.offline.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.R
import com.twou.offline.adapter.DownloadQueueAdapter
import com.twou.offline.data.IOfflineNetworkChangedListener
import com.twou.offline.databinding.ActivityDownloadQueueBinding

class DownloadQueueActivity : AppCompatActivity() {

    private var mDownloadQueueAdapter: DownloadQueueAdapter? = null
    private var mOfflineListener: OfflineManager.OfflineListener? = null
    private var mIOfflineNetworkChangedListener: IOfflineNetworkChangedListener? = null
    private var mCurrentOfflineState = -1
    private var isNetworkConnected = true
    private lateinit var binding: ActivityDownloadQueueBinding

    private val mOfflineManager = Offline.getOfflineManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityDownloadQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        initUI()
    }

    override fun onDestroy() {
        mOfflineListener?.let { mOfflineManager.removeListener(it) }
        mIOfflineNetworkChangedListener?.let { Offline.removeNetworkListener(it) }
        mDownloadQueueAdapter?.destroy()
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
        mDownloadQueueAdapter = DownloadQueueAdapter()
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
                binding.resumePauseTextView.isVisible = isNetworkConnected
            }
            OfflineManager.STATE_PAUSED -> {
                binding.resumePauseTextView.setText(R.string.offline_download_resume_all)
                binding.resumePauseTextView.visibility =
                    if (isNetworkConnected) View.VISIBLE else View.GONE
            }
            else -> {
                binding.resumePauseTextView.visibility = View.GONE
            }
        }
    }
}