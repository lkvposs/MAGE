package edu.bucknell.mage.mage_v1.Bop_It;

import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import edu.bucknell.mage.mage_v1.Game_Framework;
import edu.bucknell.mage.mage_v1.MessageReceiver;
import edu.bucknell.mage.mage_v1.NoUsernameFound;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 3/8/2016.
 */
public class PassiveConfig_BopIt extends AppCompatActivity implements NoUsernameFound.NoUsernameFoundDialogListener {

    BopIt game;
    Game_Framework.XBeeStats senderXBee;

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiverService;

    int state = 0;

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
            checkUsername();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passive_config_bopit);

        // start the MAGE network XBee broadcast receiver
        Intent messageReceiver = new Intent(this, MessageReceiver.class);
        startService(messageReceiver);
        bindService(messageReceiver, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // establish the broadcast receiver to get the new MAGE network messages
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("newMessageReceived"));

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.passive_config_bopit_toolbar);
        setSupportActionBar(myToolbar);

        Intent i = getIntent();
        game = (BopIt)i.getSerializableExtra("game");
        senderXBee = new Game_Framework.XBeeStats();
        senderXBee.longAddr = i.getByteArrayExtra("xBeeLongAddr");
        senderXBee.shortAddr = i.getByteArrayExtra("xBeeShortAddr");
        senderXBee.node_id = i.getStringExtra("xBeeNI");
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
    private void packetReceiver(String packetData, Game_Framework.XBeeStats remoteXBeeDevice) {

        // check the type of packet that was received -- only want kit-to-kit packets!
        int packetType = Character.getNumericValue(packetData.charAt(0));
        if (packetType == 2) {
            // kit-to-kit packet
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, remoteXBeeDevice);
            int packetConfigState = kitPacket.configState;
            if (state == 1 && packetConfigState == 3) {
                // Game start broadcast received
                if (kitPacket.gameStateToggle == 1) {
                    // Begin the game!
                    Intent i = new Intent(this, PlayGame_BopIt.class);
                    i.putExtra("game", game);
                    startActivity(i);
                }
            }
        }
    }

    /*
     * Name: sendInvitationAcceptance
     * A separate function that is called once the message receiving service is bound to the
     * activity. It is unknown how long it takes for the service to be bound, so this gets
     * called whenever that actually occurs.
     */
    private void sendInvitationAcceptance() {
        // Get the user's username -- this will be filled at this point
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username", "~");

        // Immediately send a message to the user who sent the game request
        game.broadcastKitPacket(1, 0, "Y;"+username, false, senderXBee, mMessageReceiverService);

        state = 1;
    }

    /*
     * Name: checkUsername
     * Check to make sure that the user has entered a username before accepting a Bop It! game
     * request. If a username is found, send the acceptance immediately, otherwise prompt
     * the user to enter a username.
     */
    private void checkUsername() {
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String checkUsername = sharedPreferences.getString("username", "~");
        if (checkUsername.equals("~")) {
            // Launch dialog for user to enter username before preceding
            DialogFragment newFragment = new NoUsernameFound();
            //newFragment.setTargetFragment(this, 0);
            newFragment.show(getFragmentManager(), "no_username_found");
        }
        else {
            // A username has already been entered, so send the acceptance immediately
            sendInvitationAcceptance();
        }
    }

    /*
     * Name: onDialogOkClick
     * User has just entered a username and clicked OK
     * The username has already been saved in the NoUsernameFound fragment
     * Now send the invitation with the username in tact
     */
    @Override
    public void onDialogOkClick(DialogFragment dialog) {
        sendInvitationAcceptance();
    }
}
