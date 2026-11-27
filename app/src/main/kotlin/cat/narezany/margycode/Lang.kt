package cat.narezany.margycode

import java.util.Locale

/** The app speaks Russian to those whose phone does, English to everyone else. */
object Lang {
    val ru: Boolean = Locale.getDefault().language in setOf("ru", "uk", "be", "kk")
}

fun tr(ru: String, en: String) = if (Lang.ru) ru else en
