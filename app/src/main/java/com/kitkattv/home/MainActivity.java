package com.kitkattv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A small Home screen for old MStar Android 4.4 TVs: one grid, no network, nothing in the background. */
public class MainActivity extends Activity {
    private static final int COLUMNS = 5;
    private static final long IDLE_MS = 3 * 60 * 1000;
    private static final long CLOCK_MOVE_MS = 60 * 1000;
    private static final String TV_PLAYER = "com.mstar.tv.tvplayer.ui";
    private static final String INPUT_LIST =
            TV_PLAYER + ".intent.action.InputSourceListViewActivity";
    /** Never offered: opening a factory or test app can change the TV's factory state. */
    private static final String[] HIDDEN = {
            "com.kitkattv.home", "com.assem.launcher", "com.android.launcher",
            "com.ctv.factorytest", "mstar.factorymenu.ui", "tvtests.com.mstar.android.tv",
            "com.android.wididemo", "com.android.mcastdemo", "com.ctv.app", TV_PLAYER,
            "com.android.providers.downloads.ui", "com.google.android.gms",
            "com.google.android.gsf.login",
    };
    private static final String SMARTTUBE = "org.smarttube.stable";
    /** Apps too new for Android 4.4: these tiles lead to the HDMI input of a streaming stick. */
    private static final String[][] STICK_APPS = {
            {"Netflix", "#B20710"}, {"Spotify", "#14833B"}, {"Shahid", "#007C89"}, {"WATCH IT", "#C77700"},
    };
    /** Shown after the inputs, in this order, when installed. */
    private static final String[] PINNED = {
            "com.cultraview.mediacenter", "com.android.vending",
            "com.android.settings", "com.cultraview.filemanager",
    };
    private static final int[] COLORS = {0xFF5A3E9B, 0xFF1F6F8B, 0xFF2E7D4F, 0xFF35506E, 0xFF8E2433};
    private static final int YOUTUBE_COLOR = 0xFFC4161C;
    private static final int INPUTS_COLOR = 0xFF3A4A63;
    private static final int RING = 0xFFFFFFFF;

