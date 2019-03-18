package sdktester.client.zixi.com.zixiclientsdktester;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.zixi.playersdk.ZixiError;
import com.zixi.playersdk.ZixiLogEvents;
import com.zixi.playersdk.ZixiPlayer;
import com.zixi.playersdk.ZixiPlayerEvents;
import com.zixi.playersdk.ZixiPlayerImplV2;
import com.zixi.playersdk.ZixiPlayerSdk;
import com.zixi.playersdk.ZixiPlayerSessionStatistics;
import com.zixi.playersdk.core.ZixiClient;
import com.zixi.playersdk.util.C;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_SHARED_PREF_URL_HISTORY = "SHARED_KEY_URL_HISTORY";
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_DISCONNECTING = 4;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_RESUMED = 3;

    private final static int LATENCIES_MS[] = {100,200,300,500,1000,1500,2000,3000,4000,6000,8000};
    private final static String LATENCIES_STR[];
    static {
        LATENCIES_STR = new String[LATENCIES_MS.length];
        int i = 0;
        for (int latency : LATENCIES_MS) {
            LATENCIES_STR[i] = String.format("%d ms", latency);
            i++;
        }
    }
    // #include <zixi_definitions.h>...
    private static final int MAX_ADAPTIVE_STREAMS_COUNT = 16;

    private class StatsTickHandler extends Handler {
        public  StatsTickHandler(Looper l) {
            super(l);
        }

        public void tick() {
            sendEmptyMessageDelayed(0,166);
        }

        public void stop() {
            removeMessages(0);
        }
        @Override
        public void handleMessage(Message msg) {
            MainActivity.this.handleTick();
        }
    }

    private void handleTick() {
        if (mPlayer != null) {
            ZixiPlayerSessionStatistics stats = mPlayer.getSessionInfo();

            long l = mPlayer.getCurrentPTS90Khz();
            if (l != ZixiPlayerImplV2.INVALID_PTS) {
                mPtsDisplay.setText(String.format("Current Pts %d", l));
            } else {
                mPtsDisplay.setText("Invalid Pts");
            }

            mStatsTicker.tick();
        }
    }

    private final static String stateToStr(int state) {
        switch (state) {
            case STATE_DISCONNECTED:
                return "Disconnected";
            case STATE_DISCONNECTING:
                return "Disconnecting";
            case STATE_PAUSED:
                return "Paused";
            case STATE_RESUMED:
                return "Resume";
            case STATE_CONNECTING:
                return "Connecting";
        }

        return "Unknown";
    }
    private static final int USER_IDLE_HIDE_UI_WHEN_PLAYING_TIMEOUT_MS = 5000;
    private static final String TAG = "ZixiClientSdkTester";

    private Button mConnectButton;
    private AspectRatioFrameLayout      mStreamAspectRatioFrame;
    private SurfaceView mStreamOutput;
    private AutoCompleteTextView mUrlInput;
    private TextView mUrlPrefix;
    private LinearLayout mUiHolder;
    private TextView                    mBitrateIndicator;
    private LinearLayout                mBitratesHolder;
    private ZixiBitrateAdapter          mBitratesAdapter;
    private ListView                    mBitratesList;
    private TextView                    mBitrateModeIndicator;
    private TextView                    mPtsDisplay;
    private LinearLayout                mLatencySelectorHolder;
    private Button                      mBitrateUpButton;
    private Button                      mBitrateDownButton;
    private Button                      mBitrateAutoMode;
    private Spinner                     mLatencySelector;
    private TextView                    mVersionText;
    private FrameLayout                 mUiTopBlankFrame;
    private int                         mPresentedBitrates;
    private boolean                     mBitrateOnAutoMode;
    private int                         mSelectedBitrateId;
    private boolean                     mDropUrlSelection;
    private boolean                     mActivityResumed;

    private StatsTickHandler            mStatsTicker;
    private int                         mPlayerState;
    private long                        mLastUserInput = -1;

    private List<String> mUrlHistory;
    private ZixiPlayer mPlayer;
    private ZixiPlayerEvents mPlayerEvents = new ZixiPlayerEvents() {
        @Override
        public void onConnecting() {

        }

        @Override
        public void onConnected(String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleUiState(STATE_PAUSED);
                }
            });
        }

        @Override
        public void onSourceConnected() {

        }

        @Override
        public void onSourceDisconnected() {

        }

        @Override
        public void onReconnecting() {

        }

        @Override
        public void onReconnected() {

        }

        @Override
        public void onFailedToConnect(final String url, final int why) {
            runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  handleUiState(STATE_DISCONNECTED);
                                  Snackbar.make(getWindow().getDecorView(), "Failed to connect to " + url + " [" + ZixiError.toString(why) + "]", Snackbar.LENGTH_LONG).show();
                              }
                          }

            );
        }

        @Override
        public void onPlaybackStarted() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleUiState(STATE_RESUMED);
                }
            });

        }

        @Override
        public void onDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleUiState(STATE_DISCONNECTED);
                }
            });
        }

        @Override
        public void onStreamBitrateChanged(final int bitrate, final int [] bitrates, final int length) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleStreamBitrateChanged(bitrate,bitrates,length);
                }
            });
        }

        @Override
        public void onStreamAspectRatioChanged(final float ar) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleStreamAspectRatioChanged(ar);
                }
            });
        }

        @Override
        public void onVideoFormatChanged(String mime, int height, int width) {

        }

        @Override
        public void onAudioFormatChanged(String mime, int channelCount, int sampleRate) {

        }

        @Override
        public void onVideoDecoderFailedToStart(final int width, final int height, final String codec) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Snackbar.make(getWindow().getDecorView(), "Failed to initialize video decoder [" + width + "x"+ height + "] " + codec, Snackbar.LENGTH_LONG).show();
                    // disconnect
                    handleConnectPressed();
                }
            });
        }

        @Override
        public void onAudioDecoderFailedToStart(int channelConfig, int sampleRate, String codec) {

        }
    };

    private void handleStreamBitrateChanged(int bitrate, int [] bitrates, int bitrates_count) {
        maybeUpdateBitrate(bitrate,bitrates, bitrates_count);
    }

    private boolean connected() {
        return mPlayer != null && mPlayer.connected();
    }
    private void loadHistory() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = MainActivity.this.getPreferences(MODE_PRIVATE);
                if (sp.contains(KEY_SHARED_PREF_URL_HISTORY)) {
                    Set<String> urls = sp.getStringSet(KEY_SHARED_PREF_URL_HISTORY,null);
                    if (urls != null) {
                        ArrayList<String > old = new ArrayList<String>();
                        for (String url : urls) {
                            old.add(url);
                        }
                        mUrlHistory = old;
                    }
                }
                maybeUpdateHistoryUi();
            }
        }).start();
    }

    private void maybeUpdateHistoryUi() {
        if (mUrlHistory != null  && mUrlHistory.size() > 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String [] urlHistory = new String [ mUrlHistory.size()];
                    mUrlHistory.toArray(urlHistory);
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            MainActivity.this,android.R.layout.simple_list_item_1, urlHistory);
                    mUrlInput.setAdapter(adapter);

                    if (mActivityResumed && !mDropUrlSelection) {
                        mUrlInput.showDropDown();
                    }
                    mDropUrlSelection = true;
                }
            });
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        initializeViews();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViews();
    }

    private void initializeViews() {
        setContentView(R.layout.activity_zixi_stream_viewer);
        mUrlHistory = null;
        mDropUrlSelection = false;
        mActivityResumed = false;
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    C.hideSystemUi(getWindow().getDecorView());
                } else {
                    // TODO: The system bars are NOT visible. Make any desired
                    // adjustments to your UI, such as hiding the action bar or
                    // other navigational controls.
                }
            }
        });

        mBitrateOnAutoMode = true;
        C.hideSystemUi(getWindow().getDecorView());
        mConnectButton = (Button)findViewById(R.id.viewer_connect);
        mUrlInput = (AutoCompleteTextView)findViewById(R.id.viewer_url);
        mStreamOutput = (SurfaceView)findViewById(R.id.viewer_stream_out);
        mBitrateIndicator = (TextView)findViewById(R.id.zixi_bitrates_not_recovered);
        mBitratesHolder = (LinearLayout)findViewById(R.id.bitrates_holder);
        mBitrateAutoMode = (Button)findViewById(R.id.zixi_bitrate_go_auto);
        mBitrateModeIndicator= (TextView)findViewById(R.id.zixi_bitrates_mode_text);
        mStreamAspectRatioFrame = (AspectRatioFrameLayout)findViewById(R.id.viewer_stream_out_aspect_ratio);
        mBitrateAutoMode.setEnabled( false);
        mBitrateAutoMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleBitrateAuto();
            }
        });
        mBitrateDownButton = (Button)findViewById(R.id.zixi_bitrate_go_down);
        mBitrateDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleBitrateDown();
            }
        });
        mBitrateUpButton = (Button)findViewById(R.id.zixi_bitrate_go_up);
        mBitrateUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleBitrateUp();
            }
        });
        mBitratesList = (ListView)findViewById(R.id.zixi_bitrate_bitrates_list);
        mLatencySelectorHolder = (LinearLayout)findViewById(R.id.viewer_latency_selector_holder);
        mLatencySelector = (Spinner)findViewById(R.id.viewer_latency_selector);
        mUiTopBlankFrame = (FrameLayout)findViewById(R.id.viewer_ui_holder_blank_top_frame);
        mPtsDisplay = (TextView)findViewById(R.id.viewer_ui_stats_text);
        mLatencySelector.setAdapter( new ArrayAdapter<String>(this, R.layout.latency_item, LATENCIES_STR));
        mLatencySelector.setSelection(4);
        mVersionText =(TextView)findViewById(R.id.viewer_zixi_version);
        mVersionText.setText("Zixi Version: " + ZixiClient.getVersion());
        mBitratesAdapter = new ZixiBitrateAdapter(this);
        mBitratesList.setAdapter(mBitratesAdapter);


        mStreamOutput.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                if (mPlayer != null ) {
                    mPlayer.setSurface(surfaceHolder);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                if (mPlayer != null ) {
                    mPlayer.setSurface(surfaceHolder);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if (mPlayer != null) {
                    mPlayer.surfaceTeardown();
                }
            }
        });
        mUrlPrefix = (TextView)findViewById(R.id.viewer_url_prefix);
        mUiHolder= (LinearLayout)findViewById(R.id.viewer_ui_holder);
        mUrlInput.setThreshold(0);
        mPlayerState = -1;

        handleUiState(STATE_DISCONNECTED);
        loadHistory();
    }

    public void onButtonClicked(View view) {
        if (view == mConnectButton) {
            handleConnectPressed();
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mPlayerState == STATE_RESUMED ||
                mPlayerState == STATE_PAUSED) {
            if (resetUserInputTimer()) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlayerState == STATE_RESUMED ||
                mPlayerState == STATE_PAUSED) {
            if (resetUserInputTimer()) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event) ;
    }

    private boolean resetUserInputTimer() {
        boolean ret = false;
        mLastUserInput = SystemClock.elapsedRealtime();
        if (mUiHolder.getVisibility() != View.VISIBLE) {
            Log.e(TAG,"resetUserInputTimer -> TO VISIBLE");
            mUiHolder.setVisibility(View.VISIBLE);
            mUiHolder.requestFocus();
            mUiHolder.requestLayout();
            ret = true;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        if ((mLastUserInput != -1) && (SystemClock.elapsedRealtime() - mLastUserInput) >= USER_IDLE_HIDE_UI_WHEN_PLAYING_TIMEOUT_MS) {
                            mUiHolder.setVisibility(View.INVISIBLE);
                        }
                    }
                }, USER_IDLE_HIDE_UI_WHEN_PLAYING_TIMEOUT_MS);
            }
        });
        return ret;
    }

    private void handleConnectPressed() {
        String decryptionKey = null;
        if (!connected()) {
            mPresentedBitrates = 0;
            String newUrl = mUrlInput.getText().toString();
            if (mUrlHistory == null) {
                mUrlHistory = new ArrayList<>();
            }
            String minUrl = new String(newUrl);
            if (minUrl.toLowerCase().startsWith("zixi://")) {
                minUrl = minUrl.substring(7,minUrl.length());
            }

            if (!mUrlHistory.contains(minUrl)) {
                mUrlHistory.add(minUrl);
                addAndStore(minUrl);
                maybeUpdateHistoryUi();
            }

            initializePlayer();

            mPlayer.connect("zixi://" + minUrl,"username","", decryptionKey, LATENCIES_MS[(int)mLatencySelector.getSelectedItemId()]);
            handleUiState(STATE_CONNECTING);
        } else {
            handleUiState(STATE_DISCONNECTING);
            mPlayer.disconnect();
            mPlayer.release();
            mPlayer = null;
        }

    }

    private void initializePlayer() {
        Log.e(TAG, "initializePlayer >>");
        if (mPlayer == null) {

            mPlayer = ZixiPlayerSdk.newPlayer(mPlayerEvents, new Handler());
            mPlayer.setAutoReconnect(true);
            mPlayer.setLogCallback(new ZixiLogEvents() {
                @Override
                public void logMessage(int level, String who, String what) {
                    if (level > Log.WARN)
                        Log.println(level,who,what);
                }
            });
            mPlayer.setSurface(mStreamOutput.getHolder());


            // New api - for setting display mode
            //[Default] mPlayer.setDisplayMode(ZixiPlayer.DISPLAY_MODE_FIT);
            //          mPlayer.setDisplayMode(ZixiPlayer.DISPLAY_MODE_CROP);

        }
        Log.e(TAG, "initializePlayer <<");
    }

    private void terminatePlayer() {
        Log.e(TAG, "terminatePlayer >>");
        if (mPlayer != null) {
            mPlayer.disconnect();
            mPlayer.release();
            mPlayer = null;
        }
        Log.e(TAG, "terminatePlayer <<");
    }

    private void maybeUpdateBitrate(int bitrate, int [] bitrates, int count){
        if (bitrate == -1 || bitrates == null || count == -1) {
            if (mPlayer != null && mPlayer.connected()) {
                bitrate = mPlayer.currentBitrate();
                bitrates = mPlayer.availableBitrates();
                if (bitrates != null) {
                    count = bitrates.length;
                } else {
                    count = 0;
                }
            }
        }

        if (bitrate != -1 && count > 0 && mPlayerState == STATE_RESUMED) {
            float f_bitrate_kbps = (float) bitrate / 1000;
            mBitrateIndicator.setText(String.format("Bitrate : %.02f kbps", f_bitrate_kbps));
            if (mBitratesHolder.getVisibility() != View.VISIBLE)
                mBitratesHolder.setVisibility(View.VISIBLE);
            mBitratesAdapter.clear();

            String values[] = new String [count];
            int active_id = -1;
            for (int i = 0; i < count; i++) {
                values[i] = String.format("%d kbps" , (int)bitrates[i] / 1000);
                if (bitrates[i] == bitrate) {
                    active_id = i;
                }
            }
            mBitratesAdapter.addAll(values);
            mSelectedBitrateId = active_id;
            mBitratesAdapter.setActiveId(active_id);
            if (mPresentedBitrates <= 0) {
                mPresentedBitrates = count;
                mBitratesList.requestLayout();
            }
        }
    }

    private void handleStreamAspectRatioChanged(float ar) {
        if (mStreamAspectRatioFrame != null) {
            mStreamAspectRatioFrame.setAspectRatio(ar);
        }
    }
    private void handleUiState(int state){
        if (state == mPlayerState && state != STATE_RESUMED) {
            return;
        }

        switch (state) {
            case STATE_DISCONNECTED:
                mUrlInput.setVisibility(View.VISIBLE);
                mUrlPrefix.setVisibility(View.VISIBLE);
                mBitratesHolder.setVisibility(View.INVISIBLE);
                mConnectButton.setText("Connect");
                mConnectButton.setEnabled(true);
                mBitrateIndicator.setVisibility(View.INVISIBLE);
                mStreamOutput.setVisibility(View.INVISIBLE);
                mLatencySelectorHolder.setVisibility(View.VISIBLE);
                break;
            case STATE_CONNECTING:
                mLatencySelectorHolder.setVisibility(View.INVISIBLE);
                mStreamOutput.setVisibility(View.VISIBLE);
                mUrlInput.setVisibility(View.INVISIBLE);
                mUrlPrefix.setVisibility(View.INVISIBLE);
                mConnectButton.setEnabled(false);
                mConnectButton.setText("Connecting...");
                mBitratesHolder.setVisibility(View.INVISIBLE);
                maybeCloseSoftKeyboard();
                break;
            case STATE_PAUSED:
                mUiHolder.setVisibility(View.VISIBLE);
                mBitratesHolder.setVisibility(View.INVISIBLE);
                mConnectButton.setEnabled(true);
                mLatencySelectorHolder.setVisibility(View.INVISIBLE);
                mConnectButton.setText("Disconnect");
                // maybeUpdateBitrate(-1,null,-1);
                break;
            case STATE_RESUMED:
                mLatencySelectorHolder.setVisibility(View.INVISIBLE);
                if (mStreamOutput.getVisibility() != View.VISIBLE) {
                    mStreamOutput.setVisibility(View.VISIBLE);

                }

                mPtsDisplay.setVisibility(View.VISIBLE);
                if (C.SDK_INT >=17) {
                    mStreamOutput.requestLayout();
                }
                mBitratesHolder.setVisibility(View.VISIBLE);

                mStreamOutput.requestLayout();
                mUiHolder.setVisibility(View.INVISIBLE);
                mConnectButton.setText("Disconnect");
                mConnectButton.setVisibility(View.VISIBLE);
                mConnectButton.setEnabled(true);
                if (mStatsTicker != null) {
                    mStatsTicker.stop();
                }
                mStatsTicker = new StatsTickHandler(getMainLooper());
                mStatsTicker.tick();
                break;
            case STATE_DISCONNECTING:
                if (mStatsTicker != null) {
                    mStatsTicker.stop();
                    mStatsTicker = null;
                }
                mPtsDisplay.setVisibility(View.GONE);
                mBitratesHolder.setVisibility(View.INVISIBLE);
                mLatencySelectorHolder.setVisibility(View.INVISIBLE);
                mLastUserInput = -1;
                mUiHolder.setVisibility(View.VISIBLE);
                mConnectButton.setEnabled(false);
                mConnectButton.setText("Disconnecting...");
                break;
        }
        Log.i(TAG,stateToStr(mPlayerState) + " -> " + stateToStr(state));
        mPlayerState = state;
    }

    private void maybeCloseSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (getWindow() != null) {
                View v = getWindow().getDecorView();
                IBinder token = v.getWindowToken();
                if (token != null) {
                    inputMethodManager.hideSoftInputFromWindow(token, 0);
                }
            }
        }
    }

    private void addAndStore(final String newUrl) {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = MainActivity.this.getPreferences(MODE_PRIVATE);
                Set<String> old = null;
                if (sp.contains(KEY_SHARED_PREF_URL_HISTORY)) {
                    old = sp.getStringSet(KEY_SHARED_PREF_URL_HISTORY,null);
                }

                if (old == null) {
                    old = new HashSet<String>();
                }
                old.add(newUrl);
                SharedPreferences.Editor editor = sp.edit();
                editor.remove(KEY_SHARED_PREF_URL_HISTORY);
                if (!editor.commit()) {
                    Log.e(TAG,"Failed to commit search history");
                }
                sp = MainActivity.this.getPreferences(MODE_PRIVATE);
                editor = sp.edit();
                editor.putStringSet(KEY_SHARED_PREF_URL_HISTORY, old);
                if (!editor.commit()) {
                    Log.e(TAG,"Failed to commit search history");
                }
            }
        })).start();
    }

    private void handleBitrateAuto(){
        mBitrateAutoMode.setEnabled( false);
        mBitrateOnAutoMode = true;
        mBitrateModeIndicator.setText("Auto");
        mPlayer.setCurrentBitrate(ZixiPlayer.INVALID_BITRATE);
    }

    private void handleBitrateUp(){
        if (mSelectedBitrateId > 0 ) {
            if (mBitrateOnAutoMode) {
                mBitrateModeIndicator.setText("Manual");
                mBitrateOnAutoMode = false;
                mBitrateAutoMode.setEnabled(true);
            }
            mSelectedBitrateId--;
            mPlayer.setCurrentBitrate(mSelectedBitrateId);
        }

    }

    private void handleBitrateDown(){
        if (mSelectedBitrateId < mPresentedBitrates) {
            if (mBitrateOnAutoMode) {
                mBitrateOnAutoMode = false;
                mBitrateModeIndicator.setText("Manual");
                mBitrateAutoMode.setEnabled(true);
            }
            mSelectedBitrateId++;
            mPlayer.setCurrentBitrate(mSelectedBitrateId);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (C.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityResumed = true;
        if (C.SDK_INT <= 23 || mPlayer == null) {
            initializePlayer();
        }

        if (mDropUrlSelection) {
            if (mUrlInput.getVisibility() == View.VISIBLE) {
                mUrlInput.showDropDown();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (C.SDK_INT <= 23) {
            terminatePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (C.SDK_INT > 23){
            terminatePlayer();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation ==Configuration.ORIENTATION_LANDSCAPE ) {
            mUiTopBlankFrame.setVisibility(View.GONE);
        } else {
            mUiTopBlankFrame.setVisibility(View.VISIBLE);
        }
        mUiHolder.requestLayout();
    }
}
