package edu.bucknell.mage.mage_v1.Bop_It;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.digi.xbee.api.RemoteXBeeDevice;

import org.w3c.dom.Text;

import java.util.List;

import edu.bucknell.mage.mage_v1.Game_Framework;
import edu.bucknell.mage.mage_v1.MessageReceiver;
import edu.bucknell.mage.mage_v1.R;

/**
 * Created by Laura on 3/8/2016.
 */
public class ActiveConfig_BopIt extends AppCompatActivity {

    BopIt game;
    int state = 0;
    ProgressBar configProgress;
    TextView progressText;

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageService;

    static boolean nodeDiscoveryCompleted = false;

    // Variables used when selecting node roles during Choose Game Parameters
    int totalNodesUsed = 0;
    int nfcNodes = 0;
    int shakeNodes = 0;
    int detectNodes = 0;
    int flipNodes = 0;
    int buttonNodes = 0;

    // Below strings are used for receiving broadcasts via the local broadcast manager
    String mMessageReceiverIntentAction = "newMessageReceived";
    String mAssignNodeRolesIntentAction = "nodeRoleAssignmentCompleted";
    String mDiscoveryFinishedIntentAction = "networkDiscoveryCompleted";

    /*
     * Connection needed to bind this activity to the message receiver service
     */
    private ServiceConnection mConnectionMessageReceiver = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to MessageReceiver, cast the IBinder and get MessageBinder instance
            MessageReceiver.MessageBinder binder = (MessageReceiver.MessageBinder) service;
            mMessageService = binder.getService();
            mBoundMessageReceiver = true;
            pollPlayers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mMessageReceiverIntentAction.equals(intent.getAction())) {
                // A new MAGE network message was received
                String msgContents = intent.getStringExtra("msgContents");

                Game_Framework.XBeeStats xBeeStats= new Game_Framework.XBeeStats();
                xBeeStats.longAddr = intent.getByteArrayExtra("xBeeLongAddr");
                xBeeStats.shortAddr = intent.getByteArrayExtra("xBeeShortAddr");
                xBeeStats.node_id = intent.getStringExtra("xBeeNI");

                packetReceiver(msgContents, xBeeStats);
            }
            else if (mDiscoveryFinishedIntentAction.equals(intent.getAction())) {
                // Network device discovery has completed -- get all of the devices

                if (!nodeDiscoveryCompleted) {
                    List<RemoteXBeeDevice> networkDevices = mMessageService.getDevices();

                    // Populate our game's list of nodes with relevant information
                    int i;
                    for (i=0; i<networkDevices.size(); i++) {
                        if (networkDevices.get(i).getNodeID() != null && networkDevices.get(i).getNodeID().charAt(0) == 'n') {
                            // This device is a node -- add it to the list
                            Game_Framework.XBeeStats newNode = new Game_Framework.XBeeStats();
                            newNode.longAddr = networkDevices.get(i).get64BitAddress().getValue();
                            newNode.shortAddr = networkDevices.get(i).get16BitAddress().getValue();
                            newNode.node_id = networkDevices.get(i).getNodeID();
                            game.nodes.add(newNode);
                            game.mNumNodes = game.nodes.size();
                        }
                    }
                    nodeDiscoveryCompleted = true;

                    // Update the UI to display the number of nodes that are available
                    TextView numNodesAvail = (TextView) findViewById(R.id.num_nodes_joined_value_bopit);
                    numNodesAvail.setText(String.valueOf(game.nodes.size()));
                }
            }
            else if (mAssignNodeRolesIntentAction.equals(intent.getAction())) {
                // The assign node roles service has finished distributing roles
                state = 5;
                playGame();
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
        // check the type of packet that was received
        int packetType = Character.getNumericValue(packetData.charAt(0));;
        if (packetType == 1) {
            // node-to-kit packet
            Game_Framework.NodePacket nodePacket = game.parseNodePacket(packetData, xBeeStats);
            int packetConfigState = nodePacket.configState;
            if (packetConfigState == 8 && state == 2) {
                // Received a game confirmation message from a node
                // Add this node to the list of nodes -- keep track of its address!
                game.nodes.add(xBeeStats);
                game.mNumNodes = game.nodes.size();

                // Update the UI to display the number of nodes that are available
                TextView numNodesAvail = (TextView) findViewById(R.id.num_nodes_joined_value_bopit);
                numNodesAvail.setText(String.valueOf(game.nodes.size()));
            }
        }
        else if (packetType == 2) {
            // kit-to-kit packet
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, xBeeStats);
            int packetConfigState = kitPacket.configState;
            if (packetConfigState == 1 && state == 1) {
                // Received a game confirmation message from another kit
                // Add this player to the list of players
                game.processKitGameAcceptance(kitPacket);

                // Update the UI to display the updated player count
                TextView numPlayersJoined = (TextView) findViewById(R.id.num_players_joined_value_bopit);
                numPlayersJoined.setText(String.valueOf(game.mNumPlayers));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.active_config_bopit);

        // start the MAGE network XBee broadcast receiver
        Intent messageReceiver = new Intent(this, MessageReceiver.class);
        startService(messageReceiver);
        bindService(messageReceiver, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.active_config_bopit_toolbar);
        setSupportActionBar(myToolbar);

        // get instance of the game that was called via intent
        Intent i = getIntent();
        game = (BopIt) i.getSerializableExtra("game");

        // Get an instance of the progress bar that we will update as the configuration process advances
        configProgress = (ProgressBar) findViewById(R.id.progressBarActiveConfigBopIt);
        progressText = (TextView) findViewById(R.id.active_bopit_progress_text);

        // establish the broadcast receiver to get the new MAGE network messages
        // AND to pick up messages sent by the AssignTeams Service
        IntentFilter filter = new IntentFilter(mMessageReceiverIntentAction);
        filter.addAction(mAssignNodeRolesIntentAction);
        filter.addAction(mDiscoveryFinishedIntentAction);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);
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

    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int viewID = v.getId();
            switch (viewID) {
                case R.id.continue_config_button_players_bopit: {
                    // Configuring user has decided enough players have joined
                    if (game.mNumPlayers > 1) {
                        state = 2;
                        discoverNodes();
                    }
                    break;
                }
                case R.id.continue_config_button_Nodes_bopit: {
                    // Configuring user has decided enough nodes have joined
                    if (game.mNumNodes > 0) {
                        state = 3;
                        chooseGameParameters();
                    }
                    break;
                }
                case R.id.nfc_scan_option_choose: {
                    updateNodeRoleCount(1, 1, checkToAddNodeRole());
                    break;
                }
                case R.id.nfc_scan_option_remove: {
                    updateNodeRoleCount(1, 0, nfcNodes > 0);
                    break;
                }
                case R.id.shake_option_choose: {
                    updateNodeRoleCount(2, 1, checkToAddNodeRole());
                    break;
                }
                case R.id.shake_option_remove: {
                    updateNodeRoleCount(2, 0, shakeNodes > 0);
                    break;
                }
                case R.id.detect_option_choose: {
                    updateNodeRoleCount(3, 1, checkToAddNodeRole());
                    break;
                }
                case R.id.detect_option_remove: {
                    updateNodeRoleCount(3, 0, detectNodes > 0);
                    break;
                }
                case R.id.flip_option_choose: {
                    updateNodeRoleCount(4, 1, checkToAddNodeRole());
                    break;
                }
                case R.id.flip_option_remove: {
                    updateNodeRoleCount(4, 0, flipNodes > 0);
                    break;
                }
                case R.id.button_option_choose: {
                    updateNodeRoleCount(5, 1, checkToAddNodeRole());
                    break;
                }
                case R.id.button_option_remove: {
                    updateNodeRoleCount(5, 0, buttonNodes > 0);
                    break;
                }
                case R.id.game_params_submit_button_bopit: {
                    // Configuring user has submitted game parameters -- need to validate
                    EditText round_duration = (EditText) findViewById(R.id.round_duration_value_bopit);
                    int round_duration_value = 0;
                    try {
                        round_duration_value = Integer.parseInt(round_duration.getText().toString());
                    } catch (NumberFormatException e) {
                        System.out.println("Could not parse " + e);
                    }
                    if (round_duration_value > 0 && totalNodesUsed > 1) {
                        // All input is acceptable -- saved entered values and advance configuration process
                        game.mGameDuration = round_duration_value;
                        game.numNFCNodes = nfcNodes;
                        game.numShakeNodes = shakeNodes;
                        game.numDetectNodes = detectNodes;
                        game.numFlipNodes = flipNodes;
                        game.numButtonNodes = buttonNodes;

                        state = 4;
                        assignNodeRoles();
                    }
                    else if (!(round_duration_value > 0)) {
                        displayWarningDialog(0);
                    }
                    else if (!(totalNodesUsed > 1)) {
                        displayWarningDialog(1);
                    }
                    break;
                }
                case R.id.play_game_button_bopit: {
                    // The configuring user wishes to begin the game

                    // Broadcast the start to all kits
                    game.broadcastKitPacket(3, 1, "", true, null, mMessageService);
                    // Broadcast the start to all nodes
                    game.broadcastNodePacket(0, 0, 0, 1, true, null, mMessageService);
                    // Launch master game play activity
                    Intent i = new Intent(v.getContext(), Master_PlayGame_BopIt.class);
                    i.putExtra("game", game);
                    startActivity(i);
                    break;
                }
                default: break;
            }
        }
    };

    /*
     * Name: pollPlayers
     * Initial kit-to-kit broadcast to send out game request.
     * Displays the 'polling players' UI
     *
     * State: 0
     */
    private void pollPlayers() {
        // Update the UI to display polling players
        LinearLayout pollPlayersView = (LinearLayout) findViewById(R.id.poll_players_active_config_bopit);
        pollPlayersView.setVisibility(View.VISIBLE);
        configProgress.setProgress(25);
        progressText.setText(R.string.active_config_progress_text_25);

        // Set the onClickListener for the button
        Button contPlayers = (Button) findViewById(R.id.continue_config_button_players_bopit);
        contPlayers.setOnClickListener(clickListener);


        // get the username to send along with game requests
        // this field is required to be populated before beginning active configuration
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username", "~");
        String kitInfo = username + ";" + game.returnName();

        // Send broadcast to all kits with a new game request
        game.broadcastKitPacket(2, 0, kitInfo, true, null, mMessageService);

        // Move to next state
        state = 1;
    }


    /*
     * Name: discoverNodes
     * Use the XBee Java library to 'discover nodes' via the XBee network.
     * Displays the 'polling nodes' UI
     *
     * State: 2
     */
    private void discoverNodes() {
        // Update the UI to get rid of the polling players UI
        LinearLayout pollPlayersView = (LinearLayout) findViewById(R.id.poll_players_active_config_bopit);
        pollPlayersView.setVisibility(View.GONE);

        // Now show the polling nodes UI
        LinearLayout pollNodesView = (LinearLayout) findViewById(R.id.poll_nodes_active_config_bopit);
        pollNodesView.setVisibility(View.VISIBLE);
        configProgress.setProgress(50);
        progressText.setText(R.string.active_config_progress_text_50);

        // Set the onClickListener for the button
        Button contNodes = (Button) findViewById(R.id.continue_config_button_Nodes_bopit);
        contNodes.setOnClickListener(clickListener);

        // Send broadcast to all nodes to get availability
        //mMessageService.discoverDevices();
        game.broadcastNodePacket(1, 2, 0, 0, true, null, mMessageService);
        // // TODO: 4/8/2016 fix what is actually sent to the nodes
    }

    /*
     * Name: chooseGameParameters
     * Enter the specifics for this game of Bop It!
     * Choose the roles for nodes to act as and the duration of each round.
     * Role can be button, NFC scan, shake, flip, or detect
     * Displays the 'choose game parameters' UI
     *
     * State: 3
     */
    private void chooseGameParameters() {
        // Update the UI to get rid of the polling nodes UI
        LinearLayout pollNodesView = (LinearLayout) findViewById(R.id.poll_nodes_active_config_bopit);
        pollNodesView.setVisibility(View.GONE);

        // Now show the choose game parameters UI
        LinearLayout chooseGameParamsView = (LinearLayout) findViewById(R.id.choose_game_params_layout_bopit);
        chooseGameParamsView.setVisibility(View.VISIBLE);
        configProgress.setProgress(75);
        progressText.setText(R.string.active_config_progress_text_75);

        // Set the onClickListener for the continuation button
        Button contParams = (Button) findViewById(R.id.game_params_submit_button_bopit);
        contParams.setOnClickListener(clickListener);

        // Add now for all of the other node role selection buttons...
        Button selectNFC = (Button) findViewById(R.id.nfc_scan_option_choose);
        selectNFC.setOnClickListener(clickListener);
        Button removeNFC = (Button) findViewById(R.id.nfc_scan_option_remove);
        removeNFC.setOnClickListener(clickListener);

        Button selectShake = (Button) findViewById(R.id.shake_option_choose);
        selectShake.setOnClickListener(clickListener);
        Button removeShake = (Button) findViewById(R.id.shake_option_remove);
        removeShake.setOnClickListener(clickListener);

        Button selectDetect = (Button) findViewById(R.id.detect_option_choose);
        selectDetect.setOnClickListener(clickListener);
        Button removeDetect = (Button) findViewById(R.id.detect_option_remove);
        removeDetect.setOnClickListener(clickListener);

        Button selectFlip = (Button) findViewById(R.id.flip_option_choose);
        selectFlip.setOnClickListener(clickListener);
        Button removeFlip = (Button) findViewById(R.id.flip_option_remove);
        removeFlip.setOnClickListener(clickListener);

        Button selectButton = (Button) findViewById(R.id.button_option_choose);
        selectButton.setOnClickListener(clickListener);
        Button removeButton = (Button) findViewById(R.id.button_option_remove);
        removeButton.setOnClickListener(clickListener);
    }

    /*
     * Name: checkToAddNodeRole
     * Checks to make sure that we are not trying to assign more node roles than
     * there are nodes to use.
     */
    private boolean checkToAddNodeRole() {
        return totalNodesUsed < game.mNumNodes;
    }


    /*
     * Name: updateNodeRoleCount
     * Updates the count of each node role while the user configures the game.
     * Correspondingly updates the UI as needed as well.
     *
     * Role values are as follows:
     * NFC Scan - 1
     * Shake - 2
     * Detect - 3
     * Flip - 4
     * Button - 5
     *
     * Operation key:
     * 1 - add
     * 0 - remove
     */
    private void updateNodeRoleCount(int role, int operation, boolean actionAllowed) {
        if (actionAllowed) {
            switch (role) {
                case 1: {
                    // Update NFC scan count
                    if (operation == 1) {
                        nfcNodes++;
                        totalNodesUsed++;
                    }
                    else {
                        nfcNodes--;
                        totalNodesUsed--;
                    }
                    TextView nfcCountText = (TextView) findViewById(R.id.nfc_scan_option_total);
                    nfcCountText.setText(String.valueOf(nfcNodes));
                    TextView totalCountText = (TextView) findViewById(R.id.total_node_count_used_text);
                    totalCountText.setText(String.valueOf(totalNodesUsed));
                    break;
                }
                case 2: {
                    // Update shake count
                    if (operation == 1) {
                        shakeNodes++;
                        totalNodesUsed++;
                    }
                    else {
                        shakeNodes--;
                        totalNodesUsed--;
                    }
                    TextView shakeCountText = (TextView) findViewById(R.id.shake_option_total);
                    shakeCountText.setText(String.valueOf(shakeNodes));
                    TextView totalCountText = (TextView) findViewById(R.id.total_node_count_used_text);
                    totalCountText.setText(String.valueOf(totalNodesUsed));
                    break;
                }
                case 3: {
                    // Update detect count
                    if (operation == 1) {
                        detectNodes++;
                        totalNodesUsed++;
                    }
                    else {
                        detectNodes--;
                        totalNodesUsed--;
                    }
                    TextView detectCountText = (TextView) findViewById(R.id.detect_option_total);
                    detectCountText.setText(String.valueOf(detectNodes));
                    TextView totalCountText = (TextView) findViewById(R.id.total_node_count_used_text);
                    totalCountText.setText(String.valueOf(totalNodesUsed));
                    break;
                }
                case 4: {
                    // Update flip count
                    if (operation == 1) {
                        flipNodes++;
                        totalNodesUsed++;
                    }
                    else {
                        flipNodes--;
                        totalNodesUsed--;
                    }
                    TextView flipCountText = (TextView) findViewById(R.id.flip_option_total);
                    flipCountText.setText(String.valueOf(flipNodes));
                    TextView totalCountText = (TextView) findViewById(R.id.total_node_count_used_text);
                    totalCountText.setText(String.valueOf(totalNodesUsed));
                    break;
                }
                case 5: {
                    // Update button count
                    if (operation == 1) {
                        buttonNodes++;
                        totalNodesUsed++;
                    }
                    else {
                        buttonNodes--;
                        totalNodesUsed--;
                    }
                    TextView buttonCountText = (TextView) findViewById(R.id.button_option_total);
                    buttonCountText.setText(String.valueOf(buttonNodes));
                    TextView totalCountText = (TextView) findViewById(R.id.total_node_count_used_text);
                    totalCountText.setText(String.valueOf(totalNodesUsed));
                    break;
                }
                default: break;
            }
        }
    }


    /*
     * Name: displayWarningDialog
     * Displays a dialog for various purposes specified by input int message.
     * Currently only implemented when accepting user input for entering game parameters.
     * Dialog is displayed when user input is invalid.
     *
     * Corresponding message value strings:
     * 0 - game duration value is not greater than 0
     * 1 - 1 or fewer node roles were selected -- needs to be at least 2
     */
    public void displayWarningDialog(int message) {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.incorrect_input_title);
        if (message == 0) {
            alertDialogBuilder.setMessage(R.string.nonpositive_duration);
        }
        else if (message == 1) {
            alertDialogBuilder.setMessage(R.string.too_few_node_roles_selected);
        }
        alertDialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // create the alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();
        // show it
        alertDialog.show();
    }


    /*
     * Name assignNodeRoles
     * For the number of node roles selected, assigns this many nodes whatever roles
     * have been chosen. Invokes a service to do the assignment.
     * Shows the choose game parameters UI.
     *
     * State: 4
     */
    private void assignNodeRoles() {
        // Start the service to perform node role assignment in the background
        Intent intent = new Intent(this, AssignNodeRolesService.class);
        intent.putExtra("game", game);
        startService(intent);
    }


    /*
     * Name:  playGame
     * Configuration is complete! This function displays the 'start game UI with a big red button.
     * Pressing the button will broadcast a kit-to-node and kit-to-kit packet to alert all players
     * and nodes that the game has begun. Then, an intent is started to switch to the master game
     * play activity.
     *
     * State: 5
     */
    private void playGame() {
        // Remove the select game parameters UI
        LinearLayout gameParamsView = (LinearLayout) findViewById(R.id.choose_game_params_layout_bopit);
        gameParamsView.setVisibility(View.GONE);

        // Add the start game UI
        LinearLayout startGameView = (LinearLayout) findViewById(R.id.active_config_play_game_display_bopit);
        startGameView.setVisibility(View.VISIBLE);

        // Update the progress bar
        configProgress.setProgress(100);
        progressText.setText(R.string.active_config_progress_text_100);

        // Set the onClickListener for the button
        Button startGameButton = (Button) findViewById(R.id.play_game_button_bopit);
        startGameButton.setOnClickListener(clickListener);
    }
}
