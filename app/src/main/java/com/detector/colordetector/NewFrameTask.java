package com.detector.colordetector;

import android.os.Handler;

import org.opencv.core.Mat;

public class NewFrameTask implements Runnable {
    private ColorCalculator colorCalculator;
    private DrawingUtils drawingUtils;
    private Mat sub;
    private Handler handler;

    public NewFrameTask(ColorCalculator colorCalculator, DrawingUtils drawingUtils, Mat sub, Handler handler) {
        this.colorCalculator = colorCalculator;
        this.drawingUtils = drawingUtils;
        this.sub = sub;
        this.handler = handler;
    }

    @Override
    public void run() {
        colorCalculator.addNewMat(sub);
        handler.post(() -> drawingUtils.setNewColorName(colorCalculator.getMedianName()));
    }
}
