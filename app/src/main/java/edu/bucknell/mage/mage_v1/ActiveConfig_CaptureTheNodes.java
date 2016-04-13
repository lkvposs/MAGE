package edu.bucknell.mage.mage_v1;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
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
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


/**
 * Created by Laura on 1/8/2016.
 *
 * Active configuration for the game Capture the Nodes. Program flow is as
 * follows:
 *
 * Poll kits to see who is going to play the game.
 * Select game parameters (game duration, number of teams)
 * Assign players to a team and broadcast the game details
 * Poll available nodes
 * Broadcast to the nodes the game configuration stats
 * Wait for node confirmation signals
 * Allow user to play the game
 */
public class ActiveConfig_CaptureTheNodes extends AppCompatActivity {

    CaptureTheNodes game;
    FragmentManager manager;
    ActiveConfigCaptureFrag frag;
    String fragment_reference_tag = "active_config_capture_frag";
    int configProcessState = 0;     // necessary to save the state of the setup
                                    // in case of pausing or stopping the app
    int state = 0;                  // state value of the FSM
    //int numNodesConfirmed = 0;

    boolean bPlayersPolled = false;
    boolean bTeamsAssignedBegun = false;
    boolean bTeamsAssigned = false;
    boolean bNodesPolled = false;
    boolean bNodesAssigned = false;

    boolean mBoundMessageReceiver;
    MessageReceiver mMessageReceiverService;

