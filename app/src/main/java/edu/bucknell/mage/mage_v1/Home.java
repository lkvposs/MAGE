package edu.bucknell.mage.mage_v1;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;

import edu.bucknell.mage.mage_v1.ble.TestBLE;
import edu.bucknell.mage.mage_v1.ble.TestXBee;


public class Home extends AppCompatActivity {

    // ------------------------------- Attributes --------------------------------------

    Button bSettings;               // Click to edit user settings
    Button bGameMenu;               // Click to see a menu of games available to play
    Button bResetNodes;
    DialogFragment gameRequest;     // Used to dismiss the dialog
    String sGameRequestName;        // The name of the last game request received
    Game_Framework.XBeeStats senderXBee;    // The XBee address of the last person to send a game request
    int NOTIFICATION_ID = 1234;     // Reference to notification needed to dynamically clear it if needed

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiverService;

    FragmentManager manager;

    String home_menu_frag_tag = "home_menu_fragment_tag";
    String new_game_request_frag_tag = "new_game_req_tag";
    String settings_menu_frag_tag = "settings_menu_tag";
    String game_menu_frag_tag = "game_menu_tag";

    // ------------------------ Services & Broadcast Receivers -------------------------

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
            String msgContents = intent.getStringExtra("msgContents");

            Game_Framework.XBeeStats xBeeStats= new Game_Framework.XBeeStats();
            xBeeStats.longAddr = intent.getByteArrayExtra("xBeeLongAddr");
            xBeeStats.shortAddr = intent.getByteArrayExtra("xBeeShortAddr");
            xBeeStats.node_id = intent.getStringExtra("xBeeNI");

