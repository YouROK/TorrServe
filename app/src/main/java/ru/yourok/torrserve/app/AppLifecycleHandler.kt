package ru.yourok.torrserve.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class AppLifecycleHandler : Application.ActivityLifecycleCallbacks {

    var currentActivity: Activity? = null

    override fun onActivityPaused(activity: Activity?) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity?) {
        currentActivity = activity
    }

    override fun onActivityStarted(activity: Activity?) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity?) {}

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}

    override fun onActivityStopped(activity: Activity?) {}

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        currentActivity = activity
    }
}
