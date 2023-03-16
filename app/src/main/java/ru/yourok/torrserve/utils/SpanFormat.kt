package ru.yourok.torrserve.utils

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import java.util.regex.Pattern

/*
* Copyright © 2014 George T. Steel
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
//https://github.com/george-steel/android-utils/blob/master/src/org/oshkimaadziig/george/androidutils/SpanFormatter.java
/**
 * Provides [String.format] style functions that work with [Spanned] strings and preserve formatting.
 *
 * @author George T. Steel
 */
object SpanFormat {
    private val FORMAT_SEQUENCE: Pattern = Pattern.compile("%([0-9]+\\$|<?)([^a-zA-z%]*)([[a-zA-Z%]&&[^tT]]|[tT][a-zA-Z])")

    /**
     * Version of [String.format] that works on [Spanned] strings to preserve rich text formatting.
     * Both the `format` as well as any `%s args` can be Spanned and will have their formatting preserved.
     * Due to the way [android.text.Spannable]s work, any argument's spans will can only be included **once** in the result.
     * Any duplicates will appear as text only.
     *
     * @param format the format string (see [java.util.Formatter.format])
     * @param args
     * the list of arguments passed to the formatter. If there are
     * more arguments than required by `format`,
     * additional arguments are ignored.
     * @return the formatted string (with spans).
     */
    fun format(format: CharSequence?, vararg args: Any?): SpannedString {
        return format(java.util.Locale.getDefault(), format, *args)
    }

    /**
     * Version of [String.format] that works on [Spanned] strings to preserve rich text formatting.
     * Both the `format` as well as any `%s args` can be Spanned and will have their formatting preserved.
     * Due to the way [android.text.Spannable]s work, any argument's spans will can only be included **once** in the result.
     * Any duplicates will appear as text only.
     *
     * @param locale
     * the locale to apply; `null` value means no localization.
     * @param format the format string (see [java.util.Formatter.format])
     * @param args
     * the list of arguments passed to the formatter.
     * @return the formatted string (with spans).
     * @see String.format
     */
    fun format(locale: java.util.Locale, format: CharSequence?, vararg args: Any?): SpannedString {
        val out = SpannableStringBuilder(format)
        var i = 0
        var argAt: Int = -1
        while (i < out.length) {
            val m: java.util.regex.Matcher = FORMAT_SEQUENCE.matcher(out)
            if (!m.find(i))
                break
            i = m.start()
            val exprEnd: Int = m.end()
            val argTerm: String? = m.group(1)
            val modTerm: String? = m.group(2)
            val typeTerm: String? = m.group(3)
            var cookedArg: CharSequence
            when (typeTerm) {
                "%" -> cookedArg = "%"
                "n" -> cookedArg = "\n"
                else -> {
                    val argIdx: Int = when (argTerm) {
                        "" -> ++argAt
                        "<" -> argAt
                        else -> argTerm!!.substring(0, argTerm.length - 1).toInt() - 1
                    }
                    val argItem: Any? = args[argIdx]
                    cookedArg = if ((typeTerm == "s") && argItem is Spanned) {
                        argItem
                    } else {
                        String.format(locale, "%$modTerm$typeTerm", argItem)
                    }
                }
            }
            out.replace(i, exprEnd, cookedArg)
            i += cookedArg.length
        }
        return SpannedString(out)
    }
}