package com.addi.salim.night_light;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.addi.salim.night_light.BLE.RBLGattAttributes;
import com.addi.salim.night_light.BLE.RBLService;
import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorChangedListener;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.slider.LightnessSlider;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.addi.salim.night_light.PermissionUtil.CAMERA_PERMISSION_REQUEST_CODE;
import static com.addi.salim.night_light.PermissionUtil.LOCATION_PERMISSION_REQUEST_CODE;
import static com.addi.salim.night_light.PermissionUtil.LOCATION_SETTING_REQUEST_CODE;

public class MainActivity extends AppCompatActivity {
    private View paletteColorTile;
    private View paletteColorPickerView;
    private ColorPickerView colorPickerView;
    private LightnessSlider lightnessSlider;
    private View liveCameraTile;
    private View liveSoundTile;
    private View accelerationSensorTile;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private LowPassFilter lowPassFilter;
    private ShakeSignalPeekDetector shakeSignalPeekDetector;
    private long timestampNanoSecondsZero;


    // Define the device name and the length of the name
    // Note the device name and the length should be consistent with the ones defined in the Duo sketch
    private String mTargetDeviceName = "Salimmm";
    private int mNameLen = mTargetDeviceName.length() + 1;

    private final static String TAG = MainActivity.class.getSimpleName();

    // Declare all variables associated with the UI components
    private Button connectToBluetoothButton;

    // Declare all Bluetooth stuff
    private BluetoothGattCharacteristic mCharacteristicTx = null;
    private RBLService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;

    private boolean flag = true;
    private boolean isConnected = false;
    private boolean isScanCompleted = false;

    private byte[] mData = new byte[3];
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 2000;   // millis

