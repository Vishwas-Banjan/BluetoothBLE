package com.vbanjan.bluetoothdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

public class MainActivity extends AppCompatActivity {
    TextView textView;
    TextView tempTextView;
    private static final String DEVICE_ADDRESS = "A8:3E:0E:B4:70:23";
    //    UUID BULB_SERVICE_UUID = UUID.fromString("df178e19-c76d-06fa-8cd7-22c7728c0d6a"); //Nokia
    UUID BULB_SERVICE_UUID = UUID.fromString("df3ba82c-96c6-ca1b-6667-15a1387df982"); // OP 5
    //    UUID BULB_SERVICE_UUID = UUID.fromString("df12e166-2f80-b799-40dc-6ed8a52ede1f"); //MOTO
    UUID BULB_SWITCH_CHAR_UUID = UUID.fromString("FB959362-F26E-43A9-927C-7E17D8FB2D8D");
    UUID BULB_TEMP_CHAR_UUID = UUID.fromString("0CED9345-B31F-457D-A6A2-B3DB9B03E39A");
    UUID BULB_TEMP_DESCRIPTOR_UUID;
    UUID BULB_BEEP_CHAR_UUID = UUID.fromString("EC958823-F26E-43A9-927C-7E17D8F32A90");
    String TAG = "demo";
    BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;

    BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
    ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build();

    ScanFilter scanFilter = new ScanFilter.Builder()
            .setServiceUuid(new ParcelUuid(BULB_SERVICE_UUID)).build();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        enableBluetooth();
        textView = findViewById(R.id.TestTextView);
        tempTextView = findViewById(R.id.tempTextView);

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

    Runnable discoverServicesRunnable;
    Handler bleHandler = new Handler();
    BluetoothGatt mGatt;
    BluetoothDevice myDevice;
    BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    int bondstate = myDevice.getBondState();
                    // Take action depending on the bond state
                    if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
                        Log.d(TAG, "onConnectionStateChange: CONNECTED");
                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        int delayWhenBonded = 0;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            delayWhenBonded = 1000;
                        }

                        final int delay = bondstate == BOND_BONDED ? delayWhenBonded : 0;
                        discoverServicesRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", myDevice.getAddress(), delay));
                                boolean result = gatt.discoverServices();
                                if (!result) {
                                    Log.e(TAG, "discoverServices failed to start");
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        bleHandler.postDelayed(discoverServicesRunnable, delay);
                    } else if (bondstate == BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        Log.i(TAG, "waiting for bonding to complete");
                    }
                }
            } else {
                // An error happened...figure out what happened!
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered: ");
            final List<BluetoothGattService> services = gatt.getServices();
            Log.i(TAG, String.format(Locale.ENGLISH, "discovered %d services for '%s'", services.size(), myDevice.getAddress()));

            // Get the TEMP characteristic
            BluetoothGattCharacteristic temp_characteristic = gatt
                    .getService(BULB_SERVICE_UUID)
                    .getCharacteristic(BULB_TEMP_CHAR_UUID);
            BULB_TEMP_DESCRIPTOR_UUID = UUID.fromString(String.valueOf(temp_characteristic.getDescriptors().get(0).getUuid()));

            BluetoothGattCharacteristic switch_characteristic = gatt
                    .getService(BULB_SERVICE_UUID)
                    .getCharacteristic(BULB_SWITCH_CHAR_UUID);

            BluetoothGattCharacteristic beep_characteristic = gatt
                    .getService(BULB_SERVICE_UUID)
                    .getCharacteristic(BULB_BEEP_CHAR_UUID);


            // Enable notifications for this characteristic locally
            gatt.setCharacteristicNotification(temp_characteristic, true);

            BluetoothGattDescriptor temp_descriptor =
                    temp_characteristic.getDescriptor(BULB_TEMP_DESCRIPTOR_UUID);
            temp_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

//            gatt.writeDescriptor(temp_descriptor); //START NOTIFICATION

//            gatt.readCharacteristic(switch_characteristic); //READ BEEP STATUS

//            gatt.readCharacteristic(beep_characteristic); //READ SWITCH STATUS

//            switch_characteristic.setValue(ByteBuffer.allocate(4).putInt(0).array()); //WRITE BULB OFF
//            gatt.writeCharacteristic(switch_characteristic);

//            switch_characteristic.setValue(ByteBuffer.allocate(4).putInt(1).array()); //WRITE BULB ON
//            gatt.writeCharacteristic(switch_characteristic);

//            beep_characteristic.setValue("Beeping".getBytes()); //WRITE SOUND ON
//            gatt.writeCharacteristic(beep_characteristic);

//            beep_characteristic.setValue("Not Beeping".getBytes()); //WRITE SOUND OFF
//            gatt.writeCharacteristic(beep_characteristic);

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: ");
            writeCharacteristics(characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            if (BULB_TEMP_DESCRIPTOR_UUID.equals(descriptor.getUuid())) {
                Log.d(TAG, "onDescriptorWrite: ");
                BluetoothGattCharacteristic characteristic = gatt
                        .getService(BULB_SERVICE_UUID)
                        .getCharacteristic(BULB_TEMP_CHAR_UUID);
                gatt.readCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            readCharacteristics(characteristic);
        }

        private void readCharacteristics(BluetoothGattCharacteristic
                                                 characteristic) {
            if (BULB_TEMP_CHAR_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "readCharacteristics: Updating Temp");
                final byte[] data = characteristic.getValue();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTemp(data);
                    }
                });
            } else if (BULB_SWITCH_CHAR_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "readCharacteristics: Read Switch Value");
                final byte[] data = characteristic.getValue();
                try {
                    Log.d(TAG, "readCharacteristics: " + new String(data, "ISO-8859-1"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else if (BULB_BEEP_CHAR_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "readCharacteristics: Read BEEP Value");
                final byte[] data = characteristic.getValue();
                try {
                    Log.d(TAG, "readCharacteristics: " + new String(data, "ISO-8859-1"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        private void writeCharacteristics(BluetoothGattCharacteristic characteristic) {
            if (BULB_SWITCH_CHAR_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "writeCharacteristics: WRITE SWITCH VALUE");
            } else if (BULB_BEEP_CHAR_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "writeCharacteristics: BEEP UPDATE");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            readCharacteristics(characteristic);
        }
    };
    private final ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult: ");
            // We scan with report delay > 0. This will never be called.
            if (result != null) {
//                ScanResult result = results.get(0);
                BluetoothDevice device = result.getDevice();
                final String deviceAddress = device.getAddress();
                textView.setText(result.toString());

                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
                        mGatt = myDevice.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                    }
                });


                // Device detected, we can automatically connect to it and stop the scan
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onBatchScanResults(List<ScanResult> results) {

        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed: ");
            // Scan error
        }
    };

    public void updateTemp(byte[] data) {
        try {
            tempTextView.setText("Temperature: " + new String(data, "ISO-8859-1") + " F");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}
