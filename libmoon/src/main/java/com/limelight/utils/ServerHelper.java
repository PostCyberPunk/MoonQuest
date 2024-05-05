package com.limelight.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.limelight.StreamPlugin;
import com.limelight.LimeLog;
import com.limelight.PluginManager;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;

public class ServerHelper {
    public static final String CONNECTION_TEST_SERVER = "android.conntest.moonlight-stream.org";

    public static ComputerDetails.AddressTuple getCurrentAddressFromComputer(ComputerDetails computer) throws IOException {
        if (computer.activeAddress == null) {
            throw new IOException("No active address for " + computer.name);
        }
        return computer.activeAddress;
    }

//    public static Intent createPcShortcutIntent(Activity parent, ComputerDetails computer) {
//        Intent i = new Intent(parent, ShortcutTrampoline.class);
//        i.putExtra(AppView.NAME_EXTRA, computer.name);
//        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
//        i.setAction(Intent.ACTION_DEFAULT);
//        return i;
//    }

//    public static Intent createAppShortcutIntent(Activity parent, ComputerDetails computer, NvApp app) {
//        Intent i = new Intent(parent, ShortcutTrampoline.class);
//        i.putExtra(AppView.NAME_EXTRA, computer.name);
//        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
//        i.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
//        i.putExtra(Game.EXTRA_APP_ID, ""+app.getAppId());
//        i.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
//        i.setAction(Intent.ACTION_DEFAULT);
//        return i;
//    }

    public static Intent createStartIntent(Activity parent, NvApp app, ComputerDetails computer,
                                           ComputerManagerService.ComputerManagerBinder managerBinder) {
        Intent intent = new Intent(parent, StreamPlugin.class);
        intent.putExtra(StreamPlugin.EXTRA_HOST, computer.activeAddress.address);
        intent.putExtra(StreamPlugin.EXTRA_PORT, computer.activeAddress.port);
        intent.putExtra(StreamPlugin.EXTRA_HTTPS_PORT, computer.httpsPort);
        intent.putExtra(StreamPlugin.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(StreamPlugin.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(StreamPlugin.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(StreamPlugin.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(StreamPlugin.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(StreamPlugin.EXTRA_PC_NAME, computer.name);
        try {
            if (computer.serverCert != null) {
                intent.putExtra(StreamPlugin.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public static void doStart(PluginManager parent, NvApp app, ComputerDetails computer,
                               ComputerManagerService.ComputerManagerBinder managerBinder) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            LimeLog.todo("Attempted to start app on offline computer");
//            Toast.makeText(parent, parent.getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        parent.ActivateStreamPlugin(createStartIntent(parent.GetActivity(), app, computer, managerBinder));
    }

    public static void doNetworkTest(final Activity parent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SpinnerDialog spinnerDialog = SpinnerDialog.displayDialog(parent,
                        parent.getResources().getString(R.string.nettest_title_waiting),
                        parent.getResources().getString(R.string.nettest_text_waiting),
                        false);

                int ret = MoonBridge.testClientConnectivity(CONNECTION_TEST_SERVER, 443, MoonBridge.ML_PORT_FLAG_ALL);
                spinnerDialog.dismiss();

                String dialogSummary;
                if (ret == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE) {
                    dialogSummary = parent.getResources().getString(R.string.nettest_text_inconclusive);
                } else if (ret == 0) {
                    dialogSummary = parent.getResources().getString(R.string.nettest_text_success);
                } else {
                    dialogSummary = parent.getResources().getString(R.string.nettest_text_failure);
                    dialogSummary += MoonBridge.stringifyPortFlags(ret, "\n");
                }
                LimeLog.todo("Network test result: " + dialogSummary);
            }
        }).start();
    }

    public static void doQuit(final Activity parent,
                              final ComputerDetails computer,
                              final NvApp app,
                              final ComputerManagerService.ComputerManagerBinder managerBinder,
                              final Runnable onComplete) {
        Toast.makeText(parent, parent.getResources().getString(R.string.applist_quit_app) + " " + app.getAppName() + "...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                            managerBinder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(parent));
                    if (httpConn.quitApp()) {
                        message = parent.getResources().getString(R.string.applist_quit_success) + " " + app.getAppName();
                    } else {
                        message = parent.getResources().getString(R.string.applist_quit_fail) + " " + app.getAppName();
                    }
                } catch (HostHttpResponseException e) {
                    if (e.getErrorCode() == 599) {
                        message = "This session wasn't started by this device," +
                                " so it cannot be quit. End streaming on the original " +
                                "device or the PC itself. (Error code: " + e.getErrorCode() + ")";
                    } else {
                        message = e.getMessage();
                    }
                } catch (UnknownHostException e) {
                    message = parent.getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = parent.getResources().getString(R.string.error_404);
                } catch (IOException | XmlPullParserException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                } finally {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                final String toastMessage = message;
                parent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
