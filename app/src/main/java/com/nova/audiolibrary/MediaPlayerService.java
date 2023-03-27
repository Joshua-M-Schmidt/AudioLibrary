package com.nova.audiolibrary;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;

import static com.nova.audiolibrary.MainActivity.FILE_DIRECTION_KEY;
import static com.nova.audiolibrary.MainActivity.IS_PLAYING_KEY;

/**
 * Created by Valdio Veliu on 16-07-11.
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {


    public static final String ACTION_PLAY = "com.nova.audiolibrary.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.nova.audiolibrary.ACTION_PAUSE";
    public static final String ACTION_STOP = "com.nova.audiolibrary.ACTION_STOP";
    public static final String ACTION_JUMP = "com.nova.audiolibrary.ACTION_JUMP";
    public static final String ACTION_JUMP_BACK = "com.nova.audiolibrary.ACTION_JUMP_BACK";
    public static final String ACTION_JUMP_FORWARD = "com.nova.audiolibrary.ACTION_JUMP_FORWARD";

    private MediaPlayer mediaPlayer;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;

    //Used to pause/resume MediaPlayer
    private int resumePosition;

    //AudioFocus
    private AudioManager audioManager;

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    //List of available Audio files
    private Audio activeAudio; //an object on the currently playing audio


    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;


    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {

            //Load data from SharedPreferences
            //StorageUtil storage = new StorageUtil(getApplicationContext());
            //audioList = storage.loadAudio();
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String audio_str = prefs.getString(MainActivity.CURRENT_SONG_KEY,"");
            Gson gson = new Gson();
            Log.i("data_str",audio_str);
            activeAudio = gson.fromJson(audio_str, Audio.class);

        } catch (NullPointerException e) {
            stopSelf();
        }

        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {


                initMediaSession();
                initMediaPlayer();

            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        removeNotification();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }


    /**
     * MediaPlayer callback methods
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed.
        stopMedia();

        removeNotification();
        //stop the service
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //Invoked when the media source is ready for playback.
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
    }

    @Override
    public void onAudioFocusChange(int focusState) {

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) pauseMedia();
                //mediaPlayer.release();
                //mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }

    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer() {
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance

        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();



        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(activeAudio.getUri());

        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            //Toast.makeText(getApplicationContext(),"start: "+prefs.getInt(activeAudio.getTitle(),0),Toast.LENGTH_SHORT).show();
            mediaPlayer.seekTo(prefs.getInt(activeAudio.getTitle(),0));
            prefs.edit().putBoolean(IS_PLAYING_KEY,true).commit();
            mediaPlayer.start();
            Intent broadcastIntent = new Intent(ACTION_PLAY);
            sendBroadcast(broadcastIntent);
        }
    }

    private void playJumpMedia(int mills) {
        mediaPlayer.seekTo(mills);
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            prefs.edit().putBoolean(IS_PLAYING_KEY,false).commit();
            mediaPlayer.stop();
        }
    }

    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            prefs.edit().putBoolean(IS_PLAYING_KEY,false).commit();
            resumePosition = mediaPlayer.getCurrentPosition();
            Intent broadcastIntent = new Intent(ACTION_PAUSE);
            sendBroadcast(broadcastIntent);
        }
    }

    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            prefs.edit().putBoolean(IS_PLAYING_KEY,true).commit();
            Intent broadcastIntent = new Intent(ACTION_PLAY);
            sendBroadcast(broadcastIntent);
        }
    }



    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();

                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();

                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }


            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    private void updateMetaData() {

        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    private void buildNotification(PlaybackStatus playbackStatus) {

        Log.i("play",playbackStatus.name());

        /**
         * Notification actions -> playbackAction()
         *  0 -> Play
         *  1 -> Pause
         *  4 -> Jump Backward
         *  55 -> Jump Forward
         */

        int notificationAction = R.drawable.baseline_play_circle_filled_24px;//needs to be initialized
        PendingIntent play_pauseAction = null;

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = R.drawable.baseline_pause_circle_filled_24px;
            //create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = R.drawable.baseline_play_circle_filled_24px;
            //create the play action
            play_pauseAction = playbackAction(0);
        }

        String CHANNEL_ID = "my_channel_01";// The id of the channel.

        Bitmap albumArt = getBitmapFromVectorDrawable(getApplicationContext(),R.drawable.ordner);


        Intent jumpbackAction = new Intent(this, MediaPlayerService.class);
        jumpbackAction.setAction(ACTION_JUMP_BACK);
        jumpbackAction.putExtra("time",activeAudio.getTime()-30000);
        PendingIntent jump_back_action =  PendingIntent.getService(this, 4, jumpbackAction, 0);

        // Create a new Notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                // Hide the timestamp
                .setShowWhen(false)
                // Set the Notification color
                .setColor(getResources().getColor(R.color.colorPrimary))
                // Set the large and small icons
                .setSmallIcon(R.drawable.play)
                .setSubText(activeAudio.getTitle())
                .setLargeIcon(albumArt)
                .setContentText(prefs.getString(FILE_DIRECTION_KEY, ""))
                // Set Notification content information
                .setContentTitle(activeAudio.getTitle())
                .setChannelId(CHANNEL_ID)
                // Add playback actions
                .addAction(R.drawable.baseline_replay_30_24px, "back", jump_back_action)
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(R.drawable.baseline_forward_30_24px, "fourth", playbackAction(5))
                // style
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken()));

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            CharSequence name = "AudioLibraryTracker";// The user-visible name of the channel.
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            manager.createNotificationChannel(mChannel);
        }

        manager.notify(NOTIFICATION_ID, notificationBuilder.build());

    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = (DrawableCompat.wrap(drawable)).mutate();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }


    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                // Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 4:
                // Next track
                playbackAction.setAction(ACTION_JUMP_BACK);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 5:
                // Previous track
                playbackAction.setAction(ACTION_JUMP_FORWARD);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_JUMP_BACK)) {
            int currentTime = prefs.getInt(activeAudio.getTitle(),0);
            Log.i("currentTime","currentTime  "+ (currentTime));
            Log.i("currentTime dif","currentTime-30000 "+ (currentTime-30000));

            if(currentTime-30000 < 0) {
                prefs.edit().putInt(activeAudio.getTitle(), 0).commit();
                mediaPlayer.seekTo(0);
            }else{
                prefs.edit().putInt(activeAudio.getTitle(), currentTime - 30000).commit();
                mediaPlayer.seekTo(currentTime - 30000);
            }
            Log.i("currentTime back",prefs.getInt(activeAudio.getTitle(),0)+"");


        } else if (actionString.equalsIgnoreCase(ACTION_JUMP_FORWARD)) {
            int currentTime = prefs.getInt(activeAudio.getTitle(),0);
            Log.i("currentTime","currentTime  "+ (currentTime));
            Log.i("currentTime dif","currentTime+30000 "+ (currentTime+30000));
            prefs.edit().putInt(activeAudio.getTitle(), currentTime + 30000).commit();
            mediaPlayer.seekTo(currentTime + 30000);
            Log.i("currentTime for",prefs.getInt(activeAudio.getTitle(),0)+"");
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }


    /**
     * Play new Audio
     */

    SharedPreferences prefs;

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            prefs.edit().putBoolean(IS_PLAYING_KEY,true).commit();
            String audio_str = prefs.getString(MainActivity.CURRENT_SONG_KEY,"");
            Gson gson = new Gson();
            activeAudio = gson.fromJson(audio_str, Audio.class);

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    private BroadcastReceiver pauseAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs.edit().putBoolean(IS_PLAYING_KEY,false).commit();
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private BroadcastReceiver playAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs.edit().putBoolean(IS_PLAYING_KEY,true).commit();
            resumeMedia();

            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    public static final String JumpMillsKey = "jump_mills_key";

    private BroadcastReceiver jumpAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            playJumpMedia(intent.getIntExtra(JumpMillsKey,0));
        }
    };

    private BroadcastReceiver jumpAudioBack = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //playJumpMedia(30000);
        }
    };

    private BroadcastReceiver jumpAudioForward = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //playJumpMedia(60000);
            Toast.makeText(getApplicationContext(),"jump 30 backwards",Toast.LENGTH_SHORT).show();
        }
    };

    private void register_playNewAudio() {
        //Register playNewMedia receiver
        registerReceiver(playNewAudio, new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO));
        registerReceiver(pauseAudio, new IntentFilter(ACTION_PAUSE));
        registerReceiver(playAudio, new IntentFilter(ACTION_PLAY));
        registerReceiver(jumpAudio, new IntentFilter(ACTION_JUMP));
        registerReceiver(jumpAudioBack, new IntentFilter(ACTION_JUMP_BACK));
        registerReceiver(jumpAudioForward, new IntentFilter(ACTION_JUMP_FORWARD));
    }
}
