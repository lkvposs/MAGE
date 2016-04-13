package edu.bucknell.mage.mage_v1.ble;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import edu.bucknell.mage.mage_v1.Game_Framework;
import edu.bucknell.mage.mage_v1.Home;
import edu.bucknell.mage.mage_v1.MessageReceiver;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 3/3/2016.
 *
 * MAGE App entry point that establishes BLE connection for the app. If something goes wrong during
 * the setup connection, destroy/quit out of the app and relaunch to try the connection again.
 */
public class BLEConnect extends AppCompatActivity {

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiverService;
    Button relaunchButton;
    Button saveNewBLEDeviceButton;
    EditText bleDeviceToConnectTo;
    FloatingActionButton goToHome;
    List<String> discoveredBLEDevices = new ArrayList<String>();

    /*
     * Connection needed to bind this activity to the message receiver service
     */
    private ServiceConnection mConnectionMessageReceiver = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to MessageReceiver, cast the IBinder and get MessageBinder instance
            MessageReceiver.MessageBinder binder = (MessageReceiver.MessageBinder) service;
            mMessageReceiverService = binder.getService();
            mBoundMessageReceiver = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("XBEE_DEVICE_OPENED_SUCCESSFULLY".equals(intent.getAction())) {
                // Everything opened correctly -- launch the MAGE Home activity
                Intent i = new Intent(context, Home.class);
                startActivity(i);
            }
            else if ("FAILED_OPENING_XBEE".equals(intent.getAction())) {
                // Error opening XBee device/opening connection interface
                // Force quit the app and relaunch
                restart(context, 1);
            }
            else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                    // Bluetooth has successfully been enabled for this device
                    // Start the MAGE network XBee message service
                    Intent messageService = new Intent(context, MessageReceiver.class);
                    startService(messageService);
                    bindService(messageService, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);
                }
            }
            else if ("NEW_BLE_DEVICE_DISCOVERED".equals(intent.getAction())) {
                String deviceName = intent.getStringExtra("newBLEdeviceName");

                // Check to see if this device has already been discovered
                int i;
                boolean foundDevice = false;
                for (i = 0; i < discoveredBLEDevices.size(); i++) {
                    if (discoveredBLEDevices.get(i).equals(deviceName)) {
                        // The device has already been added to the list -- do not add it again
                        foundDevice = true;
                        break;
                    }
                }
                if (!foundDevice) {
                    // This is the first time we have discovered this device
                    // Add it to the list
                    discoveredBLEDevices.add(deviceName);
                    addBLEDeviceToView(deviceName);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * The below is used to ensure that when a user clicks on the MAGE launcher
         * icon, if the app has already been opened, then the app is resumed at the
         * location that the user was last at, and does not restart the app from scratch.
         */
        if (!isTaskRoot()
            && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)
            && getIntent().getAction() != null
            && getIntent().equals(Intent.ACTION_MAIN)) {
            finish();
            return;
        }

        setContentView(R.layout.bleconnect_layout);

        // Establish the broadcast receive to get new MAGE network messages
        IntentFilter filter = new IntentFilter("XBEE_DEVICE_OPENED_SUCCESSFULLY");
        filter.addAction("FAILED_OPENING_XBEE");
        filter.addAction("NEW_BLE_DEVICE_DISCOVERED");
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);

        relaunchButton = (Button) findViewById(R.id.ble_connect_relaunch_button);
        relaunchButton.setOnClickListener(buttonHandler);

        saveNewBLEDeviceButton = (Button) findViewById(R.id.save_ble_device);
        saveNewBLEDeviceButton.setOnClickListener(buttonHandler);

        bleDeviceToConnectTo = (EditText) findViewById(R.id.ble_connecting_to_device);

        /*
         * If this is the first time we are launching the app, there will be nothing
         * in the shared preferences file for a BLE device to connect to. The way
         * that the BLE/XBee interface is currently setup, if there is nothing in
         * this file, then we will never scan for or connect to any BLE devices.
         * Check if the BLE tag entry in the shared preferences file is null,
         * indicating that this is the first time that the app was launched. If it
         * is null, put a random value into this field so that the user will at least
         * be able to scan for BLE devices and see what is available to connect to.
         */
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String checkBLETag = sharedPreferences.getString(getResources().getString(R.string.settings_BLEtag), "~~");
        if (checkBLETag.equals("~~")) {
            // No value has been entered yet, add one ourselves
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(getResources().getString(R.string.settings_BLEtag), "****");
            editor.commit();
        }

        bleDeviceToConnectTo.setText(checkBLETag);

        checkBLEenabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMessageReceiverService != null) {
            if (mMessageReceiverService.getBLEConnectionStatus()) {
                goToHome = (FloatingActionButton) findViewById(R.id.fab_to_home);
                goToHome.setVisibility(View.VISIBLE);
                goToHome.setOnClickListener(buttonHandler);
            }
        }
    }

    @Override
    protected void onDestroy() {

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        // Unbind from message receiver service
        if (mBoundMessageReceiver) {
            unbindService(mConnectionMessageReceiver);
            mBoundMessageReceiver = false;
        }
        super.onDestroy();
    }

    View.OnClickListener buttonHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.ble_connect_relaunch_button) {
                restart(v.getContext(), 1);
            }
            else if (v.getId() == R.id.save_ble_device) {
                saveNewBLEDevice();
            }
            else if (v.getId() == R.id.fab_to_home) {
                // Go to the home activity
                Intent i = new Intent(v.getContext(), Home.class);
                startActivity(i);
            }
        }
    };

    /*
     * If you need to connect to a new BLE device, after entering in the name,
     * when the user presses the save button, the new BLE device name will
     * be saved to Shared Preferences. Then the app will restart and try to
     * connect anew to the new device.
     */
    private void saveNewBLEDevice() {
        // Retrieve the BLE device name entered into the field
        String newBLEdevice = bleDeviceToConnectTo.getText().toString();

        // Save the value in the Shared Preferences file
        SharedPreferences sharedPreferences = this.getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getResources().getString(R.string.settings_BLEtag), newBLEdevice);
        editor.commit();

        restart(this, 1);
    }

    /*
     * Restart the app when the BLE connection does not work as it should.
     */
    private void restart(Context context, int delay) {
        if (delay == 0) {
            delay = 1;
        }
        Intent restartIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        PendingIntent intent = PendingIntent.getActivity(context, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + delay, intent);
        System.exit(2);
    }

    private void checkBLEenabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Check to make sure that Bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Allow the user to turn on Bluetooth
            AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
            alertbox.setTitle(getResources().getString(R.string.bluetooth_not_enabled_title));
            alertbox.setMessage(getResources().getString(R.string.bluetooth_not_enabled_message));
            alertbox.setPositiveButton(R.string.bluetooth_not_enabled_accept, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    bluetoothAdapter.enable();
                }
            });
            alertbox.show();
        }
        else {
            // Start the MAGE network XBee message service
            Intent messageService = new Intent(this, MessageReceiver.class);
            startService(messageService);
            bindService(messageService, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);
        }
    }

    /*
     * When a new BLE device is discovered, the name is broadcast and then
     * this function is called to actually show it to the user on the display.
     */
    private void addBLEDeviceToView(String bleDeviceName) {
        // Get a reference to the layout that we want to add to
        LinearLayout displayBLELayout = (LinearLayout) findViewById(R.id.display_BLE_devices_layout);

        // Create a new TextView
        TextView newBLEdevice = new TextView(this);
        LinearLayout.LayoutParams text_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        text_params.gravity = Gravity.CENTER_HORIZONTAL;
        newBLEdevice.setLayoutParams(text_params);
        newBLEdevice.setText(bleDeviceName);

        // Add the TextView to the layout
        displayBLELayout.addView(newBLEdevice);
    }
}
