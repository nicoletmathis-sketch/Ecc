package com.autoradio.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.location.Location;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Local weather from Open-Meteo (free, no account, no key). Refreshed at most
 * every 30 minutes on a short-lived background thread; the last result is cached
 * so the card is filled instantly at start-up, even offline.
 */
final class WeatherCard {

    private static final long MAX_AGE_MS = 30 * 60 * 1000L;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final ImageView icon;
    private final TextView temp;
    private final TextView desc;
    private final TextView range;
    private volatile boolean busy;

    WeatherCard(Activity activity, View root, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        icon = (ImageView) root.findViewById(R.id.wx_icon);
        temp = (TextView) root.findViewById(R.id.wx_temp);
        desc = (TextView) root.findViewById(R.id.wx_desc);
        range = (TextView) root.findViewById(R.id.wx_range);
        showCached();
    }

    void maybeRefresh(final Location loc) {
        if (loc == null || busy) {
            return;
        }
        if (System.currentTimeMillis() - prefs.getLong("wx_time", 0) < MAX_AGE_MS) {
            return;
        }
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] result = fetch(loc.getLatitude(), loc.getLongitude());
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (result != null && !activity.isFinishing()) {
                            prefs.edit()
                                .putLong("wx_time", System.currentTimeMillis())
                                .putInt("wx_temp", result[0])
                                .putInt("wx_code", result[1])
                                .putInt("wx_day", result[2])
                                .putInt("wx_max", result[3])
                                .putInt("wx_min", result[4])
                                .apply();
                            showCached();
                        }
                    }
                });
            }
        }, "weather").start();
    }

    private void showCached() {
        if (!prefs.contains("wx_temp")) {
            return;
        }
        int code = prefs.getInt("wx_code", 0);
        boolean day = prefs.getInt("wx_day", 1) == 1;
        temp.setText(activity.getString(R.string.wx_temp, prefs.getInt("wx_temp", 0)));
        range.setText(activity.getString(R.string.wx_range, prefs.getInt("wx_min", 0), prefs.getInt("wx_max", 0)));
        desc.setText(label(code, day));
        icon.setImageResource(iconFor(code, day));
    }

    /** @return {temp, code, isDay, max, min} or null on any failure. */
    private static int[] fetch(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                lat, lon));
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) {
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) {
                out.write(buf, 0, n);
            }
            in.close();
            JSONObject root = new JSONObject(out.toString("UTF-8"));
            JSONObject cur = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            return new int[] {
                (int) Math.round(cur.getDouble("temperature_2m")),
                cur.getInt("weather_code"),
                cur.optInt("is_day", 1),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
            };
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // WMO weather codes, as used by Open-Meteo
    private static int label(int code, boolean day) {
        if (code == 0) {
            return day ? R.string.wx_clear : R.string.wx_clear_night;
        }
        if (code <= 2) {
            return R.string.wx_partly;
        }
        if (code == 3) {
            return R.string.wx_cloudy;
        }
        if (code == 45 || code == 48) {
            return R.string.wx_fog;
        }
        if (code >= 51 && code <= 57) {
            return R.string.wx_drizzle;
        }
        if (code >= 61 && code <= 67) {
            return R.string.wx_rain;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return R.string.wx_snow;
        }
        if (code >= 80 && code <= 82) {
            return R.string.wx_showers;
        }
        if (code >= 95) {
            return R.string.wx_storm;
        }
        return R.string.wx_cloudy;
    }

    private static int iconFor(int code, boolean day) {
        if (code == 0) {
            return day ? R.drawable.ic_wx_sun : R.drawable.ic_wx_moon;
        }
        if (code <= 2) {
            return day ? R.drawable.ic_wx_partly : R.drawable.ic_wx_moon;
        }
        if (code == 45 || code == 48) {
            return R.drawable.ic_wx_fog;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return R.drawable.ic_wx_rain;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return R.drawable.ic_wx_snow;
        }
        if (code >= 95) {
            return R.drawable.ic_wx_storm;
        }
        return R.drawable.ic_wx_cloud;
    }
}
