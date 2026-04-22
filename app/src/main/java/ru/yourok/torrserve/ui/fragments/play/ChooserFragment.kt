package ru.yourok.torrserve.ui.fragments.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.commitFragment
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.Play
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.TSFragment


class ChooserFragment : TSFragment() {

    fun show(activity: PlayActivity, forceChoose: Boolean, onChoose: (Int) -> Unit) {
        val saveChoose = Settings.getChooserAction()
        if (!forceChoose && saveChoose in 1..3) {
            onChoose(saveChoose)
            return
        }

        this.onResult = {
            val action = (it as Int)
            if (action in 1..3)
                onChoose(action)

            activity.commitFragment {
                detach(this@ChooserFragment)
            }
        }
        show(activity, R.id.info_container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.chooser_fragment, container, false)
        TorrService.start()
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            findViewById<CardView>(R.id.cvPlay)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(1)
                    saveAction(1)
                }
            }
            findViewById<CardView>(R.id.cvAddPlay)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(2)
                    saveAction(2)
                }
            }
            findViewById<CardView>(R.id.cvAdd)?.setOnClickListener {
                lifecycleScope.launch {
                    onResult?.invoke(3)
                    saveAction(3)
                }
            }
            findViewById<CardView>(R.id.cvPlayer)?.setOnClickListener {
                lifecycleScope.launch {
                    val list = Players.getList()
                    val titles = list.map { it.second }.toTypedArray()

                    AlertDialog.Builder(view.context)
                        .setTitle(getString(R.string.select_player))
                        .setItems(titles) { _, which ->
                            val selectedPackage = list[which].first
                            Play.tmpPlayerPackage = selectedPackage
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun saveAction(action: Int) {
        view?.findViewById<SwitchMaterial>(R.id.cbSaveChooseAction)?.apply {
            if (isChecked)
                lifecycleScope.launch {
                    Settings.setChooserAction(action)
                }
        }
    }
}