package com.gtek.fren.ui.home;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.viewModelScope;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetector;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    private final MutableLiveData<List<EmotionResult>> _emotionResults = new MutableLiveData<>();
    public LiveData<List<EmotionResult>> emotionResults = _emotionResults;

    private final MutableLiveData<String> _processingError = new MutableLiveData<>();
    public LiveData<String> processingError = _processingError;

    private boolean isDetecting = false;
    private final EmotionClassifier emotionClassifier;

    public HomeViewModel(Application application) {
        super(application);
        emotionClassifier = new EmotionClassifier(application.getApplicationContext());
    }

    public void startDetection() {
        isDetecting = true;
        Log.d(TAG, "Detection started");
    }

    public void stopDetection() {
        isDetecting = false;
        _emotionResults.postValue(new ArrayList<>());
        Log.d(TAG, "Detection stopped");
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void processImageWithFaceDetection(final ImageProxy imageProxy, final FaceDetector faceDetector) {
        if (!isDetecting) {
            imageProxy.close();
            return;
        }

        viewModelScope.launch(Dispatchers.getDefault(), () -> {
            try {
                if (imageProxy.getImage() == null) {
                    Log.w(TAG, "Skipping frame - no image available");
                    imageProxy.close();
                    return;
                }

                InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

                withContext(Dispatchers.getMain(), () -> {
                    faceDetector.process(image)
                            .addOnSuccessListener(faces -> {
                                if (faces.isEmpty()) {
                                    Log.d(TAG, "No faces detected");
                                    _emotionResults.postValue(new ArrayList<>());
                                    return;
                                }

                                viewModelScope.launch(Dispatchers.getDefault(), () -> {
                                    for (com.google.mlkit.vision.face.Face face : faces) {
                                        try {
                                            Bitmap bitmap = toBitmap(imageProxy);
                                            Mat mat = new Mat();
                                            Utils.bitmapToMat(bitmap, mat);

                                            Rect boundingBox = face.getBoundingBox();
                                            if (isValidBoundingBox(boundingBox, mat)) {
                                                Mat faceRegion = mat.submat(new org.opencv.core.Rect(
                                                        boundingBox.left,
                                                        boundingBox.top,
                                                        boundingBox.width(),
                                                        boundingBox.height()
                                                ));

                                                Mat processedFace = preprocessFace(faceRegion);
                                                List<EmotionResult> emotions = emotionClassifier.classify(processedFace);
                                                List<EmotionResult> significantEmotions = new ArrayList<>();
                                                for (EmotionResult emotion : emotions) {
                                                    if (emotion.getConfidence() > 0.3f) {
                                                        significantEmotions.add(emotion);
                                                    }
                                                }

                                                withContext(Dispatchers.getMain(), () -> _emotionResults.postValue(significantEmotions));

                                                faceRegion.release();
                                                processedFace.release();
                                            } else {
                                                Log.w(TAG, "Invalid face bounding box: " + boundingBox);
                                            }
                                            mat.release();

                                        } catch (Exception e) {
                                            Log.e(TAG, "Error processing face: " + e.getMessage(), e);
                                            withContext(Dispatchers.getMain(), () -> _processingError.postValue("Error processing face: " + e.getMessage()));
                                        }
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Face detection failed: " + e.getMessage(), e);
                                _processingError.postValue("Face detection failed: " + e.getMessage());
                            })
                            .addOnCompleteListener(task -> imageProxy.close());
                });
            } catch (Exception e) {
                Log.e(TAG, "Image processing error: " + e.getMessage(), e);
                withContext(Dispatchers.getMain(), () -> _processingError.postValue("Image processing error: " + e.getMessage()));
                imageProxy.close();
            }
        });
    }

    private boolean isValidBoundingBox(Rect boundingBox, Mat mat) {
        return boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                boundingBox.right <= mat.cols() &&
                boundingBox.bottom <= mat.rows();
    }

    private Mat preprocessFace(Mat faceMat) {
        Mat processedMat = new Mat();
        try {
            Imgproc.resize(faceMat, processedMat, new Size(48.0, 48.0));
            Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_BGR2GRAY);
            Core.normalize(processedMat, processedMat, 0.0, 1.0, Core.NORM_MINMAX);
        } catch (Exception e) {
            Log.e(TAG, "Error preprocessing face: " + e.getMessage(), e);
            processedMat.release();
            throw e;
        }
        return processedMat;
    }

    private Bitmap toBitmap(ImageProxy imageProxy) {
        try {
            byte[] nv21 = convertImageToByteArray(imageProxy);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error converting to bitmap: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private byte[] convertImageToByteArray(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = imageProxy.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = imageProxy.getPlanes()[2];

        int ySize = yPlane.getBuffer().remaining();
        int uSize = uPlane.getBuffer().remaining();
        int vSize = vPlane.getBuffer().remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yPlane.getBuffer().get(nv21, 0, ySize);
        vPlane.getBuffer().get(nv21, ySize, vSize);
        uPlane.getBuffer().get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopDetection();
        Log.d(TAG, "ViewModel cleared");
    }
}
