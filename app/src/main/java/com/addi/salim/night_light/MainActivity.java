package com.addi.salim.night_light;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorChangedListener;
import com.flask.colorpicker.OnColorSelectedListener;

import static com.addi.salim.night_light.PermissionUtil.BLUETOOTH_SETTING_REQUEST_CODE;
import static com.addi.salim.night_light.PermissionUtil.CAMERA_PERMISSION_REQUEST_CODE;
import static com.addi.salim.night_light.PermissionUtil.LOCATION_PERMISSION_REQUEST_CODE;
import static com.addi.salim.night_light.PermissionUtil.LOCATION_SETTING_REQUEST_CODE;

public class MainActivity extends AppCompatActivity {
    private View rootView;
    private View paletteColorTile;
    private View paletteColorPickerView;
    private ColorPickerView colorPickerView;
    private View liveCameraTile;
    private View liveSoundTile;
    private View accelerationSensorTile;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private LowPassFilter lowPassFilter;
    private ShakeSignalPeekDetector shakeSignalPeekDetector;
    private long timestampNanoSecondsZero;

    private ArduinoManager arduinoManager;

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
        public void onDataReceived(byte[] data) {
            processData(data);
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
    private int selectedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        arduinoManager = ArduinoManager.getInstance(getApplicationContext());
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
                MainActivity.this.selectedColor = selectedColor;
                // Handle on color change
                final byte red = (byte) Color.red(selectedColor); //byte) ((0xFF0000 & selectedColor) >> 16);
                final byte green = (byte) Color.green(selectedColor); //((0x00FF00 & selectedColor) >> 8);
                final byte blue = (byte) Color.blue(selectedColor); //(0x0000FF & selectedColor);
                sendColor(red, green, blue);
            }
        });
        colorPickerView.addOnColorSelectedListener(new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int selectedColor) {
               /* Toast.makeText(
                        MainActivity.this,
                        "selectedColor: " + Integer.toHexString(selectedColor).toUpperCase(),
                        Toast.LENGTH_SHORT).show();*/
            }
        });


        // Bluetooth setup. Created by the RedBear team.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }
    }

    private void initUI() {
        rootView = findViewById(R.id.root);
        paletteColorTile = findViewById(R.id.palette_color_picker_tile);
        paletteColorPickerView = findViewById(R.id.palette_color_picker);
        colorPickerView = paletteColorPickerView.findViewById(R.id.color_picker_view);
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
        if (!arduinoManager.isBluetoothEnabled()) {
            if (arduinoManager.isConnected()) {
                arduinoManager.disconnect();
            }
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(accelerometerSensorEventListener);
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
        if (!arduinoManager.isConnected()) {
            connectToBluetoothButton.setText("Connecting...");
            arduinoManager.connect();
        } else {
            arduinoManager.disconnect();
        }
    }

    private void disableBluetoothUI() {
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
        paletteColorTile.setEnabled(true);
        paletteColorPickerView.setVisibility(View.VISIBLE);
        liveCameraTile.setEnabled(true);
        liveSoundTile.setEnabled(true);
        accelerationSensorTile.setEnabled(true);
        connectToBluetoothButton.setText("Disconnect");

        updateUI(paletteColorTile);
    }

    private void sendColor(byte red, byte green, byte blue) {
        final byte data[] = new byte[]{red, green, blue};
        arduinoManager.sendData(data);
    }

    private void processData(byte[] data) {
        int color = ((0xFF & data[0]) << 16);
        color = color + ((0xFF & data[1]) << 8);
        color = color + (0xFF & data[2]);
        rootView.getBackground().setColorFilter(0xFF000000 | (0xFFFFFF & color), PorterDuff.Mode.SRC_ATOP);
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
        if (requestCode == BLUETOOTH_SETTING_REQUEST_CODE
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == LOCATION_SETTING_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            triggerAndUpdateConnection();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
