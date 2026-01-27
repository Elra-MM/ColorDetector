package com.detector.colordetector;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class DrawingUtils {
    public static final Scalar WHITE_COLOR = new Scalar(255, 255, 255);
    public static final Scalar BLACK_COLOR = new Scalar(0, 0, 0);

    public static int getRadius(Mat mRgba) {
        return Math.min(mRgba.cols(), mRgba.rows()) / 10;
    }

    public static Point getCenter(Mat mRgba) {
        return new Point(mRgba.cols() * 0.5, mRgba.rows() * 0.5);
    }

    public static Rect getDetectionRect(Mat mRgba) {
        int radius = getRadius(mRgba);
        Point center = getCenter(mRgba);
        return new Rect(
            (int) (center.x - radius),
            (int) (center.y - radius),
            radius * 2,
            radius * 2
        );
    }

    public static void drawDashedCircle(Mat mRgba) {
        Point center = getCenter(mRgba);
        int radius = getRadius(mRgba);
        int thickness = Math.max(2, Math.min(mRgba.cols(), mRgba.rows()) / 100);
        
        Size axes = new Size(radius, radius);
        int dashStep = 10;
        for (int angle = 0; angle < 360; angle += dashStep) {
            Scalar color = (angle / dashStep % 2 == 0) ? WHITE_COLOR : BLACK_COLOR;
            Imgproc.ellipse(mRgba, center, axes, 0, angle, angle + dashStep, color, thickness);
        }
    }
}
