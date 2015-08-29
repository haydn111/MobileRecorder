package com.android.mobilerecorder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraRecorderService extends Service {
	private static final String TAG = "CameraRecorder";
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private Camera mServiceCamera;
	private boolean mRecordingStatus;
	private MediaRecorder mMediaRecorder;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mRecordingStatus = false;
		mServiceCamera = MobileRecorder.mCamera;
		mSurfaceView = MobileRecorder.mSurfaceView;
		mSurfaceHolder = MobileRecorder.mSurfaceHolder;
		mMediaRecorder = new MediaRecorder();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		if (!mRecordingStatus)
			startRecording();

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopRecording();
		mRecordingStatus = false;
	}

	public boolean startRecording() {
		try {
			int cameraCount = 0;
			int camIdx = 0;
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			cameraCount = Camera.getNumberOfCameras();
			for (camIdx = 0; camIdx < cameraCount; camIdx++) {
				Camera.getCameraInfo(camIdx, cameraInfo);
				if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					mServiceCamera = Camera.open(camIdx);
					break;
				}
			}

			// only called when no front facing camera available
			if (mServiceCamera == null) {
				mServiceCamera = Camera.open();
			}

			List<Size> pictureSizes = mServiceCamera.getParameters().getSupportedPictureSizes();
			List<Size> previewSizes = mServiceCamera.getParameters().getSupportedPreviewSizes();

			for (int i = 0; i < pictureSizes.size(); i++) {
				Log.e(TAG, i + ". pic size = (" + pictureSizes.get(i).width + ", " + pictureSizes.get(i).height + ")");
			}

			for (int i = 0; i < previewSizes.size(); i++) {
				Log.e(TAG, i + ". pre size = (" + previewSizes.get(i).width + ", " + previewSizes.get(i).height + ")");
			}

			Camera.Parameters cameraProfile = mServiceCamera.getParameters();

			Size mSize = previewSizes.get(0);
			boolean matchFound = false;
			for (int i = 0; !matchFound && i < pictureSizes.size(); i++) {
				for (int j = 0; !matchFound && j < previewSizes.size(); j++) {
					if (pictureSizes.get(i).width == previewSizes.get(j).width &&
							pictureSizes.get(i).height == previewSizes.get(j).height) {
						mSize = pictureSizes.get(i);
						matchFound = true;
					}
				}
			}
/*			for(int i = 0; i < pictureSizes.size(); i++) {
				if(pictureSizes.get(i).width > mPictureSize.width)
					mPictureSize = pictureSizes.get(i);
			}*/
			Log.e(TAG, "pic size = (" + mSize.width + ", " + mSize.height + ")");
			cameraProfile.setPictureSize(mSize.width, mSize.height);
			cameraProfile.setPreviewSize(mSize.width, mSize.height);
//			cameraProfile.setPictureFormat(PixelFormat.JPEG);
//			cameraProfile.set("jpeg-quality", 100);
			cameraProfile.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
			cameraProfile.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
//			cameraProfile.setPreviewFormat(PixelFormat.YCbCr_420_SP);

			mServiceCamera.setParameters(cameraProfile);
			mServiceCamera.setPreviewDisplay(mSurfaceHolder);
			mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
			mServiceCamera.startPreview();
			mServiceCamera.unlock();
			mMediaRecorder.setCamera(mServiceCamera);
			// Use a system camera profile instead of setting specific parameters to improve compatibility
			CamcorderProfile recorderProfile = CamcorderProfile.get(camIdx, CamcorderProfile.QUALITY_HIGH);
//			mMediaRecorder.setVideoEncodingBitRate(6000000);
			mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			mMediaRecorder.setProfile(recorderProfile);
//			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//			mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//			mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
			mMediaRecorder.setOutputFile(MobileRecorder.VIDEO_PATH + File.separator + MobileRecorder.cameraFileName);
//			mMediaRecorder.setVideoFrameRate(30);
//			mMediaRecorder.setVideoSize(mSize.width, mSize.height);
			mMediaRecorder.prepare();
			mMediaRecorder.start();
			mRecordingStatus = true;
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Camera Recording start failed");
			e.printStackTrace();
			return false;
		}
	}

	public void stopRecording() {
		if (mMediaRecorder != null) {
			mMediaRecorder.setOnErrorListener(null);
			mMediaRecorder.setPreviewDisplay(null);
			try {
				mServiceCamera.reconnect();
				mMediaRecorder.stop();
				mMediaRecorder.reset();
				mServiceCamera.stopPreview();
				mMediaRecorder.release();
				mServiceCamera.release();
				mServiceCamera = null;
				mRecordingStatus = false;
			} catch (IOException e) {
				e.printStackTrace();
			}/* catch (RuntimeException e) {
				Log.e(TAG, "Stopped failed exception caught");
			}*/
		}
	}
}
