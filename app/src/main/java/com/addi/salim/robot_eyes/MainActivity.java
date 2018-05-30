package com.addi.salim.robot_eyes;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.addi.salim.robot_eyes.camera.CameraSourcePreview;
import com.addi.salim.robot_eyes.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiDetector;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;

import static com.addi.salim.robot_eyes.PermissionUtil.BLUETOOTH_SETTING_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.CAMERA_PERMISSION_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.LOCATION_PERMISSION_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.LOCATION_SETTING_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.RC_HANDLE_GMS;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int CAMERA_PREVIEW_WIDTH = 640;
    private static final int CAMERA_PREVIEW_HEIGHT = 480;
    private static final double distanceToRangeFinder = 5.5d; //cm
    private static final int CAMERA_ID = CameraSource.CAMERA_FACING_BACK;
    private double angleOfView;

    private View rootView;
    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private ArduinoManager arduinoManager;
    private RangeFinderAgentFactory rangeFinderAgentFactory;
    private RangeFinderManager rangeFinderManager;

    // Declare all variables associated with the UI components
    private Button connectToBluetoothButton;

    private final ConnectionListener connectionListener = new ConnectionListener() {

        @Override
        public void onConnected() {
            enableBluetoothUI();
        }

        @Override
        public void onDisconnected() {
            disableBluetoothUI();
        }

        @Override
        public void onConnectionError() {
            finish();
        }

        @Override
        public void onDistanceReceived(int id, int distanceInCm) {
            rangeFinderManager.notify(id, distanceInCm);
        }

        @Override
        public void onAlarmReceived() {
            //TODO: take picture and email it!
        }
    };

    private final View.OnClickListener connectToBLEClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (arduinoManager.isConnected()) {
                //disconnect, no need to check for permissions!
                triggerAndUpdateConnection();
            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                checkPermissionAndConnect();
            } else {
                triggerAndUpdateConnection();
            }

        }
    };

    private final View.OnClickListener clickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            updateUI(v);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        arduinoManager = ArduinoManager.getInstance(getApplicationContext());

        // Connection button click event
        connectToBluetoothButton.setOnClickListener(connectToBLEClickListener);

        // Bluetooth setup. Created by the RedBear team.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            //createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    private void initUI() {
        rootView = findViewById(R.id.root);
        connectToBluetoothButton = findViewById(R.id.connectBtn);
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        disableBluetoothUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (angleOfView == 0) {
            // calculate the camera angle of view asynchronously.
            VisualTrackerAsyncTask visualTrackerAsyncTask = new VisualTrackerAsyncTask();
            visualTrackerAsyncTask.execute();
        } else {
            startCameraSource();
        }

        // Check if BLE is enabled on the device. Created by the RedBear team.
        if (!arduinoManager.isBluetoothEnabled()) {
            if (arduinoManager.isConnected()) {
                arduinoManager.disconnect();
            }
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BLUETOOTH_SETTING_REQUEST_CODE);
        }

        arduinoManager.addListener(connectionListener);
        if (arduinoManager.isConnected()) {
            enableBluetoothUI();
        } else {
            disableBluetoothUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        arduinoManager.removeListener(connectionListener);
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }


    private void updateUI(View selectedView) {
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {


        Context context = getApplicationContext();

        // A face detector is created to track faces.  An associated multi-processor instance
        // is set to receive the face detection results, track the faces, and maintain graphics for
        // each face on screen.  The factory is used by the multi-processor to create a separate
        // tracker instance for each face.
        FaceDetector faceDetector = new FaceDetector.Builder(context).build();
        FaceTrackerFactory faceFactory = new FaceTrackerFactory(mGraphicOverlay, rangeFinderAgentFactory);
        faceDetector.setProcessor(new MultiProcessor.Builder<>(faceFactory).build());

        // A multi-detector groups the two detectors together as one detector.  All images received
        // by this detector from the camera will be sent to each of the underlying detectors, which
        // will each do face and barcode detection, respectively.  The detection results from each
        // are then sent to associated tracker instances which maintain per-item graphics on the
        // screen.
        MultiDetector multiDetector = new MultiDetector.Builder()
                .add(faceDetector)
                .build();

        if (!multiDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.
        mCameraSource = new CameraSource.Builder(getApplicationContext(), multiDetector)
                .setFacing(CAMERA_ID)
                .setRequestedPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT)
                .setRequestedFps(15.0f)
                .build();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void triggerAndUpdateConnection() {
        if (!arduinoManager.isConnected()) {
            connectToBluetoothButton.setText("Connecting...");
            arduinoManager.connect();
        } else {
            arduinoManager.disconnect();
        }
    }

    private void disableBluetoothUI() {
        connectToBluetoothButton.setText("Connect");
    }

    private void enableBluetoothUI() {
        connectToBluetoothButton.setText("Disconnect");
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PermissionUtil.CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void checkPermissionAndConnect() {
        if (!PermissionUtil.isLocationPermissionGranted(this)) {
            PermissionUtil.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            if (PermissionUtil.isLocationEnabled(this)) {
                triggerAndUpdateConnection();
            } else {
                requestLocationEnabled();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //goToCameraActivity();
                } else {
                    Toast.makeText(this, "Visual tracker cannot be started without Camera permissions!", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!PermissionUtil.isLocationEnabled(this)) {
                        requestLocationEnabled();
                    } else {
                        triggerAndUpdateConnection();
                    }
                } else {
                    Toast.makeText(this, "Location permissions are needed to scan BLE!", Toast.LENGTH_LONG).show();
                }
            }
            break;
        }
    }

    private void requestLocationEnabled() {
        PermissionUtil.displayNetworkProvidedLocationSettingRequest(this, LOCATION_SETTING_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == BLUETOOTH_SETTING_REQUEST_CODE
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == LOCATION_SETTING_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            triggerAndUpdateConnection();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Async task used to lock a camera instance and get its angle of view, then start previewing.
     */
    private class VisualTrackerAsyncTask extends AsyncTask<Void, Void, Double> {

        @Override
        protected Double doInBackground(Void... params) {
            Camera camera;
            double angleOfView = 60d;
            double horizontalAngleOfView = 60d;

            try {
                camera = Camera.open(CAMERA_ID);
                //configure Camera parameters
                Camera.Parameters cameraParameters = camera.getParameters();
                angleOfView = cameraParameters.getVerticalViewAngle();
                horizontalAngleOfView = cameraParameters.getHorizontalViewAngle();
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }

            Log.e("****", "Vertical Angle of View = " + angleOfView);
            Log.e("****", "Horizontal Angle of View = " + horizontalAngleOfView);
            return angleOfView;
        }

        @Override
        protected void onPostExecute(Double aov) {
            super.onPostExecute(aov);
            angleOfView = aov;
            rangeFinderManager = new RangeFinderManager(arduinoManager);
            rangeFinderAgentFactory = new RangeFinderAgentFactory(CAMERA_PREVIEW_HEIGHT, CAMERA_PREVIEW_WIDTH, angleOfView, distanceToRangeFinder, rangeFinderManager);
            createCameraSource();
            startCameraSource();
        }
    }
}
