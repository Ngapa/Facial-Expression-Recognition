package com.gtek.fren.ui.privacypolicy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gtek.fren.databinding.FragmentPrivacyPolicyBinding;

public class PrivacyPolicyFragment extends Fragment {

    private FragmentPrivacyPolicyBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PrivacyPolicyViewModel privacyPolicyViewModel =
                new ViewModelProvider(this).get(PrivacyPolicyViewModel.class);

        binding = FragmentPrivacyPolicyBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView titleView = binding.textTitle;
        final TextView textView = binding.textPrivacyPolicy;
        titleView.setText("Privacy Policy");
        privacyPolicyViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
