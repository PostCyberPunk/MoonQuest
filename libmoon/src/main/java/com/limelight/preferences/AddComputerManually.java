package com.limelight.preferences;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.LimeLog;
import com.limelight.PcPlugin;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.utils.ServerHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class AddComputerManually {
    private Activity mActivity;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<>();
    private Thread addThread;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder) binder);
            startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            managerBinder = null;
        }
    };

    private boolean isWrongSubnetSiteLocalAddress(String address) {
        try {
            InetAddress targetAddress = InetAddress.getByName(address);
            if (!(targetAddress instanceof Inet4Address) || !targetAddress.isSiteLocalAddress()) {
                return false;
            }

            // We have a site-local address. Look for a matching local interface.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address) || !addr.getAddress().isSiteLocalAddress()) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue;
                    }

                    byte[] targetAddrBytes = targetAddress.getAddress();
                    byte[] ifaceAddrBytes = addr.getAddress().getAddress();

                    // Compare prefix to ensure it's the same
                    boolean addressMatches = true;
                    for (int i = 0; i < addr.getNetworkPrefixLength(); i++) {
                        if ((ifaceAddrBytes[i / 8] & (1 << (i % 8))) != (targetAddrBytes[i / 8] & (1 << (i % 8)))) {
                            addressMatches = false;
                            break;
                        }
                    }

                    if (addressMatches) {
                        return false;
                    }
                }
            }

            // Couldn't find a matching interface
            return true;
        } catch (Exception e) {
            // Catch all exceptions because some broken Android devices
            // will throw an NPE from inside getNetworkInterfaces().
            e.printStackTrace();
            return false;
        }
    }

    private URI parseRawUserInputToUri(String rawUserInput) {
        try {
            // Try adding a scheme and parsing the remaining input.
            // This handles input like 127.0.0.1:47989, [::1], [::1]:47989, and 127.0.0.1.
            URI uri = new URI("moonlight://" + rawUserInput);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
        }

        try {
            // Attempt to escape the input as an IPv6 literal.
            // This handles input like ::1.
            URI uri = new URI("moonlight://[" + rawUserInput + "]");
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
        }

        return null;
    }

    private void doAddPc(String rawUserInput) throws InterruptedException {
        boolean wrongSiteLocal = false;
        boolean invalidInput = false;
        boolean success;
        int portTestResult;

        try {
            ComputerDetails details = new ComputerDetails();

            // Check if we parsed a host address successfully
            URI uri = parseRawUserInputToUri(rawUserInput);
            if (uri != null && uri.getHost() != null && !uri.getHost().isEmpty()) {
                String host = uri.getHost();
                int port = uri.getPort();

                // If a port was not specified, use the default
                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT;
                }

                details.manualAddress = new ComputerDetails.AddressTuple(host, port);
                success = managerBinder.addComputerBlocking(details);
                if (!success) {
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
                }
            } else {
                // Invalid user input
                success = false;
                invalidInput = true;
            }
        } catch (InterruptedException e) {
            // Propagate the InterruptedException to the caller for proper handling
            throw e;
        } catch (IllegalArgumentException e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
            invalidInput = true;
        }

        // Keep the SpinnerDialog open while testing connectivity
        if (!success && !wrongSiteLocal && !invalidInput) {
            // Run the test before dismissing the spinner because it can take a few seconds.
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        } else {
            // Don't bother with the test if we succeeded or the IP address was bogus
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        }


        if (invalidInput) {
            LimeLog.todo("Invalid input: " + rawUserInput);
        } else if (wrongSiteLocal) {
            LimeLog.todo("Site-local address detected: " + rawUserInput);
        } else if (!success) {
            String dialogText;
            if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                LimeLog.todo("Blocked address: " + rawUserInput + " (" + portTestResult + ")");
            } else {
                LimeLog.todo("Failed to add computer: " + rawUserInput);
            }
            LimeLog.todo("Port test result: " + portTestResult);
        } else {
            LimeLog.todo("Seems sucess?" + rawUserInput);
        }
        if (success) {
            LimeLog.todo("Successfully added computer: " + rawUserInput);
            mPlugin.fakePair();
        }
    }

    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        String computer = computersToAdd.take();
                        doAddPc(computer);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                finish();
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt();
            }

            addThread = null;
        }
    }

    public void Destroy() {
        if (managerBinder != null) {
            joinAddThread();
            mActivity.unbindService(serviceConnection);
        }
    }
    private final PcPlugin mPlugin;
    public AddComputerManually(Activity activity, PcPlugin plugin) {
        mActivity = activity;
        mPlugin = plugin;
        //TRY:fix ip
        computersToAdd.add("192.168.123.192");
        // Bind to the ComputerManager service
        mActivity.bindService(new Intent(mActivity,
                ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
    }
    private void finish() {
        if (mPlugin != null) {
            mPlugin.stopAddComputerManually();
        }
        else {
            Destroy();
        }
    }
}
