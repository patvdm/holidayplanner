package com.pat.app.holidayplanner.domain

import java.util.UUID

/** Row ids are UUID v4 strings generated on the device. */
fun newId(): String = UUID.randomUUID().toString()

