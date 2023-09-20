package com.twou.offline.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.R
import com.twou.offline.databinding.ItemDownloadQueueBinding
import com.twou.offline.item.OfflineQueueItem
import java.util.Collections

class DownloadQueueAdapter(
    mContext: Context,
    private val mOnDownloadQueueListener: OnDownloadQueueListener
) :
    RecyclerView.Adapter<DownloadQueueAdapter.ViewHolder>() {

    private val mItems = Collections.synchronizedList(mutableListOf<OfflineQueueItem>())

    private val mOfflineManager = Offline.getOfflineManager()

    private val mDownloadListener = object : OfflineManager.OfflineListener() {
        override fun onItemRemoved(key: String) {
            run job@{
                mItems.forEachIndexed { index, item ->
                    if (item.keyItem.key == key) {
                        mItems.removeAt(index)
                        notifyItemRemoved(index)
                        return@job
                    }
                }
            }

            if (itemCount == 0) mOnDownloadQueueListener.onItemsEmpty()
        }

        override fun onItemDownloaded(key: String) {
            onItemRemoved(key)
        }
    }

    private val mFirstBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_first)
    private val mRecyclerBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler)
    private val mSingleBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_single)
    private val mLastBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_last)

    init {
        mItems.addAll(mOfflineManager.getAllDownloads())

        mOfflineManager.addListener(mDownloadListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadQueueBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mItems[position]
        holder.binding.itemCardView.background = when (holder.itemViewType) {
            ViewType.FIRST_ELEMENT -> mFirstBg
            ViewType.LAST_ELEMENT -> mLastBg
            ViewType.SINGLE_ELEMENT -> mSingleBg
            else -> mRecyclerBg
        }
        holder.binding.mainTextView.text = item.keyItem.title
        holder.binding.downloadItemView.setKeyItem(item.keyItem)
        holder.binding.removeImageView.setOnClickListener {
            mOfflineManager.remove(item.keyItem.key)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (itemCount == 1) return ViewType.SINGLE_ELEMENT
        return when (position) {
            0 -> ViewType.FIRST_ELEMENT
            mItems.size - 1 -> ViewType.LAST_ELEMENT
            else -> ViewType.ELEMENT
        }
    }

    override fun getItemCount(): Int = mItems.size

    fun getItems(): MutableList<OfflineQueueItem> = mItems

    fun destroy() {
        mOfflineManager.removeListener(mDownloadListener)
    }

    class ViewHolder(val binding: ItemDownloadQueueBinding) : RecyclerView.ViewHolder(binding.root)

    interface OnDownloadQueueListener {

        fun onItemsEmpty()
    }

    private object ViewType {
        const val FIRST_ELEMENT = 0
        const val LAST_ELEMENT = 1
        const val ELEMENT = 2
        const val SINGLE_ELEMENT = 3
    }
}
