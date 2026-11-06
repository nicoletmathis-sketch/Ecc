package com.autoradio.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CarPlay-style home screen for an Android head unit.
 *
 * Page 1 (dashboard): greeting + clock + weather + the car, now playing, Waze
 * Home/Work shortcuts, GPS speed and a big CarPlay button. Page 2: app grid.
 * Swipe left/right or use the bottom sidebar button to switch.
 *
 * No library, no background service: when another app is in front, location
 * and progress updates are stopped and this activity costs almost nothing.
 */
public class MainActivity extends Activity implements MediaCard.Host, SpeedCard.Host {

    private static final String PREFS = "slots";
    private static final String KEY_CAR_NAME = "car_name";
    private static final String KEY_ASKED_LOCATION = "asked_location";
    private static final String CAR_PHOTO_FILE = "car_photo.jpg";
    private static final int REQUEST_PHOTO = 1;
    private static final int REQUEST_LOCATION = 2;
    private static final int PHOTO_MAX_SIDE = 1024;

    /** An app slot: user choice first, then well-known packages, then a system intent. */
    private static final class Slot {
        final String key;
        final int labelRes;
        final String[] packages;
        final Intent fallback;

        Slot(String key, int labelRes, String[] packages, Intent fallback) {
            this.key = key;
            this.labelRes = labelRes;
            this.packages = packages;
            this.fallback = fallback;
        }
    }

    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private MediaCard media;
    private WeatherCard weather;
    private SpeedCard speed;
    private ImageView heroImage;
    private TextView carName;
    private TextView greeting;
    private TextView net;
    private View pageDash;
    private View pageGrid;
    private ImageView switchButton;
    private LinearLayout dots;
    private int page;
    private GestureDetector swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        defineSlots();

        View root = findViewById(R.id.root);
        net = (TextView) findViewById(R.id.net);
        greeting = (TextView) findViewById(R.id.greeting);
        pageDash = findViewById(R.id.page_dash);
        pageGrid = findViewById(R.id.page_grid);
        dots = (LinearLayout) findViewById(R.id.dots);

        setupSidebar();
        setupHero();
        setupFavorites();
        setupCarPlayCard();
        buildGrid();

