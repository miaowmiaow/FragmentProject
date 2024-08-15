package com.example.fragment.project.components.calendar

import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.fragment.project.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MonthPager(
    state: CalendarState,
    mode: CalendarMode,
    model: CalendarModel,
    monthModePagerState: PagerState,
    weekModePagerState: PagerState,
    onCalendarStateChange: (mode: CalendarMode) -> Unit,
    onSelectedDateChange: (year: Int, month: Int, day: Int) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isWeekMode = mode == CalendarMode.Week
    var selectedWeek by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(model.localCalendarDate()) }
    LaunchedEffect(state) {
        state.handleCalendarEvent(
            onSchedule = {
                selectedDate?.dayState?.onSchedule(it)
            }
        )
    }
    val pagerState = if (isWeekMode) weekModePagerState else monthModePagerState
    //周模式和月模式联动
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            if (isWeekMode) {
                val month = model.weekModeByIndex(page) ?: return@collectLatest
                selectedWeek = model.weekByWeekModeIndex(page)
                val isDate = month.weeks[selectedWeek].firstOrNull { it.isDay }
                if (isDate == null) {
                    selectedDate?.dayState?.onSelected(false)
                    val date = month.weeks[selectedWeek].first { it.isMonth }
                    date.dayState?.onSelected(true)
                    selectedDate = date
                }
                val date = (month.year - model.startYear()) * 12 + month.month - 1
                if (monthModePagerState.currentPage != date) {
                    scope.launch {
                        monthModePagerState.scrollToPage(date)
                    }
                }
            } else {
                val month = model.monthModeByIndex(page) ?: return@collectLatest
                val isDate = month.weeks[selectedWeek].firstOrNull { it.isDay }
                if (isDate == null) {
                    selectedWeek = 0
                    selectedDate?.dayState?.onSelected(false)
                    val date = month.weeks[selectedWeek].first { it.isMonth }
                    date.dayState?.onSelected(true)
                    selectedDate = date
                }
                val date = model.weekModeIndexByDate(month.year, month.month, selectedWeek)
                if (weekModePagerState.currentPage != date) {
                    scope.launch {
                        weekModePagerState.scrollToPage(date)
                    }
                }
            }
        }
    }
    BoxWithConstraints {
        val height = maxHeight
        HorizontalPager(state = pagerState) { page ->
            val month = if (isWeekMode) {
                model.weekModeByIndex(page)
            } else {
                model.monthModeByIndex(page)
            } ?: return@HorizontalPager
            val weekModeHeight = with(density) { WeekHeight.toPx() }
            val monthModeHeight = with(density) { WeekHeight.toPx() * month.weeksInMonth() }
            val monthFillModeHeight = with(density) { (height - TipArrowHeight).toPx() }
            val anchoredDraggableState = remember(monthModeHeight, monthFillModeHeight) {
                AnchoredDraggableState(
                    initialValue = mode,
                    anchors = DraggableAnchors {
                        CalendarMode.Week at weekModeHeight
                        CalendarMode.Month at monthModeHeight
                        CalendarMode.MonthFill at monthFillModeHeight
                    },
                    positionalThreshold = { distance -> distance * 0.5f },
                    velocityThreshold = { with(density) { 100.dp.toPx() } },
                    animationSpec = TweenSpec(durationMillis = 350),
                )
            }
            //展开日历
            LaunchedEffect(model) {
                if (model.firstStartup) {
                    anchoredDraggableState.animateTo(CalendarMode.Month)
                    model.firstStartup = false
                }
            }
            //日历和日程联动
            val listState = rememberLazyListState()
            var scrollEnabled by remember { mutableStateOf(false) }
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collectLatest {
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && !listState.canScrollForward) {
                        scrollEnabled = false
                    }
                }
            }
            LaunchedEffect(anchoredDraggableState) {
                snapshotFlow { anchoredDraggableState.currentValue }.collectLatest {
                    scrollEnabled = it == CalendarMode.Week && listState.canScrollForward
                    onCalendarStateChange(it)
                }
            }
            Box(
                modifier = Modifier
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Vertical,
                    )
                    .background(colorResource(id = R.color.background))
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                val isMonthFillMode = anchoredDraggableState.currentValue == CalendarMode.MonthFill
                val anchoredDraggableOffset = anchoredDraggableState.requireOffset()
                WeekContent(
                    items = month.weeks,
                    selectedWeek = selectedWeek,
                    weekModeHeight = weekModeHeight,
                    monthModeHeight = monthModeHeight,
                    monthFillModeHeight = monthFillModeHeight,
                    isMonthFillMode = isMonthFillMode,
                    offsetProvider = { anchoredDraggableOffset },
                ) { date ->
                    date.dayState = rememberDayState()
                    DayContent(date, isMonthFillMode) {
                        if (mode != CalendarMode.Week) {
                            selectedWeek = date.week
                            model.weekModeIndexByDate(date.year, date.month, date.week).let {
                                if (weekModePagerState.currentPage != it) {
                                    scope.launch {
                                        weekModePagerState.scrollToPage(it)
                                    }
                                }
                            }
                        }
                        selectedDate?.dayState?.onSelected(false)
                        date.dayState?.onSelected(true)
                        onSelectedDateChange(date.year, date.month, date.day)
                        selectedDate = date
                    }
                }
                ScheduleContent(
                    date = selectedDate,
                    mode = mode,
                    height = height,
                    listState = listState,
                    scrollEnabled = scrollEnabled,
                    offsetProvider = { anchoredDraggableOffset.toInt() },
                )
            }
        }
    }
}