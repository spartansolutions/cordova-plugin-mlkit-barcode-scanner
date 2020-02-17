/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dealrinc.gmvScanner.ui.camera;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.images.Size;

import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
    private static final String TAG = "CameraSourcePreview";

    private Context mContext;
    private SurfaceView mSurfaceView;
    private View mViewFinderView;
    private Button mTorchButton;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;
    private boolean mFlashState = false;

    public double ViewFinderWidth;
    public double ViewFinderHeight;

    private GraphicOverlay mOverlay;

    public TextView debugText;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;

        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);

        mViewFinderView = new View(mContext);
        mViewFinderView.setBackgroundResource(getResources().getIdentifier("rounded_rectangle", "drawable", mContext.getPackageName()));
        mViewFinderView.layout(10,20, 100, 200);
        addView(mViewFinderView);

        debugText = new TextView(mContext);
        debugText.setId(555);
        debugText.layout(100, 100, 100, 100);
        // debugText.setText("test text");
        debugText.setTextSize(20);
        debugText.setTextColor(0xFF0000FF);
        addView(debugText);

        mTorchButton = new Button(mContext);
        mTorchButton.setBackgroundResource(getResources().getIdentifier("torch_inactive", "drawable", mContext.getPackageName()));
        mTorchButton.layout(0,0, dpToPx(45),dpToPx(45));
        mTorchButton.setMaxWidth(50);
        mTorchButton.setRotation(90);

        mTorchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mCameraSource.setFlashMode(!mFlashState ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                    mFlashState = !mFlashState;
                    mTorchButton.setBackgroundResource(getResources().getIdentifier(mFlashState ? "torch_active" : "torch_inactive", "drawable", mContext.getPackageName()));
                } catch(Exception e) {

                }
            }
        });


        addView(mTorchButton);
    }

    public int dpToPx(int dp) {
        float density = mContext.getResources()
                .getDisplayMetrics()
                .density;
        return Math.round((float) dp * density);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource cameraSource) throws IOException, SecurityException {
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;

        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource cameraSource, GraphicOverlay overlay) throws IOException, SecurityException {
        mOverlay = overlay;
        start(cameraSource);
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {
        if (mStartRequested && mSurfaceAvailable) {
            mCameraSource.start(mSurfaceView.getHolder());
            if (mOverlay != null) {
                Size size = mCameraSource.getPreviewSize();
                int min = Math.min(size.getWidth(), size.getHeight());
                int max = Math.max(size.getWidth(), size.getHeight());
                if (isPortraitMode()) {
                    // Swap width and height sizes when in portrait, since it will be rotated by
                    // 90 degrees
                    mOverlay.setCameraInfo(min, max, mCameraSource.getCameraFacing());
                } else {
                    mOverlay.setCameraInfo(max, min, mCameraSource.getCameraFacing());
                }
                mOverlay.clear();
            }
            mStartRequested = false;
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            try {
                startIfReady();
            } catch (SecurityException se) {
                Log.e(TAG,"Do not have permission to start the camera", se);
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = 320;
        int height = 240;

        // debugText.setText("left: " + left + " top: " + top + " right: " + right + " bottom: " + bottom);

        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            debugText.setText(mCameraSource.getPreviewSize().toString());
            if (size != null) {
                width = size.getWidth();
                height = size.getHeight();
            }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            int tmp = width;
            //noinspection SuspiciousNameCombination
            width = height;
            height = tmp;
        }

        final int layoutWidth = right - left;
        final int layoutHeight = bottom - top;

        // Computes height and width for potentially doing fit width.
        int childWidth = layoutWidth;
        int childHeight = (int)(((float) layoutWidth / (float) width) * height);
        int offsetX = 0;
        int offsetY = (int)((float)layoutHeight - (float)childHeight)/2;

        // If height is too tall using fit width, does fit height instead.
        if (childHeight > layoutHeight) {
            childHeight = layoutHeight;
            childWidth = (int)(((float) layoutHeight / (float) height) * width);
            offsetX = (int)((float)layoutWidth - (float)childWidth)/2;
            offsetY = 0;
        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(offsetX, offsetY, childWidth, childHeight);
        }

        // TODO
        // mViewFinderView.layout(layoutWidth/2 -actualWidth/2,layoutHeight/2 - actualHeight/2, layoutWidth/2 + actualWidth/2, layoutHeight/2 + actualHeight/2);

        int buttonSize = dpToPx(45);
        int torchLeft = layoutWidth - (buttonSize * 2);
        int torchTop = layoutHeight - (buttonSize * 2);

        mTorchButton.layout(torchLeft, torchTop, torchLeft + buttonSize, torchTop + buttonSize);

        debugText.layout(100, 100, 1000, 1000);

        try {
            startIfReady();
        } catch (SecurityException se) {
            Log.e(TAG,"Do not have permission to start the camera", se);
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }

    private boolean isPortraitMode() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        Log.d(TAG, "isPortraitMode returning false by default");
        return false;
    }
}
