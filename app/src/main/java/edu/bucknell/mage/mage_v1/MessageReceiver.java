package edu.bucknell.mage.mage_v1;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.XBeeNetwork;
import com.digi.xbee.api.ZigBeeDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.listeners.IDiscoveryListener;
import com.digi.xbee.api.listeners.IPacketReceiveListener;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.utils.HexUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

import edu.bucknell.mage.mage_v1.ble.BLEXBeeInterface;

/**
 * Created by Laura on 1/31/2016.
 */
public class MessageReceiver extends Service {

    XBeeDevice xBeeDevice;
    XBeeNetwork xBeeNetwork;

    /*
     * connectedToBluetooth is used to indicate that the system has already connected
     * to BLE at least once. If a user presses the back button when on the Home
     * activity, they will be taken back to this activity. If this value is true,
     * allow the user to get back to the Home activity.
     */
    private static boolean connectedToBluetooth = false;

    // Binder given to clients
    private final IBinder mBinder = new MessageBinder();

    /*
     * MessageBinder - Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class MessageBinder extends Binder {
        public MessageReceiver getService() {
            // Return this instance of MessageReceiver so clients can call public methods
            return MessageReceiver.this;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("BLUETOOTH_CONNECTION_ESTABLISHED".equals(intent.getAction())) {
                try {
                    // Only call open once we know that we have connected to Bluetooth
                    xBeeDevice.open();
                    xBeeDevice.addDataListener(new MyDataReceiveListener());
                    xBeeDevice.addPacketListener(new MyPacketReceiveListenter());

                    xBeeNetwork = xBeeDevice.getNetwork();
                    xBeeNetwork.setDiscoveryTimeout(15000);
                    xBeeNetwork.addDiscoveryListener(new MyDiscoveryListener());

                    // Everything has been opened correctly -- send broadcast to launch
                    // the home activity
                    connectedToBluetooth = true;
                    Intent i = new Intent("XBEE_DEVICE_OPENED_SUCCESSFULLY");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                }catch (XBeeException e) {
                    e.printStackTrace();
                    // Something went wrong when we tried to open the connection interface
                    // Broadcast message to close the app and relaunch to try again
                    Intent i = new Intent("FAILED_OPENING_XBEE");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                }
            }
            else if ("BLUETOOTH_DISCONNECTED".equals(intent.getAction())) {
                connectedToBluetooth = false;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Receiver needed for when Bluetooth becomes enabled programmatically
        IntentFilter filters = new IntentFilter("BLUETOOTH_CONNECTION_ESTABLISHED");
        filters.addAction("BLUETOOTH_DISCONNECTED");
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filters);

        // Check SharedPreferences file for BLE shield tag name to connect to
        SharedPreferences sharedPreferences = this.getApplicationContext().getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String tagName = sharedPreferences.getString(getResources().getString(R.string.settings_BLEtag), null);
        // Create a new BLE-XBee Interface using this device
        // If no device has been entred, this will be caught when the BLE connection tries to be established
        BLEXBeeInterface blexBeeInterface = new BLEXBeeInterface(this, tagName);
        blexBeeInterface.establishBLEConnection();
        xBeeDevice = new XBeeDevice(blexBeeInterface);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // keep the service running even after executing all of its code
        return START_STICKY;
    }

    private class MyDataReceiveListener implements IDataReceiveListener {
        @Override
        public void dataReceived(XBeeMessage xBeeMessage) {
            Log.e("TEST", "From " + xBeeMessage.getDevice().get64BitAddress() + " | " + HexUtils.prettyHexString(HexUtils.byteArrayToHexString(xBeeMessage.getData())) + " | " + new String(xBeeMessage.getData()));

            // Broadcast that a new message has been received
            RemoteXBeeDevice remoteXBeeDevice = xBeeMessage.getDevice();

            Game_Framework.XBeeStats xBeeStats = new Game_Framework.XBeeStats();
            xBeeStats.longAddr = remoteXBeeDevice.get64BitAddress().getValue();
            xBeeStats.shortAddr = remoteXBeeDevice.get16BitAddress().getValue();
            xBeeStats.node_id = remoteXBeeDevice.getNodeID();

            String msgContents = new String(xBeeMessage.getData());

            sendNewMessageBroadcast(msgContents, xBeeStats);
        }
    }

    /*
     * The packet receive listener is strictly used when receiving message
     * confirmations.
     */

