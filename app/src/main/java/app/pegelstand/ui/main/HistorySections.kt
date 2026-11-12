package app.pegelstand.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pegelstand.R
import app.pegelstand.domain.AmountFormat
import app.pegelstand.domain.Streak
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.common.SoftCard
import app.pegelstand.ui.history.DayCell
import app.pegelstand.ui.history.History
import app.pegelstand.ui.theme.LocalPegelExtras
import app.pegelstand.ui.theme.PegelExtras
import app.pegelstand.ui.theme.deepColor
import app.pegelstand.ui.theme.lightColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** The app is German only, independent of the device language. */
private val German: Locale = Locale.GERMAN

@Composable
fun rememberGermanFormatter(patternRes: Int): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern, German) }
}

// ---------------------------------------------------------------------------------------------
// Streak

@Composable
fun StatsSection(streak: Streak, averageMl: Int?, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, contentPadding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Stat(
                value = streak.current.toString(),
                unit = pluralStringResource(R.plurals.stats_days, streak.current),
                label = stringResource(R.string.stats_streak),
                highlight = streak.current > 0,
                modifier = Modifier.weight(1f),
            )
            Stat(
                value = streak.longest.toString(),
                unit = pluralStringResource(R.plurals.stats_days, streak.longest),
                label = stringResource(R.string.stats_longest),
                highlight = false,
                modifier = Modifier.weight(1f),
            )
            Stat(
                value = averageMl?.let(AmountFormat::liters) ?: stringResource(R.string.stats_none),
                unit = if (averageMl != null) AmountFormat.UNIT else null,
                label = stringResource(R.string.stats_average),
                highlight = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Stat(value: String, unit: String?, label: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val extras = LocalPegelExtras.current
    val valueColor = if (highlight) extras.goldText else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = valueColor)) { append(value) }
                if (unit != null) {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Medium,
                        ),
                    ) { append(" $unit") }
                }
            },
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Woche

@Composable
fun WeekSection(week: List<DayCell>, goalMl: Int, waterColor: WaterColor, modifier: Modifier = Modifier) {
    val weekdays = stringArrayResource(R.array.weekdays_short)
    val dayFormat = rememberGermanFormatter(R.string.pattern_range_day)
    val dayMonthFormat = rememberGermanFormatter(R.string.pattern_range_day_month)
    val start = week.first().date
    val end = week.last().date
    val range = if (start.month == end.month) {
        stringResource(R.string.week_range_same_month, start.format(dayFormat), end.format(dayMonthFormat))
    } else {
        stringResource(R.string.week_range_two_months, start.format(dayMonthFormat), end.format(dayMonthFormat))
    }
    val barDescriptions = week.mapIndexed { i, cell ->
        if (cell.isFuture) {
            stringResource(R.string.week_future_description, weekdays[i])
        } else {
            stringResource(R.string.week_bar_description, weekdays[i], AmountFormat.litersWithUnit(cell.totalMl), AmountFormat.litersWithUnit(cell.goalMl))
        }
    }
    SoftCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.week_title), style = MaterialTheme.typography.titleMedium)
                Text(text = range, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GoalLegend(goalMl)
        }
        Spacer(Modifier.height(20.dp))
        WeekChart(
            week = week,
            goalMl = goalMl,
            waterColor = waterColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clearAndSetSemantics { contentDescription = barDescriptions.joinToString(", ") },
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, cell ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    WeekdayLabel(weekdays[i], cell)
                }
            }
        }
    }
}

