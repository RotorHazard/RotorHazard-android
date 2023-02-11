package io.github.pulquero.rotordroid;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Node implements Closeable {
    private static final byte READ_FREQUENCY = 0x03;
    private static final byte READ_LAP_STATS = 0x05;
    private static final byte WRITE_FREQUENCY = 0x51;
    private static final int TIMEOUT = 100;

    private final UsbSerialPort port;

    private Node(UsbSerialPort port) {
        this.port = port;
    }

    public static Node connect(Context ctx) throws IOException {
        UsbManager manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            throw new IOException("No compatible USB devices");
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        if (!manager.hasPermission(driver.getDevice())) {
            throw new IOException("No permission for USB device");
        }
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            throw new IOException("Failed to open USB device");
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one fPort (fPort 0)
        port.open(connection);
        port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
        return new Node(port);
    }

    public void setFrequency(int freq) throws IOException {
        byte[] writeFreqCmd = writeCommand(WRITE_FREQUENCY, 2);
        write16(writeFreqCmd, 1, freq);
        addChecksum(writeFreqCmd);
        port.write(writeFreqCmd, TIMEOUT);
    }

    public int getFrequency() throws IOException {
        byte[] buf = readCommand(READ_FREQUENCY, 2);
        return read16(buf, 0);
    }

    public LapStats readLapStats(long currentTime) throws IOException {
        LapStats stats = new LapStats();
        long sendTime = System.nanoTime();
        byte[] buf = readCommand(READ_LAP_STATS, 16);
        long recvTime = System.nanoTime();
        long delayMs = TimeUnit.NANOSECONDS.toMillis((recvTime - sendTime)/2);
        stats.t = (int) (currentTime + delayMs);
        byte laps = buf[0];
        int msSinceLastLap = read16(buf, 1);
        stats.rssi = buf[3];
        if (stats.rssi < 0) {
            stats.rssi = 128;
        }
        byte peakRssi = buf[4];
        byte lastPassPeak = buf[5];
        int loopTimeMicros = read16(buf, 6);
        byte flags = buf[8];
        byte lastPassNadir = buf[9];
        byte nadirRssi = buf[10];
        stats.historyRssi = buf[11];
        stats.msSinceHistoryStart = read16(buf, 12);
        stats.msSinceHistoryEnd = read16(buf, 14);
        return stats;
    }

    private byte[] readCommand(byte cmd, int payloadSize) throws IOException {
        port.write(new byte[] {cmd}, TIMEOUT);
        byte[] buf = new byte[20];
        int len = port.read(buf, TIMEOUT);
        if (len != payloadSize+1) {
            throw new IOException(String.format("%h: Unexpected response size %d", cmd, len));
        }
        byte checksum = buf[payloadSize];
        byte expectedChecksum = calculateChecksum(buf, 0, len-1);
        if (checksum != expectedChecksum) {
            throw new IOException(String.format("%h: Invalid checksum", cmd));
        }
        return buf;
    }

    public void close() throws IOException {
        port.close();
    }

    private static byte[] writeCommand(byte cmd, int payloadSize) {
        byte[] buf = new byte[1+payloadSize+1];
        buf[0] = cmd;
        return buf;
    }

    private static int write16(byte[] buf, int pos, int data) {
        buf[pos++] = (byte) (data >> 8);
        buf[pos++] = (byte) (data & 0xFF);
        return pos;
    }

    private static int read16(byte[] buf, int pos) {
        int result = buf[pos++];
        result = (result << 8) | (buf[pos++] & 0xFF);
        return result;
    }

    private static byte calculateChecksum(byte[] buf, int start, int len) {
        int checksum = 0;
        for(int i=start; i<start+len; i++) {
            checksum += (buf[i] & 0xFF);
        }
        return (byte) (checksum & 0xFF);
    }

    private static void addChecksum(byte[] buf) {
        byte checksum = calculateChecksum(buf, 1, buf.length-2);
        buf[buf.length-1] = checksum;
    }
}
