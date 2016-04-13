package edu.bucknell.mage.mage_v1.Bop_It;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import edu.bucknell.mage.mage_v1.Count_Down_Timer;
import edu.bucknell.mage.mage_v1.Game_Framework;
import edu.bucknell.mage.mage_v1.Home;
import edu.bucknell.mage.mage_v1.MessageReceiver;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 3/8/2016.
 */
public class PlayGame_BopIt extends AppCompatActivity {

    BopIt game;
    int state = 0;
    long mMillisRemaining;      // Time remaining for this user's round
    String myUsername;
    TextView remainingTimeView;
    Game_Framework.XBeeStats masterKitAddr;

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiver;
    boolean mBoundTimer;
    Count_Down_Timer mCountDownTimer;               // Reference to the service so that
                                                    // we can access public methods

    // Broadcast receiver strings
    String mMessageReceiverIntentAction = "newMessageReceived";
    String mCountDownTimerIntentAction = "remainingTime";

    // The below are used for reading NFC tags
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playgame_bopit_general);

        // Get instance of the game that was called via intent
        Intent i = getIntent();
        game = (BopIt) i.getSerializableExtra("game");

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.playgame_bopit_toolbar);
        setSupportActionBar(myToolbar);

        // Establish the broadcast receiver to get the new MAGE network messages
        // and to communicate with the count down timer
        // Add filter for receiving network messages
        IntentFilter filter = new IntentFilter(mMessageReceiverIntentAction);
        // Add filter for receiving timer updates
        filter.addAction(mCountDownTimerIntentAction);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);

        // Bind to local MAGE network XBee broadcast receiver
        Intent messageReceiverIntent = new Intent(this, MessageReceiver.class);
        startService(messageReceiverIntent);
        bindService(messageReceiverIntent, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // get this user's username
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        myUsername = sharedPreferences.getString("username", "~");

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
                // A new message was received
                String msgContents = intent.getStringExtra("msgContents");

                Game_Framework.XBeeStats xBeeStats= new Game_Framework.XBeeStats();
                xBeeStats.longAddr = intent.getByteArrayExtra("xBeeLongAddr");
                xBeeStats.shortAddr = intent.getByteArrayExtra("xBeeShortAddr");
                xBeeStats.node_id = intent.getStringExtra("xBeeNI");

                packetReceiver(msgContents, xBeeStats);
            }
            else if (mCountDownTimerIntentAction.equals(intent.getAction())) {
                // A new timer update
                boolean timerFinished = intent.getBooleanExtra("timerFinished", false);
                mMillisRemaining = intent.getLongExtra("remainingTime", 0);

                // Update the UI timer
                updateRemainingTime(mMillisRemaining);

                if (timerFinished) {
                    finishedRound();
                }
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
            if (packetConfigState == 4 && state == 0) {
                // Received broadcast from master kit that it is his/her turn to play
                // Update UI with username of master user
                TextView masterUsernameText = (TextView) findViewById(R.id.bopit_this_players_turn_general);
                masterUsernameText.setText(kitPacket.kitInfo);
            }
            else if (packetConfigState == 5 && state == 0) {
                // Received broadcast indicating that it is some other player's turn -- see if this is us!
                // Parse the kit information
                String[] kitInfo = game.parseKitInfo(kitPacket);
                if (kitInfo[0].equals(myUsername)) {
                    // It is our turn to play!
                    state = 1;
                    masterKitAddr = xBeeStats;

                    // Get the necessary information needed to play
                    // Need the round duration as well as what node options there are
                    game.mGameDuration = Integer.parseInt(kitInfo[1]);
                    determineAvailableNodeOptions(kitInfo[2]);

                    // Start round and perform necessary intializations
                    beginRound();
                }
                else {
                    // It is some other player's turn -- update UI to show who it is
                    TextView newPlayerUsername = (TextView) findViewById(R.id.bopit_this_players_turn_general);
                    newPlayerUsername.setText(kitInfo[0]);
                }
            }
            else if (packetConfigState == 0 && state == 0) {
                // Broadcast indicating the end of the game
                if (kitPacket.gameStateToggle == 1) {
                    // Get rid of old UI
                    RelativeLayout roundView = (RelativeLayout) findViewById(R.id.bopit_other_rounds_layout_general);
                    roundView.setVisibility(View.GONE);
                    // Show winner UI
                    RelativeLayout winnerView = (RelativeLayout) findViewById(R.id.bopit_winning_player_layout_general);
                    winnerView.setVisibility(View.VISIBLE);
                    // Update the UI to show the winner and the winning score
                    // Need to parse the kit information
                    String[] kitInfo = game.parseKitInfo(kitPacket);
                    String winningUser = kitInfo[0];
                    String winningScore = kitInfo[1];
                    TextView winningUserText = (TextView) findViewById(R.id.bopit_winning_username_general);
                    winningUserText.setText(winningUser);
                    TextView winningScoreText = (TextView) findViewById(R.id.bopit_winning_score_general);
                    winningScoreText.setText(winningScore);

                    // Show this user's round score
                    TextView myScoreText = (TextView) findViewById(R.id.bopit_my_score_winner_general);
                    myScoreText.setText(String.valueOf(game.mMyScore));

                    // Register the end game button
                    Button endGameButton = (Button) findViewById(R.id.bopit_end_game_button_general);
                    endGameButton.setOnClickListener(clickListener);
                }
            }
        }
        else if (packetType == 1) {
            // Node-to-kit packet
            Game_Framework.NodePacket nodePacket = game.parseNodePacket(packetData, xBeeStats);
            int packetConfigState = nodePacket.configState;
            if (packetConfigState == 7 && state == 2) {
                // A node has been triggered
                if (game.processNodeTrigger(nodePacket.sensor)) {
                    // The correct node has been triggered
                    game.generateNextTask();
                    updateRoundUI();
                }
                updateScore();
            }
        }
    }

    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bopit_start_round_button_general) {
                // This user is beginning his/her round of Bop It!
                // Get rid of Begin Round layout
                RelativeLayout beginRoundUI = (RelativeLayout) findViewById(R.id.bopit_start_round_layout_general);
                beginRoundUI.setVisibility(View.GONE);
                // Show Play Round layout
                RelativeLayout playRoundUI = (RelativeLayout) findViewById(R.id.bopit_play_round_layout_general);
                playRoundUI.setVisibility(View.VISIBLE);

                // Get the TextView that displays how much time is left
                remainingTimeView = (TextView) findViewById(R.id.bopit_time_remaining_value_general);

                // Broadcast to all nodes so that they have the correct address to send updates to
                game.broadcastNodePacket(0, 0, 0, 0, true, null, mMessageReceiver);
                // Broadcast to all nodes so that they have the correct address to send updates to
                game.broadcastNodePacket(0, 0, 0, 0, true, null, mMessageReceiver);

                // Begin the timer for the round
                game.startBopItRoundTimer(v.getContext(), mConnectionTimer);

                // Get the first task
                game.generateNextTask();
                updateRoundUI();
            }
            else if (v.getId() == R.id.bopit_end_game_button_general) {
                // Go back to the home activity
                Intent i = new Intent(v.getContext(), Home.class);
                startActivity(i);
            }
        }
    };

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
     * Name: determineAvailableNodeOptions
     * When we receive the broadcast and determine that it is our turn to play, we need to
     * parse some more of the kit information so that we know the correct node options that
     * we can generate as tasks. Do this before any other round initialization, otherwise
     * the rest of the intialization process will not work correctly.
     */
    private void determineAvailableNodeOptions(String nodeOptionsString) {
        int optionsLength = nodeOptionsString.length();
        for (int i = 0; i < optionsLength; i++) {
            switch (Character.getNumericValue(nodeOptionsString.charAt(i))) {
                case 1: {
                    game.numNFCNodes = 1;
                    break;
                }
                case 2: {
                    game.numShakeNodes = 1;
                    break;
                }
                case 3: {
                    game.numDetectNodes = 1;
                    break;
                }
                case 4: {
                    game.numFlipNodes = 1;
                    break;
                }
                case 5: {
                    game.numButtonNodes = 1;
                    break;
                }
            }
        }
    }


    /*
     * Name: beginRound
     * Initialize the necessary features to begin playing, generate the first task, and
     * update the UI to display the game play screen.
     *
     * State: 1
     */
    private void beginRound() {
        // Initialize the node options list
        game.initializeNodeOptionsList();

        // Remove the Other Rounds UI
        RelativeLayout otherRoundsUI = (RelativeLayout) findViewById(R.id.bopit_other_rounds_layout_general);
        otherRoundsUI.setVisibility(View.GONE);

        // Show the Begin Round UI
        RelativeLayout beginRoundUI = (RelativeLayout) findViewById(R.id.bopit_start_round_layout_general);
        beginRoundUI.setVisibility(View.VISIBLE);
        // Register the button in the view with the onClickListener
        Button startRoundButton = (Button) findViewById(R.id.bopit_start_round_button_general);
        startRoundButton.setOnClickListener(clickListener);

        state = 2;
    }


    /*
     * As the timer counts down during this player's turn, make sure that the UI
     * updates correspondingly.
     */
    private void updateRemainingTime(long duration) {
        remainingTimeView.setText(String.valueOf(duration/1000));
    }


    /*
     * Update the UI to display the most recent score every time that a node is triggered.
     */
    private void updateScore() {
        TextView playerScoreView = (TextView) findViewById(R.id.bopit_score_value_general);
        playerScoreView.setText(String.valueOf(game.mMyScore));
    }


    /*
     * Show the user the correct task to complete next whenever a new task is generated.
     */
    private void updateRoundUI() {
        // Update the text indicating what action to perform
        TextView nextAction = (TextView) findViewById(R.id.bopit_next_action_text_general);
        // Update the background color of the entire screen
        LinearLayout gamePlayBkgd = (LinearLayout) findViewById(R.id.bopit_general_gameplay_layout);

        switch (game.currentTask) {
            case 1: {
                nextAction.setText(getResources().getString(R.string.bopit_NFC));
                nextAction.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));
                gamePlayBkgd.setBackgroundColor(ContextCompat.getColor(this, R.color.bopIt_NFC));
                break;
            }
            case 2: {
                nextAction.setText(getResources().getString(R.string.bopit_shake));
                nextAction.setTextColor(ContextCompat.getColor(this, R.color.black));
                gamePlayBkgd.setBackgroundColor(ContextCompat.getColor(this, R.color.bopIt_shake));
                break;
            }
            case 3: {
                nextAction.setText(getResources().getString(R.string.bopit_detect));
                nextAction.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));
                gamePlayBkgd.setBackgroundColor(ContextCompat.getColor(this, R.color.bopIt_detect));
                break;
            }
            case 4: {
                nextAction.setText(getResources().getString(R.string.bopit_flip));
                nextAction.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));
                gamePlayBkgd.setBackgroundColor(ContextCompat.getColor(this, R.color.bopIt_flip));
                break;
            }
            case 5: {
                nextAction.setText(getResources().getString(R.string.bopit_button));
                nextAction.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));
                gamePlayBkgd.setBackgroundColor(ContextCompat.getColor(this, R.color.bopIt_button));
                break;
            }
            default: break;
        }
    }


    /*
     * Name: finishedRound
     * When we have finished our turn, update the UI as needed, and report our score back to the
     * master kit.
     */
    private void finishedRound() {
        // Get rid of Play Round UI
        RelativeLayout playRoundUI = (RelativeLayout) findViewById(R.id.bopit_play_round_layout_general);
        playRoundUI.setVisibility(View.GONE);

        // Show Other Rounds UI
        RelativeLayout otherRoundsUI = (RelativeLayout) findViewById(R.id.bopit_other_rounds_layout_general);
        otherRoundsUI.setVisibility(View.VISIBLE);

        // Update my score from my round
        TextView myScoreView = (TextView) findViewById(R.id.bopit_my_score_other_general);
        myScoreView.setText(String.valueOf(game.mMyScore));

        // Set background color back to white
        LinearLayout parentView = (LinearLayout) findViewById(R.id.bopit_general_gameplay_layout);
        parentView.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));

        // Broadcast to master kit my score
        game.broadcastKitPacket(6, 0, String.valueOf(game.mMyScore), false, masterKitAddr, mMessageReceiver);

        state = 0;
    }


    // ------------------------- NFC Functions --------------------------

    /*
     * Called to handle new intents generated by the Android System when an NFC tag is discovered.
     */
    @Override
    protected void onNewIntent(Intent intent) {

        if (intent.getAction() != null) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            Toast.makeText(PlayGame_BopIt.this, "NFC Data Received", Toast.LENGTH_SHORT).show();

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

                                // An NFC node has been triggered
                                if (game.processNodeTrigger(1)) {
                                    // The correct node has been triggered
                                    game.generateNextTask();
                                    updateRoundUI();
                                }
                                updateScore();

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
}
