package com.example.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

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
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;             // doing nothing
    public static final int STATE_LISTEN = 1;           // now listening for incoming connections
    public static final int STATE_CONNECTING = 2;       // now initiating and outgoing connection
    public static final int STATE_CONNECTED = 3;        // now connected to a remote device
}
