package com.example.colordetector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ColorCalculator colorCalculator;
    Window window;

    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!initOpenCV()) return;

        setContentView(R.layout.activity_main);
        window = getWindow();
        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        askCameraPermission();

        colorCalculator = new ColorCalculator(getAssets());
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
        ses.scheduleWithFixedDelay(() -> {
            colorCalculator.computeNewName();
        }, 2, 1, TimeUnit.SECONDS);
    }

    private boolean initOpenCV() {
        if (OpenCVLoader.initLocal())
        {
            Log.i("Main", "OpenCV init success");
            return true;
        }
        else {
            Log.e("Main", "OpenCV init failed");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return false;
        }
    }

    private void askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            if (isCameraAvailable()) {
                initializeCamera();
            } else {
                Toast.makeText(this, "It seems that your device does not support camera (or it is locked). Application will be closed.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean isCameraAvailable() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isCameraAvailable()) {
                    initializeCamera();
                } else {
                    Toast.makeText(this, "It seems that your device does not support camera (or it is locked). Application will be closed.", Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        Rect blackRect = DrawingUtils.drawRectangles(mRgba);
        Mat sub = mRgba.submat(blackRect);

        colorCalculator.setNewFrame(sub);

        DrawingUtils.drawText(mRgba, colorCalculator.getMedianName());
        return mRgba;
    }
}
