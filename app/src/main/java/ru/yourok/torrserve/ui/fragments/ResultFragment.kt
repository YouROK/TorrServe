package ru.yourok.torrserve.ui.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import ru.yourok.torrserve.ext.commitFragment

abstract class ResultFragment : Fragment() {

    protected var onResult: (suspend (Any?) -> Unit)? = null
    protected lateinit var viewModel: ViewModel

    @JvmName("setOnResult1")
    fun setOnResult(onResult: suspend (Any?) -> Unit) {
        this.onResult = onResult
    }

    fun show(activity: FragmentActivity, id: Int) {
        activity.commitFragment {
            replace(id, this@ResultFragment)
        }
    }
}