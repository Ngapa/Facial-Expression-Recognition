// EmotionAdapter.kt
package com.gtek.fren.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gtek.fren.databinding.ItemEmotionBinding

class EmotionAdapter : ListAdapter<EmotionResult, EmotionAdapter.EmotionViewHolder>(EmotionDiffCallback()) {

    class EmotionViewHolder(private val binding: ItemEmotionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(emotion: EmotionResult) {
            binding.tvEmotion.text = emotion.emotion
            binding.tvConfidence.text = "${emotion.confidence * 100}%"
        }
    }

    private class EmotionDiffCallback : DiffUtil.ItemCallback<EmotionResult>() {
        override fun areItemsTheSame(oldItem: EmotionResult, newItem: EmotionResult): Boolean {
            return oldItem.emotion == newItem.emotion
        }

        override fun areContentsTheSame(oldItem: EmotionResult, newItem: EmotionResult): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmotionViewHolder {
        return EmotionViewHolder(
            ItemEmotionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: EmotionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class EmotionDiffCallback : DiffUtil.ItemCallback<EmotionResult>() {
    override fun areItemsTheSame(oldItem: EmotionResult, newItem: EmotionResult): Boolean {
        return oldItem.emotion == newItem.emotion
    }

    override fun areContentsTheSame(oldItem: EmotionResult, newItem: EmotionResult): Boolean {
        return oldItem == newItem
    }
}