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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import edu.bucknell.mage.mage_v1.Count_Down_Timer;
import edu.bucknell.mage.mage_v1.Game_Framework;
import edu.bucknell.mage.mage_v1.Home;
import edu.bucknell.mage.mage_v1.MessageReceiver;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 3/9/2016.
 */
public class Master_PlayGame_BopIt extends AppCompatActivity {

    BopIt game;
    int state = 0;
    long mMillisRemaining;                          // Time remaining for this user's round
    int playersTurn = 0;
    String currentPlayersTurn;
    TextView remainingTimeView;

    Map<String, Integer> userScores;                // Keeps track of users scores

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
        setContentView(R.layout.playgame_bopit_master);

        // Get instance of the game that was called via intent
        Intent i = getIntent();
        game = (BopIt) i.getSerializableExtra("game");

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.playgame_bopit_master_toolbar);
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

        // Initialize the map containing user scores to be 0 for all users
        userScores = new ArrayMap<String, Integer>(game.mNumPlayers);
        for (int userNum = 0; userNum < game.players.size(); userNum++) {
            userScores.put(game.players.get(userNum).username, 0);
        }

        Button startRoundButton = (Button) findViewById(R.id.bopit_start_round_button);
        startRoundButton.setOnClickListener(clickListener);

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
                boolean timerFinished = intent.getBooleanExtra("timerFinished", true);
                mMillisRemaining = intent.getLongExtra("remainingTime", 0);
                
                // Update the UI timer
                updateRemainingTime(mMillisRemaining);

                if (timerFinished) {
                    state = 3;
                    startOtherPlayersTurns();
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
            // Kit-to-kit packet
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, xBeeStats);
            int packetConfigState = kitPacket.configState;
            if (packetConfigState == 6 && state == 3) {
                // The score of another player has been received

                // Store the score
                userScores.put(currentPlayersTurn, Integer.parseInt(kitPacket.kitInfo));

                // Pick the next player
                selectNextPlayerOrEndGame();
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

    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int viewID = v.getId();
            switch (viewID) {
                case R.id.bopit_start_round_button: {
                    // The master user is starting its round

                    // Broadcast to all nodes that it is the master user's turn
                    game.broadcastNodePacket(0, 0, 0, 0, true, null, mMessageReceiver);
                    // Broadcast to all nodes that it is the master user's turn
                    game.broadcastNodePacket(0, 0, 0, 0, true, null, mMessageReceiver);


                    // Get this user's username
                    SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
                    String username = sharedPreferences.getString("username", "~");

                    // Broadcast to all kits that it is the master user's turn
                    game.broadcastKitPacket(4, 0, username, true, null, mMessageReceiver);

                    // Advance to next state
                    state = 1;
                    beginRound();

                    break;
                }
                case R.id.bopit_end_game_button_master: {
                    // Go back to the home activity
                    Intent i = new Intent(v.getContext(), Home.class);
                    startActivity(i);

                    break;
                }
                default: break;
            }
        }
    };


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

        // Get rid of the begin round UI
        RelativeLayout beginRoundView = (RelativeLayout) findViewById(R.id.bopit_start_round_layout);
        beginRoundView.setVisibility(View.GONE);

        // Show the new UI for playing a round
        RelativeLayout playRoundView = (RelativeLayout) findViewById(R.id.bopit_play_round_layout);
        playRoundView.setVisibility(View.VISIBLE);

        // Get the TextView that displays how much time is left
        remainingTimeView = (TextView) findViewById(R.id.bopit_time_remaining_value);

        // Begin the timer for the round
        game.startBopItRoundTimer(this, mConnectionTimer);
        state = 2;

        // Get the first task
        game.generateNextTask();
        updateRoundUI();
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
        TextView playerScoreView = (TextView) findViewById(R.id.bopit_score_value);
        playerScoreView.setText(String.valueOf(game.mMyScore));
    }

    /*
     * Show the user the correct task to complete next whenever a new task is generated.
     */
    private void updateRoundUI() {
        // Update the text indicating what action to perform
        TextView nextAction = (TextView) findViewById(R.id.bopit_next_action_text);
        // Update the background color of the entire screen
        LinearLayout gamePlayBkgd = (LinearLayout) findViewById(R.id.bopit_master_gameplay_layout);

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
     * Name: startOtherPlayersTurns
     * After the master user finishes his or her round, initialize everything that needs to
     * happen in order to begin selecting other users' turns.
     */
    private void startOtherPlayersTurns() {
        // Update the UI to get rid of game play
        RelativeLayout gamePlayView = (RelativeLayout) findViewById(R.id.bopit_play_round_layout);
        gamePlayView.setVisibility(View.GONE);

        // Show the new UI
        RelativeLayout otherRoundsView = (RelativeLayout) findViewById(R.id.bopit_other_rounds_layout);
        otherRoundsView.setVisibility(View.VISIBLE);
        // Set the background color back to white
        LinearLayout parentView = (LinearLayout) findViewById(R.id.bopit_master_gameplay_layout);
        parentView.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark_material_light_1));

        // Update the score to correctly reflect the score that was achieved
        TextView myScore = (TextView) findViewById(R.id.bopit_my_score);
        myScore.setText(String.valueOf(game.mMyScore));

        selectNextPlayerOrEndGame();
    }


    /*
     * Name: selectNextPlayerOrEndGame
     * Whenever a user completes a round, call this function in order to select the next
     * player. If all users have gone, determine the winner and end the game.
     */
    private void selectNextPlayerOrEndGame() {
        String nodeOptions = "";
        if (game.numNFCNodes > 0) nodeOptions = nodeOptions + "1";
        if (game.numShakeNodes > 0) nodeOptions = nodeOptions + "2";
        if (game.numDetectNodes > 0) nodeOptions = nodeOptions + "3";
        if (game.numFlipNodes > 0) nodeOptions = nodeOptions + "4";
        if (game.numButtonNodes > 0) nodeOptions = nodeOptions + "5";

        if (playersTurn < game.players.size()) {
            // There are still players waiting to play -- select the next one
            currentPlayersTurn = game.players.get(playersTurn).username;

            // Broadcast to the kits the next player
            String kitInfo = currentPlayersTurn + ";" + String.valueOf(game.mGameDuration) +
                    ";" + nodeOptions;
            game.broadcastKitPacket(5, 0, kitInfo, true, null, mMessageReceiver);

            // Update our UI to display the next player whose turn it is
            TextView currentPlayer = (TextView) findViewById(R.id.bopit_this_players_turn);
            currentPlayer.setText(game.players.get(playersTurn).username);

            // Get the next player the next time around
            playersTurn++;
        }
        else {
            // All players have gone! Calculate the winner and broadcast the results
            state = 4;
            displayWinningPlayer();
        }

    }


    /*
     * Name: displayWinning Player
     * When the game is over (all players have gone), calculate the winner and broadcast
     * the results. Update the UI correspondingly.
     *
     * State: 4
     */
    private void displayWinningPlayer() {
        // End the game for nodes
        game.broadcastNodePacket(0, 0, 0, 1, true, null, mMessageReceiver);

        // Determine who the winner was
        int highestScore = 0;
        String winningUser = "";
        boolean hasSameScore = false;
        int curScoreVal;
        String kitInfo;

        // First go through and determine what the highest score is
        for (String key: userScores.keySet()) {
            curScoreVal = userScores.get(key);
            if (curScoreVal > highestScore) {
                highestScore = curScoreVal;
                hasSameScore = false;
                winningUser = key;
            }
            else if (curScoreVal == highestScore) {
                hasSameScore = true;
            }
        }
        // Check the score of the master user as well
        if (game.mMyScore > highestScore) {
            highestScore = game.mMyScore;
            hasSameScore = false;
            SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
            winningUser = sharedPreferences.getString("username", "~");
        }
        else if (game.mMyScore == highestScore) {
            hasSameScore = true;
        }

        if (hasSameScore) {
            // There was a tie
            winningUser = "There was a tie!";
        }
        kitInfo = winningUser + ";" + String.valueOf(highestScore);

        // Broadcast the winner and the winning score to all kits
        game.broadcastKitPacket(0, 1, kitInfo, true, null, mMessageReceiver);

        // Update the UI for the master player's screen
        LinearLayout roundsDisplay = (LinearLayout) findViewById(R.id.display_current_bopit_players_turn);
        roundsDisplay.setVisibility(View.GONE);

        // Show the winning player display
        LinearLayout winnerDisplay = (LinearLayout) findViewById(R.id.display_winning_bopit_player);
        winnerDisplay.setVisibility(View.VISIBLE);
        // Update the username and score appropriately
        TextView winningUsername = (TextView) findViewById(R.id.bopit_winning_username);
        winningUsername.setText(winningUser);
        TextView winningScoreView = (TextView) findViewById(R.id.bopit_winning_score);
        winningScoreView.setText(String.valueOf(highestScore));

        // Send all of the players and their scores to the web page
        reportPlayersAndScores();

        // Register the end game button
        Button endGameButton = (Button) findViewById(R.id.bopit_end_game_button_master);
        endGameButton.setOnClickListener(clickListener);
    }


    /*
     * Name: reportPlayersAndScores
     * Sends all of the players' usernames and their corresponding scores to our Thingworx
     * web app.
     * For now we only consider the case of sending 4 players' data because our system can
     * only support up to 4 players.
     */
    private void reportPlayersAndScores() {
        // First send the master kit's data
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String masterKitUsername = sharedPreferences.getString("username", "~");
        new WebReportingTask().execute(new String[]{"winningPlayer", masterKitUsername, "winningScore", String.valueOf(game.mMyScore)});

        int playersScore;
        int playerNum = 1;
        String player = "";
        String score = "";
        // Loop through each remaining player and the corresponding score
        for (String key: userScores.keySet()) {
            playersScore = userScores.get(key);
            switch (playerNum) {
                case (1): {
                    player = "player1";
                    score = "player1Score";
                    break;
                }
                case (2): {
                    player = "player2";
                    score = "player2Score";
                    break;
                }
                case (3): {
                    player = "player3";
                    score = "player3Score";
                    break;
                }
                default: break;
            }
            // Send the info to the web app
            new WebReportingTask().execute(new String[]{player, key, score, String.valueOf(playersScore)});
            playerNum++;
        }
        while ((playerNum) < 4) {
            // Need to send empty strings if we have less than 4 players
            switch (playerNum) {
                case (2): {
                    player = "player2";
                    score = "player2Score";
                    break;
                }
                case (3): {
                    player = "player3";
                    score = "player3Score";
                    break;
                }
                default: break;
            }
            String key = "";
            String emptyScore = "";
            // Send the info to the web app
            new WebReportingTask().execute(new String[]{player, key, score, emptyScore});
            playerNum++;
        }
    }


    // ------------------------- NFC Functions --------------------------

    /*
     * Called to handle new intents generated by the Android System when an NFC tag is discovered.
     */
    @Override
    protected void onNewIntent(Intent intent) {

        if (intent.getAction() != null) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            Toast.makeText(Master_PlayGame_BopIt.this, "NFC Data Received", Toast.LENGTH_SHORT).show();

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

    /* ----------------------------- end NFC functions ----------------------------- */

    /*
     * AsyncTask needed to use internet functions in order to have web app reporting.
     * The internet functions require that everything be done on a separate thread.
     */
    private class WebReportingTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                sendPropertyValues(game.IPaddr, game.appKey, game.IMEI, params[0], params[1]);
                System.err.println("sending player data for " + params[1]);
                sendPropertyValues(game.IPaddr, game.appKey, game.IMEI, params[2], params[3]);
                System.err.println("with score data of " + params[3]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /*
     * Name: sendPropertyValues
     * Opens the REST request to connect to our Thingworx mashup where we report the game
     * results. The function is only ever performed in the background on our AsyncTask.
     * This function was taken directly from a tutorial provided by Thingworx.
     */
    public void sendPropertyValues(String ipAddress, String appKey, String thing, String property, String value) throws MalformedURLException {

        URL url = new URL("http://" + ipAddress
                + "/Thingworx"
                + "/Things/AndroidDevice_" + thing
                + "/Properties/" + property
                + "?method=put"
                + "&value=" + value
                + "&appKey=" + appKey);
        System.out.println("sendPropertyValues  " + url);
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "text/plain");
            conn.connect();
            System.out.println("Connection was established ? " + conn.getResponseMessage());
            if(conn.getResponseCode()==500){
                System.out.println("There was an internal server error " + conn.getResponseMessage());
            }
            if (conn.getResponseCode() != 200 && conn.getResponseCode()!=500) {
                System.err.println("Failed : HTTP error code : " + conn.getResponseCode());
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }
            else {
                System.err.println("Sent data to TWX successfully");
            }
            conn.disconnect();
            if(conn.getResponseCode()==500){
                System.out.println("There was an internal server error " + conn.getResponseMessage());
            }
            if (conn.getResponseCode() != 200 && conn.getResponseCode()!=500) {
                System.err.println("Failed : HTTP error code : " + conn.getResponseCode());
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            } else {
                System.err.println("Sent data to TWX successfully");
            }
            conn.disconnect();
        } catch (Exception ex) {
            System.out.println("Connection was not established " + ex.getMessage());
        }
    }
}