@Composable
private fun GoalLegend(goalMl: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                color = color.copy(alpha = GOAL_LINE_ALPHA),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.week_goal, AmountFormat.litersWithUnit(goalMl)),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekdayLabel(label: String, cell: DayCell) {
    val scheme = MaterialTheme.colorScheme
    val (background, color) = when {
        cell.isToday -> scheme.primary to scheme.onPrimary
        cell.isFuture -> Color.Transparent to scheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> Color.Transparent to scheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .widthIn(min = 34.dp)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Seven bars on one baseline. Past days and today stand in a soft full-height track (an empty
 * glass), so a day with 0 shows an empty track; future days are only a dashed outline. Reached
 * days are gold, the others in the water colour. A dashed line marks the goal.
 */
@Composable
private fun WeekChart(week: List<DayCell>, goalMl: Int, waterColor: WaterColor, modifier: Modifier = Modifier) {
    val extras = LocalPegelExtras.current
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GOAL_LINE_ALPHA)
    Canvas(modifier = modifier) {
        val scaleMax = History.weekScaleMaxMl(week, goalMl).toFloat()
        val columnWidth = size.width / week.size
        val barWidth = minOf(columnWidth * 0.5f, 30.dp.toPx())
        val corner = CornerRadius(barWidth / 2f)
        val bottom = size.height
        val chartHeight = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        fun left(i: Int) = columnWidth * (i + 0.5f) - barWidth / 2f

        week.forEachIndexed { i, cell ->
            if (cell.isFuture) {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = extras.futureOutline,
                    topLeft = Offset(left(i) + stroke / 2, stroke / 2),
                    size = Size(barWidth - stroke, chartHeight - stroke),
                    cornerRadius = corner,
                    style = Stroke(width = stroke, pathEffect = dash),
                )
            } else {
                drawRoundRect(color = extras.track, topLeft = Offset(left(i), 0f), size = Size(barWidth, chartHeight), cornerRadius = corner)
            }
        }

        val goalY = bottom - chartHeight * (goalMl / scaleMax)
        drawLine(
            color = lineColor,
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )

        week.forEachIndexed { i, cell ->
            if (cell.isFuture || cell.totalMl <= 0) return@forEachIndexed
            val height = maxOf(barWidth, chartHeight * (cell.totalMl / scaleMax))
            val top = bottom - height
            val brush = fillBrush(cell.reached, waterColor, extras, top, bottom)
            drawRoundRect(brush = brush, topLeft = Offset(left(i), top), size = Size(barWidth, height), cornerRadius = corner)
        }
    }
}

private fun fillBrush(reached: Boolean, waterColor: WaterColor, extras: PegelExtras, top: Float, bottom: Float): Brush =
    if (reached) {
        Brush.verticalGradient(listOf(extras.goldLight, extras.gold), startY = top, endY = bottom)
    } else {
        Brush.verticalGradient(listOf(waterColor.lightColor, waterColor.deepColor), startY = top, endY = bottom)
    }

// ---------------------------------------------------------------------------------------------
// Monat

/**
 * Calendar of one month at a time; arrows and swipe move between the month of the first recorded
 * day and the current month. The pager runs in reverse (page 0 = current month), so history
 * that grows into the past never shifts the page that is shown.
 */
@Composable
fun MonthSection(
    today: LocalDate,
    history: Map<LocalDate, app.pegelstand.domain.DayTotal>,
    firstRecordedDay: LocalDate?,
    defaultGoalMl: Int,
    waterColor: WaterColor,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = History.monthRange(firstRecordedDay, today)
    val pager = rememberPagerState(initialPage = 0) { range.size }
    val scope = rememberCoroutineScope()
    val shownMonth = range.last.minusMonths(pager.currentPage.toLong())
    val titleFormat = rememberGermanFormatter(R.string.pattern_month_title)
    val dayFormat = rememberGermanFormatter(R.string.pattern_day_short)
    val weekdays = stringArrayResource(R.array.weekdays_short)

    SoftCard(modifier = modifier, contentPadding = 12.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < range.size - 1,
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
            }
            Text(
                text = shownMonth.atDay(1).format(titleFormat),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.month_next))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalPager(
            state = pager,
            reverseLayout = true,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val month = range.last.minusMonths(page.toLong())
            val grid = remember(month, today, history, defaultGoalMl) { History.monthGrid(month, today, history, defaultGoalMl) }
            MonthGrid(grid, selectedDay, waterColor, onSelectDay, dayFormat)
        }
        Spacer(Modifier.height(8.dp))
        DayDetail(shownMonth, selectedDay, today, history, defaultGoalMl, dayFormat)
        Spacer(Modifier.height(4.dp))
    }
}

/** Every month gets six rows, so the card keeps its height while swiping. */
private const val GRID_ROWS = 6

@Composable
private fun MonthGrid(
    grid: List<List<DayCell?>>,
    selectedDay: LocalDate?,
    waterColor: WaterColor,
    onSelectDay: (LocalDate) -> Unit,
    dayFormat: DateTimeFormatter,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until GRID_ROWS) {
            val cells = grid.getOrNull(row)
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cell = cells?.get(column)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            DayCellView(cell, cell.date == selectedDay, waterColor, { onSelectDay(cell.date) }, dayFormat)
                        } else {
                            Spacer(Modifier.height(CellHeight))
                        }
                    }
                }
            }
        }
    }
}

private val CellHeight = 58.dp

