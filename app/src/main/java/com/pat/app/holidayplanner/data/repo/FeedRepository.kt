package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.FeedRow
import com.pat.app.holidayplanner.data.local.dao.FeedDao
import com.pat.app.holidayplanner.domain.AppClock
import com.pat.app.holidayplanner.domain.FeedGrouping
import kotlinx.coroutines.flow.Flow

/** What anybody added this week. */
class FeedRepository(
    private val dao: FeedDao,
    private val clock: AppClock,
) {

    fun observeThisWeek(): Flow<List<FeedRow>> = dao.observeSince(FeedGrouping.since(clock.now()))
}
