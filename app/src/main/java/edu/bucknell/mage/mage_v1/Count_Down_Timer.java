package edu.bucknell.mage.mage_v1;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import static android.content.Intent.getIntent;

/**
 * Created by Laura on 2/4/2016.
 */
public class Count_Down_Timer extends Service {

    // Binder given to clients
    private final IBinder mBinder = new TimerBinder();

    CountDownTimer timer;
    int mChosenTimerDuration;           // Value entered by user in minutes
    int mDefaultTimerDuration = 10;     // Duration if somehow no value was provided
                                        // which should never happen

    /*
     * TimerBinder - Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class TimerBinder extends Binder {
        public Count_Down_Timer getService() {
            // Return this instance of Count_Down_Timer so clients can call public methods
            return Count_Down_Timer.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int start_info = super.onStartCommand(intent, flags, startId);

        // Get how long to run the timer for
        mChosenTimerDuration = intent.getIntExtra("game_duration", mDefaultTimerDuration);

        /*
         * Start the timer -- the value in mChosenTimerDuration is in minutes
         * but the CountDownTimer takes an input of milliseconds
         * Execute the onTick function every minute
         */
        timer = new CountDownTimer(mChosenTimerDuration*60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // broadcast the current remaining time in minutes
                sendRemainingTime(millisUntilFinished, false);
            }

            @Override
            public void onFinish() {
                // broadcast that the timer duration has elapsed
                sendRemainingTime(0, true);
            }
        };
        timer.start();

        return start_info;
    }

    /*
     * Name: sendRemainingTime
     * Called from the onTick function of the CountDownTimer, which should happen every
     * minute. Also called from the onFinish function once the timer completes. This is
     * how the timer updates whatever activity is using it of the change in time.
     */
    private void sendRemainingTime(long remainingTime, boolean timerFinished) {
        Intent intent = new Intent("remainingTime");
        intent.putExtra("timerFinished", timerFinished);
        intent.putExtra("remainingTime", remainingTime);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // method for clients
    public void cancelTimer() {
        timer.cancel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
