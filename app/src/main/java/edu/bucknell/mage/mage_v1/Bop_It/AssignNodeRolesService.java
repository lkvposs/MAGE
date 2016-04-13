package edu.bucknell.mage.mage_v1.Bop_It;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import edu.bucknell.mage.mage_v1.MessageReceiver;

/**
 * Created by Laura on 3/21/2016.
 */
public class AssignNodeRolesService extends Service {

    BopIt game;
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

        game = (BopIt) intent.getSerializableExtra("game");
        assignRoles();

        return START_NOT_STICKY;
    }

    /*
     * Assigns a role to each node based on the roles selected by the configuring user.
     * Not all nodes that responded need to be used.
     *
     * Role key:
     * 1 - NFC Scan
     * 2 - Shake
     * 3 - Detect
     * 4 - Flip
     * 5 - Button
     */
    private void assignRoles() {
        int count = 0;
        int i;
        for (i = 0; i < game.numNFCNodes; i++) {
            game.nodes.get(count).node_role = 1;
            count++;
        }
        for (i = 0; i < game.numShakeNodes; i++) {
            game.nodes.get(count).node_role = 2;
            count++;
        }
        for (i = 0; i < game.numDetectNodes; i++) {
            game.nodes.get(count).node_role = 3;
            count++;
        }
        for (i = 0; i < game.numFlipNodes; i++) {
            game.nodes.get(count).node_role = 4;
            count++;
        }
        for (i = 0; i < game.numButtonNodes; i++) {
            game.nodes.get(count).node_role = 5;
            count++;
        }

        // Once all roles have been assigned, see if we are ready to
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
        for (i = 0; i < game.nodes.size(); i++) {
            if (game.nodes.get(i).node_role != 0) {
                // A role has been assigned to this node, so tell the node of its role
                game.broadcastNodePacket(1, 2, game.nodes.get(i).node_role, 0, false, game.nodes.get(i), mMessageReceiverService);
            }
        }

        // alert the activity that team assignment is finished
        nodeRoleAssignmentCompleted();

        // my work here is done -- stop this service
        this.stopSelf();
    }

    private void nodeRoleAssignmentCompleted() {
        Intent intent = new Intent("nodeRoleAssignmentCompleted");
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
