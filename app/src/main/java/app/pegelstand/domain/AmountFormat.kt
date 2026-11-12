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

    /** Widget/app amount line: `1,25 / 2 l`. */
    fun totalOfGoal(totalMl: Int, goalMl: Int): String = "${liters(totalMl)} / ${liters(goalMl)} l"
}
