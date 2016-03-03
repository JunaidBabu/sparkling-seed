package com.example.junaid.junktests;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.allseen.lsf.sdk.AllLightingItemListener;
import org.allseen.lsf.sdk.Color;
import org.allseen.lsf.sdk.Controller;
import org.allseen.lsf.sdk.Group;
import org.allseen.lsf.sdk.Lamp;
import org.allseen.lsf.sdk.LightingController;
import org.allseen.lsf.sdk.LightingDirector;
import org.allseen.lsf.sdk.LightingItemErrorEvent;
import org.allseen.lsf.sdk.LightingSystemQueue;
import org.allseen.lsf.sdk.MasterScene;
import org.allseen.lsf.sdk.Preset;
import org.allseen.lsf.sdk.PulseEffect;
import org.allseen.lsf.sdk.Scene;
import org.allseen.lsf.sdk.SceneElement;
import org.allseen.lsf.sdk.TrackingID;
import org.allseen.lsf.sdk.TransitionEffect;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends Activity implements AllLightingItemListener {
    private static final String TAG = "MainFragment";

    Button b;
    EditText ed;
    Lamp publamp;
    Handler handler;
    public volatile Queue<Runnable> runInForeground;

    private LightingController controllerService;
    private boolean controllerServiceEnabled;
    private volatile boolean controllerServiceStarted;

    private static final String CONTROLLER_ENABLED = "CONTROLLER_ENABLED_KEY";
    private AlertDialog wifiDisconnectAlertDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        b=(Button)findViewById(R.id.button);
        ed = (EditText)findViewById(R.id.editText);
        b.setVisibility(View.GONE);
        handler = new Handler(Looper.getMainLooper());
        runInForeground = new LinkedList<Runnable>();
        LightingDirector.get().addListener(this);
        LightingDirector.get().start(
                "SampleApp",
                new LightingSystemQueue() {
                    @Override
                    public void post(Runnable r) {

                        handler.post(r);
                    }

                    @Override
                    public void postDelayed(Runnable r, int delay) {
                        handler.postDelayed(r, delay);
                    }

                    @Override
                    public void stop() {
                        // Currently nothing to do
                    }
                });

        initWifiMonitoring();


        Toast.makeText(getActivity(), "yo", Toast.LENGTH_SHORT).show();
        controllerServiceEnabled = getActivity().getSharedPreferences("PREFS_READ", Context.MODE_PRIVATE).getBoolean(CONTROLLER_ENABLED, true);
        controllerService = LightingController.get();
        controllerService.init(new SampleAppControllerConfiguration(
                getActivity().getApplicationContext().getFileStreamPath("").getAbsolutePath(), getActivity().getApplicationContext()));
    }

    public void togglelamp(View v){
        publamp.togglePower();

    }

    public void setHue(View v){
        //publamp.setHue(Integer.valueOf(ed.getText().toString()));
        publamp.setColor(Color.red());
        publamp.setBrightness(5);
    }
    private void initWifiMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //initWifiMonitoringApi14();
            Toast.makeText(getActivity(), "Min lollipop required", Toast.LENGTH_SHORT).show();

        } else {
            initWifiMonitoringApi21();
        }
    }

    protected boolean isWifiConnected() {
        NetworkInfo wifiNetworkInfo = ((ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE)).getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        // determine if wifi AP mode is on
        boolean isWifiApEnabled = false;
        WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
        // need reflection because wifi ap is not in the public API
        try {
            Method isWifiApEnabledMethod = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            isWifiApEnabled = (Boolean) isWifiApEnabledMethod.invoke(wifiManager);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Log.d(SampleAppActivity.TAG, "Connectivity state " + wifiNetworkInfo.getState().name() + " - connected:" + wifiNetworkInfo.isConnected() + " hotspot:" + isWifiApEnabled);

        return wifiNetworkInfo.isConnected() || isWifiApEnabled;
    }

    @SuppressLint("NewApi")
    private void initWifiMonitoringApi21() {
        // Set the initial wifi state
        wifiConnectionStateUpdate(isWifiConnected());

        // Listen for wifi state changes
        NetworkRequest networkRequest = (new NetworkRequest.Builder()).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                wifiConnectionStateUpdate(true);
            }

            @Override
            public void onLost(Network network) {
                wifiConnectionStateUpdate(false);
            }
        });
    }

    public boolean isControllerServiceEnabled() {
        return controllerServiceEnabled;
    }
    private void setControllerServiceStarted(final boolean startControllerService) {
        if (controllerService != null) {
            if (startControllerService) {
                if (!controllerServiceStarted) {
                    controllerServiceStarted = true;
                    controllerService.start();
                }
            } else {
                controllerService.stop();
                controllerServiceStarted = false;
            }
        }
    }

    public void setControllerServiceEnabled(final boolean enableControllerService) {

        if (enableControllerService != controllerServiceStarted) {
            SharedPreferences prefs = getActivity().getSharedPreferences("PREFS_READ", Context.MODE_PRIVATE);
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(CONTROLLER_ENABLED, enableControllerService);
            e.commit();

            setControllerServiceStarted(enableControllerService);
        }

        controllerServiceEnabled = enableControllerService;
    }

    public void postInForeground(final Runnable r) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean isForeground=true;
                if (isForeground) {
                    // Log.d(SampleAppActivity.TAG, "Foreground runnable running now");
                    handler.post(r);
                } else {
                    // Log.d(SampleAppActivity.TAG, "Foreground runnable running later");
                    runInForeground.add(r);
                }
            }
        });
    }
    private void wifiConnectionStateUpdate(boolean connected) {


        postUpdateControllerDisplay();

        if (connected) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    //                   Log.d(SampleAppActivity.TAG, "wifi connected");

                    postInForeground(new Runnable() {
                        @Override
                        public void run() {
                            //                         Log.d(SampleAppActivity.TAG_TRACE, "Starting system");

                            LightingDirector.get().setNetworkConnectionStatus(true);

                            if (isControllerServiceEnabled()) {
                                //                           Log.d(SampleAppActivity.TAG_TRACE, "Starting bundled controller service");
                                setControllerServiceStarted(true);
                            }

                            if (wifiDisconnectAlertDialog != null) {
                                wifiDisconnectAlertDialog.dismiss();
                                wifiDisconnectAlertDialog = null;
                            }
                        }
                    });
                }
            });
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    //             Log.d(SampleAppActivity.TAG, "wifi disconnected");

                    postInForeground(new Runnable() {
                        @Override
                        public void run() {
                            if (wifiDisconnectAlertDialog == null) {
                                //                       Log.d(SampleAppActivity.TAG, "Stopping system");

                                LightingDirector.get().setNetworkConnectionStatus(false);

                                setControllerServiceStarted(false);

//                                View view = activity.getLayoutInflater().inflate(R.layout.view_loading, null);
//                                ((TextView) view.findViewById(R.id.loadingText1)).setText(activity.getText(R.string.no_wifi_message));
//                                ((TextView) view.findViewById(R.id.loadingText2)).setText(activity.getText(R.string.searching_wifi));
//
//                                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
//                                alertDialogBuilder.setView(view);
//                                alertDialogBuilder.setTitle(R.string.no_wifi);
//                                alertDialogBuilder.setCancelable(false);
//                                wifiDisconnectAlertDialog = alertDialogBuilder.create();
//                                wifiDisconnectAlertDialog.show();
                            }
                        }
                    });
                }
            });
        }
    }

    private void postUpdateControllerDisplay() {

    }




    @Override
    public void onLeaderChange(Controller controller) {
        Toast.makeText(getActivity(), "on leaderchange", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onControllerErrors(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on controller errors", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onGroupInitialized(TrackingID trackingID, Group group) {
        Toast.makeText(getActivity(), "on group initialized", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onGroupChanged(Group group) {
        Toast.makeText(getActivity(), "on group changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onGroupRemoved(Group group) {
        Toast.makeText(getActivity(), "on group removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGroupError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on group error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onLampInitialized(Lamp lamp) {
        Toast.makeText(getActivity(), "on lamp initialized", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onLampChanged(Lamp lamp) {
        Toast.makeText(getActivity(), "on lamp changed"+lamp.getName(), Toast.LENGTH_SHORT).show();
        Log.e("Lamp", lamp.toString());
        try{
            //lamp.turnOn();

            publamp=lamp;
            b.setVisibility(View.VISIBLE);
        }catch (Exception e){

            e.printStackTrace();
        }
    }

    @Override
    public void onLampRemoved(Lamp lamp) {
        Toast.makeText(getActivity(), "on lamp removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onLampError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on lamp error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onMasterSceneInitialized(TrackingID trackingID, MasterScene masterScene) {
        Toast.makeText(getActivity(), "on master scene initialized", Toast.LENGTH_SHORT).show();

    }
    @Override
    public void onMasterSceneChanged(MasterScene masterScene) {
        Toast.makeText(getActivity(), "on master scene changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onMasterSceneRemoved(MasterScene masterScene) {

        Toast.makeText(getActivity(), "on master scene removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMasterSceneError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on master scene error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPresetInitialized(TrackingID trackingID, Preset preset) {
        Toast.makeText(getActivity(), "on preinitialized", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPresetChanged(Preset preset) {
        Toast.makeText(getActivity(), "on preset changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPresetRemoved(Preset preset) {
        Toast.makeText(getActivity(), "on preset removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPresetError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on preset error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPulseEffectInitialized(TrackingID trackingID, PulseEffect pulseEffect) {
        Toast.makeText(getActivity(), "onpulse effect init", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPulseEffectChanged(PulseEffect pulseEffect) {
        Toast.makeText(getActivity(), "onpulseeffect changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPulseEffectRemoved(PulseEffect pulseEffect) {
        Toast.makeText(getActivity(), "onpulse effect removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPulseEffectError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "onpulse effect error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneElementInitialized(TrackingID trackingID, SceneElement sceneElement) {
        Toast.makeText(getActivity(), "onscene element init", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneElementChanged(SceneElement sceneElement) {
        Toast.makeText(getActivity(), "onscene element changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneElementRemoved(SceneElement sceneElement) {
        Toast.makeText(getActivity(), "onscene element removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneElementError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "onsene element error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneInitialized(TrackingID trackingID, Scene scene) {
        Toast.makeText(getActivity(), "on scene initialized", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneChanged(Scene scene) {
        Toast.makeText(getActivity(), "on scene changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneRemoved(Scene scene) {
        Toast.makeText(getActivity(), "on scener removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onSceneError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "on scene error", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onTransitionEffectInitialized(TrackingID trackingID, TransitionEffect transitionEffect) {
        Toast.makeText(getActivity(), "trans effect init", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onTransitionEffectChanged(TransitionEffect transitionEffect) {
        Toast.makeText(getActivity(), "trans changed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onTransitionEffectRemoved(TransitionEffect transitionEffect) {
        Toast.makeText(getActivity(), "transition removed", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onTransitionEffectError(LightingItemErrorEvent lightingItemErrorEvent) {
        Toast.makeText(getActivity(), "transition effect error", Toast.LENGTH_SHORT).show();

    }

    public Context getActivity() {
        return this;
    }






}
