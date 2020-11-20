package ru.yourok.torrserve.ui.fragments

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.fragments.add.AddFragment
import ru.yourok.torrserve.ui.fragments.main.MainFragment
import ru.yourok.torrserve.ui.fragments.play.InfoFragment

val Fragment.fragManager get() = FragmentManager(this.requireActivity())
val AppCompatActivity.fragManager get() = FragmentManager(this)

class FragmentManager(val activity: FragmentActivity) {
    private fun clearStack() {
        if (activity.supportFragmentManager.backStackEntryCount > 0)
            activity.supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun getStackCount(): Int {
        return activity.supportFragmentManager.backStackEntryCount
    }

    fun popBackStack() {
        if (activity.supportFragmentManager.backStackEntryCount > 0)
            activity.supportFragmentManager.popBackStack()
    }

    private fun commit(block: FragmentTransaction.() -> Unit) {
        val transact = activity.supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        block(transact)
        transact.commit()
    }

////////// Fragments

    ///////////// Main

    fun setMainFragment() {
        clearStack()
        commit {
            replace(R.id.container, MainFragment.newInstance())
        }
    }

    fun setAddFragment() {
        commit {
            replace(R.id.container, AddFragment.newInstance())
            addToBackStack("TorrserveMain")
        }
    }

    ///////////// Play

    fun setInfoFragment(cmd: String) {
        clearStack()
        commit {
            replace(R.id.container, InfoFragment.newInstance())
        }
    }
}