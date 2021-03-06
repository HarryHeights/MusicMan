package e.planet.musicman;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    //Callbacks
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(LOG_TAG, "ONCREATE CALLED");
        super.onCreate(savedInstanceState);
        globT.start();
        setContentView(R.layout.layout_main);
        ListView lv = findViewById(R.id.mainViewport);
        registerForContextMenu(lv);
        pl = new PlayListManager(getApplicationContext(), this);
        sc = new SettingsManager(getApplicationContext());
        sortBy = Integer.parseInt(sc.getSetting(Constants.SETTING_SORTBY));
        searchBy = Integer.parseInt(sc.getSetting(Constants.SETTING_SEARCHBY));
        setupActionBar();
        findViewById(R.id.searchbox).setVisibility(View.GONE);
        findViewById(R.id.searchbybtn).setVisibility(View.GONE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.v(LOG_TAG, "REQUESTING PERMISSION");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_CONTACTS);
        } else {
            Log.v(LOG_TAG, "PERMISSION ALREADY GRANTED");
            startplayer();
            registerReceiver();
            setListeners();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(LOG_TAG, "ONDESTROY CALLED");
        stopplayer();
        if (brcv != null) {
            unregisterReceiver(brcv);
        }
        if (notificationManager != null)
            notificationManager.cancelAll();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(LOG_TAG, "ONSTART CALLED");
        if (serv != null && serv.player != null) {
            handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(LOG_TAG, "ONSTOP CALLED");
        if (animator != null) {
            animator.cancel();
        }
        handleSearch();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.v(LOG_TAG, "ONRESTART CALLED");
        if (serv != null)
            loadFiles();
    }

    private Menu menu;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Log.v(LOG_TAG, "Settings Pressed");
                displayDialog(Constants.DIALOG_SETTINGS);
                return true;
            case R.id.action_sortby:
                Log.v(LOG_TAG, "SortBy Pressed");
                displayDialog(Constants.DIALOG_SORT);
                return true;
            case R.id.action_search:
                Log.v(LOG_TAG, "Search Pressed");
                handleSearch();
                return true;
            case R.id.action_select:
                Log.v(LOG_TAG, "Select Pressed");
                multiSelect();
                return true;
            case R.id.action_addItemsToPlaylist:
                Log.v(LOG_TAG, "ADDTOPLAYLIST PRESSED");
                //Ask Which PlayList / New Playlist
                /*pl.createPlayList("MYPLAYLIST",pls);
                    pl.selectPlayList("MYPLAYLIST");
                    pl.sortContent(sortBy);*/
                return true;
            case R.id.action_cancel:
                Log.v(LOG_TAG, "CANCEL PRESSED");
                multiSelect();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        Log.v(LOG_TAG, "You clicked Item: " + id + " at position:" + position);
        ImageButton btn = findViewById(R.id.buttonPlay);
        if (serv != null) {
            if (serv.play(pl.viewList.get(position).id))
                setPlayButton(btn, true);
            else
                setPlayButton(btn, false);
            updateSongDisplay();
            handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(LOG_TAG, "PERM GRANTED");
                startplayer();
                loadFiles();
                registerReceiver();
                setListeners();
            } else {
                Log.v(LOG_TAG, "PERM DENIED");
                System.exit(1);
            }
            return;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.mainViewport) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.list_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.add:
                // add stuff here
                return true;
            case R.id.info:
                // edit stuff here
                return true;
            case R.id.del:
                // remove stuff here
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    //Globals
    public MusicPlayerService serv;

    private BroadcastReceiver brcv;

    private SongAdapter arrayAdapter;

    NotificationManagerCompat notificationManager;

    /* Contains all the Song Data */
    PlayListManager pl;

    /* Settings Manager */
    SettingsManager sc;

    int sortBy;
    int searchBy;

    PerformanceTimer globT = new PerformanceTimer();

    String LOG_TAG = "main";

    View.OnClickListener playbutton_click;
    View.OnClickListener prevbutton_click;
    View.OnClickListener nexbutton_click;
    View.OnClickListener shufbutton_click;
    View.OnClickListener repbutton_click;
    View.OnClickListener sortbybtn_click;

    ValueAnimator animator;

    int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 42;

    public void initPlayer() {
        /* Callback when Player Service is Ready */
        Log.v(LOG_TAG, "INIT PLAYER");
        setListAdapter();
        serv.init(pl);
        updateSongDisplay();
    }

    public void loadFiles() {
        Log.v(LOG_TAG, "LOADING FILES");
        pl.loadContent();
        pl.sortContent(sortBy);
    }

    public void handleProgressAnimation(int dur, int pos) {
        /* Creates a new ValueAnimator for the Duration Bar and the Digits, And Calls Update Song Display*/
        Log.v(LOG_TAG, "UPDATE UI, DUR: " + dur + " POS: " + pos + " ISPLAYING: " + serv.player.isPlaying());
        final ProgressBar pb = findViewById(R.id.songDurBar);
        final TextView tv = findViewById(R.id.digitDisp);
        if (animator != null)
            animator.cancel();
        animator = ValueAnimator.ofInt(0, dur);
        animator.setDuration(dur);
        animator.setCurrentPlayTime(pos);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (serv == null || serv.player == null)
                    return;
                double proc = 0;
                double dur = animation.getDuration();
                double pos = animation.getCurrentPlayTime();
                int minutesT = ((int) dur / 1000) / 60;
                int secondsT = ((int) dur / 1000) % 60;
                int minutesP = ((int) pos / 1000) / 60;
                int secondsP = ((int) pos / 1000) % 60;
                String dspt = leftpadZero(minutesP) + ":" + leftpadZero(secondsP) + " - " + leftpadZero(minutesT) + ":" + leftpadZero(secondsT);
                if (pos > 0)
                    proc = (pos / dur) * 100;
                if (serv.player.isPlaying()) {
                    pb.setProgress(safeDoubleToInt(proc));
                    tv.setText(dspt);
                }
            }
        });
        if (serv.getPlaybackStatus())
            animator.start();
        updateSongDisplay();
    }

    public void updateSongDisplay() {
        /* Set the Song Title Text */
        String text = "";
        ItemSong s = serv.getCurrentSong();
        if (s != null) {
            text = s.Title + " by " + s.Artist;
        }
        TextView txt = findViewById(R.id.songDisplay);
        txt.setText(text);
    }

    public void displayDialog(int m) {
        /*Toolbar Menu Item Dialoges*/
        switch (m) {
            case Constants.DIALOG_SORT:
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Sort By");
                b.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Sort By Selection
                        pl.sortContent(sortBy);
                        serv.reload();
                        arrayAdapter.notifyDataSetChanged();
                    }
                });
                b.setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do Nothing
                    }
                });
                CharSequence[] arr = {"Title", "Artist"};
                b.setSingleChoiceItems(arr, sortBy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                sortBy = Constants.SORT_BYTITLE;
                                sc.putSetting(Constants.SETTING_SORTBY, "" + sortBy);
                                break;
                            case 1:
                                sortBy = Constants.SORT_BYARTIST;
                                sc.putSetting(Constants.SETTING_SORTBY, "" + sortBy);
                                break;
                        }
                    }
                });
                final AlertDialog sortdia = b.create();
                LayoutInflater l = LayoutInflater.from(this);
                View e = l.inflate(R.layout.dialog_sort, null);
                sortdia.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        sortdia.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorDialogText));
                        sortdia.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorDialogText));
                    }
                });

                sortdia.setView(e);
                sortdia.show();
                break;

            case Constants.DIALOG_SETTINGS:
                AlertDialog.Builder d = new AlertDialog.Builder(this);
                d.setTitle("Settings");
                d.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                final AlertDialog setdia = d.create();
                LayoutInflater f = LayoutInflater.from(this);
                View v = f.inflate(R.layout.dialog_settings, null);
                SeekBar pb = v.findViewById(R.id.seekBar1);
                pb.setProgress((int) (serv.getVolume() * 100));
                pb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float c = (float) progress / 100;
                        Log.v(LOG_TAG, "Setting Volume: " + c);
                        if (serv != null)
                            serv.setVolume(c);
                        sc.putSetting(Constants.SETTING_VOLUME, "" + c);
                    }
                });

                setdia.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        setdia.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorDialogText));
                    }
                });

                setdia.setView(v);
                setdia.show();
                break;
            case Constants.DIALOG_SEARCHBY:
                AlertDialog.Builder b1 = new AlertDialog.Builder(this);
                b1.setTitle("Search By:");
                b1.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                b1.setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do Nothing
                    }
                });
                CharSequence[] arr1 = {"Title", "Artist", "Both"};
                b1.setSingleChoiceItems(arr1, searchBy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                searchBy = Constants.SEARCH_BYTITLE;
                                sc.putSetting(Constants.SETTING_SEARCHBY, "" + searchBy);
                                break;
                            case 1:
                                searchBy = Constants.SEARCH_BYARTIST;
                                sc.putSetting(Constants.SETTING_SEARCHBY, "" + searchBy);
                                break;
                            case 2:
                                searchBy = Constants.SEARCH_BYBOTH;
                                sc.putSetting(Constants.SETTING_SEARCHBY, "" + searchBy);
                                break;
                        }
                    }
                });
                final AlertDialog serdia = b1.create();
                LayoutInflater l1 = LayoutInflater.from(this);
                View e1 = l1.inflate(R.layout.dialog_sort, null);
                serdia.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        serdia.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorDialogText));
                        serdia.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorDialogText));
                    }
                });
                serdia.setView(e1);
                serdia.show();
                break;
        }
    }

    public void updateContentArrayAdapter() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                arrayAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupActionBar() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_USE_LOGO);
        getSupportActionBar().setIcon(R.drawable.mainicon);
        getSupportActionBar().setLogo(R.drawable.mainicon);
        ActionBar actionbar = getSupportActionBar();
        String hexColor = "#" + Integer.toHexString(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent) & 0x00ffffff); //Because ANDROID
        String t = "<font color='" + hexColor + "'>MusicMan</font>";
        actionbar.setTitle(Html.fromHtml(t));
    }

    private void handleSearch() {
        EditText ed = findViewById(R.id.searchbox);
        ImageButton iv = findViewById(R.id.searchbybtn);
        if (ed.getVisibility() == View.GONE) {
            ed.setVisibility(View.VISIBLE);
            iv.setVisibility(View.VISIBLE);
            ed.setFocusableInTouchMode(true);
            ed.requestFocus();
            ed.addTextChangedListener(new TextChangedListener<EditText>(ed) {
                public void onTextChanged(EditText target, Editable s) {
                    String searchTerm = target.getText().toString();
                    Log.v(LOG_TAG, "SEARCHTEXT: " + searchTerm);
                    if (searchTerm != "") {
                        pl.showFiltered(searchTerm, searchBy);
                    }
                }
            });
            toggleKeyboardView(this, this.getCurrentFocus(), true);
        } else {
            toggleKeyboardView(this, this.getCurrentFocus(), false);
            ed.setVisibility(View.GONE);
            iv.setVisibility(View.GONE);
        }
    }

    public void setListeners() {
        final ImageButton playbtn = findViewById(R.id.buttonPlay);
        ImageButton prevbtn = findViewById(R.id.buttonPrev);
        ImageButton nexbtn = findViewById(R.id.buttonNex);
        ImageButton shufbtn = findViewById(R.id.buttonShuff);
        ImageButton repbtn = findViewById(R.id.buttonRep);
        ImageButton sbbtn = findViewById(R.id.searchbybtn);
        playbutton_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serv != null && serv.player != null) {
                    if (serv.pauseResume())
                        setPlayButton(playbtn, true);
                    else
                        setPlayButton(playbtn, false);
                    handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
                }
            }
        };
        prevbutton_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serv != null && serv.player != null) {
                    serv.previous();
                    updateSongDisplay();
                    handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
                    setPlayButton(playbtn, true);
                }
            }
        };
        nexbutton_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serv != null && serv.player != null) {
                    serv.next();
                    updateSongDisplay();
                    handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
                    setPlayButton(playbtn, true);
                }
            }
        };
        shufbutton_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageButton btn = findViewById(R.id.buttonShuff);
                if (serv != null) {
                    if (serv.enableShuffle()) {

                        btn.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorhighlight));
                    } else {
                        btn.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
            }
        };
        repbutton_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageButton btn = findViewById(R.id.buttonRep);
                if (serv != null) {
                    if (serv.enableRepeat()) {

                        btn.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorhighlight));
                    } else {
                        btn.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
            }
        };
        sortbybtn_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayDialog(Constants.DIALOG_SEARCHBY);
            }
        };
        playbtn.setOnClickListener(playbutton_click);
        prevbtn.setOnClickListener(prevbutton_click);
        nexbtn.setOnClickListener(nexbutton_click);
        shufbtn.setOnClickListener(shufbutton_click);
        repbtn.setOnClickListener(repbutton_click);
        sbbtn.setOnClickListener(sortbybtn_click);
        ListView listview = (ListView) findViewById(R.id.mainViewport);
        listview.setOnItemClickListener(this);
    }

    private void setListAdapter() {
        ListView lv = (ListView) findViewById(R.id.mainViewport);
        arrayAdapter = new SongAdapter(this, pl.viewList);
        lv.setAdapter(arrayAdapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }

    private void registerReceiver() {
        brcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Constants.ACTION_QUIT:
                        finish();
                        break;
                    case Constants.ACTION_STATUS_NEWSONG:
                        int dur = intent.getIntExtra("dur", 0);
                        int pos = intent.getIntExtra("pos", 0);
                        handleProgressAnimation(dur, pos);
                        break;
                    case Constants.ACTION_STATUS_PLAYING:
                        ImageButton btn = findViewById(R.id.buttonPlay);
                        setPlayButton(btn, true);
                        handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
                        break;
                    case Constants.ACTION_STATUS_PAUSED:
                        ImageButton btn1 = findViewById(R.id.buttonPlay);
                        setPlayButton(btn1, false);
                        handleProgressAnimation(serv.player.getDuration(), serv.player.getCurrentPosition());
                        break;
                }
            }
        };
        IntentFilter flt = new IntentFilter();
        flt.addAction(Constants.ACTION_STATUS_NEWSONG);
        flt.addAction(Constants.ACTION_STATUS_PLAYING);
        flt.addAction(Constants.ACTION_STATUS_PAUSED);
        flt.addAction(Constants.ACTION_QUIT);
        registerReceiver(brcv, flt);
    }

    public void setPlayButton(ImageButton btn, boolean play) {
        if (play) {
            btn.setImageResource(R.drawable.main_btnpause);
        } else {
            btn.setImageResource(R.drawable.main_btnplay);
        }
    }

    private void multiSelect() {
        if (arrayAdapter.state == Constants.ARRAYADAPT_STATE_DEFAULT) {
            Log.v(LOG_TAG, "SWITCHING TO SELECT MODE");
            arrayAdapter.state = Constants.ARRAYADAPT_STATE_SELECT;
            setOptionsMenu(arrayAdapter.state);
        } else {
            Log.v(LOG_TAG, "SWITCHING TO NORMAL MODE");
            ListView v = findViewById(R.id.mainViewport);
            List<ItemSong> p = getSelected();
            Log.v(LOG_TAG, "NUMSEL: " + p.size());
            for (int i = 0; i < p.size(); i++) {
                Log.v(LOG_TAG, "PATH: " + p.get(i).file.getAbsolutePath());
            }
            arrayAdapter.state = Constants.ARRAYADAPT_STATE_DEFAULT;
            setOptionsMenu(arrayAdapter.state);
            for (int i = 0; i < pl.contentList.size(); i++) {
                pl.contentList.get(i).selected = false;
            }
        }
        arrayAdapter.notifyDataSetChanged();
    }

    private void setOptionsMenu(int state) {
        switch (state) {
            case Constants.ARRAYADAPT_STATE_DEFAULT:
                Log.v(LOG_TAG, "NORMAL MODE");
                closeOptionsMenu();//TODO#POLISHING: Make Options Menu Transition Invisible
                menu.findItem(R.id.action_addItemsToPlaylist).setVisible(false);
                menu.findItem(R.id.action_cancel).setVisible(false);
                menu.findItem(R.id.action_select).setVisible(true);
                break;
            case Constants.ARRAYADAPT_STATE_SELECT:
                Log.v(LOG_TAG, "SELECT MODE");
                closeOptionsMenu();
                menu.findItem(R.id.action_addItemsToPlaylist).setVisible(true);
                menu.findItem(R.id.action_cancel).setVisible(true);
                menu.findItem(R.id.action_select).setVisible(false);
                break;
        }
    }

    private List<ItemSong> getSelected() {
        List<ItemSong> ret = new ArrayList<>();
        for (int i = 0; i < pl.contentList.size(); i++) {
            if (pl.contentList.get(i).selected) {
                ret.add(pl.contentList.get(i));
            }
        }
        return ret;
    }

    public void notifyArrayAdapter()
    {
        arrayAdapter.notifyDataSetChanged();
    }

    //Tools
    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    public static int safeDoubleToInt(double l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    public String leftpadZero(int val) {
        if ((val - 10) < 0) {
            String rt = "0" + val;
            return rt;
        } else {
            String rt = "" + val;
            return rt;
        }
    }

    public static void toggleKeyboardView(Context context, View view, boolean b) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (b)
            imm.showSoftInput(view, 0);
        else
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void expandTouchArea(final View bigView, final View smallView, final int extraPadding) {
        bigView.post(new Runnable() {
            @Override
            public void run() {
                Rect rect = new Rect();
                smallView.getHitRect(rect);
                rect.top -= extraPadding;
                rect.left -= extraPadding;
                rect.right += extraPadding;
                rect.bottom += extraPadding;
                bigView.setTouchDelegate(new TouchDelegate(rect, smallView));
            }
        });
    }

    //Service Binding
    public void startplayer() {
        doBindService();
    }

    public void stopplayer() {
        if (serv != null) doUnbindService();
    }

    void doBindService() {
        Intent intent = new Intent(this, MusicPlayerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void doUnbindService() {
        Intent intent = new Intent(this, MusicPlayerService.class);
        unbindService(mConnection);
        stopService(intent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            serv = ((MusicPlayerService.LocalBinder) service).getService();
            serv.setVolume(Float.parseFloat(sc.getSetting(Constants.SETTING_VOLUME)));
            initPlayer();
            loadFiles();
            globT.printStep(LOG_TAG, "Service Initialization");
            long l = globT.tdur;
            Snackbar.make(findViewById(android.R.id.content), "Initialization Time: " + l + " ms.", Snackbar.LENGTH_LONG).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            serv = null;
        }
    };

    //Classes
    //TextChangedListener For Search Function
    public abstract class TextChangedListener<T> implements TextWatcher {
        private T target;

        public TextChangedListener(T target) {
            this.target = target;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            this.onTextChanged(target, s);
        }

        public abstract void onTextChanged(T target, Editable s);
    }

    //ArrayAdapter of Song List Display
    public class SongAdapter extends ArrayAdapter<ItemSong> {

        public int state = Constants.ARRAYADAPT_STATE_DEFAULT;

        private Context mContext;
        private List<ItemSong> viewList;

        public SongAdapter(@NonNull Context context, List<ItemSong> list) {
            super(context, 0, list);
            mContext = context;
            viewList = list;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View listItem = convertView;
            if (listItem == null)
                listItem = LayoutInflater.from(mContext).inflate(R.layout.list_item_song, parent, false);
            TextView sn = listItem.findViewById(R.id.listsongname);
            TextView in = listItem.findViewById(R.id.listinterpret);
            TextView ln = listItem.findViewById(R.id.songlength);
            sn.setText(viewList.get(position).Title);
            in.setText(viewList.get(position).Artist);
            String lstr = "" + TimeUnit.MILLISECONDS.toMinutes(viewList.get(position).length) + ":" + TimeUnit.MILLISECONDS.toSeconds(viewList.get(position).length - TimeUnit.MILLISECONDS.toMinutes(viewList.get(position).length) * 60000);
            ln.setText(lstr);
            final CheckBox mcb = listItem.findViewById(R.id.checkbox);
            final View exp = listItem.findViewById(R.id.hitbox);
            switch (state) {
                case Constants.ARRAYADAPT_STATE_SELECT:
                    mcb.setVisibility(View.VISIBLE);
                    final ListView lv = findViewById(R.id.mainViewport);
                    mcb.setChecked(viewList.get(position).selected);
                    mcb.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            if (((CheckBox) v).isChecked()) {
                                viewList.get(position).selected = true;

                            } else {
                                viewList.get(position).selected = false;
                            }
                        }
                    });
                    expandTouchArea(exp, (View) mcb, 1000);
                    break;
                default:
                    mcb.setVisibility(View.GONE);
                    expandTouchArea(exp, (View) mcb, 0);
                    break;
            }
            return listItem;
        }
    }
}
