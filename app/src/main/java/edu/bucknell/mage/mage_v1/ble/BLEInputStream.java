package edu.bucknell.mage.mage_v1.ble;

import com.digi.xbee.api.utils.HexUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Created by Laura on 2/26/2016.
 */
public class BLEInputStream extends InputStream {

    private BLEXBeeInterface bleInterface;

    public BLEInputStream(BLEXBeeInterface bleInterface) {
        this.bleInterface = bleInterface;
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        this.read(buffer);
        return buffer[0] & 255;
    }

    public int read(byte[] buffer) throws IOException {
        return this.read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        long deadLine = (new Date()).getTime() + 100L;

        int readBytes;
        for(readBytes = 0; (new Date()).getTime() < deadLine && readBytes <= 0; readBytes = bleInterface.getInputBuffer().read(buffer, byteOffset, byteCount)) {
            ;
        }

        if(readBytes <= 0) {
            return -1;
        } else {
            byte[] readData = new byte[readBytes];
            System.arraycopy(buffer, byteOffset, readData, 0, readBytes);
            return readBytes;
        }
    }

    public int available() throws IOException {
        return bleInterface.getInputBuffer().availableToRead();
    }

    public long skip(long byteCount) throws IOException {
        return (long)bleInterface.getInputBuffer().skip((int)byteCount);
    }
}
