package com.yoloc.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** JS Date.toISOString(): always milliseconds and a Z (Instant.toString() drops zero millis). */
private val ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

fun isoString(epochMs: Long): String = ISO_MILLIS.format(Instant.ofEpochMilli(epochMs))

/** "2026-10-17" in local time, for export file names (RN pads getFullYear/getMonth/getDate). */
fun localDateStamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString() // LocalDate.ofInstant needs API 34
