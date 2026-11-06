package com.autoradio.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * GPS speedometer + compass heading. Location updates only run while the home
 * screen is visible (start/stop follow onResume/onPause), so nothing drains in
 * the background when CarPlay or Waze is in front.
 */
final class SpeedCard implements LocationListener {

    interface Host {
        void onLocation(Location location);
    }

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    private final Activity activity;
    private final Host host;
    private final LocationManager lm;
    private final TextView value;
    private final TextView heading;
    private boolean running;

    SpeedCard(Activity activity, View root, Host host) {
        this.activity = activity;
        this.host = host;
        lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        value = (TextView) root.findViewById(R.id.speed_value);
        heading = (TextView) root.findViewById(R.id.speed_heading);
    }

    static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 23
            || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    void start() {
        if (lm == null || !hasPermission(activity)) {
            value.setText(R.string.speed_none);
            heading.setText(R.string.gps_needed);
            return;
        }
        if (running) {
            return;
        }
        heading.setText(R.string.gps_waiting);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            running = true;
        } catch (RuntimeException e) {
            heading.setText(R.string.speed_none);
        }
        // Last known fix: lets the weather card work right away
        Location last = lastKnown();
        if (last != null) {
            host.onLocation(last);
        }
    }

    void stop() {
        if (running && lm != null) {
            lm.removeUpdates(this);
        }
        running = false;
    }

    @SuppressLint("MissingPermission")
    private Location lastKnown() {
        Location best = null;
        for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (RuntimeException ignored) {
                // provider missing on this head unit
            }
        }
        return best;
    }

    @Override
    public void onLocationChanged(Location loc) {
        int kmh = loc.hasSpeed() ? Math.round(loc.getSpeed() * 3.6f) : 0;
        value.setText(String.valueOf(kmh));
        if (loc.hasBearing() && kmh >= 5) {
            int i = Math.round(loc.getBearing() / 45f) % 8;
            heading.setText("▲ " + COMPASS[i]);
        } else {
            heading.setText("");
        }
        host.onLocation(loc);
    }

    // Required on Android < 10 (not default methods there)
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        value.setText(R.string.speed_none);
        heading.setText(R.string.gps_waiting);
    }
}
