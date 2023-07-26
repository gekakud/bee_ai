package com.hoho.android.usbserial.examples;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 101; // Add this constant

    private static final long RECORDING_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final int RECORDING_DURATION_SECONDS = 20;

    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;
    private Handler handler = new Handler();
    private Runnable recordingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // Check if both camera and audio permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
            int i = 0; // do nothing
            // Both camera and audio permissions are granted, you can proceed to use the camera
      //      startCamera();
        } else {
            // Request camera and audio permissions if not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }


        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            TerminalFragment terminal = (TerminalFragment) getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null)
                terminal.status("USB device detected");
        }
        super.onNewIntent(intent);
    }

    // Callback for permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Check if both camera and audio permissions are granted after the request
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Both camera and audio permissions granted, you can proceed to use the camera
                Toast.makeText(this, "Camera and audio permissions granted.", Toast.LENGTH_SHORT).show();
                startCamera();
            } else {
                // Camera and/or audio permission denied. Handle this case, e.g., show a message or disable camera-related functionality.
                Toast.makeText(this, "Camera and audio permissions are required to use the camera.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Start the camera to begin video recording
    private void startCamera() {
        handler.postDelayed(recordingRunnable, RECORDING_INTERVAL_MS);
    }

    // Start video recording
    private void startRecording() {
        if (!isRecording) {
            try {
                // Prepare MediaRecorder
                File videoFile = createVideoFile();
                if (videoFile != null) {
                    mediaRecorder = new MediaRecorder();
                    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                    mediaRecorder.setVideoEncodingBitRate(10000000);
                    mediaRecorder.setVideoFrameRate(30);
                    mediaRecorder.setVideoSize(1280, 720);
                    mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    isRecording = true;
                    // Stop recording after specified duration
                    handler.postDelayed(recordingRunnable, RECORDING_DURATION_SECONDS * 1000);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Stop video recording
    private void stopRecording() {
        if (isRecording) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            // Continue recording after the specified interval
            handler.postDelayed(recordingRunnable, RECORDING_INTERVAL_MS);
        }
    }

    // Create a video file in the Movies directory
    private File createVideoFile() {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String videoFileName = "VIDEO_" + timeStamp + ".mp4";
        return new File(storageDir, videoFileName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(recordingRunnable);
        if (isRecording) {
            stopRecording();
        }
    }
}
