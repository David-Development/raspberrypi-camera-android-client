package de.luhmer.livestreaming.mjpeg;

import android.graphics.Bitmap;

public interface OnFrameCapturedListener {
    void onFrameCaptured(Bitmap bitmap);
}