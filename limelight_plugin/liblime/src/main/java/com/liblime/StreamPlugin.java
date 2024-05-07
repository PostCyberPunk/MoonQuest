package com.liblime;


import android.app.Activity;
import android.app.appsearch.RemoveByDocumentIdRequest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.HardwareBuffer;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.opengl.GLES30;
import android.os.Build;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewGroup;

import com.limelight.LimeLog;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.liblime.types.UnityPluginObject;
import com.limelight.utils.ServerHelper;
import com.robot9.shared.SharedTexture;
import com.liblime.stream.StreamView;
import com.liblime.stream.StreamRenderer;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;


public class StreamPlugin extends UnityPluginObject implements SurfaceHolder.Callback,
        NvConnectionListener, PerfOverlayListener /*SurfaceTexture.OnFrameAvailableListener*/ {
    private PreferenceConfiguration prefConfig;
    private SharedPreferences tombstonePrefs;
    private NvConnection conn;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean surfaceCreated = false;
    private boolean attemptedConnection = false;
    private String pcName;
    private String appName;
    private NvApp app;
    private float desiredRefreshRate;
    private StreamView streamView;
    private MediaCodecDecoderRenderer decoderRenderer;
    private boolean reportedCrash;
    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;
    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_PORT = "Port";
    public static final String EXTRA_HTTPS_PORT = "HttpsPort";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_PC_UUID = "UUID";
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_APP_HDR = "HDR";
    public static final String EXTRA_SERVER_CERT = "ServerCert";

    public StreamPlugin(PluginManager p, Activity a, Intent i) {
        //super(p,a,i, PluginManager.PluginType.STREAM);
        super(p, a, i);
        mPluginType = PluginManager.PluginType.STREAM;
        onCreate();

        isInitialized = true;
        LimeLog.debug("StreamPlugin initialized");
    }

    private final int mTexWidth = 3440;
    private final int mTextHeight = 1440;

    //TODO extract this to methods for better management
    @Override
    protected void onCreate() {

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(mActivity);
        tombstonePrefs = StreamPlugin.this.getSharedPreferences("DecoderTombstone", 0);

        //Initialize the StreamView
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRenderer = new StreamRenderer();
                mRenderer.SetTextureResolution(mTexWidth, mTextHeight);
                streamView = new StreamView(mActivity);
                streamView.setForegroundGravity(Gravity.CENTER);
                streamView.setEGLContextClientVersion(3);
                streamView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
                streamView.setPreserveEGLContextOnPause(true);
                streamView.setRenderer(mRenderer);
//                streamView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                streamView.setBackgroundColor(0x00000000);

                mActivity.addContentView(streamView, new ViewGroup.LayoutParams(mTexWidth, mTextHeight));
            }
        });

        // Warn the user if they're on a metered connection
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr.isActiveNetworkMetered()) {
            LimeLog.todo("Metered connection detected");
        }

        // Make sure Wi-Fi is fully powered up
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock");
            highPerfWifiLock.setReferenceCounted(false);
            highPerfWifiLock.acquire();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock");
                lowLatencyWifiLock.setReferenceCounted(false);
                lowLatencyWifiLock.acquire();
            }
        } catch (SecurityException e) {
            // Some Samsung Galaxy S10+/S10e devices throw a SecurityException from
            // WifiLock.acquire() even though we have android.permission.WAKE_LOCK in our manifest.
            e.printStackTrace();
        }

        appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        pcName = getIntent().getStringExtra(EXTRA_PC_NAME);

        String host = getIntent().getStringExtra(EXTRA_HOST);
        int port = getIntent().getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        int httpsPort = getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0); // 0 is treated as unknown
        int appId = getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean appSupportsHdr = getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        app = new NvApp(appName != null ? appName : "app", appId, appSupportsHdr);

        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        // Initialize the MediaCodec helper before creating the decoder
        GlPreferences glPrefs = GlPreferences.readPreferences(mActivity);
        MediaCodecHelper.initialize(mActivity, glPrefs.glRenderer);

        // Check if the user has enabled HDR
        boolean willStreamHdr = false;

