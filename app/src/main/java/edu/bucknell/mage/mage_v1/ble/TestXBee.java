package edu.bucknell.mage.mage_v1.ble;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.listeners.IPacketReceiveListener;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.utils.HexUtils;

import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 2/27/2016.
 */
public class TestXBee extends AppCompatActivity {

    XBeeDevice xBeeDevice;
    Button bSendMessageButton;
    BLEXBeeInterface bleInterface;
    private static boolean isBLEConnected = false;

    private final BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isBLEConnected) {
                isBLEConnected = true;
                try {
                    xBeeDevice.setReceiveTimeout(3000);
                    xBeeDevice.open();
                    xBeeDevice.addDataListener(new MyDataReceiveListener());
                }catch (XBeeException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_ble_layout);

        // Receiver needed for when Bluetooth becomes enabled programmatically
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver2, new IntentFilter("BLUETOOTH_CONNECTION_ESTABLISHED"));


        String tagName = "LAURA";

        bleInterface = new BLEXBeeInterface(this, tagName);
        bleInterface.establishBLEConnection();
        xBeeDevice = new XBeeDevice(bleInterface);

        bSendMessageButton = (Button) findViewById(R.id.test_xbee_button);
        bSendMessageButton.setOnClickListener(clickHandler);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver2);
        xBeeDevice.close();
        super.onStop();
    }

    View.OnClickListener clickHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.test_xbee_button) {
                byte[] dataToSend = "AAAAAAAAAA".getBytes();
                try {
                    xBeeDevice.sendBroadcastData(dataToSend);
                } catch (XBeeException e) {
                    e.printStackTrace();
                }
                byte[] moreDataToSend = "howdy doody JTEEZY".getBytes();
                try {
                    xBeeDevice.sendBroadcastData(moreDataToSend);
                } catch (XBeeException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private class MyPacketReceiveListener implements IPacketReceiveListener {
        @Override
        public void packetReceived(XBeePacket xBeePacket) {
            Log.e("PACKET RECEIVED", xBeePacket.toPrettyString());
            }
    }

    private class MyDataReceiveListener implements IDataReceiveListener {
        @Override
        public void dataReceived(XBeeMessage xBeeMessage) {
            Log.e("TEST", "From " + xBeeMessage.getDevice().get64BitAddress() + " | " + HexUtils.prettyHexString(HexUtils.byteArrayToHexString(xBeeMessage.getData())) + " " + new String(xBeeMessage.getData()));
        }
    }
}

