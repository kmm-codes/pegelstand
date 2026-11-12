package app.pegelstand.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** German litre formatting: decimal comma, at most two decimals, trailing zeros dropped. */
object AmountFormat {
    /** 1250 -> "1,25", 2000 -> "2", 2500 -> "2,5", 0 -> "0". */
    fun liters(ml: Int): String {
        val value = BigDecimal.valueOf(ml.toLong(), 3).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return value.toPlainString().replace('.', ',')
    }

    /** An amount with its unit: 1750 -> `1,75 L`. Capital L: in a bold font a lower-case l reads as a bar. */
    fun litersWithUnit(ml: Int): String = "${liters(ml)} $UNIT"

    /** Widget/app amount line: `1,25 / 2 L`. */
    fun totalOfGoal(totalMl: Int, goalMl: Int): String = "${liters(totalMl)} ${ofGoal(goalMl)}"

    /** The goal part of the amount line on its own: `/ 2 L` (the narrow widget's second line). */
    fun ofGoal(goalMl: Int): String = "/ ${litersWithUnit(goalMl)}"

    const val UNIT = "L"
}