//         Check if the user has enabled performance stats overlay
//        if (prefConfig.enablePerfOverlay) {
//            performanceOverlayView.setVisibility(View.VISIBLE);
//        }

        decoderRenderer = new MediaCodecDecoderRenderer(
                mActivity,
                prefConfig,
                new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        // The MediaCodec instance is going down due to a crash
                        // let's tell the user something when they open the app again

                        // We must use commit because the app will crash when we return from this function
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1).commit();
                        reportedCrash = true;
                    }
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered(),
                willStreamHdr,
                glPrefs.glRenderer,
                this);

        // Don't stream HDR if the decoder can't support it
        if (willStreamHdr && !decoderRenderer.isHevcMain10Hdr10Supported() && !decoderRenderer.isAv1Main10Supported()) {
            willStreamHdr = false;
            LimeLog.todo("Decoder does not support HDR10 profile");
        }

        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC && !decoderRenderer.isHevcSupported()) {
            LimeLog.todo("No HEVC Decoder found");
        }

        // Display a message to the user if AV1 was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1 && !decoderRenderer.isAv1Supported()) {
            LimeLog.todo("No Av1 Decoder found");
        }

        // H.264 is always supported
        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
            if (willStreamHdr && decoderRenderer.isHevcMain10Hdr10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265_MAIN10;
            }
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
            if (willStreamHdr && decoderRenderer.isAv1Main10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN10;
            }
        }

        float displayRefreshRate = 60;
        LimeLog.info("Display refresh rate: " + displayRefreshRate);

        // If the user requested frame pacing using a capped FPS, we will need to change our
        // desired FPS setting here in accordance with the active display refresh rate.
        int roundedRefreshRate = Math.round(displayRefreshRate);
        int chosenFrameRate = prefConfig.fps;
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + 3) {
                    // Use frame drops when rendering above the screen frame rate
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Using drop mode for FPS > Hz");
                } else if (roundedRefreshRate <= 49) {
                    // Let's avoid clearly bogus refresh rates and fall back to legacy rendering
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Bogus refresh rate: " + roundedRefreshRate);
                } else {
                    chosenFrameRate = roundedRefreshRate - 1;
                    LimeLog.info("Adjusting FPS target for screen to " + chosenFrameRate);
                }
            }
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(chosenFrameRate)
                .setApp(app)
                .setBitrate(prefConfig.bitrate)
                .setEnableSops(prefConfig.enableSops)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO) // NvConnection will perform LAN and VPN detection
                .setSupportedVideoFormats(supportedVideoFormats)
                .setClientRefreshRateX100((int) (displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(false)
                .build();

        // Initialize the connection
        conn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, config,
                PlatformBinding.getCryptoProvider(mActivity), serverCert);

        if (!decoderRenderer.isAvcSupported()) {
            // If we can't find an AVC decoder, we can't proceed
            LimeLog.todo("This device or ROM doesn't support hardware accelerated H.264 playback.");
            return;
        }

        // The connection will be started when the surface gets created
        streamView.getHolder().addCallback(this);
    }

    private boolean mayReduceRefreshRate() {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate);
    }

    @Override
    public void onDestroy() {

        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }

        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();
            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }

        RevmoveReference();
    }

    @Override
    public void onPause() {
        mIsPaused = true;
    }

    @Override
    public void onResume() {
        mIsPaused = false;
    }

    @Override
    public void stageStarting(final String stage) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //TODO
