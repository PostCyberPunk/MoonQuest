package com.limelight;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;

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
import com.limelight.types.UnityPluginObject;
import com.limelight.ui.StreamView;
import com.limelight.utils.ServerHelper;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;


public class GamePlugin extends UnityPluginObject implements SurfaceHolder.Callback,
        NvConnectionListener, PerfOverlayListener {
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

    public GamePlugin(PluginManager p, Activity a, Intent i) {
        super(p, a, i);
        onCreate();
        isInitialized = true;
    }

    @Override
    protected void onCreate() {

        // Inflate the content
//        setContentView(R.layout.activity_game);

        // Start the spinner

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(mActivity);
        tombstonePrefs = GamePlugin.this.getSharedPreferences("DecoderTombstone", 0);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                streamView = new StreamView(mActivity);
                mPluginManager.mLayout.addView(streamView, new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
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
        if (prefConfig.enableHdr) {
            // Start our HDR checklist
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Display display = getWindowManager().getDefaultDisplay();
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            willStreamHdr = true;
                            break;
                        }
                    }
                }

                if (!willStreamHdr) {
                    // Nope, no HDR for us :(
                    LimeLog.todo("Display does not support HDR10");
                }
            } else {
                LimeLog.todo("HDR requires Android 7.0 or later");
            }
        }

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

        // Set to the optimal mode for streaming
//        float displayRefreshRate = prepareDisplayForRendering();
        //TRY
        float displayRefreshRate = 60;
        prefConfig.width = mPluginManager.mTexWidth;
        prefConfig.height = mPluginManager.mTextHeight;
