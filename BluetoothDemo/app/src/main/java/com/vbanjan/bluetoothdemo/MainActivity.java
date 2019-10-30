package com.vbanjan.bluetoothdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class MainActivity extends AppCompatActivity {
    TextView textView;
    private static final String DEVICE_ADDRESS = "A8:3E:0E:B4:70:23";
    UUID BULB_SERVICE_UUID = UUID.fromString("df178e19-c76d-06fa-8cd7-22c7728c0d6a");
    UUID BULB_CHAR_UUID = UUID.fromString("FB959362-F26E-43A9-927C-7E17D8FB2D8D");
    UUID BULB_TEMP_CHAR_UUID = UUID.fromString("0CED9345-B31F-457D-A6A2-B3DB9B03E39A");
    UUID BULB_BEEP_CHAR_UUID = UUID.fromString("EC958823-F26E-43A9-927C-7E17D8F32A90");
    String TAG = "demo";
    BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;

    BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
    ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(1000)
            .build();

    ScanFilter scanFilter = new ScanFilter.Builder()
            .setServiceUuid(new ParcelUuid(BULB_SERVICE_UUID)).build();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        enableBluetooth();
        textView = findViewById(R.id.TestTextView);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        scanner.startScan(Arrays.asList(scanFilter), settings, mScanCallback);
    }

    public void enableBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }
    }

    BluetoothGatt mGatt;
    BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT client. Attempting to start service discovery");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT client");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // Get the TEMP characteristic
            BluetoothGattCharacteristic characteristic = gatt
                    .getService(BULB_SERVICE_UUID)
                    .getCharacteristic(BULB_TEMP_CHAR_UUID);

            // Enable notifications for this characteristic locally
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor =
                    characteristic.getDescriptor(BULB_TEMP_CHAR_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite: ");
            super.onDescriptorWrite(gatt, descriptor, status);
            if (BULB_TEMP_CHAR_UUID.equals(descriptor.getUuid())) {
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(BULB_SERVICE_UUID)
                        .getCharacteristic(BULB_TEMP_CHAR_UUID);
                gatt.readCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead: ");
            super.onCharacteristicRead(gatt, characteristic, status);
            readTempCharacteristic(characteristic);
        }

        private void readTempCharacteristic(BluetoothGattCharacteristic
                                                    characteristic) {
            if (BULB_TEMP_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "readTempCharacteristic: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0));
                Log.d(TAG, "readTempCharacteristic: " + characteristic.describeContents());
//                int value = ByteBuffer.wrap(data).getInt();
                textView.setText(new String(data));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            readTempCharacteristic(characteristic);
        }
    };
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult: ");
            // We scan with report delay > 0. This will never be called.
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (!results.isEmpty()) {
                ScanResult result = results.get(0);
                BluetoothDevice device = result.getDevice();
                String deviceAddress = device.getAddress();
                textView.setText(result.toString());

                BluetoothDevice myDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
                mGatt = myDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                // Device detected, we can automatically connect to it and stop the scan
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed: ");
            // Scan error
        }
    };


}
