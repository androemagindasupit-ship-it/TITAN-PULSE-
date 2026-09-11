package com.titanpulse.app;

import android.content.Context;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class CrashReporter {
    private CrashReporter() {}
    public static void install(Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File f = new File(context.getFilesDir(), "last_crash.txt");
                String msg = "thread=" + thread.getName() + "\ndevice=" + Build.MANUFACTURER + " " + Build.MODEL + "\napi=" + Build.VERSION.SDK_INT + "\n" + throwable;
                try (FileOutputStream out = new FileOutputStream(f, false)) { out.write(msg.getBytes(StandardCharsets.UTF_8)); }
                if (!FirebaseApp.getApps(context).isEmpty()) FirebaseCrashlytics.getInstance().recordException(throwable);
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }
}
