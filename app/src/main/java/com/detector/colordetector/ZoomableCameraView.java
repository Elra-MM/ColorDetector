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

    public void setZoom(float zoomFactor, float maxZoomValue) {
        if (mCamera == null) return;

        Camera.Parameters params = mCamera.getParameters();
        if (!params.isZoomSupported()) return;

        int maxZoomIdx = params.getMaxZoom();
        
        // Map zoomFactor (1.0 to maxZoomValue) to [0, maxZoomIdx]
        float ratio = (zoomFactor - 1.0f) / (maxZoomValue - 1.0f);
        int zoomValue = Math.round(ratio * maxZoomIdx);
        
        zoomValue = Math.max(0, Math.min(zoomValue, maxZoomIdx));

        if (params.getZoom() != zoomValue) {
            params.setZoom(zoomValue);
            mCamera.setParameters(params);
        }
    }
}
