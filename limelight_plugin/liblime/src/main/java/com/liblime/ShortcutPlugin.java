package com.liblime;

import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.liblime.types.UnityPluginObject;
import com.limelight.LimeLog;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.utils.ServerHelper;

import java.io.IOException;
import java.util.UUID;

public class ShortcutPlugin extends UnityPluginObject {
    public static final String UUID_EXTRA = "UUID";
    public static final String EXTRA_APP_ID = "APP_ID";
    public static final String EXTRA_APP_NAME = "APP_NAME";
    private String uuidString;
    private NvApp app;
    //    private int wakeHostTries = 10;
    private ComputerDetails computer;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        //TODO:didnt think through, we have to remove binder anyway...
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder) binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Get the computer object
                    computer = managerBinder.getComputer(uuidString);

                    if (computer == null) {
                        mPluginManager.Dialog("Error:Computer not found", PluginManager.MessageLevel.FATAL);
                        return;
                    }

                    // Force CMS to repoll this machine
                    managerBinder.invalidateStateForComputer(computer.uuid);
                    // Start polling
                    managerBinder.startPolling(new ComputerManagerListener() {
                        @Override
                        public void notifyComputerUpdated(final ComputerDetails details) {
                            // Don't care about other computers
                            LimeLog.temp("" + details.state);
                            if (!details.uuid.equalsIgnoreCase(uuidString)) {
                                return;
                            }

                            //TODO:neat feature mayber later
                            // Try to wake the target PC if it's offline (up to some retry limit)
                            if (details.state == ComputerDetails.State.OFFLINE) {
                                mPluginManager.Dialog("Error:Computer offline", PluginManager.MessageLevel.FATAL);
                            }
//                                if (details.state == ComputerDetails.State.OFFLINE && details.macAddress != null && --wakeHostTries >= 0) {
//                                try {
//                                    // Make a best effort attempt to wake the target PC
//                                    WakeOnLanSender.sendWolPacket(computer);
//
//                                    // If we sent at least one WoL packet, reset the computer state
//                                    // to force ComputerManager to poll it again.
//                                    managerBinder.invalidateStateForComputer(computer.uuid);
//                                    return;
//                                } catch (IOException e) {
//                                    // If we got an exception, we couldn't send a single WoL packet,
//                                    // so fallthrough into the offline error path.
//                                    e.printStackTrace();
//                                }
//                            }

                            if (details.state != ComputerDetails.State.UNKNOWN) {

                                // If the managerBinder was destroyed before this callback,
                                // just finish the activity.
                                if (managerBinder == null) {
                                    finish();
                                    return;
                                }

                                if (details.state == ComputerDetails.State.ONLINE && details.pairState == PairingManager.PairState.PAIRED) {

                                    // Launch game if provided app ID, otherwise launch app view
                                    if (app != null) {

                                        if (details.runningGameId == 0 || details.runningGameId == app.getAppId()) {
//                                                    intentStack.add(ServerHelper.createStartIntent(ShortcutTrampoline.this, app, details, managerBinder));
                                            Intent intent = ServerHelper.createStartIntent(mActivity, app, details, managerBinder);
                                            managerBinder.stopPolling();
                                            final PluginManager m = mPluginManager;
                                            // Close this activity
                                            finish();
                                            m.ActivatePlugin(PluginManager.PluginType.STREAM, intent);

                                            // Now start the activities
                                        } else {
                                            //TODO: neat feature, but maybe later
                                            // Create the start intent immediately, so we can safely unbind the managerBinder
                                            // below before we return.
//                                                    final Intent startIntent = ServerHelper.createStartIntent(ShortcutTrampoline.this, app, details, managerBinder);
//
//                                                    UiHelper.displayQuitConfirmationDialog(ShortcutTrampoline.this, new Runnable() {
//                                                        @Override
//                                                        public void run() {
//                                                            intentStack.add(startIntent);
//
//                                                            // Close this activity
//                                                            finish();
//
//                                                            // Now start the activities
//                                                            startActivities(intentStack.toArray(new Intent[]{}));
//                                                        }
//                                                    }, new Runnable() {
//                                                        @Override
//                                                        public void run() {
//                                                            // Close this activity
//                                                            finish();
//                                                        }
//                                                    });
                                            finish();
                                        }
                                    } else {
                                        //TODO: neat feature, but maybe later
                                        // Close this activity
                                        finish();

//                                                // Add the PC view at the back (and clear the task)
//                                                Intent i;
//                                                i = new Intent(ShortcutTrampoline.this, PcView.class);
//                                                i.setAction(Intent.ACTION_MAIN);
//                                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
//                                                intentStack.add(i);
//
//                                                // Take this intent's data and create an intent to start the app view
//                                                i = new Intent(getIntent());
//                                                i.setClass(ShortcutTrampoline.this, AppView.class);
//                                                intentStack.add(i);
//
//                                                // If a game is running, we'll make the stream the top level activity
//                                                if (details.runningGameId != 0) {
//                                                    intentStack.add(ServerHelper.createStartIntent(ShortcutTrampoline.this,
//                                                            new NvApp(null, details.runningGameId, false), details, managerBinder));
//                                                }
//
//                                                // Now start the activities
//                                                startActivities(intentStack.toArray(new Intent[]{}));
                                    }

                                } else if (details.state == ComputerDetails.State.OFFLINE) {
                                    // Computer offline - display an error dialog
                                    mPluginManager.Dialog("Error:Computer offline", PluginManager.MessageLevel.FATAL);
                                } else if (details.pairState != PairingManager.PairState.PAIRED) {
                                    // Computer not paired - display an error dialog
                                    mPluginManager.Dialog("Error:Computer not paird", PluginManager.MessageLevel.FATAL);
                                }

                                // We don't want any more callbacks from now on, so go ahead
                                // and unbind from the service
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    public ShortcutPlugin(PluginManager p, Activity a, Intent i) {
        super(p, a, i);
        mPluginType = PluginManager.PluginType.SHORTCUT;
        onCreate();
    }

    @Override
    protected void onCreate() {
        String appIdString = getIntent().getStringExtra(EXTRA_APP_ID);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        if (validateInput(uuidString, appIdString)) {
            if (appIdString != null && !appIdString.isEmpty()) {
                app = new NvApp(getIntent().getStringExtra(EXTRA_APP_NAME),
                        Integer.parseInt(appIdString),
                        false);
            }

            // Bind to the computer manager service
            bindService(new Intent(mActivity, ComputerManagerService.class), serviceConnection,
                    Service.BIND_AUTO_CREATE);

        }
    }

    protected boolean validateInput(String uuidString, String appIdString) {
        // Validate UUID
        if (uuidString == null) {
            mPluginManager.Dialog("No UUID provided");
            return false;
        }

        try {
            UUID.fromString(uuidString);
        } catch (IllegalArgumentException ex) {
            mPluginManager.Dialog("Invalid UUID provided:" + uuidString);
            return false;
        }

        // Validate App ID (if provided)
        if (appIdString != null && !appIdString.isEmpty()) {
            try {
                Integer.parseInt(appIdString);
            } catch (NumberFormatException ex) {
                mPluginManager.Dialog("Invalid App ID provided" + appIdString);
                return false;
            }
        }

        return true;
    }

    @Override
    public void onPause() {
        onDestroy();
    }

    @Override
    public void onResume() {

    }

    @Override
    public void onDestroy() {

        if (managerBinder != null) {
            managerBinder.stopPolling();
            unbindService(serviceConnection);
            managerBinder = null;
        }
        RevmoveReference();
    }
}


