package com.twou.offline.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.twou.offline.R
import com.twou.offline.databinding.ItemDownloadQueueBinding
import com.twou.offline.item.OfflineQueueItem

class DownloadQueueAdapter(
    mContext: Context,
    private val mCallback: (DownloadQueueCallback) -> Unit
) :
    RecyclerView.Adapter<DownloadQueueAdapter.ViewHolder>() {

    private val mItems: MutableList<OfflineQueueItem> = mutableListOf()

    private val mFirstBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_first)
    private val mRecyclerBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler)
    private val mSingleBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_single)
    private val mLastBg = ContextCompat.getDrawable(mContext, R.drawable.bg_recycler_last)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadQueueBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mItems[position]
        holder.binding.itemCardView.background = if (itemCount == 1) {
            mSingleBg
        } else {
            when (position) {
                0 -> mFirstBg
                mItems.size - 1 -> mLastBg
                else -> mRecyclerBg
            }
        }
        holder.binding.mainTextView.text = item.keyItem.title
        holder.binding.downloadItemView.setKeyItem(item.keyItem)
        holder.binding.removeImageView.setOnClickListener {
            mCallback(DownloadQueueCallback.OnRemoveItemCallback(item.keyItem.key))
        }
    }

    override fun getItemCount() = mItems.size

    override fun getItemId(position: Int) = mItems[position].keyItem.key.hashCode().toLong()

    fun getItems() = mItems

    fun setData(items: List<OfflineQueueItem>) {
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = getItems().size

            override fun getNewListSize() = items.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                getItems()[oldItemPosition].keyItem.key == items[newItemPosition].keyItem.key

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                getItems()[oldItemPosition].queueState == items[newItemPosition].queueState
                        && newItemPosition != 0 && newItemPosition != items.size - 1

        })
        result.dispatchUpdatesTo(this)
        mItems.clear()
        mItems.addAll(items)
    }

    class ViewHolder(val binding: ItemDownloadQueueBinding) : RecyclerView.ViewHolder(binding.root)

    sealed class DownloadQueueCallback {
        data class OnRemoveItemCallback(val itemKey: String) : DownloadQueueCallback()
    }
}
