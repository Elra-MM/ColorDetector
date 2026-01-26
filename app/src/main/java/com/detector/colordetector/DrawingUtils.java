package com.detector.colordetector;

import androidx.annotation.NonNull;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class DrawingUtils {
    public static final Scalar WHITE_COLOR = new Scalar(255, 255, 255);
    public static final Scalar BLACK_COLOR = new Scalar(0, 0, 0);

    protected DrawingUtils() {
    }

    public int getDetectionSquareSize(Mat mRgba) {
        int frameWidth = mRgba.cols();
        int frameHeight = mRgba.rows();
        return Math.min(frameWidth, frameHeight) / 10;
    }

    public Rect getDetectionSquare(Mat mRgba, int detectionSquareSize) {
        return getSquare(getCenterPoint(mRgba), detectionSquareSize);
    }

    public Point getCenterPoint(Mat mRgba) {
        int frameWidth = mRgba.cols();
        int frameHeight = mRgba.rows();

        return new Point(frameWidth * 0.5, frameHeight * 0.5);
    }

    /**
     * Draws a single dashed circle alternating between black and white colors.
     */
    public void drawDetectionCircle(Mat mRgba, int detectionSquareSize) {
        Point centerPoint = getCenterPoint(mRgba);
        int thickness = Math.min(mRgba.cols(), mRgba.rows()) / 100 + 5;
        
        // Radius slightly larger than the detection square to encompass it
        int radius = detectionSquareSize + 20;
        Size axes = new Size(radius, radius);

        // Draw the dashed circle using small arcs (ellipses)
        int dashStep = 10; // degrees for each segment
        for (int angle = 0; angle < 360; angle += dashStep) {
            Scalar color = (angle / dashStep % 2 == 0) ? WHITE_COLOR : BLACK_COLOR;
            Imgproc.ellipse(mRgba, centerPoint, axes, 0, angle, angle + dashStep, color, thickness);
        }
    }

    private static @NonNull Rect getSquare(Point centerPoint, int size) {
        Point borderTopLeft = new Point(centerPoint.x - size * 0.5, centerPoint.y - size * 0.5);
        return new Rect(borderTopLeft, new Size(size, size));
    }
}
