package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SearchHit
import com.pat.app.holidayplanner.data.local.dao.SearchDao
import com.pat.app.holidayplanner.domain.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchRepository(private val dao: SearchDao) {

    /** Results for what was typed, or nothing at all when nothing was. */
    fun search(raw: String): Flow<List<SearchHit>> =
        if (SearchQuery.isSearchable(raw)) dao.search(SearchQuery.like(raw)) else flowOf(emptyList())
}
