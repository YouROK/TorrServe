package ru.yourok.torrserve.ui.fragments

import android.graphics.PorterDuff
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

    suspend fun showProgress(prog: Int = -1) = withContext(Dispatchers.Main) {
        if (activity != null && isActive) {
            val progress = activity?.findViewById<ProgressBar>(R.id.progressBar)
            progress?.progressDrawable?.setColorFilter(
                ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent), PorterDuff.Mode.SRC_IN
            )
            progress?.indeterminateDrawable?.setColorFilter(
                ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent), PorterDuff.Mode.SRC_IN
            )
            progress?.apply {
                visibility = View.VISIBLE
                isIndeterminate = prog < 0
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                    setProgress(prog, true)
                else
                    setProgress(prog)
            }
        }
    }

    suspend fun hideProgress() = withContext(Dispatchers.Main) {
        if (activity != null && isActive)
            activity?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
    }
}