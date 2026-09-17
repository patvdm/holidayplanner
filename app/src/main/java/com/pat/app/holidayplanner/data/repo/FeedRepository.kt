package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.FeedRow
import com.pat.app.holidayplanner.data.local.dao.FeedDao
import com.pat.app.holidayplanner.domain.AppClock
import com.pat.app.holidayplanner.domain.FeedGrouping
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** What the other person added this week. Empty until somebody is signed in. */
class FeedRepository(
    private val dao: FeedDao,
    private val users: UserRepository,
    private val clock: AppClock,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeThisWeek(): Flow<List<FeedRow>> = users.currentUserId.flatMapLatest { me ->
        if (me.isNullOrBlank()) flowOf(emptyList()) else dao.observeSince(me, FeedGrouping.since(clock.now()))
    }
}
