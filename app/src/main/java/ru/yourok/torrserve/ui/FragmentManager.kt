package ru.yourok.torrserve.ui

import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.main.MainFragment


class FragmentManager(val activity: AppCompatActivity) {
    private fun clearStack() {
        if (activity.supportFragmentManager.backStackEntryCount > 0)
            activity.supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun setMainFragment() {
        clearStack()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.container, MainFragment.newInstance())
            .commitNow()
    }

    fun setAddFragment() {
        clearStack()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.container, MainFragment.newInstance())
            .commitNow()
    }
}