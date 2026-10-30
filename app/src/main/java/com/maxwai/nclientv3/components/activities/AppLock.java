package com.maxwai.nclientv3.components.activities;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.PINActivity;
import com.maxwai.nclientv3.R;

/**
 * Custom (fork only) fix: the lock screen used to be shown only when starting the app from its launcher
 * icon. Deep links (https://nhentai.net/...) open the exported activities directly, without it.
 * <p>
 * Every {@link GeneralActivity} now asks this class on create/start. If a lock is configured and the app
 * isn't unlocked (cold start, or in the background for more than {@link #GRACE_MS}), the lock screen is
 * shown on top of the activity.
 */
public final class AppLock {
    public static final String EXTRA_OVERLAY = "com.maxwai.nclientv3.LOCK_OVERLAY";
    private static final long GRACE_MS = 30_000;

    private static boolean unlocked = false;
    private static boolean prompting = false;
    private static int startedActivities = 0;
    private static long lastStop = 0;

    private AppLock() {
    }

    private static boolean hasLock(@NonNull Context context) {
        return context.getSharedPreferences("Settings", 0)
            .getBoolean(context.getString(R.string.preference_key_has_credentials), false);
    }

    private static boolean isLocked(@NonNull Context context) {
        if (!hasLock(context)) return false;
        if (!unlocked) return true;
        return startedActivities == 0 && SystemClock.elapsedRealtime() - lastStop > GRACE_MS;
    }

    /**
     * Shows the lock screen over {@code activity} if needed
     */
    static void guard(@NonNull GeneralActivity activity) {
        if (activity instanceof PINActivity || prompting || !isLocked(activity)) return;
        unlocked = false;
        prompting = true;
        activity.startActivity(new Intent(activity, PINActivity.class).putExtra(EXTRA_OVERLAY, true));
    }

    static void onStarted(@NonNull GeneralActivity activity) {
        guard(activity);
        startedActivities++;
    }

    static void onStopped() {
        if (--startedActivities <= 0) {
            startedActivities = 0;
            lastStop = SystemClock.elapsedRealtime();
        }
    }

    public static void unlock() {
        unlocked = true;
    }

    public static void promptEnded() {
        prompting = false;
    }
}
