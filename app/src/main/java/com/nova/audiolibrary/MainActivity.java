package com.nova.audiolibrary;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.obsez.android.lib.filechooser.ChooserDialog;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.nova.audiolibrary.MediaPlayerService.ACTION_JUMP;
import static com.nova.audiolibrary.MediaPlayerService.ACTION_JUMP_BACK;
import static com.nova.audiolibrary.MediaPlayerService.ACTION_JUMP_FORWARD;
import static com.nova.audiolibrary.MediaPlayerService.ACTION_PAUSE;
import static com.nova.audiolibrary.MediaPlayerService.ACTION_PLAY;
import static com.nova.audiolibrary.MediaPlayerService.JumpMillsKey;

public class MainActivity extends AppCompatActivity {
    
    // shared preference key for the file directory to load audio files from
    public static final String FILE_DIRECTION_KEY = "file_direction_key";
    
    // shared preference key for if app is used the first time
    public static final String FIRST_USE_KEY = "first_run_key";
    
    // shared prefs keys to communicate between activity and audio service
    public static final String IS_PLAYING_KEY = "is_playing_key";
    public static final String CURRENT_POSITION_KEY = "current_position_key";

    SharedPreferences prefs;

    private MediaPlayerService player;
    boolean serviceBound = false;
    
    // list of audiofiles to display in the app
    ArrayList<Audio> audio_list;

    Gson gson;

    DiscreteSeekBar seekBar;
    TextView title;

    RecyclerView recyclerView;
    RecyclerView_Adapter adapter;
    
    // audio control elements
    ImageButton play_pause;
    ImageButton jumpBack30;
    ImageButton jumpForward30;
    TextView duration;
    TextView nowTime;
    LinearLayout bottomContainer;


