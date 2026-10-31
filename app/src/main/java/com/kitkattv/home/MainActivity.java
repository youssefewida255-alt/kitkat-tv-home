package com.kitkattv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A small Home screen for old MStar Android 4.4 TVs: one grid, no network, nothing in the background. */
public class MainActivity extends Activity {
    private static final int COLUMNS = 5;
    private static final String TV_PLAYER = "com.mstar.tv.tvplayer.ui";
    private static final String INPUT_LIST =
            TV_PLAYER + ".intent.action.InputSourceListViewActivity";
    /** Never offered: opening a factory or test app can change the TV's factory state. */
    private static final String[] HIDDEN = {
            "com.kitkattv.home", "com.assem.launcher", "com.android.launcher",
            "com.ctv.factorytest", "mstar.factorymenu.ui", "tvtests.com.mstar.android.tv",
            "com.android.wididemo", "com.android.mcastdemo", "com.ctv.app", TV_PLAYER,
    };
    /** Shown first, in this order, when installed. */
    private static final String[] PINNED = {
            "org.smarttube.stable", "com.cultraview.mediacenter", "com.android.vending",
            "com.android.settings", "com.cultraview.filemanager",
    };
    private static final int[] COLORS = {
            0xFF8E2433, 0xFF1F6F8B, 0xFF2E7D4F, 0xFF6A4BA3, 0xFF9A6516, 0xFF35506E,
    };

    private final List<Tile> tiles = new ArrayList<Tile>();
    private TextView clock;
    private TextView date;
    private GridView grid;
    private final BaseAdapter adapter = new TileAdapter();

    private final BroadcastReceiver ticker = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            showTime();
        }
    };

    private static final class Tile {
        final String label;
        final Drawable icon;
        final Intent intent;

        Tile(String label, Drawable icon, Intent intent) {
            this.label = label;
            this.icon = icon;
            this.intent = intent;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(px(48), px(28), px(48), px(20));

        clock = text(46, Color.WHITE);
        date = text(18, 0xFFAAAAAA);
        root.addView(clock);
        root.addView(date);

        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setVerticalSpacing(px(16));
        grid.setHorizontalSpacing(px(16));
        grid.setPadding(px(6), px(6), px(6), px(6));
        grid.setClipToPadding(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setDrawSelectorOnTop(true);
        grid.setSelector(ring());
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                open(tiles.get(position));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = px(24);
        root.addView(grid, lp);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        loadTiles();
        showTime();
        registerReceiver(ticker, new IntentFilter(Intent.ACTION_TIME_TICK));
        grid.requestFocus();
    }

    @Override protected void onPause() {
        unregisterReceiver(ticker);
        super.onPause();
    }

    /** Home has nowhere to go back to. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event);
    }

    private void loadTiles() {
        tiles.clear();
        PackageManager pm = getPackageManager();
        Intent inputs = new Intent(INPUT_LIST);
        if (pm.resolveActivity(inputs, 0) != null) {
            tiles.add(new Tile("TV Inputs", null, inputs));
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<Tile> rest = new ArrayList<Tile>();
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        for (String pin : PINNED) {
            for (ResolveInfo app : apps) {
                if (app.activityInfo.packageName.equals(pin)) {
                    tiles.add(tile(pm, app));
                    break;
                }
            }
        }
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            if (!contains(PINNED, pkg) && !isHidden(pkg)) {
                rest.add(tile(pm, app));
            }
        }
        Collections.sort(rest, new Comparator<Tile>() {
            @Override public int compare(Tile a, Tile b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        tiles.addAll(rest);
        adapter.notifyDataSetChanged();
    }

    private Tile tile(PackageManager pm, ResolveInfo app) {
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName, app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        String label = app.activityInfo.packageName.equals("org.smarttube.stable")
                ? "YouTube" : String.valueOf(app.loadLabel(pm));
        return new Tile(label, app.loadIcon(pm), launch);
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

    private void open(Tile tile) {
        try {
            startActivity(tile.intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, tile.label + " could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTime() {
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(DateFormat.format("EEEE, MMMM d", now));
    }

    private TextView text(float sp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        return view;
    }

    private GradientDrawable ring() {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(14));
        ring.setColor(0x22FFFFFF);
        ring.setStroke(px(4), Color.WHITE);
        return ring;
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
                    ViewGroup.LayoutParams.MATCH_PARENT, px(150)));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(14));
            bg.setColor(COLORS[position % COLORS.length]);
            box.setBackground(bg);

            LinearLayout column = new LinearLayout(MainActivity.this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            if (tile.icon != null) {
                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageDrawable(tile.icon);
                column.addView(icon, new LinearLayout.LayoutParams(px(72), px(72)));
            }
            TextView label = text(19, Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setText(tile.label);
            label.setPadding(px(8), px(8), px(8), 0);
            column.addView(label);
            box.addView(column, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return box;
        }
    }
}
