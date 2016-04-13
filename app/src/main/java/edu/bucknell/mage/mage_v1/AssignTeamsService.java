package edu.bucknell.mage.mage_v1;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by Laura on 2/9/2016.
 * This service is used during Active Configuration for assigning players to
 * a team uninterrupted. It will broadcast the necessary details as they are
 * determined.
 *
 * This service binds to the message receiving/sending service in order to
 * send the broadcasts to the other kits.
 */
public class AssignTeamsService extends Service {

    CaptureTheNodes game;
    MessageReceiver mMessageReceiverService;
    boolean mBound = false;
    boolean mReadyToBroadcast = false;
    boolean mAlreadyBroadcasted = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to MessageReceiver, cast the IBinder and get MessageBinder
            MessageReceiver.MessageBinder binder = (MessageReceiver.MessageBinder) service;
            mMessageReceiverService = binder.getService();
            mBound = true;
            if (mReadyToBroadcast && !mAlreadyBroadcasted) {
                mAlreadyBroadcasted = true;
                sendResults();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // Bind to local MAGE network XBee broadcast receiver
        Intent messageReceiverIntent = new Intent(this, MessageReceiver.class);
        bindService(messageReceiverIntent, mConnection, Context.BIND_AUTO_CREATE);

        game = (CaptureTheNodes) intent.getSerializableExtra("game");
        assignTeams();

        return START_NOT_STICKY;
    }

    private void assignTeams() {
        int player = 0;             // Keep track of which player we're assigning
        int teamToAssign = 1;       // What team we are currently assigning players to
        int playersAssigned = 0;    // How many players have currently been assigned to a team
        int playersPerTeam = game.mNumPlayers/game.mNumTeams;

        while (player < game.mNumPlayers-1) {
            if (playersAssigned < playersPerTeam) {
                game.players.get(player).playerTeam = teamToAssign;
                player++;
                playersAssigned++;
            }
            else {
                teamToAssign++;
                playersAssigned = 0;
            }
        }

        // Once all teams have been assigned, see if we are ready to
        // broadcast the results
        tryBroadcast();
    }

    private void tryBroadcast() {
        if (mBound && !mAlreadyBroadcasted) {
            sendResults();
            mAlreadyBroadcasted = true;
        }
    }

    private void sendResults() {
        int i;
        for (i=0; i < game.players.size(); i++) {
            // Broadcast game stats and team number to player
            String kitInfo = Integer.toString(game.mGameDuration) + ";" + Integer.toString(game.mNumTeams) + ";" + Integer.toString(game.players.get(i).playerTeam);
            game.broadcastKitPacket(3, 0, kitInfo, false, game.players.get(i).xBeeDevice, mMessageReceiverService);
        }

        // alert the activity that team assignment is finished
        teamAssignmentCompleted();

        // my work here is done -- stop this service
        this.stopSelf();
    }

    private void teamAssignmentCompleted() {
        Intent intent = new Intent("teamAssignmentCompleted");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unbind from message receiver service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}
