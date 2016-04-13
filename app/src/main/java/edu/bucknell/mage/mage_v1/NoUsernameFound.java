package edu.bucknell.mage.mage_v1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;

/**
 * Created by Laura on 1/10/2016.
 */

public class NoUsernameFound extends DialogFragment {

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */

    public interface NoUsernameFoundDialogListener {
        public void onDialogOkClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    NoUsernameFoundDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoUsernameFoundDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoUsernameFoundDialogListener so we can send events to the host
            mListener = (NoUsernameFoundDialogListener) getTargetFragment();
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoUsernameFoundDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Missing Username")
            .setMessage("Please enter a username before starting this game.");
        final EditText input_username = new EditText(getActivity());
        builder.setView(input_username)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // save username in SharedPreferences file
                        String username = input_username.getText().toString();
                        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(getActivity().getResources().getString(R.string.settings_username), username);
                        editor.commit();

                        mListener.onDialogOkClick(NoUsernameFound.this);
                    }
                });

        return builder.create();
    }
}
