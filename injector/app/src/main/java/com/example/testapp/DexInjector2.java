package com.example.testapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

public class DexInjector2 {

    private static final Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            int result = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);

            if (result != PackageManager.PERMISSION_GRANTED) {
                showDialog(activity);
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    };

    @Nullable
    public static Throwable inject(Application application) {
        PathClassLoader classLoader = (PathClassLoader) application.getClassLoader();
        application.registerActivityLifecycleCallbacks(callbacks);
        return injectForAndroidNougat(classLoader);
    }

    @Nullable
    private static Throwable injectForAndroidNougat(PathClassLoader classLoader) {
        try {
            Field field = BaseDexClassLoader.class.getDeclaredField("pathList");
            field.setAccessible(true);
            final Object pathList = field.get(classLoader);
            final Method method = classLoader.getClass().getMethod("addDexPath", String.class);
            method.invoke(classLoader, "/sdcard/greencat/delta.dex");

            field = pathList.getClass().getDeclaredField("dexElements");
            field.setAccessible(true);
            final Object[] dexElements = (Object[]) field.get(pathList);
            final Object apk = dexElements[0];
            final Object dex = dexElements[1];
            final Object myArray = Array.newInstance(field.getType().getComponentType(), 2);

            Array.set(myArray, 0, dex);
            Array.set(myArray, 1, apk);
            field.set(pathList, myArray);

            Log.d("xxx", "DEX file successfully loaded");
            return null;

        } catch (Throwable e) {
            Log.e("xxx", "Failed to load DEX file", e);
            return e;
        }
    }

    private static void showDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("GreenCat");
        builder.setMessage("You need to grant external storage permissions for this tool.");
        builder.setCancelable(true);
        builder.setPositiveButton("OK", (dialog, which) -> {});
        builder.create().show();
    }
}
