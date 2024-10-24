package com.detector.colordetector;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ColorCalculator colorCalculator;
    private DrawingUtils drawingUtils;
    private Window window;
    private ExecutorService executorServiceComputeMedians;

    private ScheduledExecutorService scheduledExecutorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initOpenCV();

        setContentView(R.layout.activity_main);
        window = getWindow();

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        askCameraPermission();

        colorCalculator = new ColorCalculator(getAssets(), findViewById(R.id.color_txt));
        drawingUtils = new DrawingUtils();

        scheduleComputationOfNewName();

        executorServiceComputeMedians = Executors.newFixedThreadPool(1);

        Button privacyPolicyButton = findViewById(R.id.button_privacy_policy);
        privacyPolicyButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PrivacyPolicyActivity.class);
            startActivity(intent);
        });
    }

    private void scheduleComputationOfNewName() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown() && !scheduledExecutorService.isTerminated())
            return;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                colorCalculator.computeNewName();
            } catch (Exception e) {
                Log.e(TAG, "Error in the scheduled task", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void initOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV init success");
        } else {
            Log.e(TAG, "OpenCV init failed");
            showPopup("opencv_init_failed");
        }
    }

    private void askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (isCameraAvailable()) {
                initializeCamera();
                Log.d(TAG, "Permissions granted");
            } else {
                Log.d(TAG, "It seems that your device does not support camera (or it is locked). Application will be closed.");
                showPopup("camera_not_available");
            }
        } else {
//            OpenCV already call the request permission
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private boolean isCameraAvailable() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.d(TAG, "Camera ID: " + cameraId + " is available and facing back");
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isCameraAvailable()) {
                    initializeCamera();
                    Log.d(TAG, "Permissions granted");
                } else {
                    Log.e(TAG, "It seems that your device does not support camera (or it is locked). Application will be closed.");
                    showPopup("camera_not_available");
                }
            } else {
                Log.e(TAG, "else show popup , grantResults.length: " + grantResults.length);
                showPopup("camera_not_granted");
            }
        }
    }

    private void initializeCamera() {
        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.disableFpsMeter();
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        scheduledExecutorService.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        askCameraPermission();
        scheduleComputationOfNewName();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        scheduledExecutorService.shutdown();
        executorServiceComputeMedians.shutdown();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        scheduleComputationOfNewName();
    }

    @Override
    public void onCameraViewStopped() {
        scheduledExecutorService.shutdown();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        int detectionSquareSize = drawingUtils.getDetectionSquareSize(mRgba);
        Rect detectionSquare = drawingUtils.getDetectionSquare(mRgba, detectionSquareSize);
        drawingUtils.drawSquares(mRgba, detectionSquareSize);

        Mat sub = mRgba.submat(detectionSquare);

        if (executorServiceComputeMedians.isTerminated()) {
            executorServiceComputeMedians = Executors.newFixedThreadPool(1);
            executorServiceComputeMedians.submit(() -> colorCalculator.computeNewMedian(sub));

        }
        executorServiceComputeMedians.shutdown();
        return mRgba;
    }

    private void showPopup(String msgKey) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup);

        String msg = getString(getResources().getIdentifier(msgKey, "string", getPackageName()));
        dialog.<TextView>findViewById(R.id.popup_txt).setText(msg);

        View btn = dialog.findViewById(R.id.popup_btn);
        btn.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }
}