        media = new MediaCard(this, root, this);
        weather = new WeatherCard(this, root, prefs);
        speed = new SpeedCard(this, root, this);
        findViewById(R.id.speed_card).setBackground(Ui.card(this));
        findViewById(R.id.speed_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!SpeedCard.hasPermission(MainActivity.this)) {
                    askLocation();
                }
            }
        });
        findViewById(R.id.media_card).setBackground(Ui.card(this));

        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Ui.dp(MainActivity.this, 80) && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                    showPage(dx < 0 ? 1 : 0);
                    return true;
                }
                return false;
            }
        });
        showPage(0);

        if (!SpeedCard.hasPermission(this) && !prefs.getBoolean(KEY_ASKED_LOCATION, false)) {
            prefs.edit().putBoolean(KEY_ASKED_LOCATION, true).apply();
            askLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        greeting.setText(greetingRes());
        net.setText(networkLabel());
        media.start();
        speed.start();
    }

    @Override
    protected void onPause() {
        media.stop();
        speed.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        media.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Home screen: back returns to the dashboard, never leaves.
        showPage(0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while already home: back to the dashboard
        showPage(0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        swipe.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---- hosts for the cards --------------------------------------------------

    @Override
    public void openMusicApp() {
        open(slots.get("spotify"));
    }

    @Override
    public void onLocation(Location location) {
        weather.maybeRefresh(location);
    }

    // ---- slots ----------------------------------------------------------------

    private void defineSlots() {
        add(new Slot("waze", R.string.tile_waze, new String[] {"com.waze"},
            new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))));
        add(new Slot("spotify", R.string.tile_spotify, new String[] {"com.spotify.music", "com.spotify.lite"},
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)));
        add(new Slot("phone", R.string.tile_phone, new String[] {
            "com.syu.bt", "com.microntek.bluetooth", "com.ts.bluetooth",
            "com.google.android.dialer", "com.android.dialer"}, new Intent(Intent.ACTION_DIAL)));
        add(new Slot("messages", R.string.tile_messages, new String[] {
            "com.google.android.apps.messaging", "com.android.mms", "com.syu.bt", "com.microntek.bluetooth"},
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)));
        add(new Slot("carplay", R.string.tile_carplay,
            new String[] {"com.zjinnova.zlink", "cn.manstep.phonemirrorBox"}, null));
        add(new Slot("radio", R.string.tile_radio, new String[] {
            "com.syu.radio", "com.microntek.radio", "com.ts.radio", "com.android.fmradio", "com.caf.fmradio"}, null));
        add(new Slot("bluetooth", R.string.tile_bluetooth, new String[] {
            "com.syu.bt", "com.microntek.bluetooth", "com.ts.bluetooth"},
            new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        add(new Slot("files", R.string.tile_files, new String[] {
            "com.android.documentsui", "com.google.android.documentsui", "com.mediatek.filemanager",
            "com.android.fileexplorer", "com.estrongs.android.pop", "com.syu.filemanager"}, null));
        add(new Slot("settings", R.string.tile_settings, new String[0], new Intent(Settings.ACTION_SETTINGS)));
    }

    private void add(Slot s) {
        slots.put(s.key, s);
    }

    private void open(Slot slot) {
        Intent intent = resolve(slot);
        if (intent == null || !start(intent)) {
            Toast.makeText(this, getString(R.string.not_found, getString(slot.labelRes)), Toast.LENGTH_SHORT).show();
            pickApp(slot);
        }
    }

    private Intent resolve(Slot slot) {
        PackageManager pm = getPackageManager();
        String chosen = prefs.getString(slot.key, null);
        if (chosen != null) {
            Intent i = pm.getLaunchIntentForPackage(chosen);
            if (i != null) {
                return i;
            }
        }
        for (String pkg : slot.packages) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                return i;
            }
        }
        if (slot.fallback != null && slot.fallback.resolveActivity(pm) != null) {
            return slot.fallback;
        }
        return null;
    }

    private boolean start(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Short press opens the slot, long press lets the user pick the app. */
    private void bindSlot(View v, final String key) {
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(slots.get(key));
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                pickApp(slots.get(key));
                return true;
            }
        });
    }

    // ---- sidebar & pages ------------------------------------------------------

    private void setupSidebar() {
        sideIcon(R.id.side_waze, Ui.BLUE, "waze");
        sideIcon(R.id.side_spotify, Ui.RED, "spotify");
        sideIcon(R.id.side_phone, Ui.GREEN, "phone");
        sideIcon(R.id.side_messages, Ui.GREEN, "messages");
        switchButton = (ImageView) findViewById(R.id.side_switch);
        switchButton.setBackground(Ui.icon(this, Ui.GRAPHITE, sideSizeDp()));
        pad(switchButton, 0.24f);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(page == 0 ? 1 : 0);
            }
        });
        findViewById(R.id.clock).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                start(new Intent(Settings.ACTION_SETTINGS));
                return true;
            }
        });
    }

    private void sideIcon(int id, int[] colors, String slot) {
        ImageView v = (ImageView) findViewById(id);
        v.setBackground(Ui.icon(this, colors, sideSizeDp()));
        pad(v, 0.21f);
        bindSlot(v, slot);
    }

    private float sideSizeDp() {
        return getResources().getDimension(R.dimen.side_icon) / getResources().getDisplayMetrics().density;
    }

    private static void pad(ImageView v, float ratio) {
        int p = Math.round(v.getLayoutParams().width * ratio);
        v.setPadding(p, p, p, p);
    }

    private void showPage(int p) {
        page = p;
        pageDash.setVisibility(p == 0 ? View.VISIBLE : View.GONE);
        pageGrid.setVisibility(p == 1 ? View.VISIBLE : View.GONE);
        switchButton.setImageResource(p == 0 ? R.drawable.ic_grid : R.drawable.ic_dash);
        dots.removeAllViews();
        dots.setVisibility(p == 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 2; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 7), Ui.dp(this, 7));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            d.setLayoutParams(lp);
            d.setBackground(Ui.dot(this, i == p));
            dots.addView(d);
        }
    }

    private int greetingRes() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) {
            return R.string.hello_morning;
        }
        return h >= 12 && h < 18 ? R.string.hello_afternoon : R.string.hello_evening;
    }

    @SuppressWarnings("deprecation")
    private String networkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            if (ni == null || !ni.isConnected()) {
                return "";
            }
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                return "Wi-Fi";
            }
            String sub = ni.getSubtypeName();
            if ("LTE".equalsIgnoreCase(sub)) {
                return "4G";
            }
            if ("NR".equalsIgnoreCase(sub)) {
                return "5G";
            }
            return sub != null ? sub : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- dashboard cards ------------------------------------------------------

    private void setupHero() {
        final View hero = findViewById(R.id.hero);
        hero.post(new Runnable() {
            @Override
            public void run() {
                hero.setBackground(Ui.heroCard(MainActivity.this, hero.getWidth()));
            }
        });
        hero.setClipToOutline(true);
        hero.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showCarOptions();
                return true;
            }
        });
        heroImage = (ImageView) findViewById(R.id.hero_image);
        carName = (TextView) findViewById(R.id.car_name);
        carName.setText(prefs.getString(KEY_CAR_NAME, getString(R.string.default_car_name)));
        loadCarPhoto();
        findViewById(R.id.badge_plk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.plk_search)));
                if (search.resolveActivity(getPackageManager()) == null || !start(search)) {
                    open(slots.get("spotify"));
                }
            }
        });
        findViewById(R.id.weather).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!SpeedCard.hasPermission(MainActivity.this)) {
                    askLocation();
                }
            }
        });
    }

    private void setupFavorites() {
        setupFavorite(R.id.fav_home, "addr_home", R.string.home, R.drawable.ic_home);
        setupFavorite(R.id.fav_work, "addr_work", R.string.work, R.drawable.ic_work);
    }

    private void setupFavorite(int id, final String key, final int labelRes, int iconRes) {
        final View card = findViewById(id);
        card.setBackground(Ui.card(this));
        ImageView icon = (ImageView) card.findViewById(R.id.mini_icon);
        icon.setImageResource(iconRes);
        icon.setBackground(Ui.icon(this, Ui.GRAPHITE,
            getResources().getDimension(R.dimen.mini_icon) / getResources().getDisplayMetrics().density));
        ((TextView) card.findViewById(R.id.mini_title)).setText(labelRes);
        final TextView sub = (TextView) card.findViewById(R.id.mini_subtitle);
        refreshFavorite(sub, key);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addr = prefs.getString(key, "");
                if (addr.isEmpty()) {
                    editAddress(key, labelRes, sub);
                } else {
                    navigateTo(addr);
                }
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                editAddress(key, labelRes, sub);
                return true;
            }
        });
    }

    private void refreshFavorite(TextView sub, String key) {
        String addr = prefs.getString(key, "");
        sub.setText(addr.isEmpty() ? getString(R.string.set_address) : getString(R.string.via_waze));
    }

    private void editAddress(final String key, int labelRes, final TextView sub) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        input.setSingleLine(true);
        input.setHint(R.string.address_hint);
        input.setText(prefs.getString(key, ""));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.address_title, getString(labelRes)))
            .setView(input)
            .setPositiveButton(R.string.ok, (d, w) -> {
                prefs.edit().putString(key, input.getText().toString().trim()).apply();
                refreshFavorite(sub, key);
            })
            .setNeutralButton(R.string.clear, (d, w) -> {
                prefs.edit().remove(key).apply();
                refreshFavorite(sub, key);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /** Waze deep link: starts navigation straight away (falls back to any maps app). */
    private void navigateTo(String address) {
        String q;
        try {
            q = URLEncoder.encode(address, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            q = address;
        }
        Intent waze = new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + q + "&navigate=yes"));
        if (getPackageManager().getLaunchIntentForPackage("com.waze") != null) {
            waze.setPackage("com.waze");
        }
        if (!start(waze) && !start(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + q)))) {
            open(slots.get("waze"));
        }
    }

    private void setupCarPlayCard() {
        View card = findViewById(R.id.carplay_card);
        card.setBackground(Ui.card(this));
        findViewById(R.id.carplay_icon).setBackground(Ui.icon(this, Ui.DARK, 52));
        TextView button = (TextView) findViewById(R.id.carplay_button);
        button.setBackground(Ui.pill(this, Ui.SYSTEM_BLUE));
        bindSlot(card, "carplay");
        bindSlot(button, "carplay");
    }

    // ---- app grid -------------------------------------------------------------

    private void buildGrid() {
        LinearLayout grid = (LinearLayout) findViewById(R.id.grid);
        Object[][] items = {
            {"waze", R.string.tile_waze, R.drawable.ic_nav, Ui.BLUE},
            {"spotify", R.string.tile_spotify, R.drawable.ic_music, Ui.RED},
            {"phone", R.string.tile_phone, R.drawable.ic_phone, Ui.GREEN},
            {"messages", R.string.tile_messages, R.drawable.ic_messages, Ui.GREEN},
            {"carplay", R.string.tile_carplay, R.drawable.ic_carplay, Ui.DARK},
            {"radio", R.string.tile_radio, R.drawable.ic_radio, Ui.ORANGE},
            {"bluetooth", R.string.tile_bluetooth, R.drawable.ic_bt, Ui.BT_BLUE},
            {"files", R.string.tile_files, R.drawable.ic_files, Ui.BLUE},
            {"settings", R.string.tile_settings, R.drawable.ic_settings, Ui.GRAY},
            {null, R.string.tile_all, R.drawable.ic_plus, Ui.GRAPHITE},
        };
        float iconDp = getResources().getDimension(R.dimen.grid_icon) / getResources().getDisplayMetrics().density;
        int iconPx = (int) getResources().getDimension(R.dimen.grid_icon);
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageResource((Integer) items[i][2]);
            icon.setBackground(Ui.icon(this, (int[]) items[i][3], iconDp));
            int p = Math.round(iconPx * 0.22f);
            icon.setPadding(p, p, p, p);
            icon.setClickable(true);
            icon.setContentDescription(getString((Integer) items[i][1]));
            cell.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));
            TextView label = new TextView(this);
            label.setText((Integer) items[i][1]);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(0, getResources().getDimension(R.dimen.grid_label));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(this, 8), 0, 0);
            cell.addView(label);
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            String key = (String) items[i][0];
            if (key != null) {
                bindSlot(icon, key);
            } else {
                icon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickApp(null);
                    }
                });
            }
        }
    }

    // ---- permissions ----------------------------------------------------------

    private void askLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION) {
            speed.start();
        }
    }

    // ---- car image ------------------------------------------------------------

    private void showCarOptions() {
        CharSequence[] items = {
            getString(R.string.change_photo), getString(R.string.rename_car), getString(R.string.reset_photo)};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.car_options)
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    try {
                        startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQUEST_PHOTO);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 1) {
                    renameCar();
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    photoFile().delete();
                    loadCarPhoto();
                }
            })
            .show();
    }

    private void renameCar() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setText(carName.getText());
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.rename_car)
            .setView(input)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                prefs.edit().putString(KEY_CAR_NAME, name).apply();
                carName.setText(name);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (saveCarPhoto(data.getData())) {
            loadCarPhoto();
        } else {
            Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File photoFile() {
        return new File(getFilesDir(), CAR_PHOTO_FILE);
    }

    /** Copies the chosen image, downscaled, into private storage. */
    private boolean saveCarPhoto(Uri uri) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) {
                return false;
            }
            try (OutputStream out = new FileOutputStream(photoFile())) {
                return bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
            }
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return false;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private void loadCarPhoto() {
        Bitmap bitmap = null;
        File file = photoFile();
        if (file.exists()) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
            } catch (OutOfMemoryError e) {
                bitmap = null;
            }
        }
        if (bitmap == null) {
            // Bundled car + figurine scene (has transparency, so full 32-bit, ~2 MB)
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            try {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hero_scene, opts);
            } catch (OutOfMemoryError e) {
                bitmap = null;
            }
        }
        heroImage.setImageBitmap(bitmap);
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width, height) / sample > PHOTO_MAX_SIDE) {
            sample *= 2;
        }
        return sample;
    }

    // ---- app picker -----------------------------------------------------------

    /** With a slot: assigns the chosen app to it. Without: opens the chosen app. */
    private void pickApp(final Slot slot) {
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        final List<String[]> rows = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) {
                rows.add(new String[] {String.valueOf(ri.loadLabel(pm)), pkg});
            }
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(rows, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return collator.compare(a[0], b[0]);
            }
        });
        CharSequence[] labels = new CharSequence[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            labels[i] = rows.get(i)[0];
        }
        String title = slot == null ? getString(R.string.pick_app)
            : getString(R.string.pick_app_for, getString(slot.labelRes));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(labels, (dialog, which) -> {
                String pkg = rows.get(which)[1];
                if (slot != null) {
                    prefs.edit().putString(slot.key, pkg).apply();
                    Toast.makeText(this, getString(R.string.assigned, getString(slot.labelRes), rows.get(which)[0]),
                        Toast.LENGTH_SHORT).show();
                } else {
                    Intent i = pm.getLaunchIntentForPackage(pkg);
                    if (i != null) {
                        start(i);
                    }
                }
            })
            .show();
    }
}
