package ru.yourok.torrserve.ui.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import ru.yourok.torrserve.ext.commitFragment

abstract class TSFragment : Fragment() {
    var onResult: (suspend (Any?) -> Unit)? = null
    protected lateinit var viewModel: ViewModel

    fun show(activity: FragmentActivity, id: Int, back: Boolean = false) {
        activity.commitFragment {
            replace(id, this@TSFragment)
            addToBackStack(this.toString())
        }
    }
}