package edu.bucknell.mage.mage_v1.ble;

import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Laura on 2/26/2016.
 */
public class BLEOutputStream extends OutputStream {

    private BLEXBeeInterface bleInterface;

    public BLEOutputStream(BLEXBeeInterface bleInterface) {
        this.bleInterface = bleInterface;
    }

    @Override
    public void write(int oneByte) throws IOException {
        this.write(new byte[]{(byte)oneByte});
    }

    public void write(byte[] buffer) throws IOException {
        this.write(buffer, 0, buffer.length);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        final byte[] finalData = new byte[count + offset];
        System.arraycopy(buffer, offset, finalData, 0, count);
        bleInterface.startBLESend(finalData);
    }
}