    private class MyPacketReceiveListenter implements IPacketReceiveListener{
        @Override
        public void packetReceived(XBeePacket xBeePacket) {
            // Broadcast that a confirmation packet was received
            sendConfirmationMsgBroadcast();
        }
    }

    /*
     * The discovery listener is used to discover all XBee devices on the network.
     */
    private class MyDiscoveryListener implements IDiscoveryListener {

        @Override
        public void deviceDiscovered(RemoteXBeeDevice remoteXBeeDevice) {
            // Add the device to our network
            xBeeNetwork.addRemoteDevice(remoteXBeeDevice);
        }

        @Override
        public void discoveryError(String error) {

        }

        @Override
        public void discoveryFinished(String error) {
            // Send broadcast indicating that discovery is over
            sendDiscoveryFinishedBroadcast();
        }
    }

    private void sendDiscoveryFinishedBroadcast() {
        Intent intent = new Intent("networkDiscoveryCompleted");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendConfirmationMsgBroadcast() {
        Intent intent = new Intent("confirmationMsgReceived");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /*
     * Name: sendNewMessageBroadcast
     * Called whenever a new message is received via Bluetooth. Generates a new intent
     * to be sent via a local broadcast to the activity that started this service in order
     * for the activity to process the new message.
     */
    private void sendNewMessageBroadcast(String msgContents, Game_Framework.XBeeStats XBeeStats) {
        Intent intent = new Intent("newMessageReceived");
        intent.putExtra("msgContents", msgContents);
        intent.putExtra("xBeeLongAddr", XBeeStats.longAddr);
        intent.putExtra("xBeeShortAddr", XBeeStats.shortAddr);
        intent.putExtra("xBeeNI", XBeeStats.node_id);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        xBeeDevice.close();
        super.onDestroy();
    }

    // Method for clients
    /*
     * Send message via the MAGE network to specified address
     * OUT-GOING messages to KITS
     */
    public void sendMessage(String message, boolean broadcast, Game_Framework.XBeeStats XBeeStats) {

        if (broadcast) {
            try {
                xBeeDevice.sendBroadcastData(message.getBytes());
            } catch (XBeeException e) {
                e.printStackTrace();
            }
        }
        else {
            RemoteXBeeDevice remoteXBeeDevice = new RemoteXBeeDevice(xBeeDevice, new XBee64BitAddress(XBeeStats.longAddr), new XBee16BitAddress(XBeeStats.shortAddr), XBeeStats.node_id);
            try {
                xBeeDevice.sendData(remoteXBeeDevice, message.getBytes());
            } catch (XBeeException e) {
                e.printStackTrace();
            }
        }

    }

    // Method for clients
    /*
     * Send message via the MAGE network to specified address
     * OUT-GOING messages to NODES
     */
    public void sendMessage(byte[] message, boolean broadcast, Game_Framework.XBeeStats XBeeStats) {

        if (broadcast) {
            try {
                xBeeDevice.sendBroadcastData(message);
            } catch (XBeeException e) {
                e.printStackTrace();
            }
        }
        else {
            RemoteXBeeDevice remoteXBeeDevice = new RemoteXBeeDevice(xBeeDevice, new XBee64BitAddress(XBeeStats.longAddr), new XBee16BitAddress(XBeeStats.shortAddr), XBeeStats.node_id);
            try {
                xBeeDevice.sendData(remoteXBeeDevice, message);
            } catch (XBeeException e) {
                e.printStackTrace();
            }
        }

    }

    // Method for clients
    public void discoverDevices() {
        xBeeNetwork.startDiscoveryProcess();
    }

    // Method for clients
    public List<RemoteXBeeDevice> getDevices() {
        return xBeeNetwork.getDevices();
    }

    // Method for clients
    public boolean getBLEConnectionStatus() {
        return connectedToBluetooth;
    }
}
