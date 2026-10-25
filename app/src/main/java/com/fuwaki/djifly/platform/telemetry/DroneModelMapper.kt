package com.fuwaki.djifly.platform.telemetry

/**
 * 把 MSDK 的 ProductType 名映射为平台机型目录的 modelCode（backend aircraft_model.model_code）。
 * 目录里没有的机型原样上报 ProductType 名，后端不推测载重、不影响计价。未知 / 未连接返回 null，不上报连机记录。
 */
object DroneModelMapper {
    private val KNOWN = mapOf(
        "DJI_FLYCART_30" to "FC30",
        "FLYCART_30" to "FC30",
        "M350_RTK" to "M350RTK",
        "M300_RTK" to "M300RTK",
        "M30_SERIES" to "M30",
    )
    private val UNKNOWN = setOf("", "UNKNOWN", "UNRECOGNIZED", "N/A", "未知设备")

    fun modelCode(productTypeName: String?): String? {
        val name = productTypeName?.trim()?.uppercase() ?: return null
        if (name in UNKNOWN) return null
        return KNOWN[name] ?: name
    }

    fun validSerial(serial: String?): String? =
        serial?.trim()?.takeUnless { it.isEmpty() || it.uppercase() in UNKNOWN }
}
