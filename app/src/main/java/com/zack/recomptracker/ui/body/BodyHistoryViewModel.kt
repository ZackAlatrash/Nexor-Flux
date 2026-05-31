package com.zack.recomptracker.ui.body

import androidx.lifecycle.ViewModel
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class BodyHistoryItem {
    data class Logged(val date: LocalDate, val entity: DailyLogEntity) : BodyHistoryItem()
    data class Missing(val date: LocalDate) : BodyHistoryItem()
}

class BodyHistoryViewModel(
    logRepository: LogRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    val items: Flow<List<BodyHistoryItem>> = logRepository.observeDailyLogs()
        .map { logs -> buildItems(logs) }

    private fun buildItems(logs: List<DailyLogEntity>): List<BodyHistoryItem> {
        val today = dateProvider.today()
        val logsByDate = logs.associateBy { LocalDate.parse(it.date) }
        val earliest = logs.minOfOrNull { LocalDate.parse(it.date) }
        val start = listOfNotNull(today.minusDays(89), earliest).min()
        val dayCount = ChronoUnit.DAYS.between(start, today)
        return (0..dayCount).map { offset ->
            val date = today.minusDays(offset)
            val entity = logsByDate[date]
            if (entity != null) BodyHistoryItem.Logged(date, entity)
            else BodyHistoryItem.Missing(date)
        }
    }
}
