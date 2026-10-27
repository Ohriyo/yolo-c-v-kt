package com.yoloc.app.util

import java.util.UUID

/** RFC 4122 version 4 UUID for local primary keys (RN: src/utils/uuid.ts). */
fun uuid(): String = UUID.randomUUID().toString()
