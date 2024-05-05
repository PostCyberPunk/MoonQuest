package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.types.ComputerObject;
import com.limelight.types.PcList;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.preferences.AddComputerManually;
import com.limelight.types.UnityPluginObject;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.xmlpull.v1.XmlPullParserException;

public class PcPlugin extends UnityPluginObject {
    private AddComputerManually m_addComputerManually;
    private PcList pcList;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, is;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
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

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(mActivity).getClientCertificate();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    public PcPlugin(PluginManager p, Activity a) {
        super(p, a);
        onCreate();
        isInitialized= true;
    }

    @Override
    protected void onCreate() {
        inForeground = true;
        LimeLog.debug("PcPlugin onCreate");

        // Bind to the computer manager service
        bindService(new Intent(mActivity, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcList = new PcList();
    }

    @Override
    public void onDestroy() {

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
        stopAddComputerManually();
    }

    @Override
    public void onResume() {

        inForeground = true;
        startComputerUpdates();
        stopAddComputerManually();
    }

    @Override
    public void onPause() {

        inForeground = false;
        stopComputerUpdates(false);
        stopAddComputerManually();
    }

    @Override
    public void onStop() {

        Dialog.closeDialogs();
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            LimeLog.todo("Computer is offline or has no active address");
            return;
        }
        if (managerBinder == null) {
            LimeLog.todo("Manager binder is null");
            return;
        }

        LimeLog.todo("Pairing with computer: " + computer.name);
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(mActivity));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        //TODO:open the app list
                        doAppList(computer, false, false);
                        LimeLog.todo("Already paired");

                    } else {
                        final String pinStr = PairingManager.generatePinString();

                        // Spin the dialog off in a thread because it blocks
                        LimeLog.todo("Displaying Pairing Dialog");
                        UnityMessager.Warn("PIN" + pinStr);
                        LimeLog.severe("PIN: " + pinStr);

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            LimeLog.todo("Pairing failed: Incorrect PIN");
                        } else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                LimeLog.todo("Pairing failed: In-game");
                            } else {
                                LimeLog.todo("Pairing failed: Unknown reason");
                            }
                        } else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            LimeLog.todo("Pairing failed: Already in progress");
                        } else if (pairState == PairState.PAIRED) {
                            LimeLog.todo("Pairing successful");
                            // Just navigate to the app view without displaying a toast

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        } else {
                            // Should be no other values
                            LimeLog.todo("Pairing failed: Unknown state");
                        }
                    }
                } catch (UnknownHostException e) {
                    LimeLog.todo("Pairing failed: Unknown host");
                } catch (FileNotFoundException e) {
                    LimeLog.todo("Pairing failed: File Not Found:" + e.getMessage());
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    LimeLog.todo("Pairing failed: " + e.getMessage());
                }

                LimeLog.todo("Pairing complete");
                doAppList(computer, true, false);
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            LimeLog.todo("Computer is offline");
            return;
        }
        if (managerBinder == null) {
            LimeLog.todo("Manager binder is null");
            return;
        }

        Intent i = new Intent(mActivity, AppPlugin.class);
        i.putExtra(AppPlugin.NAME_EXTRA, computer.name);
        i.putExtra(AppPlugin.UUID_EXTRA, computer.uuid);
        mPluginManager.ActivateAppPlugin(i);
        finish();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        LimeLog.debug("Computer updated: " + details.pairState);
                        updateComputer(details);
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcList.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcList.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        } else {
            // Add a new entry
            pcList.addComputer(new ComputerObject(details));

        }
        LimeLog.todo("Update the computer list view");
    }

    public void stopAddComputerManually() {
        if (m_addComputerManually != null) {
            m_addComputerManually.Destroy();
            m_addComputerManually = null;
        }
    }

    //Try
    private void fakeAdd() {
        m_addComputerManually = new AddComputerManually(mActivity, this);
    }

    public void fakePair() {
        LimeLog.debug("Start fakePair");
        ComputerObject computer = (ComputerObject) pcList.getItem(0);
        doPair(computer.details);
    }

    public void fakeStart() {
        if (pcList.getCount() == 0) {
            fakeAdd();
        } else {
            ComputerObject computer = (ComputerObject) pcList.getItem(0);
            if (computer.details.pairState == PairState.NOT_PAIRED) {
                fakePair();
            } else {
                doAppList(computer.details, false, false);
            }
        }
    }
    //End of class-----------
}
