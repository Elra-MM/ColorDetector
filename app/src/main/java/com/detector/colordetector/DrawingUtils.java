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
    public static final Scalar BLACK_COLOR = new Scalar(0,0,0);

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

    //We draw 2 squares bigger than the detection square in UX reasons (better precision)
    public void drawSquares(Mat mRgba, int detectionSquareSize) {
        Point centerPoint = getCenterPoint(mRgba);
        int thickness = Math.min(mRgba.cols(), mRgba.rows()) / 100 + 10;

        Rect blackSquare = getSquare(centerPoint, detectionSquareSize + 70);
        drawSquare(mRgba, blackSquare, BLACK_COLOR, thickness);

        Rect whiteSquare = getSquare(centerPoint, detectionSquareSize + 110);
        drawSquare(mRgba, whiteSquare, WHITE_COLOR, thickness);
    }

    private void drawSquare(Mat mRgba, Rect square, Scalar color, int thickness) {
        Imgproc.rectangle(mRgba, square, color, thickness);
    }

    private static @NonNull Rect getSquare(Point centerPoint, int size) {
        Point borderTopLeft = new Point(centerPoint.x - size * 0.5, centerPoint.y - size * 0.5);
        return new Rect(borderTopLeft, new Size(size, size));
    }
}
