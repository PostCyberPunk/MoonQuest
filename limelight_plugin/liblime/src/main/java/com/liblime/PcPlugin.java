package com.liblime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.liblime.types.ComputerObject;
import com.liblime.types.PcList;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.preferences.AddComputerManually;
import com.liblime.types.UnityPluginObject;
import com.limelight.utils.ServerHelper;
import com.unity3d.player.UnityPlayer;

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
        mPluginType = PluginManager.PluginType.PC;
        onCreate();
        isInitialized = true;
        LimeLog.debug("PcPlugin Initialized");
        mPluginManager.Callback("UIPC");
    }

    @Override
    protected void onCreate() {
        inForeground = true;

        // Bind to the computer manager service
        bindService(new Intent(mActivity, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcList = new PcList();
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
    public void onDestroy() {
        stopComputerUpdates(false);
        stopAddComputerManually();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
        RevmoveReference();
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
                        success = true;
                        LimeLog.todo("Already paired");
                    } else {
                        final String pinStr = PairingManager.generatePinString();

                        // Spin the dialog off in a thread because it blocks
                        mPluginManager.Callback("pairc|" + pinStr);
                        LimeLog.warning("PIN: " + pinStr);

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
                            success = true;
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
                final boolean finalSuccess = success;
                UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LimeLog.todo("Pairing complete");
                        if (finalSuccess) {
                            doAppList(computer, true, false);
                            PluginManager.GetInstance().Callback("pairc|1");
                        } else {
                            PluginManager.GetInstance().Callback("pairc|0");
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            LimeLog.todo("Computer is offline");
            return;
        }
        if (managerBinder == null) {
            LimeLog.severe("Manager binder is null");
            return;
        }

        Intent i = new Intent(mActivity, AppPlugin.class);
        i.putExtra(AppPlugin.NAME_EXTRA, computer.name);
        i.putExtra(AppPlugin.UUID_EXTRA, computer.uuid);
        LimeLog.debug("Starting AppPlugin");
        if (newlyPaired) {
            mPluginManager.Callback("pairc|1");
        }
        mPluginManager.ActivatePlugin(PluginManager.PluginType.APP, i);
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
                        LimeLog.verbose("Computer updated: " + details.pairState);
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
            //check if the existinEntry is same as the updated one
            //TODO:add equal overload this,ok differ not working
//            if (existingEntry.details.pairState != details.pairState
//                    || existingEntry.details.state != details.state
//                    || details.name != existingEntry.details.name) {
//            }
            // Replace the information in the existing entry
            existingEntry.details = details;
            pcList.updateComputer(existingEntry);
        } else {
            // Add a new entry
            pcList.addComputer(new ComputerObject(details));

        }
        notifyUpdateList();
    }

    public void stopAddComputerManually() {
        if (m_addComputerManually != null) {
            m_addComputerManually.Destroy();
            m_addComputerManually = null;
        }
    }

    //Bridge
    public String GetList() {
        String result = pcList.getUpdatedList();
        freezeUpdates = false;
        return result;
    }

    private void notifyUpdateList() {
        if (pcList.needUpdate() && !freezeUpdates) {
            LimeLog.verbose("notify unity to update the computer list view");
            mPluginManager.Callback("pclist");
            freezeUpdates = true;
        }
    }

    public void AddComputerManually(String url) {
        m_addComputerManually = new AddComputerManually(mActivity, this, url);
    }

    public void PairComputer(String uuid) {
        ComputerObject computer = (ComputerObject) pcList.getItem(uuid);
        doPair(computer.details);
    }

    public void StartAppList(String uuid) {
        ComputerObject computer = (ComputerObject) pcList.getItem(uuid);
        doAppList(computer.details, false, false);
    }

    //End of class-----------
}
