package com.gtek.fren.ui.home;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetector;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

@OptIn(markerClass = ExperimentalGetImage.class)
public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private static final float EMOTION_CONFIDENCE_THRESHOLD = 0.3f;
    private final ExecutorService executorService;
    private final EmotionClassifier emotionClassifier;
    private final MutableLiveData<List<EmotionClassifier.EmotionResult>> emotionResults;
    private final MutableLiveData<String> processingError;
    private boolean isDetecting = true;

    public ImageProcessor(ExecutorService executorService, EmotionClassifier emotionClassifier, MutableLiveData<List<EmotionClassifier.EmotionResult>> emotionResults, MutableLiveData<String> processingError) {
        this.executorService = executorService;
        this.emotionClassifier = emotionClassifier;
        this.emotionResults = emotionResults;
        this.processingError = processingError;
    }

    public void setIsDetecting(boolean isDetecting) {
        this.isDetecting = isDetecting;
    }

    public void processImageWithFaceDetection(final ImageProxy imageProxy, final FaceDetector faceDetector) {
        if (!isDetecting) {
            imageProxy.close();
            return;
        }


        executorService.execute(() -> {
            try {
                if (!isValidImageProxy(imageProxy)) {
                    imageProxy.close();
                    return;
                }

                InputImage inputImage = InputImage.fromMediaImage(Objects.requireNonNull(imageProxy.getImage()), imageProxy.getImageInfo().getRotationDegrees());

                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> handleDetectedFaces(faces, imageProxy))
                        .addOnFailureListener(this::handleFaceDetectionFailure)
                        .addOnCompleteListener(task -> imageProxy.close());
            } catch (Exception e) {
                handleImageProcessingError(e, imageProxy);
            }
        });
    }

    private boolean isValidImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null || imageProxy.getPlanes().length < 3) {
            Log.e(TAG, "Skipping frame - ImageProxy is incomplete or null");
            return false;
        }
        return true;
    }

    private void handleDetectedFaces(List<Face> faces, ImageProxy imageProxy) {
        if (faces.isEmpty()) {
            Log.d(TAG, "No faces detected");
            emotionResults.postValue(new ArrayList<>());
            return;
        }

        executorService.execute(() -> {
            try {
                Bitmap bitmap = toBitmap(imageProxy);
                Mat fullImageMat = new Mat();
                Utils.bitmapToMat(Objects.requireNonNull(bitmap), fullImageMat);

                List<EmotionClassifier.EmotionResult> allEmotions = new ArrayList<>();
                for (Face face : faces) {
                    List<EmotionClassifier.EmotionResult> faceEmotions = processSingleFace(face, fullImageMat);
                    allEmotions.addAll(faceEmotions);
                }
                emotionResults.postValue(allEmotions);
                fullImageMat.release();

            } catch (Exception e) {
                Log.e(TAG, "Error processing faces: " + e.getMessage(), e);
                processingError.postValue("Error processing faces:: " + e.getMessage());
            }
        });
    }

    private List<EmotionClassifier.EmotionResult> processSingleFace(Face face, Mat fullImageMat) {
        Rect boundingBox = face.getBoundingBox();
        List<EmotionClassifier.EmotionResult> significantEmotions = new ArrayList<>();
        if (isValidBoundingBox(boundingBox, fullImageMat)) {
            org.opencv.core.Rect opencvRect = new org.opencv.core.Rect(
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.width(),
                    boundingBox.height()
            );
            Mat faceRegion = fullImageMat.submat(opencvRect);
            Mat processedFace = preprocessFace(faceRegion);
            List<EmotionClassifier.EmotionResult> emotions = emotionClassifier.classify(processedFace);
            for (EmotionClassifier.EmotionResult emotion : emotions) {
                if (emotion.getConfidence() > EMOTION_CONFIDENCE_THRESHOLD) {
                    significantEmotions.add(emotion);
                }
            }
            faceRegion.release();
            processedFace.release();
        } else {
            Log.w(TAG, "Invalid face bounding box: " + boundingBox);
        }
        return significantEmotions;
    }

    private void handleFaceDetectionFailure(Exception e) {
        Log.e(TAG, "Face detection failed: " + e.getMessage(), e);
        processingError.postValue("Face detection failed: " + e.getMessage());
    }

    private void handleImageProcessingError(Exception e, ImageProxy imageProxy) {
        Log.e(TAG, "Image processing error: " + e.getMessage(), e);
        processingError.postValue("Image processing error: " + e.getMessage());
        imageProxy.close();
    }

    private boolean isValidBoundingBox(Rect boundingBox, Mat mat) {
        return boundingBox.left >= 0 && boundingBox.top >= 0 &&
                boundingBox.right <= mat.width() && boundingBox.bottom <= mat.height() &&
                boundingBox.width() > 0 && boundingBox.height() > 0;
    }

    private Mat preprocessFace(Mat faceRegion) {
        Mat grayFace = new Mat();
        Imgproc.cvtColor(faceRegion, grayFace, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(grayFace, grayFace);
        Size targetSize = new Size(48, 48);
        Imgproc.resize(grayFace, grayFace, targetSize);
        return grayFace;
    }

    private Bitmap toBitmap(ImageProxy imageProxy) {
        try {
            byte[] nv21 = convertImageToByteArray(imageProxy);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: " + e.getMessage(), e);
            processingError.postValue("Error processing image: " + e.getMessage());
            return null;
        }
    }

    private byte[] convertImageToByteArray(ImageProxy imageProxy) {
        imageProxy.getPlanes();
        if (imageProxy.getPlanes().length < 3) {
            throw new IllegalArgumentException("ImageProxy planes are null or insufficient");
        }

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
}