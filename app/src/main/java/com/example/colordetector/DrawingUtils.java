package com.example.colordetector;

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
    private final int RECTANGLE_THICKNESS = 25;
    private static final int RECT_WIDTH = 250; // Fixed width in pixels
    private static final int RECT_HEIGHT = 250; // Fixed height in pixels

    protected DrawingUtils() {
        // Set up the paint with desired attributes
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(80);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setAntiAlias(true);

        backgroundPaint.setColor(android.graphics.Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void drawText(Mat mRgba, String name) {
        // Create a bitmap from the Mat object
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        // Reset the canvas to draw on the bitmap
        canvas.setBitmap(bitmap);

        // Calculate the size of the text
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(name, 0, name.length(), textBounds);

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
        canvas.drawText(name, x, y, paint);

        // Convert the bitmap back to a Mat object
        Utils.bitmapToMat(bitmap, mRgba);
    }

    public Rect drawRectangles(Mat frame) {
        int width = frame.width();
        int height = frame.height();

        int whiteRectWidth = RECT_WIDTH + 50;
        int whiteRectHeight = RECT_HEIGHT + 50;

        Point centerPoint = new Point(width * 0.5, height * 0.5);
        Rect blackRect = getRect(centerPoint, RECT_WIDTH, RECT_HEIGHT);
        Rect whiteRect = getRect(centerPoint, whiteRectWidth, whiteRectHeight);

        // Draw the rectangles on the frame
        Imgproc.rectangle(frame, whiteRect, new Scalar(255, 255, 255), RECTANGLE_THICKNESS);
        Imgproc.rectangle(frame, blackRect, new Scalar(0, 0, 0), RECTANGLE_THICKNESS);
        return blackRect;
    }

    private static @NonNull Rect getRect(Point centerPoint, int width, int height) {
        Point borderTopLeft = new Point(centerPoint.x - width * 0.5, centerPoint.y - height * 0.5);
        return new Rect(borderTopLeft, new Size(width, height));
    }


}