    // Below strings are used for receiving broadcasts via the local broadcast manager
    String mMessageReceiverIntentAction = "newMessageReceived";
    String mAssignTeamsIntentAction = "teamAssignmentCompleted";

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
            pollPlayers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundMessageReceiver = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            configProcessState = savedInstanceState.getInt("configProcessState");
            state = savedInstanceState.getInt("state");
        }

        setContentView(R.layout.activity_activeconfig_capture);

        // start the MAGE network XBee broadcast receiver
        Intent messageReceiver = new Intent(this, MessageReceiver.class);
        startService(messageReceiver);
        bindService(messageReceiver, mConnectionMessageReceiver, Context.BIND_AUTO_CREATE);

        // setup the ActionBar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.active_config_capture_toolbar);
        setSupportActionBar(myToolbar);

        // get instance of the game that was called via intent
        Intent i = getIntent();
        game = (CaptureTheNodes)i.getSerializableExtra("game");

        // get a reference to the fragment manager
        manager = getFragmentManager();
        // display the fragment associated with active configuration
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        frag = new ActiveConfigCaptureFrag();
        fragmentTransaction.add(R.id.active_config_capture_activity_layout, frag, fragment_reference_tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        // establish the broadcast receiver to get the new MAGE network messages
        // AND to pick up messages sent by the AssignTeams Service
        IntentFilter filter = new IntentFilter(mMessageReceiverIntentAction);
        filter.addAction(mAssignTeamsIntentAction);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        switch (configProcessState) {
            case 0: if (mBoundMessageReceiver) {
                pollPlayers();
            }
                break;
            case 1: chooseGameParameters();
                    break;
            case 2: if (!bTeamsAssignedBegun) {
                assignTeams();
            }
                    break;
            case 3: if (bTeamsAssigned) {
                pollNodes(0);
            }
                    break;
            case 4: assignNodes();
                    break;
            case 5: confirmNodes();
                    break;
            case 6: playGame();
                    break;
            default: break;
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putInt("configProcessState", configProcessState);
        outState.putInt("state", state);
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
            else if (mAssignTeamsIntentAction.equals(intent.getAction())) {
                // Teams have been assigned to all players and broadcasted correspondingly
                bTeamsAssigned = true;

                // move on to the next state in the FSM
                state = 3;
                pollNodes(1);
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
            if (packetConfigState == 4) {
                // a message from a node indicating that it wants to join the game
                if (state == 3) {
                    // add the node to the node pool
                    game.processNodeGameAcceptance(nodePacket);

                    // display the updated node count
                    ActiveConfigCaptureFrag activeFrag = (ActiveConfigCaptureFrag) manager.findFragmentByTag(fragment_reference_tag);
                    if (activeFrag != null && activeFrag.isVisible()) {
                        activeFrag.updateNumPlayersOrNodes(1, game.mNumNodes);
                    }
                }
            }
        }
        else if (packetType == 2) {
            // kit-to-kit packet
            Game_Framework.KitPacket kitPacket = game.parseKitPacket(packetData, xBeeStats);
            int packetConfigState = kitPacket.configState;
            if (packetConfigState == 1) {
                // a kit has accepted the game invitation
                if (state == 1) {
                    // add the user to the user pool
                    game.processKitGameAcceptance(kitPacket);

                    // display the updated player count
                    ActiveConfigCaptureFrag activeFrag = (ActiveConfigCaptureFrag) manager.findFragmentByTag(fragment_reference_tag);
                    if (activeFrag != null && activeFrag.isVisible()) {
                        activeFrag.updateNumPlayersOrNodes(0, game.mNumPlayers);
                    }
                }
            }
        }
    }

    /*
     * Button click handler
     */
    View.OnClickListener clickHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // get reference to active fragment
            int viewID = v.getId();

            if (viewID == R.id.continue_config_button_players) {
                if (game.mNumPlayers > 1) {
                    // move to the next state in the FSM
                    state = 2;
                    chooseGameParameters();
                }
            }
            else if (viewID == R.id.game_params_submit_button) {
                // validate the entered values for game duration and number of teams
                EditText game_duration = (EditText) findViewById(R.id.game_duration_value);
                EditText num_teams = (EditText) findViewById(R.id.num_teams_value);
                int game_duration_value = 0;
                int num_teams_value = 0;
                try {
                    game_duration_value = Integer.parseInt(game_duration.getText().toString());
                    num_teams_value = Integer.parseInt(num_teams.getText().toString());
                } catch (NumberFormatException e) {
                    System.out.println("Could not parse " + e);
                }
                if ((game_duration_value > 0) && (num_teams_value > 1) && (game.mNumPlayers%num_teams_value == 0)) {
                    // all input is acceptable -- save entered values
                    // and then continue to the next step of configuration
                    game.mNumTeams = num_teams_value;
                    game.mGameDuration = game_duration_value;
                    assignTeams();
                }
                else if (game_duration_value <=0) {
                    displayWarningDialog(0);
                }
                else if (num_teams_value <= 0) {
                    displayWarningDialog(1);
                }
                else if (game.mNumPlayers%num_teams_value != 0) {
                    displayWarningDialog(2);
                }
            }
            else if (viewID == R.id.continue_config_button_Nodes) {
                if (game.mNumNodes > 0) {
                    state = 4;
                    assignNodes();
                }
            }
            else if (viewID == R.id.confirm_nodes_abort_button) {
                // reset any node information gathered from the previous poll
                game.mNumNodes = 0;
                frag.updateNumPlayersOrNodes(1, game.mNumNodes);
                game.nodes.clear();

                // broadcast a kit-to-kit packet alerting other kits to also
                // clear logged node data
                game.broadcastKitPacket(6, 0, "", true, null, mMessageReceiverService);

                // update the FSM state to be able to correctly receive packets
                state = 3;
                pollNodes(2);
            }
            else if (viewID == R.id.play_game_button) {
                // broadcast kit-to-kit packet and kit-to-node packet
                // to inform the start of the game
                // begin executing the game play activity

                // broadcast kit-to-node packet indicating game start
                game.broadcastNodePacket(0, 0, 0, 1, true, null, mMessageReceiverService);
                // broadcast kit-to-kit packet indicating game start
                game.broadcastKitPacket(5, 1, "", true, null, mMessageReceiverService);

                // start the game play activity
                game.startGamePlay(v.getContext(), game);
            }
        }
    };

    /*
     * Name: displayWarningDialog
     * Displays a dialog for various purposes specified by input int message.
     * Currently only implemented when accepting user input for entering game parameters.
     * Dialog is displayed when user input is invalid.
     *
     * Corresponding message value strings:
     * 0 - game duration value is not greater than 0
     * 1 - number of teams value is not greater than 0
     * 2 - number of teams value is not divisible by number of players
     */
    public void displayWarningDialog(int message) {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.incorrect_input_title);
        if (message == 0) {
            alertDialogBuilder.setMessage(R.string.nonpositive_duration);
        }
        else if (message == 1) {
            alertDialogBuilder.setMessage(R.string.nonpositive_num_teams);
        }
        else if (message == 2) {
            alertDialogBuilder.setMessage(R.string.nondivisible_num_teams);
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
     * Name: pollPlayers
     * Initial kit-to-kit broadcast to send out game request.
     * Displays the 'polling players' UI
     *
     * FSM State: 0
     * UI: Polling Players
     */
    private void pollPlayers() {
        // update the UI to display polling players
        frag.pollingPlayersDisplay(true);
        Button contConfig = (Button) findViewById(R.id.continue_config_button_players);
        contConfig.setOnClickListener(clickHandler);

        // get the username to send along with game requests
        // this field is required to be populated before beginning active configuration
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username", "~");
        String kitInfo = username + ";" + game.returnName();

        if (!bPlayersPolled) {
            // send broadcast to all kits with a new game request
            game.broadcastKitPacket(2, 0, kitInfo, true, null, mMessageReceiverService);
            bPlayersPolled = true;
        }

        // move to next state in FSM
        state = 1;
    }

    /*
     * Name: chooseGameParamters
     * Presents the 'game parameters' UI where the configuring user is able to input
     * a chosen game duration and number of teams. Both the specified game duration and number
     * of teams must be larger than 0. The number of players logged during the polling players
     * step (state 1) must be divisible by the number of teams entered. If any of these criteria
     * are not met, the user will be prompted by a dialog to change the selected values.
     *
     * FSM State: 2
     * UI: Choose Game Parameters
     */
    private void chooseGameParameters() {

        configProcessState = 1;

        // remove the polling players UI
        frag.pollingPlayersDisplay(false);

        // display the available game parameters to modify
        frag.chooseGameParamsDisplay(true);
        Button submitParams = (Button) findViewById(R.id.game_params_submit_button);
        if (submitParams != null) {
            submitParams.setOnClickListener(clickHandler);
        }

    }

    /*
     * Name: assignTeams()
     * Cycles through each player that was logged during the polling players step (state 1) and
     * assigns each player to a team. Number of teams specified during the chooseGameParameters()
     * function. As assignments are made, a kit-to-kit packet is broadcast to each player
     * individually, which is why players and their corresponding XBee IDs were logged when their
     * game acceptances were initially received. This individual broadcast contains the duration
     * of the game, the number of teams playing the game, and what team a player is on.
     *
     * FSM State: 2
     * UI: Choose Game Parameters
     */
    private void assignTeams() {

        configProcessState = 2;

        // Always automatically add the configuring player to the last team
        game.mMyTeamNum = game.mNumTeams;

        // Start the service to perform team assignment in the background
        Intent intent = new Intent(this, AssignTeamsService.class);
        intent.putExtra("game", game);
        startService(intent);
        bTeamsAssignedBegun = true;

        configProcessState = 3;
    }

    /*
     * Name: pollNodes
     * Broadcasts a kit-to-node packet to discover available nodes for use throughout the game.
     * INPUT int arrivalPath -- designates which function called pollNodes
     * arrivalPath = 0 --> onResume called -- indicates some interruption in app logic execution occured
     * arrivalPath = 1 --> assignTeams called
     * arrivalPath = 2 --> retrying to poll nodes after a nonsuccessful confirmation attempt
     *
     * the arrivalPath input is necessary to determine the correct UI to display
     *
     * FSM State: 3
     * UI: Polling Nodes
     */
    private void pollNodes(int arrivalPath) {

        // update the UI appropriately
        if (arrivalPath == 1) {
            // remove the UI corresponding to game parameters
            frag.chooseGameParamsDisplay(false);
        }
        else if (arrivalPath == 2) {
            // remove the UI corresponding to confirming nodes
            frag.confirmNodesDisplay(false);
        }

        // add the new UI to the fragment
        frag.pollingNodesDisplay(true);
        Button submitButton = (Button) findViewById(R.id.continue_config_button_Nodes);
        submitButton.setOnClickListener(clickHandler);

        if (!bNodesPolled) {
            // broadcast message to nodes for game availability
            game.broadcastNodePacket(1, 1, 1, 0, true, null, mMessageReceiverService);
            bNodesPolled = true;
        }
    }

    /*
     * Name: assignNodes
     * Assigns the role of each node that has responded its availability for use. For this game,
     * each node will have the same role, so only 1 kit-to-node packet is required to be broadcast
     * to all nodes.
     *
     * FSM State: 4
     * UI: Polling Nodes
     */
    public void assignNodes() {

        /*
         * THIS FUNCTION WAS REMOVED AND THE FUNCTIONALITY MOVED TO POLL NODES
         */
        configProcessState = 4;
        /*
        if (!bNodesAssigned) {
            // create kit-to-node packet to broadcast game and role to each node
            game.broadcastNodePacket(0, 1, 1, 0, true, null, mMessageReceiverService);
            bNodesAssigned = true;
        }
        */
        confirmNodes();
    }

    /*
     * Name: confirmNodes
     * Update the UI to reflect the current state of processing node confirmation messages that
     * they received their game and role details.
     *
     * FSM State: 4
     * UI: Confirm Nodes
     */
    public void confirmNodes() {

        configProcessState = 5;

        // remove the previous UI
        frag.pollingNodesDisplay(false);
        // show the new UI
        frag.confirmNodesDisplay(true);
        Button confirmNodesAbortButton = (Button) findViewById(R.id.confirm_nodes_abort_button);
        confirmNodesAbortButton.setOnClickListener(clickHandler);

        state = 5;
        playGame();
        // Logic for handling message confirmations is located in the packetReceiver() function
    }

    /*
     * Name: playGame
     * Configuration is complete! This function displays the 'start game' UI with a big red button.
     * Pressing the button will broadcast a kit-to-node and kit-to-kit packet to alert all players
     * and nodes that the game has begun. Then, an intent is started to switch to the game play
     * activity.
     *
     * FSM State: 5
     * UI: Play Game
     */
    public void playGame() {

        configProcessState = 6;

        // remove the confirm nodes UI
        frag.confirmNodesDisplay(false);
        // show the new UI
        frag.playGameDisplay(true);
        Button playGameButton = (Button) findViewById(R.id.play_game_button);
        playGameButton.setOnClickListener(clickHandler);
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