//                if (spinner != null) {
//                    spinner.setMessage(getResources().getString(R.string.conn_starting) + " " + stage);
//                }
            }
        });
    }

    @Override
    public void stageComplete(String stage) {
    }

    private void stopConnection() {
        if (connecting || connected) {
            connecting = connected = false;

            // Update GameManager state to indicate we're no longer in game
//            UiHelper.notifyStreamEnded(this);

            // Stop may take a few hundred ms to do some network I/O to tell
            // the server we're going away and clean up. Let it run in a separate
            // thread to keep things smooth for the UI. Inside moonlight-common,
            // we prevent another thread from starting a connection before and
            // during the process of stopping this one.
            new Thread() {
                public void run() {
                    conn.stop();
                }
            }.start();
        }
    }

    @Override
    public void stageFailed(final String stage, final int portFlags, final int errorCode) {
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    UnityMessager.Error(stage + " failed: " + errorCode);

                    // If video initialization failed and the surface is still valid, display extra information for the user
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        LimeLog.todo("Video decoder init failed: " + errorCode);
                    }

                    String dialogText = "Connection error:" + " " + stage + " (error " + errorCode + ")";

                    if (portFlags != 0) {
                        dialogText += "\n\n" + "check port" + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }

                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                        dialogText += "\n\n Network blocked";
                    }

                    UnityMessager.Error("Connection failed:" + dialogText);
                }
            }
        });
    }

    @Override
    public void connectionTerminated(final int errorCode) {
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        final int portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode);
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe("Connection terminated: " + errorCode);
                    stopConnection();

                    // Display the error dialog if it was an unexpected termination.
                    // Otherwise, just finish the activity immediately.
                    if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                        String message;

                        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                            // If we got a blocked result, that supersedes any other error message
                            message = "Nested text bloacked";
                        } else {
                            switch (errorCode) {
                                case MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC:
                                    LimeLog.todo("no video received");
                                    message = "no_video_received_error";
                                    break;

                                case MoonBridge.ML_ERROR_NO_VIDEO_FRAME:
                                    message = "no_frame_received_error";
                                    break;

                                case MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION:
                                case MoonBridge.ML_ERROR_PROTECTED_CONTENT:
                                    message = "early_termination_error";
                                    break;

                                case MoonBridge.ML_ERROR_FRAME_CONVERSION:
                                    message = "frame_conversion_error";
                                    break;

                                default:
                                    String errorCodeString;
                                    // We'll assume large errors are hex values
                                    if (Math.abs(errorCode) > 1000) {
                                        errorCodeString = Integer.toHexString(errorCode);
                                    } else {
                                        errorCodeString = Integer.toString(errorCode);
                                    }
                                    message = "Nested text error code: " + errorCodeString;
                                    break;
                            }
                        }

                        if (portFlags != 0) {
                            message += "\n\n" + "checkport error" + "\n" +
                                    MoonBridge.stringifyPortFlags(portFlags, "\n");
                        }
                        UnityMessager.Error("Connection terminated: " + message);
                    } else {
                        finish();
                    }
                }
            }
        });
    }

    @Override
    public void connectionStatusUpdate(final int connectionStatus) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (prefConfig.disableWarnings) {
                    return;
                }

                if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                    if (prefConfig.bitrate > 5000) {
                        LimeLog.info("Slow connection");
//                        notificationOverlayView.setText(getResources().getString(R.string.slow_connection_msg));
                    } else {
//                        notificationOverlayView.setText(getResources().getString(R.string.poor_connection_msg));
                        LimeLog.info("Poor connection");
                    }
//                    requestedNotificationOverlayVisibility = View.VISIBLE;
                } else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                    LimeLog.info("Connection okay");
//                    requestedNotificationOverlayVisibility = View.GONE;
                }

            }
        });
    }

    @Override
    public void connectionStarted() {

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                connected = true;
                connecting = false;

                // Update GameManager state to indicate we're in game
//                UiHelper.notifyStreamConnected(Game.this);
            }
        });

        // Report this shortcut being used (off the main thread to prevent ANRs)
        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = StreamPlugin.this.getIntent().getStringExtra(EXTRA_PC_UUID);
