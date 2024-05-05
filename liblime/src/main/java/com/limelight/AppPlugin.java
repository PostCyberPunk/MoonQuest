package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.types.AppList;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.types.AppObject;
import com.limelight.types.UnityPluginObject;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.ServerHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.xmlpull.v1.XmlPullParserException;

public class AppPlugin extends UnityPluginObject {
    private AppList m_AppList;
    private String uuidString;
    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    private ComputerManagerService.ComputerManagerBinder managerBinder;
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

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    try {
                        m_AppList = new AppList();
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }
                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    public AppPlugin(PluginManager p, Activity a, Intent i) {
        super(p, a, i);
        onCreate();
        isInitialized = true;
    }

    @Override
    protected void onCreate() {
        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        String computerName = getIntent().getStringExtra(NAME_EXTRA);

        LimeLog.debug("AppPlugin created for pc : " + computerName);

        // Bind to the computer manager service
        bindService(new Intent(mActivity, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                //TODO:This also need to be apply on unity side
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            LimeLog.todo("Lost Connection to PC");
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LimeLog.todo("PC is not paired");
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }
                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (m_AppList != null) {

            LimeLog.todo("AppList is not null");
        }
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: " + lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            //TODO:block
        }
    }

    @Override
    public void onDestroy() {

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    public void onResume() {

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    public void onPause() {

        inForeground = false;
        stopComputerUpdates();
    }


    //TODO:notify unity here
    private void updateUiWithServerinfo(final ComputerDetails details) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // Look through our current app list to tag the running app
                for (int i = 0; i < m_AppList.getCount(); i++) {
                    AppObject existingApp = (AppObject) m_AppList.getItem(i);

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    } else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    } else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    } else {
                        // This app wasn't running and still isn't
                    }
                }

                int count = m_AppList.getCount();
                LimeLog.debug("App count" + count);
                if (count > 0) {
                    final AppObject app = (AppObject) m_AppList.getItem(0);
                    LimeLog.info("Starting app: " + app.app.getAppName());
                    ServerHelper.doStart(mPluginManager, app.app, computer, managerBinder);
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (int i = 0; i < m_AppList.getCount(); i++) {
                        AppObject existingApp = (AppObject) m_AppList.getItem(i);
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        m_AppList.addApp(new AppObject(app));

                        updated = true;
                    }
                }

                // Next handle app removals
                int i = 0;
                while (i < m_AppList.getCount()) {
                    boolean foundExistingApp = false;
                    AppObject existingApp = (AppObject) m_AppList.getItem(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        m_AppList.removeApp(existingApp);
                        updated = true;

                        // Check this same index again because the item at i+1 is now at i after
                        // the removal
                        continue;
                    }

                    // Move on to the next item
                    i++;
                }
            }
        });
    }

}
