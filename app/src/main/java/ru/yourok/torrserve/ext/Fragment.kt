package ru.yourok.torrserve.ext

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction

fun FragmentActivity.commitFragment(block: FragmentTransaction.() -> Unit) {
    val transact = supportFragmentManager.beginTransaction()
        .setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
    block(transact)
    // experimental, to avoid frequent FCs in TSFragment
    transact.commitAllowingStateLoss() //transact.commit()
}

fun FragmentActivity.getLastFragment(): Fragment? {
    if (supportFragmentManager.backStackEntryCount > 0)
        return supportFragmentManager.fragments.last()
    return null
}

fun Fragment.getLastFragment() = requireActivity().getLastFragment()

fun FragmentActivity.clearStackFragment() {
    if (supportFragmentManager.backStackEntryCount > 0)
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
}

fun Fragment.clearStackFragment() = requireActivity().clearStackFragment()

fun FragmentActivity.popBackStackFragment() {
    if (supportFragmentManager.backStackEntryCount > 0)
        supportFragmentManager.popBackStack()
}

fun Fragment.popBackStackFragment() = requireActivity().popBackStackFragment()