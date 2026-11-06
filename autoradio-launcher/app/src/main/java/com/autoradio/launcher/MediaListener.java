package com.autoradio.launcher;

import android.service.notification.NotificationListenerService;

/**
 * Empty notification listener. Android only lets an app read the "now playing"
 * media sessions (title, artist, cover) if the user enabled notification access
 * for it; this service is what appears in that settings screen. It does nothing else.
 */
public class MediaListener extends NotificationListenerService {
}
