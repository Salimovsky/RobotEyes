package com.addi.salim.robot_eyes;

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
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.addi.salim.robot_eyes.BLE.RBLGattAttributes;
import com.addi.salim.robot_eyes.BLE.RBLService;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import static android.content.Context.BIND_AUTO_CREATE;

public class ArduinoManager {
    private final static String TAG = ArduinoManager.class.getSimpleName();


    // defined commands
    private final static byte CMD_SEND_NEW_FACE_ANGLE = 0x01;
    private final static byte CMD_SEND_FACE_REMOVED = 0x02;
    private final static byte CMD_SEND_ALARM = 0x03;
    private final static byte CMD_SEND_DISMISS_ALARM = 0x04;
    private final static byte CMD_RECEIVE_FACE_DISTANCE = 0x01;

    private static ArduinoManager singleton;
    // Define the device name and the length of the name
    // Note the device name and the length should be consistent with the ones defined in the Duo sketch
    private String mTargetDeviceName = "Salimmm";
    private int mNameLen = mTargetDeviceName.length() + 1;

    // Declare all Bluetooth stuff
    private BluetoothGattCharacteristic mCharacteristicTx = null;
    private RBLService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;

    private final Context context;

    private boolean isConnected = false;
    private boolean isScanCompleted = false;

    private static final long SCAN_PERIOD = 2000;   // millis

    final private static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Map<ConnectionListener, Boolean> listeners = new WeakHashMap<>();

    // Process service connection. Created by the RedBear Team
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                notifyOnConnectionError();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Callback function to search for the target Duo board which has matched UUID
    // If the Duo board cannot be found, debug if the received UUID matches the predefined UUID on the board
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {
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
    };

    // Process the Gatt and get data if there is data coming from Duo board. Created by the RedBear Team
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(context, "Disconnected",
                        Toast.LENGTH_SHORT).show();
                isConnected = false;
                notifyOnDisconnected();
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Toast.makeText(context, "Connected",
                        Toast.LENGTH_SHORT).show();

                getGattService(mBluetoothLeService.getSupportedGattService());
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] receivedData = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
                notifyOnDataReceived(receivedData);
            } else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
            }
        }
    };

    public ArduinoManager(Context context) {
        this.context = context;
    }

    public static synchronized ArduinoManager getInstance(Context context) {
        if (singleton == null) {
            singleton = new ArduinoManager(context);
        }
        return singleton;
    }

    public void initialize() {
        final BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(context, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            notifyOnConnectionError();
            return;
        }

        Intent gattServiceIntent = new Intent(context, RBLService.class);
        context.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        context.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private void sendData(byte[] commandAndData) {
        mCharacteristicTx.setValue(commandAndData);
        mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
    }

    public void sendObjectAngle(int id, float radianAngle) {
        final int roundedAngle = (int) (radianAngle * 0xFF);
        final byte sign = (byte) ((radianAngle < 0) ? 0B10000000 : 0);
        final byte[] commandAndData = {CMD_SEND_NEW_FACE_ANGLE, (byte) (id & 0xFF), (byte) (((byte) ((roundedAngle & 0xFF00) >> 8)) | sign), (byte) (roundedAngle & 0xFF)};
        sendData(commandAndData);
    }

    public void sendObjectRemoved(int id) {
        final byte[] commandAndData = {CMD_SEND_FACE_REMOVED, (byte) (id & 0xFF), 0x00, 0x00};
        sendData(commandAndData);
    }

    public void sendObjectDistanceAlarm(int id, int distanceCm) {
        final byte[] commandAndData = {CMD_SEND_ALARM, (byte) (id & 0xFF), 0x00, 0x00};
        sendData(commandAndData);
        notifyOnAlarm(distanceCm);
    }

    public void sendDismissAlarm() {
        final byte[] commandAndData = {CMD_SEND_DISMISS_ALARM, 0x00, 0x00, 0x00};
        sendData(commandAndData);
        notifyDismissAlarm();
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    private void notifyOnConnected() {
        for (ConnectionListener listener : listeners.keySet()) {
            listener.onConnected();
        }
    }

    private void notifyOnDisconnected() {
        for (ConnectionListener listener : listeners.keySet()) {
            listener.onDisconnected();
        }
    }

    private void notifyOnConnectionError() {
        for (ConnectionListener listener : listeners.keySet()) {
            listener.onConnectionError();
        }
    }

    private void notifyOnAlarm(int distanceCm) {
            for (ConnectionListener listener : listeners.keySet()) {
                listener.onAlarmTriggered(distanceCm);
            }
    }

    private void notifyDismissAlarm() {
        for (ConnectionListener listener : listeners.keySet()) {
            listener.onAlarmDismissed();
        }
    }


    private void notifyOnDataReceived(byte[] data) {
        final byte command = data[0];
        final int id = (0x000000FF & data[1]);
        final int distanceInCm = (0x000000FF & data[2]);

        if (command == CMD_RECEIVE_FACE_DISTANCE) {
            for (ConnectionListener listener : listeners.keySet()) {
                listener.onDistanceReceived(id, distanceInCm);
            }
        }
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

    // Get Gatt service information for setting up the communication
    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null) {
            return;
        }

        isConnected = true;
        notifyOnConnected();
        mCharacteristicTx = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx, true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    // Scan all available BLE-enabled devices
    public void connect() {
        if (isScanCompleted) {
            mBluetoothLeService.connect(mDeviceAddress);
        } else {
            // Scan all available devices through BLE
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
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void disconnect() {
        mBluetoothLeService.disconnect();
        mBluetoothLeService.close();
        isConnected = false;
        notifyOnDisconnected();
    }

    public void addListener(ConnectionListener listener) {
        listeners.put(listener, Boolean.TRUE);
    }

    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }
}
