package ru.yourok.torrserve.ui.fragments

import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.commitFragment
import ru.yourok.torrserve.ext.getLastFragment

abstract class TSFragment : Fragment() {
    var onResult: (suspend (Any?) -> Unit)? = null
    protected lateinit var viewModel: ViewModel

    fun show(activity: FragmentActivity, id: Int, back: Boolean = false) {
        if (activity.getLastFragment()?.javaClass?.name == this.javaClass.name)
            return
        activity.commitFragment {
            replace(id, this@TSFragment)
            if (back)
                addToBackStack(this.toString())
        }
    }

    suspend fun showProgress() = withContext(Dispatchers.Main) {
        if (activity != null && isActive)
            activity?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
    }

    suspend fun hideProgress() = withContext(Dispatchers.Main) {
        if (activity != null && isActive)
            activity?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
    }
}