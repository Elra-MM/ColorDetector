package com.detector.colordetector;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCamera2View;

public class ZoomableCameraView extends JavaCamera2View {
    private static final String TAG = "ZoomableCameraView";

    public ZoomableCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the zoom level using the Camera2 crop region.
     * @param zoomFactor The zoom level (1.0 to max)
     * @param sensorRect The active array size of the sensor
     */
    public void setZoom(float zoomFactor, Rect sensorRect) {
        if (mCameraDevice == null || sensorRect == null || mPreviewRequestBuilder == null) return;

        // Calculate the crop region based on the zoom factor
        int centerX = sensorRect.width() / 2;
        int centerY = sensorRect.height() / 2;
        int deltaX = (int) (0.5f * sensorRect.width() / zoomFactor);
        int deltaY = (int) (0.5f * sensorRect.height() / zoomFactor);

        Rect cropRect = new Rect(
                centerX - deltaX,
                centerY - deltaY,
                centerX + deltaX,
                centerY + deltaY
        );

        try {
            // Update the preview request with the new zoom (crop region)
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRect);
            if (mCaptureSession != null) {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "Failed to update zoom", e);
        }
    }
}
