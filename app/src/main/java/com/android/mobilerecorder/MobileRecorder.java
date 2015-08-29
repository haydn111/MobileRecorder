package com.android.mobilerecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MobileRecorder extends Activity implements SurfaceHolder.Callback {
	private static final String TAG = "MobileRecorder";
	public static final String VIDEO_PATH = Environment.getExternalStorageDirectory().getPath() + File.separator + "MobileRecorder";
	public static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd_hh:mm:ss", Locale.getDefault());
	public static String cameraFileName, screenFileName, fileName;
	public static boolean orientation = true;	// true = portrait, false = landscape;
	public static boolean combineOnStop = false;


	private CheckBox checkBox;

	public static SurfaceView mSurfaceView;
	public static SurfaceHolder mSurfaceHolder;
	public static Camera mCamera = null;

	private static final int REQUEST_CODE = 1;
	private MediaProjectionManager mMediaProjectionManager;
	private ScreenRecorder mScreenRecorder;
	private boolean recordingStatus = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		File dir = new File(VIDEO_PATH);
		dir.mkdir();

		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
		
		Button btnStart = (Button) findViewById(R.id.StartService);
		btnStart.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!recordingStatus) {
					// A dialog box will popup to let user agree with screen recording
					Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
					startActivityForResult(captureIntent, REQUEST_CODE);
				}
			}
		});

		Button btnStop = (Button) findViewById(R.id.StopService);
		btnStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (recordingStatus) {
					// stop camera recording
					stopService(new Intent(MobileRecorder.this, CameraRecorderService.class));
//					stopService(new Intent(MobileRecorder.this, CameraRecorder.class));

					// stop screen recording
					if (mScreenRecorder != null) {
						mScreenRecorder.quit();
						mScreenRecorder = null;
					}

					Toast.makeText(v.getContext(), getResources().getString(R.string.recording_stopped), Toast.LENGTH_SHORT).show();
					recordingStatus = false;

					// start to generate combined video upon user request
					if (combineOnStop) {
						checkBox.setChecked(false);
						combineOnStop = false;
						Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							public void run() {
								Intent combiningIntent = new Intent(MobileRecorder.this, GenerateCombinedVideo.class);
								combiningIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								startService(combiningIntent);
								Log.e(TAG, "Generating service should start.");
							}
						}, 1000);
					}
				}
			}
		});

		checkBox = (CheckBox) findViewById(R.id.checkBox);
		checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
				if (isChecked) {
					new AlertDialog.Builder(buttonView.getContext())
							.setIcon(android.R.drawable.ic_dialog_alert)
							.setTitle(getResources().getString(R.string.alert_dialog_title))
							.setMessage(getResources().getString(R.string.alert_dialog_content))
							.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									combineOnStop = false;
									checkBox.setChecked(false);
								}
							})
							.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									combineOnStop = true;
								}
							})
							.create().show();
				} else {
					combineOnStop = false;
				}
				Log.e(TAG, "Checkbox = " + isChecked + ", combineOnStop = " + combineOnStop);
			}
		});

		Button btnAbout = (Button) findViewById(R.id.about);
		btnAbout.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				final TextView message = new TextView(v.getContext());
				final SpannableString s =
						new SpannableString(v.getContext().getText(R.string.about_txt));
				Linkify.addLinks(s, Linkify.WEB_URLS);
				message.setText(s);
				message.setMovementMethod(LinkMovementMethod.getInstance());
				message.setPadding(55, 60, 60, 10);

				new AlertDialog.Builder(v.getContext())
						.setTitle(R.string.about_title)
						.setCancelable(true)
						.setIcon(android.R.drawable.ic_dialog_info)
						.setPositiveButton(android.R.string.ok, null)
						.setView(message)
						.create().show();
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
		if (mediaProjection == null) {
			Log.e(TAG, "media projection is null");
			return;
		}

		// prepare to start
		fileName = timeFormat.format(new Date(System.currentTimeMillis())).replace('/', '-').replace(' ', '-');
		if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
			orientation = true;	// portrait
			cameraFileName = fileName + ".camera_portrait";
			screenFileName = fileName + ".screen_portrait";
		}
		else {
			orientation = false;	// landscape
			cameraFileName = fileName + ".camera_landscape";
			screenFileName = fileName + ".screen_landscape";
		}

		// start camera recording service
//		Intent cameraIntent = new Intent(MobileRecorder.this, CameraRecorderService.class);
//		cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		startService(cameraIntent);

		Thread recordThread = new Thread(){
			public void run(){
				Intent cameraIntent = new Intent(MobileRecorder.this, CameraRecorderService.class);
				cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startService(cameraIntent);
			}
		};
		recordThread.start();

//		Intent intent = new Intent(MobileRecorder.this, CameraRecorder.class);
//		intent.putExtra(CameraRecorder.INTENT_VIDEO_PATH, VIDEO_PATH); //eg: "/video/camera/"
//		startService(intent);

		// configure and start screen recording
		// Get screen size as required image dimensions
		DisplayMetrics dm = getBaseContext().getResources().getDisplayMetrics();
		float screenWidth = dm.widthPixels;
		float screenHeight = dm.heightPixels;
		File file = new File(VIDEO_PATH, File.separator + screenFileName);
		final int bitrate = 6000000;
		mScreenRecorder = new ScreenRecorder((int)screenWidth, (int)screenHeight, bitrate, 1, mediaProjection, file.getAbsolutePath());
		mScreenRecorder.start();
		Toast.makeText(this, getResources().getString(R.string.recording), Toast.LENGTH_SHORT).show();
		recordingStatus = true;
		moveTaskToBack(true);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(mScreenRecorder != null){
			mScreenRecorder.quit();
			mScreenRecorder = null;
		}

		stopService(new Intent(MobileRecorder.this, CameraRecorderService.class));
		mCamera = null;

		recordingStatus = false;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		mSurfaceView.setZOrderOnTop(true);
		SurfaceHolder h = mSurfaceView.getHolder();
		h.setFormat(PixelFormat.TRANSPARENT);
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}
}