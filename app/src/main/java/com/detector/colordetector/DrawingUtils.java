package com.detector.colordetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class DrawingUtils {

    private Canvas canvas = new Canvas();
    private Paint paint = new Paint();
    private Paint backgroundPaint = new Paint();
    private final int PADDING = 20;
    public static final Scalar WHITE_COLOR = new Scalar(255, 255, 255);
    public static final Scalar BLACK_COLOR = new Scalar(0,0,0);
    private String color_name = "";

    protected DrawingUtils() {
        // Set up the paint with desired attributes
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(80);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setAntiAlias(true);

        backgroundPaint.setColor(android.graphics.Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setNewColorName(String name) {
        color_name = name;
    }
    public void drawText(Mat mRgba) {
        // Create a bitmap from the Mat object
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        // Reset the canvas to draw on the bitmap
        canvas.setBitmap(bitmap);

        // Calculate the size of the text
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(color_name, 0, color_name.length(), textBounds);

        // Calculate the position for the text and background
        float x = (bitmap.getWidth() - textBounds.width()) / 2;
        float y = textBounds.height() + 20; // 20 pixels padding from the top

        // Add padding to the rectangle
        float rectLeft = x - PADDING;
        float rectTop = y - textBounds.height() - PADDING;
        float rectRight = x + textBounds.width() + PADDING;
        float rectBottom = y + PADDING;

        // Draw a white rectangle behind the text
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, backgroundPaint);

        // Draw the text on the canvas
        canvas.drawText(color_name, x, y, paint);

        // Convert the bitmap back to a Mat object
        Utils.bitmapToMat(bitmap, mRgba);
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