//        ShortcutHelper shortcutHelper = new ShortcutHelper(this);
//        shortcutHelper.reportComputerShortcutUsed(computer);
//        if (appName != null) {
//            // This may be null if launched from the "Resume Session" PC context menu item
//            shortcutHelper.reportGameLaunched(computer, app);
//        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface changed before creation!");
        }

        if (!attemptedConnection) {
            attemptedConnection = true;

            // Update GameManager state to indicate we're "loading" while connecting
//            UiHelper.notifyStreamConnecting(Game.this);

            decoderRenderer.setRenderTarget(streamView);
            conn.start(new AndroidAudioRenderer(mActivity, prefConfig.enableAudioFx),
                    decoderRenderer, StreamPlugin.this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        float desiredFrameRate;

        surfaceCreated = true;

        // Android will pick the lowest matching refresh rate for a given frame rate value, so we want
        // to report the true FPS value if refresh rate reduction is enabled. We also report the true
        // FPS value if there's no suitable matching refresh rate. In that case, Android could try to
        // select a lower refresh rate that avoids uneven pull-down (ex: 30 Hz for a 60 FPS stream on
        // a display that maxes out at 50 Hz).
        if (mayReduceRefreshRate() || desiredRefreshRate < prefConfig.fps) {
            desiredFrameRate = prefConfig.fps;
        } else {
            // Otherwise, we will pretend that our frame rate matches the refresh rate we picked in
            // prepareDisplayForRendering(). This will usually be the highest refresh rate that our
            // frame rate evenly divides into, which ensures the lowest possible display latency.
            desiredFrameRate = desiredRefreshRate;
        }

        // Tell the OS about our frame rate to allow it to adapt the display refresh rate appropriately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // We want to change frame rate even if it's not seamless, since prepareDisplayForRendering()
            // will not set the display mode on S+ if it only differs by the refresh rate. It depends
            // on us to trigger the frame rate switch here.
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }

        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone
            decoderRenderer.prepareForStop();

            if (connected) {
                stopConnection();
            }
        }
    }

    @Override
    public void displayMessage(final String message) {
        LimeLog.todo("NVConn: " + message);
    }

    @Override
    public void displayTransientMessage(final String message) {
        LimeLog.todo("NVConn: " + message);
    }

    @Override
    public void onPerfUpdate(final String text) {
        //TODO:Get this from java to unity
        LimeLog.verbose("Perfomance Debug: " + text);
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        LimeLog.info("Display HDR mode: " + (enabled ? "enabled" : "disabled"));
        decoderRenderer.setHdrMode(enabled, hdrMetadata);
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        LimeLog.info(String.format((Locale) null, "Rumble on gamepad %d: %04x %04x", controllerNumber, lowFreqMotor, highFreqMotor));


    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        LimeLog.info(String.format((Locale) null, "Rumble on gamepad triggers %d: %04x %04x", controllerNumber, leftTrigger, rightTrigger));


    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {

    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {

    }

//    @Override
//    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        streamView.requstRender();
//    }

//    @Override
//    public void onPointerCaptureChanged(boolean hasCapture) {
//        super.onPointerCaptureChanged(hasCapture);
//    }

    //////////////////////Shared Texture//////////////////////
    public StreamRenderer mRenderer;
    private SharedTexture mSharedTexture;
    private HardwareBuffer mShareBuffer;
    private int[] mHWBFboTextureId;
    private int[] mHWBFboID;
    private boolean mIsPaused = false;

    public String GetResolution() {
        return mTexWidth + "x" + mTextHeight;
    }

    public int getTexturePtr() {
        return mHWBFboTextureId == null ? 0 : mHWBFboTextureId[0];
    }

    public void releaseSharedTexture() {
        if (mHWBFboTextureId != null) {
            GLES30.glDeleteTextures(mHWBFboTextureId.length, mHWBFboTextureId, 0);
            mHWBFboTextureId = null;
        }

        if (mHWBFboID != null) {
            GLES30.glDeleteTextures(mHWBFboID.length, mHWBFboID, 0);
            mHWBFboID = null;
        }

        if (mSharedTexture != null) {
            mSharedTexture.release();
            mSharedTexture = null;
        }
    }

    public void updateSharedTexture() {
        HardwareBuffer sb = mRenderer.getHardwareBuffer();
        if (sb == null || mShareBuffer == sb) {
            return;
        }

        releaseSharedTexture();

        mHWBFboTextureId = new int[1];
        mHWBFboID = new int[1];

        GLES30.glGenTextures(mHWBFboTextureId.length, mHWBFboTextureId, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mHWBFboTextureId[0]);

        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        SharedTexture sharedTexture = new SharedTexture(sb);

        sharedTexture.bindTexture(mHWBFboTextureId[0]);
        mSharedTexture = sharedTexture;
        mShareBuffer = sb;
    }
    //End of class
}
