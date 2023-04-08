package ru.yourok.torrserve.ui.fragments

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.commitFragment
import ru.yourok.torrserve.ext.getLastFragment
import ru.yourok.torrserve.utils.ThemeUtil

abstract class TSFragment : Fragment() {
    var onResult: (suspend (Any?) -> Unit)? = null
    protected lateinit var viewModel: ViewModel

    fun show(activity: FragmentActivity?, id: Int, back: Boolean = false) {
        if (activity?.getLastFragment()?.javaClass?.name == this.javaClass.name)
            return
        activity?.commitFragment {
            replace(id, this@TSFragment)
            if (back)
                addToBackStack(this.toString())
        }
    }
    // https://material.io/components/progress-indicators/android
    suspend fun showProgress(prog: Int = -1) = withContext(Dispatchers.Main) {
        if (isActive) {
            val color = ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent)
            activity?.findViewById<LinearProgressIndicator>(R.id.progressBar)?.apply {
                setIndicatorColor(color)
                visibility = View.VISIBLE
                isIndeterminate = prog < 0
                if (!isIndeterminate)
                    setProgressCompat(prog, true)
            }
        }
    }

    suspend fun hideProgress() = withContext(Dispatchers.Main) {
        if (isActive)
            activity?.findViewById<LinearProgressIndicator>(R.id.progressBar)?.apply {
                visibility = View.GONE
                isIndeterminate = true
            }
    }
}