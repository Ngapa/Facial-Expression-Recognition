package com.gtek.fren.ui.aboutemotion;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gtek.fren.databinding.FragmentAboutEmotionBinding;

public class AboutEmotionFragment extends Fragment {

    private FragmentAboutEmotionBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        AboutEmotionViewModel aboutEmotionViewModel =
                new ViewModelProvider(this).get(AboutEmotionViewModel.class);

        binding = FragmentAboutEmotionBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView titleView = binding.textTitle;
        final TextView textView = binding.textAboutEmotion;
        titleView.setText("About Emotion");
        aboutEmotionViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}