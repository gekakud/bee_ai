package com.hoho.android.usbserial.examples;

import static android.content.ContentValues.TAG;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener {

    static float MaxWeight = 0;
    static float MinWeight = 0;

    static float CurrBattery = 0;

    static float CurrWeight = 0;

    private enum UsbPermission { Unknown, Requested, Granted, Denied }

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private TextView receiveText;
    private ControlLines controlLines;

    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;

    private Timer weightTimer;
    private TimerTask weightTimerTask;

    private Uri videoUri;
    private Handler captureHandler = new Handler();
    private Runnable captureRunnable = this::startVideoRecording;

    // MediaRecorder variables
    private MediaRecorder mediaRecorder;
    private String videoFilePath = "";
    private Camera camera;
    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;

    private boolean isTakingPictures = false;
    private Handler pictureHandler = new Handler(Looper.getMainLooper());
    private int intervalInSeconds = 2 * 1; // Take a picture every 1 minutes


    private boolean isRecording = false;

    // Constants for video recording
    private static final int RECORDING_INTERVAL_MINUTES = 10; // Replace YY with desired interval in minutes
    private static final long RECORDING_INTERVAL_MS = RECORDING_INTERVAL_MINUTES * 60 * 1000;
    private static final int RECORDING_DURATION_SECONDS = 2; // Replace XX with desired recording duration in seconds


    private Runnable takePictureRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTakingPictures) {
                takePicture();
                pictureHandler.postDelayed(this, intervalInSeconds * 1000);
            }
        }
    };

    private void takePicture() {
        if (camera != null) {
            camera.takePicture(null, null, pictureCallback);
        }
    }

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            // Save the image data to a file
            File pictureFile = getOutputMediaFile(false);
            if (pictureFile != null) {
                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    Log.d("YourFragment", "Picture saved: " + pictureFile.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace(); // Print the stack trace for debugging
                }
            }

            // Restart the preview to continue taking pictures
            camera.setDisplayOrientation(90);
            camera.startPreview();
        }
    };




    private Handler recordingHandler = new Handler();
    private Runnable recordingRunnable = new Runnable() {
        @Override
        public void run() {
            startVideoRecording();
            recordingHandler.postDelayed(this, RECORDING_INTERVAL_MS);
        }
    };

    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };

        mainLooper = new Handler(Looper.getMainLooper());
    }
    // Declare batteryReceiver as a class member
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    CurrBattery = (float) (level * 100) / scale;
                }
            }
        }
    };



   private boolean prepareVideoRecorder() {
        mediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set the audio and video sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set the video output format and encoding parameters
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        mediaRecorder.setVideoEncodingBitRate(20000000); // Adjust as per your requirement
        mediaRecorder.setVideoFrameRate(60); // Adjust as per your requirement

        // Step 4: Set the output file path
        videoFilePath = getOutputMediaFile(true).toString();
        mediaRecorder.setOutputFile(videoFilePath);

        // Step 5: Set the preview output
        mediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        // Set the maximum recording duration
        mediaRecorder.setMaxDuration(RECORDING_DURATION_SECONDS * 1000);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepareVideoRecorder() failed: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }

        return true;
    }

    private File getOutputMediaFile(boolean VidFile) {
        // Implement your logic to generate the file path and file name for the video
        // For example:
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File mediaStorageDir;
        if (VidFile)
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Bee Hive Monitoring");
        else
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Bee Hive Monitoring");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory for saving video/picture.");
                return null;
            }
        }
        if (VidFile == true)
            return new File(mediaStorageDir.getPath() + File.separator + "VIDEO_" + timeStamp + ".mp4");
        else
            return new File(mediaStorageDir.getPath() + File.separator + "PIC_" + timeStamp + ".jpg");

    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;
        try {
            releaseCameraAndPreview();
            camera = Camera.open(id);
            qOpened = (camera != null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Camera");
            e.printStackTrace();
        }
        return qOpened;
    }

    private void releaseCameraAndPreview() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private Runnable stopRecordingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                try {
                    // Stop the recording and reset the flag.
                    mediaRecorder.stop();
                    isRecording = false;
                } catch (IllegalStateException e) {
                    // Handle any exceptions while stopping recording.
                    e.printStackTrace();
                }
                // Release the MediaRecorder resources.
                releaseMediaRecorder();
            }
        }
    };

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // Clear configuration settings.
            mediaRecorder.release(); // Release the MediaRecorder object.
            mediaRecorder = null;
            camera.lock(); // Lock camera for later use (e.g., to restart the preview).
        }
    }


