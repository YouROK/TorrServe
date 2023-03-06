package ru.yourok.torrserve.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class ActivityCallbacks : Application.ActivityLifecycleCallbacks {

    var currentActivity: Activity? = null
    var numStarted = 0

    override fun onActivityPaused(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        if (numStarted == 0) {
            //app went to foreground
        }
        numStarted++
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
        numStarted--
        if (numStarted == 0) {
            // app went to background
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
    }

}