    private final List<Tile> tiles = new ArrayList<Tile>();
    private final Handler handler = new Handler();
    private final BaseAdapter adapter = new TileAdapter();
    private final GradientDrawable backdrop = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {Color.BLACK, Color.BLACK});
    private TextView greeting;
    private TextView title;
    private TextView subtitle;
    private TextView clock;
    private TextView date;
    private GridView grid;
    private FrameLayout idleLayer;
    private TextView idleClock;
    private View focused;
    private int glow = Color.BLACK;
    private ValueAnimator glowAnimator;
    private int selectedPosition;

    private final BroadcastReceiver ticker = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            showTime();
        }
    };

    private final Runnable showIdle = new Runnable() {
        @Override public void run() {
            idleLayer.setAlpha(0f);
            idleLayer.setVisibility(View.VISIBLE);
            idleLayer.animate().alpha(1f).setDuration(800).start();
            moveIdleClock.run();
        }
    };

    /** Keeps the idle clock moving so nothing burns into the panel. */
    private final Runnable moveIdleClock = new Runnable() {
        @Override public void run() {
            float x = (float) Math.random() * Math.max(0, idleLayer.getWidth() - idleClock.getWidth());
            float y = (float) Math.random() * Math.max(0, idleLayer.getHeight() - idleClock.getHeight());
            idleClock.setX(x);
            idleClock.setY(y);
            handler.postDelayed(this, CLOCK_MOVE_MS);
        }
    };

    private static final class Tile {
        final String label;
        final String about;
        final Drawable icon;
        final Intent intent;
        final int color;
        final boolean needsStick;

        Tile(String label, String about, Drawable icon, Intent intent, int color, boolean needsStick) {
            this.label = label;
            this.about = about;
            this.icon = icon;
            this.intent = intent;
            this.color = color;
            this.needsStick = needsStick;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(backdrop);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(52), px(30), px(52), px(16));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout header = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text(18, 0xCCFFFFFF);
        title = text(44, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        subtitle = text(17, 0xBBFFFFFF);
        left.addView(greeting);
        left.addView(title);
        left.addView(subtitle);
        header.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clock = text(40, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        date = text(16, 0xBBFFFFFF);
        right.addView(clock);
        right.addView(date);
        header.addView(right);
        root.addView(header);

        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setVerticalSpacing(px(22));
        grid.setHorizontalSpacing(px(22));
        grid.setPadding(px(14), px(14), px(14), px(14));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new GradientDrawable());
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                open(tiles.get(position));
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                select(v, position);
            }

            @Override public void onNothingSelected(AdapterView<?> p) {
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = px(14);
        root.addView(grid, lp);
        frame.addView(root);

        idleLayer = new FrameLayout(this);
        idleLayer.setBackgroundColor(Color.BLACK);
        idleLayer.setVisibility(View.GONE);
        idleClock = text(96, 0xDDFFFFFF);
        idleClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        idleLayer.addView(idleClock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(idleLayer);
        setContentView(frame);
    }

    @Override protected void onResume() {
        super.onResume();
        loadTiles();
        showTime();
        registerReceiver(ticker, new IntentFilter(Intent.ACTION_TIME_TICK));
        grid.requestFocus();
        grid.setSelection(Math.min(selectedPosition, Math.max(0, tiles.size() - 1)));
        grid.post(new Runnable() {
            @Override public void run() {
                select(grid.getSelectedView(), grid.getSelectedItemPosition());
            }
        });
        wake();
    }

    @Override protected void onPause() {
        unregisterReceiver(ticker);
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    /** Any key wakes the idle clock, and that first key does nothing else. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        boolean sleeping = idleLayer.getVisibility() == View.VISIBLE;
        wake();
        return sleeping || super.dispatchKeyEvent(event);
    }

    /** Home has nowhere to go back to. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event);
    }

    private void wake() {
        handler.removeCallbacks(showIdle);
        handler.removeCallbacks(moveIdleClock);
        idleLayer.animate().cancel();
        idleLayer.setVisibility(View.GONE);
        handler.postDelayed(showIdle, IDLE_MS);
    }

    private void loadTiles() {
        tiles.clear();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Intent inputs = new Intent(INPUT_LIST);
        boolean hasInputs = pm.resolveActivity(inputs, 0) != null;

        ResolveInfo youtube = find(apps, SMARTTUBE);
        if (youtube != null) {
            tiles.add(new Tile("YouTube", "Videos, without leaving the couch", youtube.loadIcon(pm),
                    launch(youtube), YOUTUBE_COLOR, false));
        }
        for (String[] app : STICK_APPS) {
            tiles.add(new Tile(app[0], "Needs a streaming stick on HDMI", null,
                    hasInputs ? inputs : null, Color.parseColor(app[1]), true));
        }
        if (hasInputs) {
            tiles.add(new Tile("TV Inputs", "HDMI and AV", null, inputs, INPUTS_COLOR, false));
        }
        int next = 0;
        for (String pin : PINNED) {
            ResolveInfo app = find(apps, pin);
            if (app != null) {
                tiles.add(tile(pm, app, COLORS[next++ % COLORS.length]));
            }
        }
        List<ResolveInfo> rest = new ArrayList<ResolveInfo>();
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            if (!contains(PINNED, pkg) && !pkg.equals(SMARTTUBE) && !isHidden(pkg)) {
                rest.add(app);
            }
        }
        final PackageManager manager = pm;
        Collections.sort(rest, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(manager))
                        .compareToIgnoreCase(String.valueOf(b.loadLabel(manager)));
            }
        });
        for (ResolveInfo app : rest) {
            tiles.add(tile(pm, app, COLORS[next++ % COLORS.length]));
        }
        adapter.notifyDataSetChanged();
    }

    private static ResolveInfo find(List<ResolveInfo> apps, String pkg) {
        for (ResolveInfo app : apps) {
            if (app.activityInfo.packageName.equals(pkg)) {
                return app;
            }
        }
        return null;
    }

    private Intent launch(ResolveInfo app) {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName, app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private Tile tile(PackageManager pm, ResolveInfo app, int color) {
        return new Tile(String.valueOf(app.loadLabel(pm)), "", app.loadIcon(pm), launch(app), color, false);
    }

    private static boolean isHidden(String pkg) {
        String lower = pkg.toLowerCase(Locale.US);
        return contains(HIDDEN, pkg) || lower.contains("factory") || lower.contains("test");
    }

    private static boolean contains(String[] list, String value) {
        for (String item : list) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void open(final Tile tile) {
        if (tile.needsStick) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                    .setTitle(tile.label)
                    .setMessage(tile.label + " can't run on this TV's Android 4.4.\n"
                            + "Plug in a streaming stick, then pick its HDMI input.")
                    .setNegativeButton("Back", null);
            if (tile.intent != null) {
                dialog.setPositiveButton("Open TV Inputs", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        start(tile);
                    }
                });
            }
            dialog.show();
            return;
        }
        start(tile);
    }

    private void start(Tile tile) {
        try {
            startActivity(tile.intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, tile.label + " could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void select(View view, int position) {
        if (position < 0 || position >= tiles.size()) {
            return;
        }
        selectedPosition = position;
        Tile tile = tiles.get(position);
        title.setText(tile.label);
        subtitle.setText(tile.about);
        fadeGlow(tile.color);
        if (focused != null && focused != view) {
            focused.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            ((GradientDrawable) focused.getBackground()).setStroke(0, RING);
        }
        if (view != null) {
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
            ((GradientDrawable) view.getBackground()).setStroke(px(3), RING);
        }
        focused = view;
    }

    private void fadeGlow(int target) {
        if (glowAnimator != null) {
            glowAnimator.cancel();
        }
        glowAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), glow, target);
        glowAnimator.setDuration(260);
        glowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                glow = (Integer) animation.getAnimatedValue();
                backdrop.setColors(new int[] {glow, 0xFF08080C, Color.BLACK});
            }
        });
        glowAnimator.start();
    }

    private void showTime() {
        Date now = new Date();
        String time = DateFormat.getTimeFormat(this).format(now);
        clock.setText(time);
        idleClock.setText(time);
        date.setText(DateFormat.format("EEEE, MMMM d", now));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? "Up late" : hour < 12 ? "Good morning"
                : hour < 18 ? "Good afternoon" : "Good evening");
    }

    private TextView text(float sp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        return view;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private final class TileAdapter extends BaseAdapter {
        @Override public int getCount() {
            return tiles.size();
        }

        @Override public Object getItem(int position) {
            return tiles.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View old, ViewGroup parent) {
            Tile tile = tiles.get(position);
            FrameLayout box = new FrameLayout(MainActivity.this);
            box.setLayoutParams(new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, px(128)));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {tile.color, shade(tile.color)});
            bg.setCornerRadius(px(16));
            box.setBackground(bg);

            LinearLayout column = new LinearLayout(MainActivity.this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            if (tile.icon != null) {
                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageDrawable(tile.icon);
                column.addView(icon, new LinearLayout.LayoutParams(px(60), px(60)));
            }
            TextView label = text(tile.icon == null ? 26 : 17, Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setText(tile.label);
            if (tile.icon == null) {
                label.setTypeface(Typeface.DEFAULT_BOLD);
            }
            label.setPadding(px(8), px(4), px(8), 0);
            column.addView(label);
            box.addView(column, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return box;
        }

        private int shade(int color) {
            return Color.rgb(Color.red(color) / 2, Color.green(color) / 2, Color.blue(color) / 2);
        }
    }
}
