package com.scaleplex.driver.api

/** Live status returned by a scale in response to a status / info query. */
data class ScaleStatus(
    val online: Boolean,
    val model: String = "",
    val firmware: String = "",
    val storeTotal: Long = 0,
)