@Composable
private fun DayCellView(
    cell: DayCell,
    selected: Boolean,
    waterColor: WaterColor,
    onClick: () -> Unit,
    dayFormat: DateTimeFormatter,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = LocalPegelExtras.current
    val description = if (cell.isFuture) {
        cell.date.format(dayFormat)
    } else {
        stringResource(R.string.month_detail, cell.date.format(dayFormat), AmountFormat.litersWithUnit(cell.totalMl), AmountFormat.litersWithUnit(cell.goalMl))
    }
    Column(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = CellHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.surfaceContainerHighest else Color.Transparent)
            .clickable(enabled = !cell.isFuture, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Drop(cell, waterColor, extras, modifier = Modifier.size(width = 22.dp, height = 28.dp))
        Spacer(Modifier.height(3.dp))
        val (numberBackground, numberColor) = when {
            cell.isToday -> scheme.primary to scheme.onPrimary
            cell.isFuture -> Color.Transparent to scheme.onSurfaceVariant.copy(alpha = 0.5f)
            else -> Color.Transparent to scheme.onSurfaceVariant
        }
        Box(
            modifier = Modifier
                .widthIn(min = 24.dp)
                .clip(CircleShape)
                .background(numberBackground)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = numberColor,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * A small drop filled from the bottom in proportion to total / goal: gold and full when reached,
 * an empty tinted drop for a past day without intake, a dashed outline for a future day.
 */
@Composable
private fun Drop(cell: DayCell, waterColor: WaterColor, extras: PegelExtras, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            val path = dropPath(size.width, size.height)
            val stroke = 1.2.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))
            val fillTop = if (cell.reached) 0f else dropFillTop(cell.fraction, size.width, size.height)
            val brush = fillBrush(cell.reached, waterColor, extras, fillTop, size.height)
            onDrawBehind {
                if (cell.isFuture) {
                    drawPath(path, color = extras.futureOutline, style = Stroke(width = stroke, pathEffect = dash))
                } else {
                    clipPath(path) {
                        drawRect(color = extras.track)
                        if (cell.totalMl > 0) drawRect(brush = brush, topLeft = Offset(0f, fillTop), size = Size(size.width, size.height - fillTop))
                    }
                }
            }
        },
    )
}

private fun dropPath(w: Float, h: Float): Path {
    val r = w / 2f
    val cx = w / 2f
    val cy = h - r
    return Path().apply {
        moveTo(cx, 0f)
        cubicTo(cx + r * 0.3f, h * 0.2f, w, cy - r * 0.6f, w, cy)
        arcTo(Rect(0f, cy - r, w, cy + r), 0f, 180f, false)
        cubicTo(0f, cy - r * 0.6f, cx - r * 0.3f, h * 0.2f, cx, 0f)
        close()
    }
}

/**
 * Water level (y from the top) at which the filled part of the drop covers [fraction] of its
 * area. Filling by height would make a drop at 60 % look almost full, since most of a drop's
 * area sits in its round bottom. Numeric over the same outline as [dropPath]: a circle at the
 * bottom, sides narrowing towards the tip.
 */
private fun dropFillTop(fraction: Float, w: Float, h: Float): Float {
    if (fraction <= 0f) return h
    if (fraction >= 1f) return 0f
    val r = w / 2f
    val cy = h - r
    val rows = 64
    val widths = FloatArray(rows) { i ->
        val y = h * (i + 0.5f) / rows
        if (y >= cy) {
            val dy = y - cy
            2f * kotlin.math.sqrt(maxOf(0f, r * r - dy * dy))
        } else {
            2f * r * Math.pow((y / cy).toDouble(), 0.75).toFloat()
        }
    }
    val total = widths.sum()
    var filled = 0f
    for (i in rows - 1 downTo 0) {
        filled += widths[i]
        if (filled >= fraction * total) return h * i / rows
    }
    return 0f
}

@Composable
private fun DayDetail(
    shownMonth: YearMonth,
    selectedDay: LocalDate?,
    today: LocalDate,
    history: Map<LocalDate, app.pegelstand.domain.DayTotal>,
    defaultGoalMl: Int,
    dayFormat: DateTimeFormatter,
) {
    val cell = selectedDay
        ?.takeIf { YearMonth.from(it) == shownMonth && it <= today }
        ?.let { History.cell(it, today, history, defaultGoalMl) }
    val extras = LocalPegelExtras.current
    val text = if (cell != null) {
        stringResource(
            R.string.month_detail,
            cell.date.format(dayFormat),
            AmountFormat.litersWithUnit(cell.totalMl),
            AmountFormat.litersWithUnit(cell.goalMl),
        )
    } else {
        stringResource(R.string.month_hint)
    }
    Text(
        text = text,
        style = if (cell != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = when {
            cell == null -> MaterialTheme.colorScheme.onSurfaceVariant
            cell.reached -> extras.goldText
            else -> MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(horizontal = 8.dp),
    )
}

private const val GOAL_LINE_ALPHA = 0.6f
