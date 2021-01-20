package ru.yourok.torrserve.utils

import android.content.Context
import android.content.pm.PackageManager


fun Context.isOnTV(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}