package com.bytedance.camera.demo;

import android.content.Intent;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.bytedance.camera.demo.utils.Utils.MEDIA_TYPE_IMAGE;
import static com.bytedance.camera.demo.utils.Utils.MEDIA_TYPE_VIDEO;
import static com.bytedance.camera.demo.utils.Utils.getOutputMediaFile;
import com.bytedance.camera.demo.utils.Utils;

public class CustomCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private SurfaceView mSurfaceView;
    private Camera mCamera;
    private SurfaceHolder mHolder;
    File mediaFile;
    private int BACK = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;
//    private int CAMERA_TYPE = Camera.CameraInfo.CAMERA_FACING_BACK;
//    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private int currentCameraType = 0;
    private boolean isRecording = false;

    private int rotationDegree = 0;

//    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_custom_camera);

        mSurfaceView = findViewById(R.id.img);
        mCamera = getCamera(BACK);
        //todo 给SurfaceHolder添加Callback
        mHolder=mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        findViewById(R.id.btn_picture).setOnClickListener(v -> {
                    //todo 拍一张照片
                    mCamera.takePicture(null, null, mPicture);
                });

        Button record = findViewById(R.id.btn_record);
        record.setOnClickListener(v -> {
                    //todo 录制，第一次点击是start，第二次点击是stop
                    if (isRecording) {
                        //todo 停止录制
                        mMediaRecorder.stop();
                        releaseMediaRecorder();
                        mCamera.lock();
                        record.setText("continue");
                        isRecording = false;
                    } else {
                        //todo 录制
                        if (prepareVideoRecorder()) {
                            mMediaRecorder.start();
                            record.setText("Stop");
                            isRecording = true;
                            } else {
                           releaseMediaRecorder();
                       }

                    }
                });

        findViewById(R.id.btn_facing).setOnClickListener(v -> {
                    //todo 切换前后摄像头
            try {

                changeCamera();

            } catch (IOException e) {

                // TODO Auto-generated catch block

                e.printStackTrace();

            }
                });

        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            //todo 调焦，需要判断手机是否支持

//            mCamera = getCameraInstance();
            Camera.Parameters params = mCamera.getParameters();
            if (params.getMaxNumMeteringAreas() > 0){ // check that metering areas are supported
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                Rect areaRect1 = new Rect(-100, -100, 100, 100);    // specify an area in center of image
                meteringAreas.add(new Camera.Area(areaRect1, 600)); // set weight to 60%
                Rect areaRect2 = new Rect(800, -1000, 1000, -800);  // specify an area in upper right of image
                meteringAreas.add(new Camera.Area(areaRect2, 400)); // set weight to 40%
                params.setMeteringAreas(meteringAreas);

            }
            mCamera.setParameters(params);
        });
    }
    private void changeCamera() throws IOException{
        mCamera.stopPreview();
        mCamera.release();
        if(currentCameraType == FRONT){
            mCamera = getCamera(BACK);
        }else if(currentCameraType == BACK){
            mCamera = getCamera(FRONT);
        }
        try {
            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    public Camera getCamera(int position) {
        currentCameraType = position;
        if (mCamera != null) {
            releaseCameraAndPreview();
        }
        Camera cam = Camera.open(position);

        //todo 摄像头添加属性，例是否自动对焦，设置旋转方向等
        int result = getCameraDisplayOrientation(0);
        cam.setDisplayOrientation(result);
        return cam;
    }


    private static final int DEGREE_90 = 90;
    private static final int DEGREE_180 = 180;
    private static final int DEGREE_270 = 270;
    private static final int DEGREE_360 = 360;

    private int getCameraDisplayOrientation(int cameraId) {//前面定义了//
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = DEGREE_90;
                break;
            case Surface.ROTATION_180:
                degrees = DEGREE_180;
                break;
            case Surface.ROTATION_270:
                degrees = DEGREE_270;
                break;
            default:
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % DEGREE_360;
            result = (DEGREE_360 - result) % DEGREE_360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + DEGREE_360) % DEGREE_360;
        }
        return result;
    }


    private void releaseCameraAndPreview() {
        //todo 释放camera资源
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
            }
    }

    Camera.Size size;

    private void startPreview(SurfaceHolder holder) {
        //todo 开始预览
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private MediaRecorder mMediaRecorder;

//    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean prepareVideoRecorder() {
        //todo 准备MediaRecorder
                mMediaRecorder = new MediaRecorder();
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
                mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
                mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
 //               mMediaRecorder.setOrientationHint(rotationDegree);
                try {
                    mMediaRecorder.prepare();
//                    mMediaRecorder.start();
                } catch (IOException e) {
                    releaseMediaRecorder();
                    e.printStackTrace();
                    return false;
                }
                return true;
//            }

    };


    private void releaseMediaRecorder() {
        //todo 释放MediaRecorder
        if (mMediaRecorder != null) {

            mMediaRecorder.reset();   // clear recorder configuration

            mMediaRecorder.release(); // release the recorder object

            mMediaRecorder = null;

            mCamera.lock();           // lock camera for later use

        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
//        startPreview(holder);
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
            }

        // stop preview before making changes
        try {
            mCamera.stopPreview();

        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){

        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //todo 释放Camera和MediaRecorder资源
        if (mCamera != null) {

            // Call stopPreview() to stop updating the preview surface.

            mCamera.stopPreview();
        }
        if (mMediaRecorder != null) {

            mMediaRecorder.reset();   // clear recorder configuration

            mMediaRecorder.release(); // release the recorder object

            mMediaRecorder = null;

            mCamera.lock();           // lock camera for later use

        }
    }


    private Camera.PictureCallback mPicture = (data, camera) -> {
        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.d("mPicture", "Error accessing file: " + e.getMessage());
        }
        mCamera.startPreview();
    };


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = Math.min(w, h);

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

}
