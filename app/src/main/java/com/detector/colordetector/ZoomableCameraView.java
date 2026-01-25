package com.detector.colordetector;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCameraView;

public class ZoomableCameraView extends JavaCameraView {

    public ZoomableCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setZoom(float zoomFactor) {
        if (mCamera == null) return;

        Camera.Parameters params = mCamera.getParameters();
        if (!params.isZoomSupported()) return;

        int maxZoom = params.getMaxZoom();
        // zoomFactor is between 1.0 and maxZoomValue (e.g. 10.0)
        // We map it to the range [0, maxZoom]
        int zoomValue = (int) ((zoomFactor - 1.0f) * maxZoom / 9.0f); 
        zoomValue = Math.max(0, Math.min(zoomValue, maxZoom));

        params.setZoom(zoomValue);
        mCamera.setParameters(params);
    }
}