    final private static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    // Process service connection. Created by the RedBear Team
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final View.OnClickListener connectToBLEClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (isConnected) {
                //disconnect, no need to check for permissions!
                triggerAndUpdateConnection();
            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                checkPermissionAndConnect();
            } else {
                triggerAndUpdateConnection();
            }

        }
    };

    // Callback function to search for the target Duo board which has matched UUID
    // If the Duo board cannot be found, debug if the received UUID matches the predefined UUID on the board
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] serviceUuidBytes = new byte[16];
                    String serviceUuid = "";
                    for (int i = (21 + mNameLen), j = 0; i >= (6 + mNameLen); i--, j++) {
                        serviceUuidBytes[j] = scanRecord[i];
                    }
                    /*
                     * This is where you can test if the received UUID matches the defined UUID in the Arduino
                     * Sketch and uploaded to the Duo board: 0x713d0000503e4c75ba943148f18d941e.
                     */
                    serviceUuid = bytesToHex(serviceUuidBytes);
                    if (stringToUuidString(serviceUuid).equals(RBLGattAttributes.BLE_SHIELD_SERVICE.toUpperCase(Locale.ENGLISH)) && device.getName().equals(mTargetDeviceName)) {
                        mDevice = device;
                        mDeviceAddress = mDevice.getAddress();
                        mBluetoothLeService.connect(mDeviceAddress);
                        isScanCompleted = true;
                    }
                }
            });
        }
    };

    // Process the Gatt and get data if there is data coming from Duo board. Created by the RedBear Team
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnected",
                        Toast.LENGTH_SHORT).show();
                disableBluetoothUI();
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();

                getGattService(mBluetoothLeService.getSupportedGattService());
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                mData = intent.getByteArrayExtra(RBLService.EXTRA_DATA);

                receiveData(mData);
            } else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
                //displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
            }
        }
    };

    private final View.OnClickListener clickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            updateUI(v);
            switch (v.getId()) {
                case R.id.palette_color_picker_tile:
                    break;
                case R.id.camera_color_picker_tile:
                    openCameraColorPicker();
                    break;
                case R.id.live_sound_sensor_tile:
                    break;
                case R.id.accel_sensor_tile:
                    break;
            }
        }
    };

    private final SensorEventListener accelerometerSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                //processNewEvent(event.timestamp, event.values[0], event.values[1], event.values[2]);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Connection button click event
        connectToBluetoothButton.setOnClickListener(connectToBLEClickListener);
        paletteColorTile.setOnClickListener(clickListener);
        liveCameraTile.setOnClickListener(clickListener);
        liveSoundTile.setOnClickListener(clickListener);
        accelerationSensorTile.setOnClickListener(clickListener);

        colorPickerView.addOnColorChangedListener(new OnColorChangedListener() {
            @Override
            public void onColorChanged(int selectedColor) {
                // Handle on color change
                Log.d("ColorPicker", "onColorChanged: 0x" + Integer.toHexString(selectedColor));
            }
        });
        colorPickerView.addOnColorSelectedListener(new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int selectedColor) {
                Toast.makeText(
                        MainActivity.this,
                        "selectedColor: " + Integer.toHexString(selectedColor).toUpperCase(),
                        Toast.LENGTH_SHORT).show();
            }
        });


        // Bluetooth setup. Created by the RedBear team.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        Intent gattServiceIntent = new Intent(MainActivity.this,
                RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void initUI() {
        paletteColorTile = findViewById(R.id.palette_color_picker_tile);
        paletteColorPickerView = findViewById(R.id.palette_color_picker);
        colorPickerView = paletteColorPickerView.findViewById(R.id.color_picker_view);
        lightnessSlider = paletteColorPickerView.findViewById(R.id.lightness_slider);
        liveCameraTile = findViewById(R.id.camera_color_picker_tile);
        liveSoundTile = findViewById(R.id.live_sound_sensor_tile);
        accelerationSensorTile = findViewById(R.id.accel_sensor_tile);
        connectToBluetoothButton = findViewById(R.id.connectBtn);
        disableBluetoothUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSensorManager.registerListener(accelerometerSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if BLE is enabled on the device. Created by the RedBear team.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();

        flag = false;
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(accelerometerSensorEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null)
            unbindService(mServiceConnection);
    }

    private void updateUI(View selectedTile) {
        paletteColorTile.setSelected(false);
        liveCameraTile.setSelected(false);
        liveSoundTile.setSelected(false);
        accelerationSensorTile.setSelected(false);

        paletteColorPickerView.setVisibility(View.INVISIBLE);
        switch (selectedTile.getId()) {
            case R.id.palette_color_picker_tile:
                paletteColorTile.setSelected(true);
                paletteColorPickerView.setVisibility(View.VISIBLE);
                break;
            case R.id.camera_color_picker_tile:
                //liveCameraTile.setSelected(true);
                break;
            case R.id.live_sound_sensor_tile:
                liveSoundTile.setSelected(true);
                break;
            case R.id.accel_sensor_tile:
                accelerationSensorTile.setSelected(true);
                break;
        }
    }

    private void triggerAndUpdateConnection() {
        if (isConnected == false) {
            connectToBluetoothButton.setText("Connecting...");
            if (isScanCompleted) {
                mBluetoothLeService.connect(mDeviceAddress);
            } else {
                // Scan all available devices through BLE
                scanAndConnect();
            }
        } else {
            mBluetoothLeService.disconnect();
            mBluetoothLeService.close();
            disableBluetoothUI();
        }
    }

    private void disableBluetoothUI() {
        isConnected = false;
        paletteColorTile.setEnabled(false);
        paletteColorPickerView.setVisibility(View.INVISIBLE);
        liveCameraTile.setEnabled(false);
        liveSoundTile.setEnabled(false);
        accelerationSensorTile.setEnabled(false);
        connectToBluetoothButton.setText("Connect");

        paletteColorTile.setSelected(false);
        liveCameraTile.setSelected(false);
        liveSoundTile.setSelected(false);
        accelerationSensorTile.setSelected(false);
    }

    private void enableBluetoothUI() {
        isConnected = true;
        paletteColorTile.setEnabled(true);
        paletteColorPickerView.setVisibility(View.VISIBLE);
        liveCameraTile.setEnabled(true);
        liveSoundTile.setEnabled(true);
        accelerationSensorTile.setEnabled(true);
        connectToBluetoothButton.setText("Disconnect");

        updateUI(paletteColorTile);
    }

    private void sendColor(int red, int green, int blue) {
        final byte redByte = (byte) (0xFF & red);
        final byte greenByte = (byte) (0xFF & green);
        final byte blueByte = (byte) (0xFF & blue);

        byte data[] = new byte[]{redByte, greenByte, blueByte};
        sendData(data);
    }

    private void sendData(byte... data) {
        mCharacteristicTx.setValue(data);
        mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
    }

    private void receiveData(byte[] data) {

    }

    // Get Gatt service information for setting up the communication
    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null) {
            return;
        }

        enableBluetoothUI();
        mCharacteristicTx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    // Scan all available BLE-enabled devices
    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }


    // Convert an array of bytes into Hex format string
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // Convert a string to a UUID format
    private String stringToUuidString(String uuid) {
        StringBuffer newString = new StringBuffer();
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

        return newString.toString();
    }

    // Create a list of intent filters for Gatt updates. Created by the RedBear team.
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    private void processNewEvent(long timestamp, double... signal) {
        final double x = signal[0] + signal[1] + signal[2];
        final double y = signal[1];
        final double z = signal[2];

        if (lowPassFilter.isInitialized()) {
            final double[] filteredSignal = lowPassFilter.applyLowPassFilter(timestamp, x, y, z);

            if (shakeSignalPeekDetector.addAndDetectPeak(timestamp, filteredSignal[0])) {
                //TODO: change led color
            }

        } else {
            timestampNanoSecondsZero = timestamp;
            shakeSignalPeekDetector.initialize(timestampNanoSecondsZero);
            shakeSignalPeekDetector.addAndDetectPeak(timestamp, x);
            lowPassFilter.initialize(timestampNanoSecondsZero);
            lowPassFilter.applyLowPassFilter(timestamp, x, y, z);
        }
    }

    private void openCameraColorPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            requestCameraPermission();
        } else {
            goToCameraColorPickerActivity();
        }
    }

    private void goToCameraColorPickerActivity() {
        final Intent intentColorPickerActivity = new Intent(this, ColorPickerBaseActivity.class);
        startActivity(intentColorPickerActivity);
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

    private void scanAndConnect() {
        // Scan all available devices through BLE
        scanLeDevice();

        Timer mTimer = new Timer();
        mTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (!isScanCompleted) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast toast = Toast
                                    .makeText(
                                            MainActivity.this,
                                            "Couldn't search Ble Shield device!",
                                            Toast.LENGTH_SHORT);
                            toast.setGravity(0, 0, Gravity.CENTER);
                            toast.show();
                        }
                    });
                }
            }
        }, SCAN_PERIOD);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    goToCameraColorPickerActivity();
                } else {
                    Toast.makeText(this, "Camera color picker cannot be started without Camera permissions!", Toast.LENGTH_LONG).show();
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
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == LOCATION_SETTING_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            triggerAndUpdateConnection();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
