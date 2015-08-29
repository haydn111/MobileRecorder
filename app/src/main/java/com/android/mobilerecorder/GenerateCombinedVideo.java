package com.android.mobilerecorder;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;

import javax.inject.Inject;

import dagger.ObjectGraph;

public class GenerateCombinedVideo extends Service {
    private static final String TAG = "GenerateCombinedVideo";
    private NotificationCompat.Builder mBuilder;
    private NotificationManager notificationManager;
    private String fileName, screenFileName, cameraFileName, cameraTempFileName;
    private int notificationId = 0321;

    public static boolean generatingInProgress;

    @Inject
    FFmpeg ffmpeg;

    int screenW, screenH;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setAutoCancel(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        generatingInProgress = true;

        fileName = MobileRecorder.fileName;
        screenFileName = MobileRecorder.screenFileName;
        cameraFileName = MobileRecorder.cameraFileName;

        // Get video dimensions
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(MobileRecorder.VIDEO_PATH + File.separator + screenFileName);
        screenW = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        screenH = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        Log.e("TAG", "screenW = " + screenW + ", screenH = " + screenH);

        // Initialize FFMpeg Binary
        ObjectGraph.create(new DaggerDependencyModule(this)).inject(this);
        loadFFMpegBinary();

        // Clean old temp files
        try {
            new File(MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled.mp4").delete();
        }
        catch (Exception e) {
        }
        try {
            new File(MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled_rotated.mp4").delete();
        }
        catch (Exception e) {
        }

        File dir = new File(MobileRecorder.VIDEO_PATH + File.separator + "output");
        dir.mkdir();
        dir = new File(MobileRecorder.VIDEO_PATH + File.separator + "temp");
        dir.mkdir();

        cameraTempFileName = "temp_camera_scaled.mp4";

        boolean success1 = false;
        boolean success2 = false;
        boolean success3 = false;

        success1 = scale();
        if (success1)
            success2 = rotate();
        if (success2)
            success3 = overlay();
        if (success3) {
            // stop service
            generatingInProgress = false;
            this.stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        generatingInProgress = false;

        // Clean temp files
        try {
            new File(MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled.mp4").delete();
        } catch (Exception e) {
        }
        try {
            new File(MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled_rotated.mp4").delete();
        } catch (Exception e) {
        }
    }

    // Scale the camera output to be 1/3 of screen width/height
    private boolean scale() {
        int newCameraW, newCameraH;
        if (screenW > screenH) {    // screen recording is landscape
            newCameraW= screenW / 3;
            newCameraH = (int)(screenH * ((double)newCameraW / (double)screenW));
        }
        else {  // portrait
            newCameraW= screenH / 3;
            newCameraH = (int)(screenW * ((double)newCameraW / (double)screenH));
        }


        execFFmpegBinary("-i " + MobileRecorder.VIDEO_PATH + File.separator + cameraFileName
                + " -strict experimental -vf scale="
                + Integer.toString(newCameraW) + ":" + Integer.toString(newCameraH) + " "
                + MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled.mp4");

        return true;
    }

    // If screen recording is portrait, rotate the scaled camera output
    private boolean rotate() {
        if (screenW < screenH) {
            execFFmpegBinary("-i " + MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator
                    + "temp_camera_scaled.mp4 -strict experimental -vf transpose=2 "
                    + MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + "temp_camera_scaled_rotated.mp4");
            cameraTempFileName = "temp_camera_scaled_rotated.mp4";
        }

        return true;
    }

    // Overlay the camera file to screen recording
    private boolean overlay() {
        execFFmpegBinary("-i " + MobileRecorder.VIDEO_PATH + File.separator + screenFileName
                + " -i " + MobileRecorder.VIDEO_PATH + File.separator + "temp" + File.separator + cameraTempFileName
                + " -strict experimental -filter_complex overlay=main_w-overlay_w-10:main_h-overlay_h-10 "
                + MobileRecorder.VIDEO_PATH + File.separator + "output" + File.separator + fileName + ".mp4");

        return true;
    }

    private void loadFFMpegBinary() {
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    showUnsupportedExceptionDialog();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            showUnsupportedExceptionDialog();
        }
    }

    private void execFFmpegBinary(final String command) {
        try {
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    Log.e(TAG, "FAILED with output : " + s);
                }

                @Override
                public void onSuccess(String s) {
                    Log.e(TAG, "SUCCESS with output : " + s);
                }

                @Override
                public void onProgress(String s) {
                    Log.d(TAG, "Started command : ffmpeg " + command);
                    Log.e(TAG, "progress : " + s);
                    mBuilder.setContentText(getResources().getString(R.string.combining)).setContentIntent(null);
                    notificationManager.notify(notificationId, mBuilder.build());
                }

                @Override
                public void onStart() {
                    Log.d(TAG, "Started command : ffmpeg " + command);
                    mBuilder.setContentText(getResources().getString(R.string.combining)).setContentIntent(null);
                    notificationManager.notify(notificationId, mBuilder.build());
                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "Finished command : ffmpeg " + command);
                    // update notification
                    Log.e(TAG, "Notification changed to 'Done'");
                    Intent mIntent = new Intent();
                    mIntent.setAction(android.content.Intent.ACTION_VIEW);
                    File file = new File(MobileRecorder.VIDEO_PATH + File.separator + "output" + File.separator + fileName + ".mp4");
                    mIntent.setDataAndType(Uri.fromFile(file), "video/*");
                    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, mIntent, 0);

                    mBuilder.setContentText(getResources().getString(R.string.combining_finished) + fileName + ".mp4")
                            .setContentIntent(pendingIntent);
                    notificationManager.notify(notificationId, mBuilder.build());
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void showUnsupportedExceptionDialog() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(this.getString(R.string.device_not_supported))
                .setMessage(this.getString(R.string.device_not_supported_message))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.exit(0);
                    }
                })
                .create()
                .show();

    }
}
