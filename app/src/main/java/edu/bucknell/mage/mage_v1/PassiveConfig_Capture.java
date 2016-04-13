package edu.bucknell.mage.mage_v1;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;

public class PassiveConfig_Capture extends AppCompatActivity {

    CaptureTheNodes game;
    FragmentManager manager;
    PassiveConfigCaptureFrag frag;
    Game_Framework.XBeeStats senderXBee;
    String frag_ref_tag = "passive_config_capture_frag";

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiverService;

    int configState = 0;

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
            sendInvitationAcceptance();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passiveconfig_capture);

        // start the MAGE network XBee broadcast receiver
        Intent messageReceiver = new Intent(this, MessageReceiver.class);
        startService(messageReceiver);
        bindService(messageReceiver, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // establish the broadcast receiver to get the new MAGE network messages
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("newMessageReceived"));

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.passive_config_capture_toolbar);
        setSupportActionBar(myToolbar);

        Intent i = getIntent();
        game = (CaptureTheNodes)i.getSerializableExtra("game");
        senderXBee = new Game_Framework.XBeeStats();
        senderXBee.longAddr = i.getByteArrayExtra("xBeeLongAddr");
        senderXBee.shortAddr = i.getByteArrayExtra("xBeeShortAddr");
        senderXBee.node_id = i.getStringExtra("xBeeNI");

        // get a reference to the fragment manager
        manager = getFragmentManager();
        // display the fragment associated with passive configuration
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        frag = new PassiveConfigCaptureFrag();
        fragmentTransaction.add(R.id.passive_config_capture_activity_layout, frag, frag_ref_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        configState = 1;
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

        // check the type of packet that was received
        int packetType = Character.getNumericValue(packetData.charAt(0));
        if (packetType == 1) {
            // node-to-kit packet
            Game_Framework.NodePacket nodePacket = game.parseNodePacket(packetData, remoteXBeeDevice);
            int packetConfigState = nodePacket.configState;
            if (packetConfigState == 4) {
                // packet from node indicating the nodes availability
                // add this node to the list of nodes available for use

                if (configState == 2) {
                    // add node to node pool
                    game.processNodeGameAcceptance(nodePacket);
                }
            }
        }
        else if (packetType == 2) {
            // kit-to-kit packet
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, remoteXBeeDevice);
            int packetConfigState = kitPacket.configState;
            switch (packetConfigState) {
                case 3: {
                    // a packet from the configuring user containing the game parameters
                    // parameters include the game duration, number of teams, and the team number assigned to this player

                    if (configState == 1) {
                        // parse kit info
                        String[] params = game.parseKitInfo(kitPacket);

                        // params will be in the following order:
                        // game duration, number of teams, my team number
                        game.mGameDuration = Integer.parseInt(params[0]);
                        game.mNumTeams = Integer.parseInt(params[1]);
                        game.mMyTeamNum = Integer.parseInt(params[2]);

                        // update the passive configuration state
                        configState = 2;

                        // change the UI to be waiting for node availability responses
                        frag.visuallyModifyProgress(50, getString(R.string.active_config_progress_text_50));
                        TextView frag_waiting_for_text = (TextView) findViewById(R.id.waiting_for_text_passive_capture_text);
                        frag_waiting_for_text.setText(R.string.waiting_for_nodes_passive_capture);
                    }
                    break;
                }
                case 5: {
                    // a packet from the configuring user indicating the start of the game
                    if (configState == 2) {
                        // launch game play activity
                        game.startGamePlay(this, game);
                    }
                    break;
                }
                case 6: {
                    // a packet from the configuring user alerting all kits that it was unable
                    // to receive a confirmation signal from a node so it needs to resend the requests
                    // -- need to wipe all of the currently stored node data and restart logging
                    if (configState == 2) {
                        // wipe all node data
                        game.mNumNodes = 0;
                        game.nodes.clear();
                    }
                    break;
                }
                default: break;
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
        // immediately send a message to the user who sent the game request
        game.broadcastKitPacket(1, 0, "Y", false, senderXBee, mMessageReceiverService);
    }

    @Override
    public void onBackPressed() {
        // handles the backstack for fragment navigation

        if (manager.getBackStackEntryCount() > 1) {
            manager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
