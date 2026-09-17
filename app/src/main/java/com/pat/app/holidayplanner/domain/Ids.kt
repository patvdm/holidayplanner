package com.pat.app.holidayplanner.domain

import java.util.UUID

/** Row ids are UUID v4 strings generated on the device. */
fun newId(): String = UUID.randomUUID().toString()

/** Hearts use a deterministic id so the same user can only heart a spot once. */
fun heartId(spotId: String, userId: String): String = "${spotId}_$userId"
