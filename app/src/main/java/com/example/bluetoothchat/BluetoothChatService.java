package com.example.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

// This class does all the work for setting up and managing Bluetooth connections with other
// devices. It has a thread that listens for incoming connections, a thread for connecting with a
// device and a thread for performing data transmission when connected.
public class BluetoothChatService {

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";

    /*
    A UUID (Universally Unique IDentifier) is 128 bits long, and can guarantee
    uniqueness across space and time.  UUIDs were originally used in the
    Apollo Network Computing System and later in the Open Software
    Foundation's (OSF) Distributed Computing Environment (DCE), and then
    in Microsoft Windows platforms.

        The formal definition of the UUID string representation is provided by the following ABNF [7]:

          UUID                   = time-low "-" time-mid "-"
                                   time-high-and-version "-"
                                   clock-seq-and-reserved
                                   clock-seq-low "-" node
          time-low               = 4hexOctet
          time-mid               = 2hexOctet
          time-high-and-version  = 2hexOctet
          clock-seq-and-reserved = hexOctet
          clock-seq-low          = hexOctet
          node                   = 6hexOctet
          hexOctet               = hexDigit hexDigit
          hexDigit =
                "0" / "1" / "2" / "3" / "4" / "5" / "6" / "7" / "8" / "9" /
                "a" / "b" / "c" / "d" / "e" / "f" /
                "A" / "B" / "C" / "D" / "E" / "F"

       The following is an example of the string representation of a UUID as
       a URN:

       urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6

       See: https://www.ietf.org/rfc/rfc4122.txt for more detail
    */

    // UUID for this application
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200-c9a66");

    // Member fields
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mHandler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final short STATE_NONE = 0;             // doing nothing
    public static final short STATE_LISTEN = 1;           // now listening for incoming connections
    public static final short STATE_CONNECTING = 2;       // now initiating and outgoing connection
    public static final short STATE_CONNECTED = 3;        // now connected to a remote device

    public BluetoothChatService(Context context, Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    private synchronized void setState(int state) {
        mState = state;
        // Give the new state the the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }

        setState(STATE_LISTEN);
    }

    /*
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device the BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) { return; }
            r = connectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    public void connectionFailed() {
        setState(STATE_LISTEN);

        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    public void connectionLost() {
        setState(STATE_LISTEN);

        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves like a server-side
     * client. It runs until a connection is accepted or until cancelled.
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket bluetoothServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {

            }
            bluetoothServerSocket = tmp;
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket = null;
            // Listen to the server socket if device is not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a successful connection or
                    // an exception
                    socket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
            }
            // If connection was accepted
            if (socket != null) {
                synchronized (BluetoothChatService.this) {
                    switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminat new socket
                            try {
                                socket.close();
                            } catch (IOException e) {

                            }
                            break;
                    }
                }
            }
        }

        public void cancel() {
            try {
                bluetoothServerSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;

        public ConnectThread(BluetoothDevice bluetoothDevice) {
            this.bluetoothDevice = bluetoothDevice;
            BluetoothSocket tmp = null;
            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {

            }
            bluetoothSocket = tmp;
        }

        public void run() {
            setName("ConnectThread");
            // Always cancel a discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                bluetoothSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                // Close the socket
                try {
                    bluetoothSocket.close();
                } catch (IOException e2) {

                }

                // Restart the service to restart listening mode
                BluetoothChatService.this.start();
                return;
            }
            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                connectThread = null;
            }

            // Start the connected thread
            connected(bluetoothSocket, bluetoothDevice);
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {

            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        BluetoothSocket bluetoothSocket;
        InputStream inputStream;
        OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {

            }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            // Keep listening to the InputSsstream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {

            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {

            }
        }
    }
}
