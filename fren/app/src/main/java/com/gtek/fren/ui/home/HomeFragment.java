package com.gtek.fren.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.gtek.fren.databinding.FragmentHomeBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private Camera camera;
    private boolean isDetecting = false;
    private CameraSelector cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
    private static final String TAG = "HomeFragment";
    private HomeViewModel homeViewModel;
    private EmotionAdapter emotionAdapter;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize OpenCV
        try {
            boolean isOpenCVInitialized = OpenCVLoader.initDebug();
            if (!isOpenCVInitialized) {
                Log.e(TAG, "OpenCV initialization failed");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OpenCV: " + e.getMessage());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");
        homeViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())).get(HomeViewModel.class);
        emotionAdapter = new EmotionAdapter();
        setupUI();
        setupObservers();
        checkCameraPermission();
    }

    private void setupObservers() {
        homeViewModel.getEmotionResults().observe(getViewLifecycleOwner(), emotions -> {
            if (emotions != null) {
                emotionAdapter.submitList(emotions);
            }
        });
    }

    private void checkCameraPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Camera permission granted: " + hasPermission);

        if (hasPermission) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupUI() {
        Log.d(TAG, "Setting up UI components");
        binding.rvEmotions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvEmotions.setAdapter(emotionAdapter);

        binding.btnStart.setOnClickListener(v -> {
            isDetecting = true;
            Log.d(TAG, "Detection started");
            homeViewModel.startDetection();
        });

        binding.btnStop.setOnClickListener(v -> {
            isDetecting = false;
            Log.d(TAG, "Detection stopped");
            homeViewModel.stopDetection();
        });

        binding.btnSwitchCamera.setOnClickListener(v -> {
            Log.d(TAG, "Switching camera");
            switchCamera();
        });
    }

    private void switchCamera() {
        Log.d(TAG, "Current camera: " + (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA ? "Front" : "Back"));
        if (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            if (hasBackCamera()) {
                cameraFacing = CameraSelector.DEFAULT_BACK_CAMERA;
                Log.d(TAG, "Switching to Back Camera");
            } else {
                Toast.makeText(requireContext(), "Back camera not available", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Back camera not available");
                return;
            }
        } else {
            if (hasFrontCamera()) {
                cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
                Log.d(TAG, "Switching to Front Camera");
            } else {
                Toast.makeText(requireContext(), "Front camera not available", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Front camera not available");
                return;
            }
        }
        startCamera();
    }

    private void startCamera() {
        Log.d(TAG, "Starting camera");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Log.d(TAG, "Camera provider initialized");

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isDetecting) {
                        Log.d(TAG, "Processing image for detection");
                        processImage(imageProxy);
                    } else {
                        imageProxy.close();
                        Log.d(TAG, "Image processing skipped");
                    }
                });

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraFacing,
                        preview,
                        imageAnalyzer
                );

                Log.d(TAG, "Camera bound to lifecycle");
            } catch (Exception e) {
                Log.e(TAG, "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private boolean hasBackCamera() {
        return requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private boolean hasFrontCamera() {
        return requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    private void processImage(ImageProxy imageProxy) {
        Log.d(TAG, "Processing image with ML Kit Face Detector");
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        homeViewModel.processImageWithFaceDetection(imageProxy, detector);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraExecutor.shutdown();
        binding = null;
    }
}
