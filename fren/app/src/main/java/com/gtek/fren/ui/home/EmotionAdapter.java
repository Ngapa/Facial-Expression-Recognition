// EmotionAdapter.java
package com.gtek.fren.ui.home;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.gtek.fren.databinding.ItemEmotionBinding;

public class EmotionAdapter extends ListAdapter<EmotionClassifier.EmotionResult, EmotionAdapter.EmotionViewHolder> {

    public EmotionAdapter() {
        super(new EmotionDiffCallback());
    }

    @NonNull
    @Override
    public EmotionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return null;
    }

    static class EmotionViewHolder extends RecyclerView.ViewHolder {
        private final ItemEmotionBinding binding;

        public EmotionViewHolder(@NonNull ItemEmotionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(EmotionClassifier.EmotionResult emotion) {
            binding.tvEmotion.setText(emotion.getEmotion());
            binding.tvConfidence.setText(String.format("%s%%", emotion.getConfidence() * 100));
        }
    }

    private static class EmotionDiffCallback extends DiffUtil.ItemCallback<EmotionClassifier.EmotionResult> {
        @Override
        public boolean areItemsTheSame(@NonNull EmotionClassifier.EmotionResult oldItem, @NonNull EmotionClassifier.EmotionResult newItem) {
            // Bandingkan berdasarkan unique identifier
            return oldItem.getEmotion().equals(newItem.getEmotion());
        }

        @Override
        public boolean areContentsTheSame(@NonNull EmotionClassifier.EmotionResult oldItem, @NonNull EmotionClassifier.EmotionResult newItem) {
            // Bandingkan semua content
            return oldItem.getEmotion().equals(newItem.getEmotion())
                    && oldItem.getConfidence() == newItem.getConfidence();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull EmotionViewHolder holder, int position) {
        EmotionClassifier.EmotionResult item = getItem(position);
        if (item != null) {
            holder.bind(item);
        }
    }
}
