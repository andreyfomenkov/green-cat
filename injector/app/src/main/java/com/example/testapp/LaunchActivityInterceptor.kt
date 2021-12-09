package com.example.testapp

import android.app.Activity
import android.app.Application
import android.os.Bundle

class LaunchActivityInterceptor(
    private val application: Application,
    private val onStart: (activity: Activity) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        application.unregisterActivityLifecycleCallbacks(this)
        onStart(activity)
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}