//        streamView.getHolder().setFixedSize(pref, 100);

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
                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
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

    private boolean isRefreshRateEqualMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                refreshRate <= prefConfig.fps + 3;
    }

    private boolean isRefreshRateGoodMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                Math.round(refreshRate) % prefConfig.fps <= 3;
    }

    private boolean shouldIgnoreInsetsForResolution(int width, int height) {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            for (Display.Mode candidate : display.getSupportedModes()) {
                // Ignore insets if this is an exact match for the display resolution
                if ((width == candidate.getPhysicalWidth() && height == candidate.getPhysicalHeight()) ||
                        (height == candidate.getPhysicalWidth() && width == candidate.getPhysicalHeight())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean mayReduceRefreshRate() {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate);
    }

    private float prepareDisplayForRendering() {
        Display display = getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams windowLayoutParams = getWindow().getAttributes();
        float displayRefreshRate;

        //TODO: Check if we can use the new API for this
        // On M, we can explicitly set the optimal display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode bestMode = display.getMode();
            boolean isNativeResolutionStream = PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height);
            boolean refreshRateIsGood = isRefreshRateGoodMatch(bestMode.getRefreshRate());
            boolean refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.getRefreshRate());

            LimeLog.info("Current display mode: " + bestMode.getPhysicalWidth() + "x" +
                    bestMode.getPhysicalHeight() + "x" + bestMode.getRefreshRate());

            for (Display.Mode candidate : display.getSupportedModes()) {
                boolean refreshRateReduced = candidate.getRefreshRate() < bestMode.getRefreshRate();
                boolean resolutionReduced = candidate.getPhysicalWidth() < bestMode.getPhysicalWidth() ||
                        candidate.getPhysicalHeight() < bestMode.getPhysicalHeight();
                boolean resolutionFitsStream = candidate.getPhysicalWidth() >= prefConfig.width &&
                        candidate.getPhysicalHeight() >= prefConfig.height;

                LimeLog.info("Examining display mode: " + candidate.getPhysicalWidth() + "x" +
                        candidate.getPhysicalHeight() + "x" + candidate.getRefreshRate());

                if (candidate.getPhysicalWidth() > 4096 && prefConfig.width <= 4096) {
                    // Avoid resolutions options above 4K to be safe
                    continue;
                }

                // On non-4K streams, we force the resolution to never change unless it's above
                // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
                // or it's a native resolution stream.
                if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                    if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                            display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                        continue;
                    }
                }

                // Make sure the resolution doesn't regress unless if it's over 60 FPS
                // where we may need to reduce resolution to achieve the desired refresh rate.
                if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                    continue;
                }

                if (mayReduceRefreshRate() && refreshRateIsEqual && !isRefreshRateEqualMatch(candidate.getRefreshRate())) {
                    // If we had an equal refresh rate and this one is not, skip it. In min latency
                    // mode, we want to always prefer the highest frame rate even though it may cause
                    // microstuttering.
                    continue;
                } else if (refreshRateIsGood) {
                    // We've already got a good match, so if this one isn't also good, it's not
                    // worth considering at all.
                    if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                        continue;
                    }

                    if (mayReduceRefreshRate()) {
                        // User asked for the lowest possible refresh rate, so don't raise it if we
                        // have a good match already
                        if (candidate.getRefreshRate() > bestMode.getRefreshRate()) {
                            continue;
                        }
                    } else {
                        // User asked for the highest possible refresh rate, so don't reduce it if we
                        // have a good match already
                        if (refreshRateReduced) {
                            continue;
                        }
                    }
                } else if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                    // We didn't have a good match and this match isn't good either, so just don't
                    // reduce the refresh rate.
                    if (refreshRateReduced) {
                        continue;
                    }
                } else {
                    // We didn't have a good match and this match is good. Prefer this refresh rate
                    // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                    // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                    // match the frame rate even if the active display mode is 90 Hz.
                }

                bestMode = candidate;
                refreshRateIsGood = isRefreshRateGoodMatch(candidate.getRefreshRate());
                refreshRateIsEqual = isRefreshRateEqualMatch(candidate.getRefreshRate());
            }

            LimeLog.info("Best display mode: " + bestMode.getPhysicalWidth() + "x" +
                    bestMode.getPhysicalHeight() + "x" + bestMode.getRefreshRate());

            // Only apply new window layout parameters if we've actually changed the display mode
            if (display.getMode().getModeId() != bestMode.getModeId()) {
                // If we only changed refresh rate and we're on an OS that supports Surface.setFrameRate()
                // use that instead of using preferredDisplayModeId to avoid the possibility of triggering
                // bugs that can cause the system to switch from 4K60 to 4K24 on Chromecast 4K.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        display.getMode().getPhysicalWidth() != bestMode.getPhysicalWidth() ||
                        display.getMode().getPhysicalHeight() != bestMode.getPhysicalHeight()) {
                    // Apply the display mode change
                    windowLayoutParams.preferredDisplayModeId = bestMode.getModeId();
                    getWindow().setAttributes(windowLayoutParams);
                } else {
                    LimeLog.info("Using setFrameRate() instead of preferredDisplayModeId due to matching resolution");
                }
            } else {
                LimeLog.info("Current display mode is already the best display mode");
            }

            displayRefreshRate = bestMode.getRefreshRate();
        }
        // On L, we can at least tell the OS that we want a refresh rate
        else {
            float bestRefreshRate = display.getRefreshRate();
            for (float candidate : display.getSupportedRefreshRates()) {
                LimeLog.info("Examining refresh rate: " + candidate);

                if (candidate > bestRefreshRate) {
                    // Ensure the frame rate stays around 60 Hz for <= 60 FPS streams
                    if (prefConfig.fps <= 60) {
                        if (candidate >= 63) {
                            continue;
                        }
                    }

                    bestRefreshRate = candidate;
                }
            }

            LimeLog.info("Selected refresh rate: " + bestRefreshRate);
            windowLayoutParams.preferredRefreshRate = bestRefreshRate;
            displayRefreshRate = bestRefreshRate;

            // Apply the refresh rate change
            getWindow().setAttributes(windowLayoutParams);
        }

        // Until Marshmallow, we can't ask for a 4K display mode, so we'll
        // need to hint the OS to provide one.
        boolean aspectRatioMatch = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // We'll calculate whether we need to scale by aspect ratio. If not, we'll use
            // setFixedSize so we can handle 4K properly. The only known devices that have
            // >= 4K screens have exactly 4K screens, so we'll be able to hit this good path
            // on these devices. On Marshmallow, we can start changing to 4K manually but no
            // 4K devices run 6.0 at the moment.
            Point screenSize = new Point(0, 0);
            display.getSize(screenSize);

            double screenAspectRatio = ((double) screenSize.y) / screenSize.x;
            double streamAspectRatio = ((double) prefConfig.height) / prefConfig.width;
            if (Math.abs(screenAspectRatio - streamAspectRatio) < 0.001) {
                LimeLog.info("Stream has compatible aspect ratio with output display");
                aspectRatioMatch = true;
            }
        }

        if (prefConfig.stretchVideo || aspectRatioMatch) {
            // Set the surface to the size of the video
            streamView.getHolder().setFixedSize(prefConfig.width, prefConfig.height);
        } else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio((double) prefConfig.width / (double) prefConfig.height);
        }

        // Set the desired refresh rate that will get passed into setFrameRate() later
        desiredRefreshRate = displayRefreshRate;

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate;
        } else {
            // Use the lower of the current refresh rate and the selected refresh rate.
            // The preferred refresh rate may not actually be applied (ex: Battery Saver mode).
            return Math.min(getWindowManager().getDefaultDisplay().getRefreshRate(), displayRefreshRate);
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            // TODO: Do we want to use WindowInsetsController here on R+ instead of
            // SYSTEM_UI_FLAG_IMMERSIVE_STICKY? They seem to do the same thing as of S...

            // In multi-window mode on N+, we need to drop our layout flags or we'll
            // be drawing underneath the system UI.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mActivity.isInMultiWindowMode()) {
                GamePlugin.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            } else {
                // Use immersive mode
                GamePlugin.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
    };

    @Override
    public void onDestroy() {
//        super.onDestroy();


        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onStop() {
        super.onStop();


        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();

//            if (prefConfig.enableLatencyToast) {
//                int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
//                int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
//                String message = null;
//                if (averageEndToEndLat > 0) {
//                    message = getResources().getString(R.string.conn_client_latency) + " " + averageEndToEndLat + " ms";
//                    if (averageDecoderLat > 0) {
//                        message += " (" + getResources().getString(R.string.conn_client_latency_hw) + " " + averageDecoderLat + " ms)";
//                    }
//                } else if (averageDecoderLat > 0) {
//                    message = getResources().getString(R.string.conn_hardware_latency) + " " + averageDecoderLat + " ms";
//                }
//
//                // Add the video codec to the post-stream toast
//                if (message != null) {
//                    message += " [";
//
//                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
//                        message += "H.264";
//                    } else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
//                        message += "HEVC";
//                    } else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
//                        message += "AV1";
//                    } else {
//                        message += "UNKNOWN";
//                    }
//
//                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
//                        message += " HDR";
//                    }
//
//                    message += "]";
//                }
//
//                if (message != null) {
//                    //TODO
//                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
//                }
//            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }

        finish();
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
                    LimeLog.severe(stage + " failed: " + errorCode);

                    // If video initialization failed and the surface is still valid, display extra information for the user
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        LimeLog.todo("Video decoder init failed: " + errorCode);
                    }

                    String dialogText = getResources().getString(R.string.conn_error_msg) + " " + stage + " (error " + errorCode + ")";

                    if (portFlags != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }

                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.nettest_text_blocked);
                    }

                    LimeLog.todo("COnnection failed:" + dialogText);
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
                            message = getResources().getString(R.string.nettest_text_blocked);
                        } else {
                            switch (errorCode) {
                                case MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC:
                                    message = getResources().getString(R.string.no_video_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_NO_VIDEO_FRAME:
                                    message = getResources().getString(R.string.no_frame_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION:
                                case MoonBridge.ML_ERROR_PROTECTED_CONTENT:
                                    message = getResources().getString(R.string.early_termination_error);
                                    break;

                                case MoonBridge.ML_ERROR_FRAME_CONVERSION:
                                    message = getResources().getString(R.string.frame_conversion_error);
                                    break;

                                default:
                                    String errorCodeString;
                                    // We'll assume large errors are hex values
                                    if (Math.abs(errorCode) > 1000) {
                                        errorCodeString = Integer.toHexString(errorCode);
                                    } else {
                                        errorCodeString = Integer.toString(errorCode);
                                    }
                                    message = getResources().getString(R.string.conn_terminated_msg) + "\n\n" +
                                            getResources().getString(R.string.error_code_prefix) + " " + errorCodeString;
                                    break;
                            }
                        }

                        if (portFlags != 0) {
                            message += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                    MoonBridge.stringifyPortFlags(portFlags, "\n");
                        }
                        LimeLog.todo("Connection terminated: " + message);
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
        computer.uuid = GamePlugin.this.getIntent().getStringExtra(EXTRA_PC_UUID);
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

            decoderRenderer.setRenderTarget(holder);
            conn.start(new AndroidAudioRenderer(mActivity, prefConfig.enableAudioFx),
                    decoderRenderer, GamePlugin.this);
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
        LimeLog.todo("Perfomance Debug: " + text);
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
//    public void onPointerCaptureChanged(boolean hasCapture) {
//        super.onPointerCaptureChanged(hasCapture);
//    }
}