// ...

    private void startVideoRecording() {
        if (safeCameraOpen(0)) {
            if (prepareVideoRecorder()) {
                try {
                    mediaRecorder.start();
                    isRecording = true;

                    // Schedule stopping the recording after XX seconds
                    Log.d("MyApp", "Before postDelayed");
                    recordingHandler.postDelayed(stopRecordingRunnable, RECORDING_DURATION_SECONDS * 1000);
                    Log.d("MyApp", "After postDelayed");
                } catch (IllegalStateException e) {
                    // This exception will be thrown if the MediaRecorder is not in a valid state.
                    // Possible reasons include an improperly configured MediaRecorder or an invalid state transition.
                    Log.e(TAG, "MediaRecorder start failed: " + e.getMessage());
                    e.printStackTrace();
                    // Handle the error gracefully. You can show an error message to the user,
                    // disable the recording functionality, or take any other appropriate action.
                    // For example, you might want to release the MediaRecorder and reset the recording state.

                    releaseMediaRecorder();
                    releaseCameraAndPreview();
                    // Handle recording start failure
                } catch (Exception e) {
                    // This will catch any other exceptions that may be thrown during video recording.
                    // Handle the error as needed.
                    Log.e(TAG, "Error during video recording: " + e.getMessage());
                    e.printStackTrace();
                    // Handle the error gracefully. You can show an error message to the user,
                    // disable the recording functionality, or take any other appropriate action.
                    // For example, you might want to release the MediaRecorder and reset the recording state.

                    releaseMediaRecorder();
                    releaseCameraAndPreview();
                    // Handle recording start failure
                }
            } else {
                releaseMediaRecorder();
                releaseCameraAndPreview();
                // Handle recording preparation failure
            }
        } else {
            // Handle camera open failure
        }
    }


    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        // Start the video recording loop
        recordingHandler.post(recordingRunnable);

        // Start taking pictures when the fragment is resumed
        isTakingPictures = true;
        pictureHandler.post(takePictureRunnable);


        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    // Add the getOptimalPreviewSize method
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) width / height;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
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
    @Override
    public void onPause() {
        if(connected) {
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        getActivity().unregisterReceiver(batteryReceiver);
        // Stop the video capture task and any ongoing video recording
       // stopVideoCaptureTask();
        isTakingPictures = false;
        pictureHandler.removeCallbacks(takePictureRunnable);

        super.onPause();
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        // Find views by ID
        mPreview = view.findViewById(R.id.surface_view);

        // Initialize the receive_text TextView
        receiveText = view.findViewById(R.id.receive_text);

        // Other views initialization here...

        // Setup camera preview
        mPreviewHolder = mPreview.getHolder();
        mPreviewHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    // Release the camera if it is already opened
                    if (camera != null) {
                        camera.release();
                        camera = null;
                    }

                    // Open the camera
                    camera = camera.open();
                    camera.setDisplayOrientation(90);

                    // Set the camera parameters
                    Camera.Parameters parameters = camera.getParameters();
                    Camera.Size previewSize = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), mPreview.getWidth(), mPreview.getHeight());
                    parameters.setPreviewSize(previewSize.width, previewSize.height);
                    camera.setParameters(parameters);

                    // Set the camera preview display
                    camera.setPreviewDisplay(mPreviewHolder);
                    camera.setDisplayOrientation(90);
                    camera.startPreview();

                } catch (IOException e) {
                    Log.e(TAG, "Error setting up camera preview: " + e.getMessage());
                    Toast.makeText(getContext(), "Error setting up camera preview!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // Empty. The surface will be recreated when needed.
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                // Release the camera when the surface is destroyed
                if (camera != null) {
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                }
            }
        });

        // Your existing code here...
        // Initialize the ControlLines object
        controlLines = new ControlLines(view);


        return view;
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if( id == R.id.send_break) {
            if(!connected) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    usbSerialPort.setBreak(true);
                    Thread.sleep(100); // should show progress bar instead of blocking UI thread
                    usbSerialPort.setBreak(false);
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send <break>\n");
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                } catch(UnsupportedOperationException ignored) {
                    Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial
     */
    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receive(data);
        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    public String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }

    private void sendDataToAPI() {
        try {
            // Create the JSON payload
            // Create the JSON payload
            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("device_id", "device-001");
            jsonPayload.put("timestamp", getCurrentDateTime());
            jsonPayload.put("temperature", 25.5);
            jsonPayload.put("humidity", 60.0);
            jsonPayload.put("weight", CurrWeight);
            JSONObject lightObject = new JSONObject();
            lightObject.put("lux", 500);
            lightObject.put("is_dark", false);
            jsonPayload.put("light", lightObject);
            JSONObject pressureObject = new JSONObject();
            pressureObject.put("value", 1012.5);
            pressureObject.put("unit", "hPa");
            jsonPayload.put("pressure", pressureObject);
            jsonPayload.put("battery_level", CurrBattery);
            jsonPayload.put("status", "online");
            JSONObject picObject = new JSONObject();
            picObject.put("pic_id", "");
            picObject.put("sync", false);
            jsonPayload.put("pic", picObject);
            JSONObject vidObject = new JSONObject();
            if (videoFilePath.length() > 1)
                vidObject.put("vid_id",videoFilePath );
            else
                vidObject.put("vid_id","" );

            vidObject.put("sync", false);
            jsonPayload.put("vid", vidObject);


            // Create the URL object for the API endpoint
            URL url = new URL("http://34.165.42.165:5000/api/data");

            // Create the HttpURLConnection object
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to POST
            connection.setRequestMethod("POST");

            // Set the Content-Type header to specify JSON data
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            // Enable output and set the payload
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(jsonPayload.toString().getBytes("UTF-8"));
            outputStream.close();

            // Get the response code
            int responseCode = connection.getResponseCode();

            // Handle the response code (e.g., check if the request was successful)

            // Close the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendDataToServer(){
        if (connected)
           sendDataToAPI();
//
    }
    /*
     * Serial + UI
     */
    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if(withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected");
            connected = true;
            controlLines.start();

            // starting timer
            weightTimer = new Timer();
            weightTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    sendDataToServer();
                }
            },100, 3000);

        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        if (controlLines != null)
            controlLines.stop();

        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
    }

    private void send(String str) {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
        byte[] data = (str + '\n').getBytes();
            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append("send " + data.length + " bytes\n");
            spn.append(HexDump.dumpHexString(data)).append("\n");
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (receiveText != null)
                receiveText.append(spn);

            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            onRunError(e);
        }
    }

    private void read() {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }


    private float extractWeightFromData(String data) {
        // Assuming the weight value is in the format XX.Y.Kg
        // Extract the numeric part of the weight string
        String weightString = data.split(" ")[0];
        float weight = 0.0f;

        try {
            weight = Float.parseFloat(weightString);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return weight;
    }
    private void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append("receive " + data.length + " bytes\n");
        if (data.length > 0) {
            spn.append(HexDump.dumpText(data, 0, 8)).append("\n");
            // Extract the weight value from the received data
            float weight = extractWeightFromData(new String(data));
            CurrWeight = weight;
            if (weight > MaxWeight)
                MaxWeight = weight;
            else if(weight < MinWeight)
                MinWeight = weight;
        }
        spn.append("Max weight = " + MaxWeight + "\n");
        spn.append("Min weight = " + MinWeight + "\n");
        spn.append("Battery = " + CurrBattery);
        if (receiveText != null)
           receiveText.setText(spn);
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (receiveText != null)
            receiveText.append(spn);

    }

    class ControlLines {
        private static final int refreshInterval = 2000; // msec

        private final Runnable runnable;
        private final ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            rtsBtn.setOnClickListener(this::toggle);
            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (!connected) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) { ctrl = "RTS"; usbSerialPort.setRTS(btn.isChecked()); }
                if (btn.equals(dtrBtn)) { ctrl = "DTR"; usbSerialPort.setDTR(btn.isChecked()); }
            } catch (IOException e) {
                status("set" + ctrl + "() failed: " + e.getMessage());
            }
        }

        private void run() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS)) rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS)) ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR)) dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR)) dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))   cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))   riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(false);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            riBtn.setChecked(false);
        }
    }
}
