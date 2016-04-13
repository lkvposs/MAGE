package edu.bucknell.mage.mage_v1.ble;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.EditText;

import com.digi.xbee.api.connection.IConnectionInterface;
import com.digi.xbee.api.connection.android.CircularByteBuffer;
import com.digi.xbee.api.exceptions.InterfaceInUseException;
import com.digi.xbee.api.exceptions.InvalidConfigurationException;
import com.digi.xbee.api.exceptions.InvalidInterfaceException;
import com.digi.xbee.api.exceptions.PermissionDeniedException;
import com.digi.xbee.api.utils.HexUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 2/26/2016.
 */
public class BLEXBeeInterface implements IConnectionInterface {

    private final static String TAG = "BLEXBeeInterface";
    private String bleTag;
    private BLEInputStream inputStream;
    private BLEOutputStream outputStream;
    private boolean isConnected = false;
    private static boolean isBLEConnected = false;
    Context mContext;
    private Handler mHandler;
    private CircularByteBuffer inputBuffer;
    private CircularByteBuffer outputBuffer;
    private Thread receiveThread;
    private boolean receivedMessage = false;
    private boolean working = false;
    private boolean sendThreadRunning = false;

    /* UART UUIDs needed for Bluetooth connection */
    private static final ParcelUuid UART_SERVICE = ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    /* UIID for the UART BLE client characteristic which is necessary for notifications */
    private static final UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    BluetoothGattCharacteristic TX;
    BluetoothGattCharacteristic RX;
    BluetoothGattService UART_service;

    private ScanCallback scanCallback;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt mConnectedGatt;

    /* Stops scanning after 5 seconds */
    private static final long SCAN_PERIOD = 10000;

    private int receivedNumBytes = 0;
    private int expectedNumBytes = 0;
    private boolean readLengthMSBfirst = false;
    private boolean readLengthLSBfirst = false;

    private Timer timer;
    private static boolean TIMER_RUNNING = false;
    private long receiveTimeout = 2000;
    private TimerTask messageReceiveTimer;

    public BLEXBeeInterface(Context context, String bleTag) {
        this.bleTag = bleTag;
        this.mContext = context;
    }

