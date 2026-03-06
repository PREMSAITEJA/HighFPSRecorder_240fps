package com.example.highfps;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

public class CameraPreviewView extends TextureView implements TextureView.SurfaceTextureListener {
    private PreviewCallback previewCallback;

    public CameraPreviewView(Context context) {
        super(context);
        init();
    }

    public CameraPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraPreviewView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setSurfaceTextureListener(this);
    }

    public void setPreviewCallback(PreviewCallback callback) {
        this.previewCallback = callback;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (previewCallback != null) {
            previewCallback.onPreviewReady(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void notifyPreviewUnavailable() {
        if (previewCallback != null) {
            previewCallback.onPreviewUnavailable();
        }
    }
}