    private BroadcastReceiver pauseAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            play_pause.setImageResource(R.drawable.baseline_play_circle_filled_24px);
            play_pause.setTag(R.drawable.baseline_play_circle_filled_24px);
            prefs.edit().putBoolean(IS_PLAYING_KEY, false).commit();
        }
    };

    private BroadcastReceiver playAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs.edit().putBoolean(IS_PLAYING_KEY, true).commit();
            play_pause.setImageResource(R.drawable.baseline_pause_circle_filled_24px);
            play_pause.setTag(R.drawable.baseline_pause_circle_filled_24px);
        }
    };

    private BroadcastReceiver jumpTo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "jump", Toast.LENGTH_SHORT).show();
        }
    };

    private BroadcastReceiver jumpBack = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "jump back", Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        checkForFirstUse(); // otherwise open IntroActivity

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // set before setting title

        setTitleAsFolderPath();

        bottomContainer = findViewById(R.id.bottom_container);

        seekBar = findViewById(R.id.seekbar);
        title = findViewById(R.id.title);
        play_pause = findViewById(R.id.play_pause);
        jumpBack30 = findViewById(R.id.back_30);
        jumpForward30 = findViewById(R.id.forward_30);

        duration = findViewById(R.id.duration);
        nowTime = findViewById(R.id.nowtime);

        registerReceiver(pauseAudio, new IntentFilter(ACTION_PAUSE));
        registerReceiver(playAudio, new IntentFilter(ACTION_PLAY));
        registerReceiver(jumpBack, new IntentFilter(ACTION_JUMP_BACK));
        registerReceiver(jumpTo, new IntentFilter(ACTION_JUMP_FORWARD));

        gson = new Gson();


        checkIfCanReadStorage(); // if so init the audio list

        initAudioService();

        initJumpButton();

    }

    // start audio service or connect and sync with the app audio controls if existing

    public void initAudioService(){
        boolean is_service_running = isMyServiceRunning(MediaPlayerService.class);
        current_position = prefs.getInt(CURRENT_POSITION_KEY, 0);
        if (is_service_running) {
            audio_list.get(current_position).setTime(audio_list.get(current_position).getTime());
            nowTime.setText(milliSecondsToTimer(audio_list.get(current_position).getTime()));
            duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
            int time = audio_list.get(current_position).getLenght();
            int playTime = audio_list.get(current_position).getTime();
            float progressNumber = (float) playTime / (float) time * 1000f;
            seekBar.setProgress((int) progressNumber);
            prefs.edit().putInt(audio_list.get(current_position).getTitle(), playTime).commit();
            audio_list.get(current_position).setTime(playTime);
            adapter.notifyItemChanged(current_position);

            t.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if ((prefs.getInt(audio_list.get(current_position).getTitle(), 0)) < audio_list.get(current_position).getLenght()) {
                                if (prefs.getBoolean(IS_PLAYING_KEY, true)) {
                                    audio_list.get(current_position).setTime(prefs.getInt(audio_list.get(current_position).getTitle(), 0) + 1000);
                                    nowTime.setText(milliSecondsToTimer(audio_list.get(current_position).getTime()));
                                    duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                                    int time = audio_list.get(current_position).getLenght();
                                    int playTime = audio_list.get(current_position).getTime();
                                    float progressNumber = (float) playTime / (float) time * 1000f;
                                    seekBar.setProgress((int) progressNumber);

                                    prefs.edit().putInt(audio_list.get(current_position).getTitle(), playTime).commit();
                                    audio_list.get(current_position).setTime(playTime);
                                    adapter.notifyItemChanged(current_position);
                                    Log.i("currentTime inc", prefs.getInt(audio_list.get(current_position).getTitle(), 0) + " ");
                                }
                            }
                        }
                    });
                }

            }, 0, 1000);

            seekBar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                @Override
                public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                    if (fromUser) {
                        Intent broadcastIntent = new Intent(ACTION_JUMP);
                        float position = ((float) ((value) * 0.001)) * audio_list.get(current_position).getLenght();
                        broadcastIntent.putExtra(JumpMillsKey, (int) position);
                        sendBroadcast(broadcastIntent);
                        nowTime.setText(milliSecondsToTimer((int) position));
                        duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                        Log.i("currentpos", (int) position + "");
                        audio_list.get(current_position).setTime((int) position);
                        prefs.edit().putInt(audio_list.get(current_position).getTitle(), (int) position).commit();
                    }
                }

                @Override
                public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

                }
            });

            if (prefs.getBoolean(IS_PLAYING_KEY, true)) {
                play_pause.setImageResource(R.drawable.baseline_pause_circle_filled_24px);
                play_pause.setTag(R.drawable.baseline_pause_circle_filled_24px);
            } else {
                play_pause.setImageResource(R.drawable.baseline_play_circle_filled_24px);
                play_pause.setTag(R.drawable.baseline_play_circle_filled_24px);
            }

            play_pause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ((int) play_pause.getTag() == R.drawable.baseline_pause_circle_filled_24px) {
                        prefs.edit().putBoolean(IS_PLAYING_KEY, false).commit();
                        play_pause.setImageResource(R.drawable.baseline_play_circle_filled_24px);
                        play_pause.setTag(R.drawable.baseline_play_circle_filled_24px);
                        Intent broadcastIntent = new Intent(ACTION_PAUSE);
                        sendBroadcast(broadcastIntent);
                    } else {
                        prefs.edit().putBoolean(IS_PLAYING_KEY, true).commit();
                        play_pause.setImageResource(R.drawable.baseline_pause_circle_filled_24px);
                        play_pause.setTag(R.drawable.baseline_pause_circle_filled_24px);
                        Intent broadcastIntent = new Intent(ACTION_PLAY);
                        sendBroadcast(broadcastIntent);
                    }

                }
            });

            seekBar.setProgress((int) ((float) (audio_list.get(current_position).getTime()) / (float) (audio_list.get(current_position).getLenght()) * 100f));

            String current_audio_string = gson.toJson(audio_list.get(current_position));
            prefs.edit().putString(CURRENT_SONG_KEY, current_audio_string).commit();

            nowTime.setText(milliSecondsToTimer(audio_list.get(current_position).getTime()));
            duration.setText(milliSecondsToTimer(time));

            title.setText(audio_list.get(current_position).getTitle());
        }
    }

    private void checkIfCanReadStorage(){
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.READ_CONTACTS)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_CONTACTS},
                        5);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }

        } else {
            audio_list = loadAudio();
            initList(audio_list);
        }
    }

    private void initJumpButton() {
        jumpBack30.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadcastIntent = new Intent(ACTION_JUMP);
                float position = audio_list.get(current_position).getTime() - 30000;
                if (position < 0) {
                    position = 0;
                }

                seekBar.setProgress((int) ((position / audio_list.get(current_position).getLenght()) * 1000));
                broadcastIntent.putExtra(JumpMillsKey, (int) position);
                sendBroadcast(broadcastIntent);
                nowTime.setText(milliSecondsToTimer((int) position));
                duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                Log.i("currentpos", (int) position + "");
                audio_list.get(current_position).setTime((int) position);
                prefs.edit().putInt(audio_list.get(current_position).getTitle(), (int) position).commit();
            }
        });

        jumpForward30.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadcastIntent = new Intent(ACTION_JUMP);
                float position = audio_list.get(current_position).getTime() + 30000;
                if (position < audio_list.get(current_position).getLenght()) {
                    seekBar.setProgress((int) ((position / audio_list.get(current_position).getLenght()) * 1000));
                    broadcastIntent.putExtra(JumpMillsKey, (int) position);
                    sendBroadcast(broadcastIntent);
                    nowTime.setText(milliSecondsToTimer((int) position));
                    duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                    Log.i("currentpos", (int) position + "");
                    audio_list.get(current_position).setTime((int) position);
                    prefs.edit().putInt(audio_list.get(current_position).getTitle(), (int) position).commit();
                }
            }
        });
    }

    // check if it is the first time the user opens the app and if so display the welcome activity

    private void checkForFirstUse(){
        if (prefs.getBoolean(FIRST_USE_KEY, true)) {
            prefs.edit().putBoolean(FIRST_USE_KEY, false).commit();
            startActivity(new Intent(MainActivity.this, IntroActivity.class));
        }
    }

    // display read folder as title and concat to 20 characters if the path is to long

    private void setTitleAsFolderPath(){
        String path = prefs.getString(FILE_DIRECTION_KEY, "");
        String sub;
        if (path.length() > 20) {
            sub = "..." + path.substring(path.length() - 20);
        } else {
            sub = path;
        }
        getSupportActionBar().setTitle(sub);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("logresume", "resuming");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 5: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    audio_list = loadAudio();
                    initList(audio_list);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    private void initList(final ArrayList<Audio> audio_list) {
        if (audio_list != null && audio_list.size() > 0) {
            recyclerView = findViewById(R.id.my_recycler_view);
            adapter = new RecyclerView_Adapter(audio_list, getApplication());
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.addOnItemTouchListener(
                    new RecyclerItemClickListener(getApplicationContext(), recyclerView, new RecyclerItemClickListener.OnItemClickListener() {
                        @Override
                        public void onItemClick(View view, int position) {
                            prefs.edit().putInt(CURRENT_POSITION_KEY, position).commit();
                            if (t != null) {
                                t.cancel();
                                t.purge();
                            }
                            t = new Timer();
                            playAudio();

                        }

                        @Override
                        public void onLongItemClick(View view, int position) {
                            if(prefs.getInt(CURRENT_POSITION_KEY,-1) != -1){
                                if(position != prefs.getInt(CURRENT_POSITION_KEY,-1)){
                                    startDeleteDialog(position);
                                }
                            }else{
                                startDeleteDialog(position);
                            }


                        }
                    })
            );
        }
    }

    public void startDeleteDialog(final int position) {
        final Audio toDelete = audio_list.get(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Add the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                Audio currentAudio = audio_list.get(current_position);

                String fileName= toDelete.getUri();
                File file = new File(fileName);
                boolean deleted = file.delete();

                if(deleted){
                    audio_list.remove(toDelete);

                    int newCurrent_position = audio_list.indexOf(currentAudio);

                    current_position = newCurrent_position;
                    prefs.edit().putInt(CURRENT_POSITION_KEY, current_position).commit();

                    adapter.notifyItemRemoved(position);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });
        // Set other dialog properties
        builder.setMessage(toDelete.getTitle())
                .setTitle("Delete");

        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private ArrayList<Audio> loadAudio() {
        final ArrayList<Audio> tempAudioList = new ArrayList<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.AudioColumns.DATA, MediaStore.Audio.AudioColumns.TITLE, MediaStore.MediaColumns._ID, MediaStore.Audio.ArtistColumns.ARTIST};
        Cursor c = this.getContentResolver().query(uri, projection, MediaStore.Audio.Media.DATA + " like ? ", new String[]{"%" + prefs.getString(FILE_DIRECTION_KEY, "") + "%"}, null);

        if (c != null) {
            while (c.moveToNext()) {

                String path = c.getString(0);
                String title = c.getString(1);
                String artist = c.getString(3);
                Uri audio_uri = Uri.fromFile(new File(path));
                try {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    Log.i("faulty uri", audio_uri.getPath());
                    mmr.setDataSource(getApplicationContext(), audio_uri);
                    String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    int milliseconds = Integer.parseInt(durationStr);
                    int listening_time = prefs.getInt(title, 0);


                    Audio audioModel = new Audio(title, milliseconds, listening_time, audio_uri.getPath(), artist);

                    Log.i("audio_info", "Name : " + title + " Path : " + path + " listening_time : " + listening_time + " time : " + milliseconds);

                    tempAudioList.add(audioModel);
                }catch (IllegalArgumentException e){
                    e.printStackTrace();
                }
            }
            c.close();
        }

        return sortAudio(tempAudioList);
    }

    private ArrayList<Audio> sortAudio(ArrayList<Audio> audio_list) {
        ArrayList<Audio> tempAudioList = new ArrayList<>();

        while (audio_list.size() > 0) {
            Audio longest = getLongestListenedAudio(audio_list);
            tempAudioList.add(longest);
            audio_list.remove(longest);
        }

        return tempAudioList;
    }

    private Audio getLongestListenedAudio(ArrayList<Audio> audio_list) {
        if (audio_list.size() > 0) {
            Audio biggest = audio_list.get(0);
            audio_list.remove(biggest);

            for (Audio audio : audio_list) {
                if (audio.getTime() > biggest.getTime()) {
                    biggest = audio;
                }
            }

            return biggest;
        } else {
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        if (prefs != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }

        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // open the dialog to choose a new folder to search for files

        if (id == R.id.action_settings) {
            new ChooserDialog()
                    .with(MainActivity.this)
                    .withFilter(true, false)
                    .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                    // to handle the result(s)
                    .withChosenListener(new ChooserDialog.Result() {
                        @Override
                        public void onChoosePath(String path, File pathFile) {

                            prefs.edit().putString(FILE_DIRECTION_KEY, path).commit();

                            Intent intent = getIntent();
                            finish();
                            startActivity(intent);

                        }
                    })
                    .build()
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.nova.audiolibrary.PlayNewAudio";

    public static final String CURRENT_SONG_KEY = "current_song_paying_key_com_nova";


    public static String milliSecondsToTimer(long millis) {
        StringBuffer buf = new StringBuffer();

        int hours = (int) (millis / (1000 * 60 * 60));
        int minutes = (int) ((millis % (1000 * 60 * 60)) / (1000 * 60));
        int seconds = (int) (((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000);

        buf
                .append(String.format("%02d", hours))
                .append(":")
                .append(String.format("%02d", minutes))
                .append(":")
                .append(String.format("%02d", seconds));

        return buf.toString();
    }

    Timer t = new Timer();

    int current_position;

    private void playAudio() {

        current_position = prefs.getInt(CURRENT_POSITION_KEY, 0);

        //Set the schedule function and rate
        t.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if ((audio_list.get(current_position).getTime()) < audio_list.get(current_position).getLenght()) {
                            if (prefs.getBoolean(IS_PLAYING_KEY, false)) {
                                audio_list.get(current_position).setTime(audio_list.get(current_position).getTime() + 1000);
                                nowTime.setText(milliSecondsToTimer(audio_list.get(current_position).getTime()));
                                duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                                int time = audio_list.get(current_position).getLenght();
                                int playTime = audio_list.get(current_position).getTime();
                                float progressNumber = (float) playTime / (float) time * 1000f;
                                seekBar.setProgress((int) progressNumber);
                                prefs.edit().putInt(audio_list.get(current_position).getTitle(), playTime).commit();
                                audio_list.get(current_position).setTime(playTime);
                                adapter.notifyItemChanged(current_position);
                            }
                        }
                    }
                });
            }

        }, 0, 1000);

        seekBar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) {
                    Intent broadcastIntent = new Intent(ACTION_JUMP);
                    float position = ((float) ((value) * 0.001)) * audio_list.get(current_position).getLenght();
                    broadcastIntent.putExtra(JumpMillsKey, (int) position);
                    sendBroadcast(broadcastIntent);
                    nowTime.setText(milliSecondsToTimer((int) position));
                    duration.setText(milliSecondsToTimer(audio_list.get(current_position).getLenght()));
                    Log.i("currentpos", (int) position + "");
                    audio_list.get(current_position).setTime((int) position);
                    prefs.edit().putInt(audio_list.get(current_position).getTitle(), (int) position).commit();
                }
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });

        play_pause.setImageResource(R.drawable.baseline_pause_circle_filled_24px);
        play_pause.setTag(R.drawable.baseline_pause_circle_filled_24px);
        play_pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((int) play_pause.getTag() == R.drawable.baseline_pause_circle_filled_24px) {

                    prefs.edit().putBoolean(IS_PLAYING_KEY, false).commit();
                    play_pause.setImageResource(R.drawable.baseline_play_circle_filled_24px);
                    play_pause.setTag(R.drawable.baseline_play_circle_filled_24px);
                    Intent broadcastIntent = new Intent(ACTION_PAUSE);
                    sendBroadcast(broadcastIntent);
                } else {
                    prefs.edit().putBoolean(IS_PLAYING_KEY, true).commit();
                    play_pause.setImageResource(R.drawable.baseline_pause_circle_filled_24px);
                    play_pause.setTag(R.drawable.baseline_pause_circle_filled_24px);
                    Intent broadcastIntent = new Intent(ACTION_PLAY);
                    sendBroadcast(broadcastIntent);
                }

            }
        });

        int time = audio_list.get(current_position).getLenght();
        int playTime = audio_list.get(current_position).getTime();
        float progressNumber = (float) playTime / (float) time * 100f;

        seekBar.setProgress((int) progressNumber);

        String current_audio_string = gson.toJson(audio_list.get(current_position));
        prefs.edit().putString(CURRENT_SONG_KEY, current_audio_string).commit();

        nowTime.setText(milliSecondsToTimer(playTime));
        duration.setText(milliSecondsToTimer(time));

        title.setText(audio_list.get(current_position).getTitle());

        //Check is service is active
        if (!serviceBound) {
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(playAudio);
        unregisterReceiver(pauseAudio);
        unregisterReceiver(jumpTo);
        unregisterReceiver(jumpBack);

        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            player.stopSelf();
        }
    }
}