            packetReceiver(msgContents, xBeeStats);
        }
    };

    /*
     * Name: packetReceiver
     * All kit-to-kit and node-to-kit packets that are received during execution of this activity
     * are sent through here. First the packet type is determined, then the packet is parsed as
     * necessary.
     * Packet type 1 -- node-to-kit packet
     * Packet type 2 -- kit-to-kit packet
     * Packet type 3 -- kit-to-node packet [THESE WILL NOT BE RECEIVED IN THIS GAME]
     */
    private void packetReceiver(String packetData, Game_Framework.XBeeStats xBeeStats) {
        // Check the type of packet that was received
        int packetType = Character.getNumericValue(packetData.charAt(0));
        if (packetType == 2) {
            // Kit-to-kit packet
            // Parse the packet ourselves since we have no instance of a game
            // But we are only looking for 1 specific packet indicating a game request
            if (Character.getNumericValue(packetData.charAt(1)) == 2) {
                // This is a packet with a new game request

                // Save the XBee address of the sender so that we can later message them with our request
                senderXBee = xBeeStats;

                // Get the username of the sender and the game name to be played
                String kitInfo = packetData.substring(3);
                String delim = "[;]";
                String[] params = kitInfo.split(delim);

                String requester = params[0];
                String gameRequested = params[1];
                sGameRequestName = gameRequested;

                // Notify the user of the new game request
                showNewGameReqDialog_final(requester, gameRequested);
            }
        }
    }

    // ---------------------------- Game Request Dialog ----------------------------------

    void showNewGameReqDialog_final(String inviter, String gameRequested) {
        // This dialog appears whenever a new game request is received by the phone

        // Generate the notification indicating a new game request
        newGameNotification(inviter, gameRequested);

        // Create the dialog
        DialogFragment newGameReq = GameRequest.newInstance(inviter, gameRequested);
        // Show the dialog
        FragmentTransaction request_transaction = manager.beginTransaction();
        request_transaction.add(newGameReq, new_game_request_frag_tag).addToBackStack(null).commitAllowingStateLoss();

        // update this global variable in order to be able to close the dialog if Ignore is pressed
        gameRequest = newGameReq;
    }

    public void acceptRequest(View v) {
        // this method is called when a player accepts a game request
        // it launches the game menu fragment (because that is where the array of playable games
        // is stored) and then immediately calls the method launchPassiveConfig(String) to start the
        // correct activity

        // close the dialog box
        gameRequest.dismiss();

        // Cancel the notification
        NotificationManager notificationManager = (NotificationManager)getSystemService(this.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);

        // create instance of the fragment to launch
        GameMenu temp_frag = new GameMenu();

        // create an active instance of the Game Menu fragment
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        fragmentTransaction.replace(R.id.home_activity_layout, temp_frag, game_menu_frag_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        // call the public method to start the correct passive configuration activity
        temp_frag.launchPassiveConfig(sGameRequestName, senderXBee, this);
    }

    public void ignoreRequest(View v) {
        // called whenever the Ignore button is selected within the new game request dialog box

        gameRequest.dismiss();

        // Clear the notification
        NotificationManager notificationManager = (NotificationManager)getSystemService(this.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // ----------------------------- Settings Fragment ------------------------------------

    void displaySettings() {
        // display the settings fragment by replacing the home menu fragment

        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        fragmentTransaction.replace(R.id.home_activity_layout, new MainSettings(), settings_menu_frag_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    // ----------------------------- Game Menu Fragment ----------------------------------

    void displayGameMenu() {
        // display the game menu fragment by replacing the home menu fragment

        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        fragmentTransaction.replace(R.id.home_activity_layout, new GameMenu(), game_menu_frag_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    // ----------------------------- Create New Game Notification ------------------------

    private void newGameNotification(String requesterName, String requestedGame){
        // Generate the notification to be displayed when a new game request is received

        // prepare intent which is triggered if the notification is selected
        Intent intent = new Intent(this, Home.class);

        // use System.currentTimeMillis() to have a unique ID for the pending intent
        PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);

        // build notification
        // the addAction re-use the same intent to keep the example short
        Notification newGameNotification  = new Notification.Builder(this)
                .setContentTitle("New MAGE Game Request!")
                .setContentText("Player " + requesterName + " wants you to join a game of " + requestedGame + "!")
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .build();


        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notificationManager.notify(NOTIFICATION_ID, newGameNotification);
    }
    // -----------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_home);

        // get Fragment Manager in order to add fragments to this activity
        manager = getFragmentManager();

        // add HomeMenu fragment to this activity
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        fragmentTransaction.add(R.id.home_activity_layout, new HomeMenu(), home_menu_frag_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        // establish the broadcast receiver to get new MAGE network messages
        IntentFilter filter = new IntentFilter("newMessageReceived");
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);

        // start the MAGE network XBee message service
        Intent messageReceiver = new Intent(this, MessageReceiver.class);
        startService(messageReceiver);
        bindService(messageReceiver, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // Check if Wifi is on -- needed for web reporting
        // Give users the option to leave off in case they are in an area with no wifi
        checkWifiState();
    }

    /*
     * Name: checkWifiState
     * Checks whether or not wifi is on for a device. If it is not, alert the user that
     * he or she should turn it on, but give them the option in case they are obviously
     * not within range. Wifi is needed for web reporting, which is a non-critical feature
     * for game play.
     */
    private void checkWifiState() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
            // Wifi is turned off -- ask user to turn it on
            AlertDialog.Builder wifiOffAlert = new AlertDialog.Builder(this);
            wifiOffAlert.setTitle(getResources().getString(R.string.wifi_disabled_title));
            wifiOffAlert.setMessage(getResources().getString(R.string.wifi_disabled_message));
            wifiOffAlert.setPositiveButton(getResources().getString(R.string.wifi_disabled_positive_button), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    wifi.setWifiEnabled(true);
                }
            });
            wifiOffAlert.show();
        }
    }

    View.OnClickListener HomeButtonHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.edit_settings_button) {
                // show settings fragment
                displaySettings();
            }
            else if (v.getId() == R.id.game_menu_button) {
                // show game menu fragment
                displayGameMenu();
            }
            else if (v.getId() == R.id.reset_nodes_button) {
                Game_Framework neededToSendMessage = new Game_Framework();
                neededToSendMessage.broadcastNodePacket(0, 0, 0, 1, true, null, mMessageReceiverService);
            }
        }
    };

    @Override
    public void onBackPressed() {
        // handles the backstack for fragment navigation

        if (manager.getBackStackEntryCount() > 1) {
            manager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        bSettings = (Button) findViewById(R.id.edit_settings_button);
        if (bSettings != null) {
            bSettings.setOnClickListener(HomeButtonHandler);
        }
        bGameMenu = (Button) findViewById(R.id.game_menu_button);
        if (bGameMenu != null) {
            bGameMenu.setOnClickListener(HomeButtonHandler);
        }
        bResetNodes = (Button) findViewById(R.id.reset_nodes_button);
        if (bResetNodes != null) {
            bResetNodes.setOnClickListener(HomeButtonHandler);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        // Unbind from message receiver service
        if (mBoundMessageReceiver) {
            unbindService(mConnectionMessageReceiver);
            mBoundMessageReceiver = false;
        }
    }

}