    public void establishBLEConnection() {
        mHandler = new Handler();

        // Check if a BLE device has been provided to connect to
        if (bleTag == null) {
            // No BLE device discovered
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("Missing BLE Device")
                    .setMessage("Please enter the tag found on your BLE shield in" +
                            "order to connection to the MAGE network.");
            final EditText ble_tag = new EditText(mContext);
            builder.setView(ble_tag)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Save BLE tag in SharedPreferences file
                            // Assumes the user will enter the correct information
                            String new_ble_tag = ble_tag.getText().toString();
                            SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(mContext.getResources().getString(R.string.settings_BLEtag), new_ble_tag);
                            editor.commit();

                            // Save the entered BLE Tag for this interface as well
                            bleTag = new_ble_tag;
                            if (!new_ble_tag.equals("")) {
                                // Connect to the device tag specified by the user
                                connectToBLEDevice();
                            }
                        }
                    });
        }
        else {
            connectToBLEDevice();
        }
    }

    /*
     * Checks to make sure that a BLE device has been specified to connect to.
     */
    @Override
    public void open() throws InterfaceInUseException, InvalidInterfaceException, InvalidConfigurationException, PermissionDeniedException {
        if (!isBLEConnected) {
            // TODO: 2/27/2016 SOMETHING
        }
        else {
            inputStream = new BLEInputStream(this);
            startReadThread();
            outputStream = new BLEOutputStream(this);
            inputBuffer = new CircularByteBuffer(1024);
            outputBuffer = new CircularByteBuffer(1024);
            Log.w(TAG, "\nCircular thing should have been created\n");
            isConnected = true;
        }
    }

    /*
     * Establishes a connection between the app and a specified BLE device. The BLE
     * device should be passed into the constructor of this interface. If no BLE device
     * has been entered by the user, they will be prompted to enter a device.
     *
     * A BLE device is connected to by scanning for ONLY the device passed.
     */
    public void connectToBLEDevice() {
        // Initialize a Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Receiver needed for when Bluetooth becomes enabled programmatically
        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // Check to make sure that Bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Allow the user to turn on Bluetooth
            AlertDialog.Builder alertbox = new AlertDialog.Builder(mContext);
            alertbox.setTitle(mContext.getResources().getString(R.string.bluetooth_not_enabled_title));
            alertbox.setMessage(mContext.getResources().getString(R.string.bluetooth_not_enabled_message));
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

    /*
     * Scan for available BLE devices.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void scanLollipop() {
        if (scanCallback == null) {
            initCallbackLollipop();
        }

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
     * Callback to handle BLE scan events.
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
                //Log.d(TAG, "onBatchScanResults: " + results.size() + " results");
                for (ScanResult result: results){
                    processResult(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                //Log.w(TAG, "BLE Scan Failed: " + errorCode);
            }

            private void processResult(ScanResult result) {
                Log.i(TAG, "New LE Device: " + result.getDevice().getName());
                // Broadcast this newly discovered BLE device to be able
                // to display it to the user in case they need to connect to it
                Intent intent = new Intent("NEW_BLE_DEVICE_DISCOVERED");
                intent.putExtra("newBLEdeviceName", result.getDevice().getName());
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

                // Check the name of the device that we just connected to
                String deviceName = result.getDevice().getName();
                if (deviceName.equals(bleTag)) {
                    // This is the device we want - connect to the GATT Profile of the device
                    BluetoothDevice device = result.getDevice();
                    mConnectedGatt = device.connectGatt(mContext, true, mGattCallback);
                }
            }
        };
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: " + status + " -> " + newState);
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
                Intent intent = new Intent("BLUETOOTH_DISCONNECTED");
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                Log.d(TAG, "GATT failure, disconnecting...");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            /* Get the UART Service */
            UART_service = gatt.getService(UART_SERVICE_UUID);


            /* Save reference to each UART characteristic */
            TX = UART_service.getCharacteristic(UART_TX);
            RX = UART_service.getCharacteristic(UART_RX);

            /* Setup notifications on RX characteristic changes (i.e. data received) */
            gatt.setCharacteristicNotification(RX, true);
            // Enable remote notifications
            BluetoothGattDescriptor desc = RX.getDescriptor(CLIENT_UUID);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);

            if (!isBLEConnected) {
                isBLEConnected = true;
                Intent intent = new Intent("BLUETOOTH_CONNECTION_ESTABLISHED");
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (UART_RX.equals(characteristic.getUuid())) {
                // Get the received UART contents
                //String received_message = characteristic.getStringValue(0);
                byte[] received_bytes = characteristic.getValue();
                Log.e(TAG, HexUtils.prettyHexString(received_bytes));
                // Add the received message to our buffer
                inputBuffer.write(received_bytes, 0, received_bytes.length);
                receivedNumBytes += received_bytes.length;

                /*
                 * expectedNumBytes + 4:
                 * 1 byte for the checksum
                 * 1 byte for the start byte
                 * 2 bytes for the length
                 */

                // Updating expectedNumBytes
                if (readLengthLSBfirst) {
                    readLengthLSBfirst = false;
                    expectedNumBytes += (received_bytes[0]) + 4;
                }
                else if (readLengthMSBfirst) {
                    readLengthMSBfirst = false;
                    expectedNumBytes = ((received_bytes[0] << 8) + received_bytes[1]) + 4;
                }
                else if (expectedNumBytes == 0) {
                    if (received_bytes.length >= 3) {
                        // Receiving only first 1 or 2 bytes of new message with nothing in input buffer
                        // Prevents array out of bounds exception
                        expectedNumBytes = ((received_bytes[1] << 8) + received_bytes[2]) + 4;
                    }
                }

                // Comparing expectedNumBytes vs. receivedNumBytes and deciding if receivedMessage should be true
                // receviedMessage is only set to true once there is a break in messages
                if (expectedNumBytes == receivedNumBytes) {
                    receivedMessage = true;
                    expectedNumBytes = 0;
                    receivedNumBytes = 0;
                }
                else if (expectedNumBytes < receivedNumBytes) {
                    // Two messages received

                    int startByteIndex = received_bytes.length-(receivedNumBytes-expectedNumBytes);
                    if (receivedNumBytes - expectedNumBytes >= 3) {
                        // The length of the next packet received made it through with the latest BLE packet
                        receivedNumBytes = receivedNumBytes - expectedNumBytes;
                        expectedNumBytes = ((received_bytes[(startByteIndex)+1] << 8) + received_bytes[startByteIndex + 2]) + 4;
                    }
                    else if (receivedNumBytes - expectedNumBytes == 2) {
                        readLengthLSBfirst = true;
                        receivedNumBytes = 2;
                        expectedNumBytes = received_bytes[startByteIndex+1] << 8;
                    }
                    else if(receivedNumBytes - expectedNumBytes == 1) {
                        readLengthMSBfirst = true;
                        receivedNumBytes = 1;
                    }

                }
                // else if expectedNumBytes > receivedNumBytes: do nothing
            }
        }


        /*
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (UART_RX.equals(characteristic.getUuid())) {
                // Get the received UART contents
                //String received_message = characteristic.getStringValue(0);
                byte[] received_bytes = characteristic.getValue();
                Log.e(TAG, HexUtils.prettyHexString(received_bytes));
                // Add the received message to our buffer
                inputBuffer.write(received_bytes, 0, received_bytes.length);
                if (received_bytes.length < 20) {
                    if (TIMER_RUNNING) {
                        timer.cancel();
                        timer.purge();
                        TIMER_RUNNING = false;
                    }
                    receivedMessage = true;
                }
                else {
                    // If a timer is running, cancel it and start a new one
                    if (TIMER_RUNNING) {
                        timer.cancel();
                        timer.purge();
                        TIMER_RUNNING = false;
                    }
                    // Start a new timer
                    timer = new Timer(true);
                    messageReceiveTimer  = new TimerTask() {
                        @Override
                        public void run() {
                            receivedMessage = true;
                        }
                    };
                    timer.schedule(messageReceiveTimer, receiveTimeout);
                    TIMER_RUNNING = true;
                }
            }
        } */

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead: we tried to do a read");
            synchronized (mContext) {
                try {
                    mContext.wait(350);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (outputBuffer.availableToRead() > 0) {
                // There is still data to send
                // Send the next 20 bytes or whatever is left
                int dataToSendLength = (outputBuffer.availableToRead() <= 20) ? outputBuffer.availableToRead() : 20;
                byte[] dataToSend = new byte[dataToSendLength];
                outputBuffer.read(dataToSend, 0, dataToSendLength);
                TX.setValue(dataToSend);
                mConnectedGatt.writeCharacteristic(TX);
            }
            else {
                sendThreadRunning = false;
            }
        }
    };

    @Override
    public void close() {
        stopScan();
        stopReadThread();
        inputStream = null;
        outputStream = null;
        isConnected = false;
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);

        mConnectedGatt.disconnect();
        mConnectedGatt.close();
    }

    public void stopScan(){
        // Stop scan and flush pending scan
        bluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(scanCallback);
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);

        Log.d(TAG, "BLE Scan stopped");
    }

    @Override
    public boolean isOpen() {
        return isConnected;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void writeData(byte[] data) throws IOException {
        if(data == null) {
            throw new NullPointerException("Data to be sent cannot be null.");
        } else {
            if(this.getOutputStream() != null) {
                this.getOutputStream().write(data);
                this.getOutputStream().flush();
            }

        }
    }

    @Override
    public void writeData(byte[] data, int offset, int length) throws IOException {
        if(data == null) {
            throw new NullPointerException("Data to be sent cannot be null.");
        } else if(offset < 0) {
            throw new IllegalArgumentException("Offset cannot be less than 0.");
        } else if(length < 1) {
            throw new IllegalArgumentException("Length cannot be less than 0.");
        } else if(offset >= data.length) {
            throw new IllegalArgumentException("Offset must be less than the data length.");
        } else if(offset + length > data.length) {
            throw new IllegalArgumentException("Offset + length cannot be great than the data length.");
        } else {
            if(this.getOutputStream() != null) {
                this.getOutputStream().write(data, offset, length);
                this.getOutputStream().flush();
            }

        }
    }

    @Override
    public int readData(byte[] data) throws IOException {
        if(data == null) {
            throw new NullPointerException("Buffer cannot be null.");
        } else {
            int readBytes = 0;
            if(this.getInputStream() != null) {
                readBytes = this.getInputStream().read(data);
            }

            return readBytes;
        }
    }

    @Override
    public int readData(byte[] data, int offset, int length) throws IOException {
        if(data == null) {
            throw new NullPointerException("Buffer cannot be null.");
        } else if(offset < 0) {
            throw new IllegalArgumentException("Offset cannot be less than 0.");
        } else if(length < 1) {
            throw new IllegalArgumentException("Length cannot be less than 0.");
        } else if(offset >= data.length) {
            throw new IllegalArgumentException("Offset must be less than the buffer length.");
        } else if(offset + length > data.length) {
            throw new IllegalArgumentException("Offset + length cannot be great than the buffer length.");
        } else {
            int readBytes = 0;
            if(this.getInputStream() != null) {
                readBytes = this.getInputStream().read(data, offset, length);
            }

            return readBytes;
        }
    }

    public CircularByteBuffer getInputBuffer() {
        return this.inputBuffer;
    }

    public void startBLESend(byte[] data) {
        Log.d(TAG, "Calling startBLESend with data " + HexUtils.prettyHexString(data));
        // Push all data to the output buffer
        outputBuffer.write(data, 0, data.length);

        // Check to see if a BLE TX is already taking place
        // If it is, the added data will be sent out in time
        // If not, send the data now
        if (!sendThreadRunning) {
            // Start sending up to first 20 bytes of data
            sendThreadRunning = true;

            // Get how many bytes there are to send
            int dataToSendLength = (data.length <= 20) ? data.length : 20;
            byte[] dataToSend = new byte[dataToSendLength];
            outputBuffer.read(dataToSend, 0, dataToSendLength);
            TX.setValue(dataToSend);
            synchronized (mContext) {
                try {
                    mContext.wait(350);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mConnectedGatt.writeCharacteristic(TX);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                    // Bluetooth has successfully been enabled for this device -- scan for available connections
                    scanLollipop();
                }
            }
        }
    };

    private void startReadThread() {
        receiveThread = new Thread() {
            @Override
            public void run() {
                BLEXBeeInterface.this.working = true;

                while(BLEXBeeInterface.this.working) {
                    // Check to see if a new message was received
                    if (BLEXBeeInterface.this.receivedMessage) {
                        BLEXBeeInterface.this.receivedMessage = false;
                        synchronized (BLEXBeeInterface.this) {
                            BLEXBeeInterface.this.notify();
                        }
                    }
                }
            }
        };
        receiveThread.start();
    }

    private void stopReadThread() {
        this.working = false;
        if (this.receiveThread != null) {
            this.receiveThread.interrupt();
        }
    }
}
