package edu.bucknell.mage.mage_v1;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcF;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;

import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Laura on 1/27/2016.
 */
public class PlayGame_Capture extends AppCompatActivity {

    CaptureTheNodes game;
    FragmentManager manager;
    PlayGame_Capture_Frag frag;
    String frag_ref_tag = "playgame_frag_tag";

    int mState = 1;
    boolean mTeamsDisplayed = false;

    List<Integer> nodeCountPerTeam;         // Keep track of the node count for each team
    Map<String, Integer> nodeMapToTeam;     // Keep track of what team and node is currently on

    String mMessageReceiverIntentAction = "newMessageReceived";
    String mCountDownTimerIntentAction = "remainingTime";

    // Initially display the amount of time -- needs to be in milliseconds
    long mMillisRemaining;

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiver;
    boolean mBoundTimer;
    Count_Down_Timer mCountDownTimer;               // Reference to the service so that
                                                    // we can access public methods

    // The below are used for reading NFC tags
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playgame_capture_layout);

        // get instance of the game that was called via intent
        Intent i = getIntent();
        game = (CaptureTheNodes) i.getSerializableExtra("game");

        mMillisRemaining = game.mGameDuration*60000;
        // Setup the map to keep track of which node has been captured by which team
        nodeMapToTeam = new ArrayMap<String, Integer>(game.mNumNodes);
        int nodeNum;
        for (nodeNum=0; nodeNum < game.nodes.size(); nodeNum++) {
            // Initialize all nodes to be "on team 0" (aka not on a team)
            nodeMapToTeam.put(game.nodes.get(nodeNum).node_id, 0);
        }
        // Setup the array to keep track of how many nodes each team has
        nodeCountPerTeam = new ArrayList<Integer>(game.mNumTeams);
        int teamNum;
        for (teamNum = 0; teamNum < game.mNumTeams; teamNum++) {
            // Initialize all node counts to be 0
            nodeCountPerTeam.add(0);
        }

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.playgame_capture_toolbar);
        setSupportActionBar(myToolbar);

        // Establish the broadcast receiver to get the new MAGE network messages
        // and to communicate with the count down timer
        // Add filter for receiving network messages
        IntentFilter filter = new IntentFilter(mMessageReceiverIntentAction);
        // Add filter for receiving timer updates
        filter.addAction(mCountDownTimerIntentAction);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);

        // Bind to local count down timer service
        Intent intent = new Intent(this, Count_Down_Timer.class);
        intent.putExtra("game_duration", game.mGameDuration);
        startService(intent);
        bindService(intent, mConnectionTimer, Context.BIND_AUTO_CREATE);

        // Bind to local MAGE network XBee broadcast receiver
        Intent messageReceiverIntent = new Intent(this, MessageReceiver.class);
        startService(messageReceiverIntent);
        bindService(messageReceiverIntent, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // get a reference to the fragment manager
        manager = getFragmentManager();
        // display the fragment associated with game play
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        frag = new PlayGame_Capture_Frag();
        fragmentTransaction.add(R.id.playgame_capture_activity_layout, frag, frag_ref_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        // --------------- the below code is used for reading NFC tags ----------------------
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        // Create a generic PendingIntent that will be delivered to this activity. The NFC stack
        // will fill in the intent with the details of the discovered tag before delivering it to
        // this activity.
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        // Setup an intent filter for all MIME based dispatches
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndef.addDataType("*/*");    /* Handles all MIME based dispatches.
                                               You should specify only the ones that you need. */
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }
        mFilters = new IntentFilter[]{ndef,};
        // Setup a tech list for all NfcF tags
        mTechLists = new String[][]{new String[]{NfcF.class.getName()}};
        // ---------------------------- end NFC code ----------------------------------------

        checkNFCSettings();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mState == 1) {
            // The game is being played

            // Register the 'End Game' button with the OnClickListener
            Button endGameButton = (Button) findViewById(R.id.end_game_play_button);
            endGameButton.setOnClickListener(buttonHandler);

            // Display the team number of the user
            TextView myTeamNumber = (TextView) findViewById(R.id.my_team_number);
            myTeamNumber.setText(String.valueOf(game.mMyTeamNum));
        }

        if (!mTeamsDisplayed) {
            mTeamsDisplayed = true;
            // Display the teams and all of the node counts
            frag.initDisplayTeams(nodeCountPerTeam, game.mMyTeamNum);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from count down timer service
        if (mBoundTimer) {
            unbindService(mConnectionTimer);
            mBoundTimer = false;
        }
        // Unbind from message receiver service
        if (mBoundMessageReceiver) {
            unbindService(mConnectionMessageReceiver);
            mBoundMessageReceiver = false;
        }
        // Unregister the Local Broadcast Receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    /*
     * Broadcast receiver that picks up local broadcasts from the message receiving service
     * and the count down timer service.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mMessageReceiverIntentAction.equals(intent.getAction())) {
                // a new message was received
                String msgContents = intent.getStringExtra("msgContents");

                Game_Framework.XBeeStats xBeeStats= new Game_Framework.XBeeStats();
                xBeeStats.longAddr = intent.getByteArrayExtra("xBeeLongAddr");
                xBeeStats.shortAddr = intent.getByteArrayExtra("xBeeShortAddr");
                xBeeStats.node_id = intent.getStringExtra("xBeeNI");

                packetReceiver(msgContents, xBeeStats);
            }
            else if (mCountDownTimerIntentAction.equals(intent.getAction())) {
                // a new timer update
                boolean timerFinished = intent.getBooleanExtra("timerFinished", true);
                long remainingTime = intent.getLongExtra("remainingTime", 0);
                mMillisRemaining = remainingTime;

                // update the UI timer
                frag.updateRemainingGameTime(remainingTime);
                if (timerFinished) {
                    // time is up! the game is over
                    displayWinningTeam();
                }
            }
        }
    };

    View.OnClickListener buttonHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.end_game_play_button) {
                // Launch a dialog telling the user that ending the game will do so for all players
                AlertDialog.Builder alertbox = new AlertDialog.Builder(v.getContext());
                alertbox.setTitle(R.string.end_game_dialog_title);
                alertbox.setMessage(getString(R.string.end_game_dialog_message));
                alertbox.setPositiveButton("I'm Sure", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelGameEarly(true);
                    }
                });
                alertbox.setNegativeButton("No! Keep playing!", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                alertbox.show();
            }
            else if (v.getId() == R.id.capture_end_game_button) {
                // Go back to the home activity
                Intent i = new Intent(v.getContext(), Home.class);
                startActivity(i);
            }
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
        int packetType = Character.getNumericValue(packetData.charAt(0));
        if (packetType == 2) {
            // Kit-to-kit packet -- The only type of packet that this app should receive
            // during a game of Capture the Nodes
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, xBeeStats);
            int packetConfigState = kitPacket.configState;
            if (packetConfigState == 0 && mState == 1) {
                // The correct type of packet to be receiving

                if (kitPacket.gameStateToggle == 1) {
                    // A user has sent a message to end the game early
                     cancelGameEarly(false);
                }
                else {
                    // Otherwise a normal game packet is received
                    // Parse the kit info
                    String[] params = game.parseKitInfo(kitPacket);
                    int teamNumber = Integer.parseInt(params[0]);
                    String NFC_ID = params[1];

                    updateGameStats(NFC_ID, teamNumber);
                }
            }
        }
    }

    /*
     * Connection needed to bind this activity to the count down timer service
     */
    private ServiceConnection mConnectionTimer = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to Count_Down_Timer, cast the IBinder and get TimerBinder instance
            Count_Down_Timer.TimerBinder binder = (Count_Down_Timer.TimerBinder) service;
            mCountDownTimer = binder.getService();
            mBoundTimer = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundTimer = false;
        }
    };

    /*
     * Connection needed to bind this activity to the message receiver service
     */
    private ServiceConnection mConnectionMessageReceiver = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to MessageReceiver, cast the IBinder and get MessageBinder instance
            MessageReceiver.MessageBinder binder = (MessageReceiver.MessageBinder) service;
            mMessageReceiver = binder.getService();
            mBoundMessageReceiver = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    /*
     * Called to handle new intents generated by the Android System when an NFC tag is discovered.
     */
    @Override
    protected void onNewIntent(Intent intent) {

        if (intent.getAction() != null) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            Toast.makeText(PlayGame_Capture.this, "NFC Data Received", Toast.LENGTH_SHORT).show();

            String[] techList = tag.getTechList();
            for (int i = 0; i < techList.length; i++) {
                if (techList[i].equals(Ndef.class.getName())) {
                    // the Spark Marks tags are Ndef Type 1 tags
                    Ndef ndefTag = Ndef.get(tag);

                    NdefMessage ndefMessage = ndefTag.getCachedNdefMessage();
                    NdefRecord[] records = ndefMessage.getRecords();
                    for (NdefRecord ndefRecord : records) {
                        if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                            try {
                                String NFC_ID = readText(ndefRecord);

                                // Update the game stats for this app
                                updateGameStats(NFC_ID, game.mMyTeamNum);

                                // Broadcast a kit-to-kit packet to tell the other users that a new node
                                // has been captured
                                String kitInfo = String.valueOf(game.mMyTeamNum) + ";" + NFC_ID;
                                game.broadcastKitPacket(0, 0, kitInfo, true, null, mMessageReceiver);

                                //Log.e("NFC TAG", "CONTENTS = " + NFC_ID);
                            } catch (UnsupportedEncodingException e) {
                                Log.e("NFC TAG", "Unsupported Encoding", e);
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Name: readText
     * Reads the contents contained on an NFC tag and returns them in a string.
     * The function was taken in its entirety from the tutorial found on
     * http://code.tutsplus.com/tutorials/reading-nfc-tags-with-android--mobile-17278.
     */
    private String readText(NdefRecord record) throws UnsupportedEncodingException {
        byte [] payload = record.getPayload();

        String type8 = "UTF-8";
        String type16 = "UTF-16";

        // Get the Text Encoding
        String textEncoding = ((payload[0] & 128) == 0) ? type8 : type16;

        // Get the Language Code
        int languageCodeLength = payload[0] & 0063;

        // Get the Text
        return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength -1, textEncoding);
    }

    /*
     * Name: checkNFCSettings
     * Immediately check if the user has NFC enabled on his or her phone. If it is not
     * enabled, alert the user with a dialog box informing them. Depending on the version
     * of Android being used on the phone, the dialog box will either take him directly
     * to the NFC settings or more generally to the Wireless settings on the phone.
     *
     * 2/5/2016 - Android does not currently allow you to programmatically enable/disable
     * NFC on a user's device, unless the device is rooted, which we cannot assume all
     * users' devices will be. The best we can do is direct the user to where in his
     * settings he can deliberately enable NFC.
     */
    private void checkNFCSettings() {
        android.nfc.NfcAdapter mNfcAdapter= android.nfc.NfcAdapter.getDefaultAdapter(this);

        if (!mNfcAdapter.isEnabled()) {

            AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
            alertbox.setTitle(R.string.NFC_not_enabled_title);
            alertbox.setMessage(getString(R.string.NFC_not_enabled_message));
            alertbox.setPositiveButton("Turn On", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                        startActivity(intent);
                    }
                }
            });
            alertbox.setNegativeButton("Close", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            alertbox.show();

        }
    }

    /*
     * Name: updateGameStats
     * Locally updates the node count for each team and updates which team currently has
     * 'ownership' of a node.
     */
    private void updateGameStats(String NFC_ID, int teamNumber) {

        int previousTeam;

        // See what team had captured the node before
        if (nodeMapToTeam.containsKey(NFC_ID)) {
            previousTeam = (Integer) nodeMapToTeam.get(NFC_ID);
        }
        else {
            previousTeam = -1;
        }

        if (previousTeam != -1 && teamNumber!= previousTeam) {
            // A new team has captured this node
            if (previousTeam != 0) {
                // The node had previously been captured

                // Decrement the node count of the team that previously had the node
                nodeCountPerTeam.set(previousTeam-1, nodeCountPerTeam.get(previousTeam-1)-1);
            }
            // Increment the node count of the team to have just captured the node
            nodeCountPerTeam.set(teamNumber - 1, nodeCountPerTeam.get(teamNumber - 1) + 1);
            // Mark that this team has this node captured
            nodeMapToTeam.put(NFC_ID, teamNumber);
            // Update the UI displaying the node counts
            frag.updateNodeCount(teamNumber, previousTeam, nodeCountPerTeam);
        }
    }

    /*
     * Name: cancelGameEarly
     * This function is called whenever any player decides to quit the game before
     * the timer has completed elapsed.
     */
    private void cancelGameEarly(boolean IamQuitting) {
        /*
         * If input IamQuitting is true, that means that the local user is the one
         * deciding to abort game play, so the corresponding message needs to be
         * broadcast to all nodes and kits. If IamQuitting is false, that means that
         * some other player sent a message indicating that game play is over. In the
         * latter case, we do not need to broadcast the message because that is already
         * being done somewhere else.
         *
         * Course of action when quitting a game early:
         * 1. Broadcast message to all kits (if IamQuitting is true)
         * 2. Broadcast message to all nodes (if IamQuitting is true)
         * 3. Cancel the timer
         * 4. Display the corresponding statistics
         */

        if (IamQuitting) {
            // Broadcast message to all kits
            game.broadcastKitPacket(0, 1, "", true, null, mMessageReceiver);
            // Broadcast message to all nodes
            game.broadcastNodePacket(0, 0, 0, 1, true, null, mMessageReceiver);
        }
        // Cancel the timer
        mCountDownTimer.cancelTimer();

        // Display the corresponding statistics
        displayWinningTeam();
    }

    /*
     * Advances the UI for this game play activity to show the winning team
     * or a tie. Stops receiving any other messages that might happen to come
     * in at this time.
     */
    private void displayWinningTeam() {
        mState = 2;
        frag.displayWinningTeam(nodeCountPerTeam);

        // Display button to return to home activity
        Button endGameButton = (Button) findViewById(R.id.capture_end_game_button);
        endGameButton.setOnClickListener(buttonHandler);
        endGameButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        // Handles the backstack for fragment navigation

        if (manager.getBackStackEntryCount() > 1) {
            manager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
