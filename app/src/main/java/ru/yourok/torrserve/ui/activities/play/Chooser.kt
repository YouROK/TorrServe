package ru.yourok.torrserve.ui.activities.play

import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.play.ChooserFragment

object Chooser {
    fun show(activity: PlayActivity, onChoose: (Int) -> Unit) {
        val saveChoose = Settings.getChooserAction()
        if (saveChoose in 1..3)
            onChoose(saveChoose)

        val chFrag = ChooserFragment()
        chFrag.onResult = {
            val action = (it as Int)
            if (action in 1..3)
                onChoose(action)
        }
        chFrag.show(activity, R.id.top_container)
    }
}