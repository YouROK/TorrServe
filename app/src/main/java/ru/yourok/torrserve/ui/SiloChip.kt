package ru.yourok.torrserve.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import ru.yourok.torrserve.R

/**
 * Универсальная кнопка выбора одного значения из нескольких (chips / segmented button).
 *
 * Правило стиля приложения (см. docs/STYLE.md):
 * - обычная - серая рамка, светлый текст;
 * - выбранная - янтарная подложка, янтарная рамка 2dp, янтарный текст;
 * - фокус - подложка hover и янтарная рамка 3dp (видно с пульта);
 * - минимальная высота 40dp, отступы 16x9dp, радиус 8dp, текст 14sp.
 *
 * Используется в окне раздачи (действия, пользователи), во вкладках файлов,
 * в плашках аккаунтов. Новые переключатели делать только этим компонентом.
 */
class SiloChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        background = ContextCompat.getDrawable(context, R.drawable.silo_choice_bg)
        setTextColor(ContextCompat.getColorStateList(context, R.color.silo_choice_text))
        textSize = 14f
        gravity = Gravity.CENTER
        minHeight = dp(40)
        setPadding(dp(16), dp(9), dp(16), dp(9))
        isFocusable = true
        isClickable = true
    }

    /** Привязка подписи и состояния выбора. */
    fun bind(label: CharSequence, selected: Boolean) {
        text = label
        isSelected = selected
    }

    /** Вариант с чуть меньшими отступами (вкладки типов файлов). */
    fun useCompactPadding() {
        setPadding(dp(18), dp(8), dp(18), dp(8))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
