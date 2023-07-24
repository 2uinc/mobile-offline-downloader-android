package com.twou.offline.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.databinding.ItemDownloadQueueBinding
import com.twou.offline.item.OfflineQueueItem

class DownloadQueueAdapter : RecyclerView.Adapter<DownloadQueueAdapter.ViewHolder>() {

    private val mItems = mutableListOf<OfflineQueueItem>()

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
        }
    }

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

        holder.binding.dividerView.visibility =
            if (position == 0) View.GONE else View.VISIBLE
        holder.binding.mainTextView.text = item.keyItem.title

        holder.binding.downloadItemView.setKeyItem(item.keyItem)

        holder.binding.removeImageView.setOnClickListener {
            mOfflineManager.remove(item.keyItem.key)
        }
    }

    override fun getItemCount(): Int = mItems.size

    fun destroy() {
        mOfflineManager.removeListener(mDownloadListener)
    }

    class ViewHolder(val binding: ItemDownloadQueueBinding) : RecyclerView.ViewHolder(binding.root)
}
