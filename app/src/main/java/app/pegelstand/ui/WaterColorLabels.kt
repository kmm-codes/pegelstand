package app.pegelstand.ui

import androidx.annotation.StringRes
import app.pegelstand.R
import app.pegelstand.domain.WaterColor

/** German label of a water colour preset. */
@get:StringRes
val WaterColor.labelRes: Int
    get() = when (this) {
        WaterColor.BLUE -> R.string.water_color_blue
        WaterColor.TURQUOISE -> R.string.water_color_turquoise
        WaterColor.MINT -> R.string.water_color_mint
        WaterColor.LAVENDER -> R.string.water_color_lavender
        WaterColor.PINK -> R.string.water_color_pink
        WaterColor.CORAL -> R.string.water_color_coral
    }
