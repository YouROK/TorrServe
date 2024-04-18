package ru.yourok.torrserve.ui.fragments

import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
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
        // hide progress after main menu click
        lifecycleScope.launch { hideProgress() }
    }

    // https://material.io/components/progress-indicators/android
    suspend fun showProgress(prog: Int = -1) = withContext(Dispatchers.Main) {
        if (isActive) {
            val themedContext = ContextThemeWrapper(App.context, ThemeUtil.selectedTheme)
            val color = ThemeUtil.getColorFromAttr(themedContext, R.attr.colorAccent)
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