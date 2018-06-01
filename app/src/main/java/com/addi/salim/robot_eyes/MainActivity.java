package com.addi.salim.robot_eyes;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.addi.salim.robot_eyes.PermissionUtil.BLUETOOTH_SETTING_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.CAMERA_PERMISSION_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.LOCATION_PERMISSION_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.LOCATION_SETTING_REQUEST_CODE;
import static com.addi.salim.robot_eyes.PermissionUtil.RC_HANDLE_GMS;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String APP_TAG = "RobotEyes";
    private static final int CAMERA_PREVIEW_WIDTH = 640;
    private static final int CAMERA_PREVIEW_HEIGHT = 480;
    private static final double distanceToRangeFinder = 5.5d; //cm
    private static final int CAMERA_ID = CameraSource.CAMERA_FACING_BACK;
    private boolean safeToTakePicture = false;
    private static final String[] SCOPES = {GmailScopes.GMAIL_SEND};
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private double angleOfView;
    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private ArduinoManager arduinoManager;
    private RangeFinderAgentFactory rangeFinderAgentFactory;
    private RangeFinderManager rangeFinderManager;

    // Gmail API
    private SendEmail sendEmail;
    private GoogleAccountCredential mCredential;
    // Declare all variables associated with the UI components
    private Button connectToBluetoothButton;

    private final CameraSource.PictureCallback pictureCallback = new CameraSource.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data) {
            checkPreRequirements();
            final Set<String> to = new HashSet<>();
            to.add("salim.addi@gmail.com");
            sendEmail.emailPictureAlarm(mCredential, to, data);

            //finished saving picture
            safeToTakePicture = true;
        }
    };

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
            if (mCameraSource == null) {
                return;
            }

            if (safeToTakePicture) {
                mCameraSource.takePicture(null, pictureCallback);
                safeToTakePicture = false;
            }
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

            // check all conditions to send email alarms are satisfied!
            checkPreRequirements();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        arduinoManager = ArduinoManager.getInstance(getApplicationContext());
        sendEmail = new SendEmail(APP_TAG, getCacheDir());

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

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    private void initUI() {
        View rootView = findViewById(R.id.root);
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

        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            if (angleOfView == 0) {
                // calculate the camera angle of view asynchronously.
                VisualTrackerAsyncTask visualTrackerAsyncTask = new VisualTrackerAsyncTask();
                visualTrackerAsyncTask.execute();
            } else {
                startCameraSource();
            }
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
                safeToTakePicture = true;
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

    /**
     * verify that all the preconditions are satisfied. The preconditions are: Google Play Services installed, an
     * account was selected. If any of the preconditions are not satisfied, the app will prompt the user as appropriate.
     */
    private void checkPreRequirements() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (Strings.isNullOrEmpty(mCredential.getSelectedAccountName())) {
            chooseAccount();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                checkPreRequirements();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        switch (requestCode) {
            case BLUETOOTH_SETTING_REQUEST_CODE:
                // User chose not to enable Bluetooth.
                if (resultCode == Activity.RESULT_CANCELED) {
                    finish();
                    return;
                } else if (resultCode == Activity.RESULT_OK) {
                    triggerAndUpdateConnection();
                }
                break;
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this,
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", Toast.LENGTH_LONG).show();
                } else {
                    checkPreRequirements();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        checkPreRequirements();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    checkPreRequirements();
                }
                break;
        }
    }

    /**
     * Async task used to lock a camera instance and get its angle of view, then start previewing.
     */
    private class VisualTrackerAsyncTask extends AsyncTask<Void, Void, Double> {

        @Override
        protected Double doInBackground(Void... params) {
            Camera camera = null;
            double angleOfView = 60d;
            double horizontalAngleOfView = 60d;

            try {
                camera = Camera.open(CAMERA_ID);
                //configure Camera parameters
                Camera.Parameters cameraParameters = camera.getParameters();
                angleOfView = cameraParameters.getVerticalViewAngle();
                horizontalAngleOfView = cameraParameters.getHorizontalViewAngle();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (camera != null) {
                    camera.release();
                }
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
