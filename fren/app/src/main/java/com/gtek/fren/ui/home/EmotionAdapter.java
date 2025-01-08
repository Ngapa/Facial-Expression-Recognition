// EmotionAdapter.java
package com.gtek.fren.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.gtek.fren.databinding.ItemEmotionBinding;

public class EmotionAdapter extends ListAdapter<EmotionResult, EmotionAdapter.EmotionViewHolder> {

    public EmotionAdapter() {
        super(new EmotionDiffCallback());
    }

    static class EmotionViewHolder extends RecyclerView.ViewHolder {
        private final ItemEmotionBinding binding;

        public EmotionViewHolder(@NonNull ItemEmotionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(EmotionResult emotion) {
            binding.tvEmotion.setText(emotion.getEmotion());
            binding.tvConfidence.setText(String.format("%s%%", emotion.getConfidence() * 100));
        }
    }

    private static class EmotionDiffCallback extends DiffUtil.ItemCallback<EmotionResult> {
        @Override
        public boolean areItemsTheSame(@NonNull EmotionResult oldItem, @NonNull EmotionResult newItem) {
            return oldItem.getEmotion().equals(newItem.getEmotion());
        }

        @Override
        public boolean areContentsTheSame(@NonNull EmotionResult oldItem, @NonNull EmotionResult newItem) {
            return oldItem.equals(newItem);
        }
    }

    @NonNull
    @Override
    public EmotionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEmotionBinding binding = ItemEmotionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new EmotionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EmotionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }
}
