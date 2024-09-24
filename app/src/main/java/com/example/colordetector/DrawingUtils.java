package com.example.colordetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class DrawingUtils {

    public static void drawText(Mat mRgba, String name) {
        // Create a bitmap from the Mat object
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        // Create a canvas to draw on the bitmap
        Canvas canvas = new Canvas(bitmap);

        // Set up the paint with desired attributes
        Paint paint = new Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(80);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setAntiAlias(true);

        // Calculate the size of the text
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(name, 0, name.length(), textBounds);

        // Calculate the position for the text and background
        float x = (bitmap.getWidth() - textBounds.width()) / 2;
        float y = textBounds.height() + 20; // 20 pixels padding from the top

        // Add padding to the rectangle
        int padding = 20;
        float rectLeft = x - padding;
        float rectTop = y - textBounds.height() - padding;
        float rectRight = x + textBounds.width() + padding;
        float rectBottom = y + padding;

        // Draw a white rectangle behind the text
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(android.graphics.Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, backgroundPaint);

        // Draw the text on the canvas
        canvas.drawText(name, x, y, paint);

        // Convert the bitmap back to a Mat object
        Utils.bitmapToMat(bitmap, mRgba);
    }

    public static Rect drawRectangles(Mat mRgba) {
        int frameWidth = mRgba.cols();
        int frameHeight = mRgba.rows();

        // Define the size of the rectangles relative to the frame size
        int blackRectWidth = frameWidth / 4;
        int blackRectHeight = frameHeight / 4;
        int whiteRectWidth = blackRectWidth + 50;
        int whiteRectHeight = blackRectHeight + 50;
        int thickness = 25;

        Point centerPoint = new Point(frameWidth * 0.5, frameHeight * 0.5);

        // Update the black rectangle based on the new size
        Rect blackRect = new Rect((int) (centerPoint.x - blackRectWidth * 0.5), (int) (centerPoint.y - blackRectHeight * 0.5), blackRectWidth, blackRectHeight);

        // Define the white rectangle based on the black rectangle
        Rect whiteRect = new Rect((int) (centerPoint.x - whiteRectWidth * 0.5), (int) (centerPoint.y - whiteRectHeight * 0.5), whiteRectWidth, whiteRectHeight);

        // Draw the rectangles on the frame
        Imgproc.rectangle(mRgba, whiteRect, new Scalar(255, 255, 255), thickness);
        Imgproc.rectangle(mRgba, blackRect, new Scalar(0, 0, 0), thickness);
        return blackRect;
    }
}