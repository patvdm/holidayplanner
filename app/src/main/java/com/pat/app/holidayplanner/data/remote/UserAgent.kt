package com.pat.app.holidayplanner.data.remote

/**
 * Who the app says it is to Wikimedia, on the API and on the picture servers alike. Both refuse
 * anything that does not identify itself: the API with a "too many requests", the picture servers
 * with a flat 403, which is how every picture failed to show while every search succeeded.
 */
const val APP_USER_AGENT = "HolidayPlanner/1.0 (Android; private two-person app)"
