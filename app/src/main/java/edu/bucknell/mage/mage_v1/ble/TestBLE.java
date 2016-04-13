package edu.bucknell.mage.mage_v1.ble;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;

import com.digi.xbee.api.XBee;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.bucknell.mage.mage_v1.BuildConfig;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 2/18/2016.
 */
public class TestBLE extends AppCompatActivity{

    private static final String TAG = "Test BLE";
    private static final String DEVICE_NAME = "Adafruit Bluefruit LE 4A01";
    /* Stops scanning after 5 seconds */
    private static final long SCAN_PERIOD = 5000;

    private Handler mHandler;

    /* UART UUIDs */
    private static final ParcelUuid UART_SERVICE = ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    /* UIID for the UART BLE client characteristic which is necessary for notifications */
    private static final UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    BluetoothGattCharacteristic TX;
    BluetoothGattCharacteristic RX;

    private ScanCallback scanCallback;
    private BluetoothAdapter bluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;

    private Context mContext;

    private Button sendMessage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_ble_layout);
        mHandler = new Handler();
        mContext = this;

        // Initialize a Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();

        // Receiver needed for when Bluetooth becomes enabled programmatically
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        sendMessage = (Button) findViewById(R.id.test_xbee_button);
        sendMessage.setOnClickListener(clickHandler);
    }

    View.OnClickListener clickHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            byte[] data = "1234567890abcdefghijklmnopqrstuvwxyz".getBytes();
            TX.setValue(data);
            mConnectedGatt.writeCharacteristic(TX);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Allow the user to turn on Bluetooth
            AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
            alertbox.setTitle(R.string.bluetooth_not_enabled_title);
            alertbox.setMessage(R.string.bluetooth_not_enabled_message);
            alertbox.setPositiveButton(R.string.bluetooth_not_enabled_accept, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    bluetoothAdapter.enable();
                }
            });
            alertbox.show();
        }
        else {
            scanLollipop();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                    // Bluetooth has successfully been enabled for this device -- scan for devices
                    scanLollipop();
                }
            }
        }
    };

    /*
     * Scan for available BLE devices.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void scanLollipop() {

        if(scanCallback == null)
            initCallbackLollipop();

        /* Stops scanning after a pre-defined scan period. */
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, SCAN_PERIOD);

        ScanFilter uartFilter = new ScanFilter.Builder()
                .setServiceUuid(UART_SERVICE)
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(uartFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
    }

    /*
     * Create the callback to handle BLE scan events.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initCallbackLollipop(){
        if(scanCallback != null) return;
        this.scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                processResult(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                Log.d(TAG, "onBatchScanResults: " + results.size() + " results");
                for (ScanResult result: results){
                    processResult(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.w(TAG, "BLE Scan Failed: " + errorCode);
            }

            private void processResult(ScanResult result) {
                Log.i(TAG, "New LE Device: " + result.getDevice().getName());
                BluetoothDevice device = result.getDevice();
                mConnectedGatt = device.connectGatt(mContext, true, mGattCallback);
            }
        };
    }

    public void stopScan(){
        // Stop scan and flush pending scan
        bluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(scanCallback);
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);

        if(BuildConfig.DEBUG) Log.d("BLE", "BLE Scan stopped");
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            //Log.d(TAG, "Connection State Change: " + status + " -> " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                Log.d(TAG, "Discovering services...");
            }
            else if (status != BluetoothGatt.GATT_SUCCESS) {
                /* 
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
                Log.d(TAG, "GATT failure, disconnecting...");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            
            /* Get the UART Service */
            BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);

            /* Save reference to each UART characteristic */
            TX = uartService.getCharacteristic(UART_TX);
            RX = uartService.getCharacteristic(UART_RX);

            /* Setup notifications on RX characteristic changes (i.e. data received) */
            gatt.setCharacteristicNotification(RX, true);
            // Enable remote notifications
            BluetoothGattDescriptor desc = RX.getDescriptor(CLIENT_UUID);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.
             */
            if (UART_RX.equals(characteristic.getUuid())) {
                // Get the received UART contents
                String received_message = characteristic.getStringValue(0);
                Log.d(TAG, "MESSAGE: " + received_message);
            }
        }

    };
}
