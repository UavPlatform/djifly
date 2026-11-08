// GENERATED — DO NOT EDIT.
// 契约路径表：调用点只允许引用 ApiPaths 常量，禁止散落路径字面量。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: 39ab6d5c22fdae6e99c4dfd3df95f0ef1bf2430247a399b0e1c33f883967ac63)
// 生成命令：cd djifly && node tools/openapi-gen.mjs

package com.fuwaki.djifly.platform.network.generated

/**
 * 后端端点路径常量（与 openapi/drone-backend.openapi.json 同源）。
 *
 * 常量值刻意不带前导斜杠：Retrofit 会把它解析到 baseUrl 之下，
 * 与既有调用点的字面量保持逐字节一致。
 */
object ApiPaths {

    /** POST user/login */
    const val USER_LOGIN = "user/login"

    /** POST user/refresh */
    const val USER_REFRESH = "user/refresh"

    /** GET dispatch/pilot/orders */
    const val DISPATCH_PILOT_ORDERS = "dispatch/pilot/orders"

    /** POST dispatch/orders/{orderId}/start-transport */
    const val DISPATCH_ORDERS_START_TRANSPORT = "dispatch/orders/{orderId}/start-transport"

    /** POST dispatch/orders/{orderId}/deliver */
    const val DISPATCH_ORDERS_DELIVER = "dispatch/orders/{orderId}/deliver"

    /** GET dispatch/orders/{orderId}/materials */
    const val DISPATCH_ORDERS_MATERIALS = "dispatch/orders/{orderId}/materials"

    /** POST dispatch/orders/{orderId}/materials/upload-ticket */
    const val DISPATCH_ORDERS_MATERIALS_UPLOAD_TICKET = "dispatch/orders/{orderId}/materials/upload-ticket"

    /** POST dispatch/orders/{orderId}/materials/{materialId}/confirm */
    const val DISPATCH_ORDERS_MATERIALS_CONFIRM = "dispatch/orders/{orderId}/materials/{materialId}/confirm"

    /** POST dispatch/pilot/orders/{orderId}/event-packets */
    const val DISPATCH_PILOT_ORDERS_EVENT_PACKETS = "dispatch/pilot/orders/{orderId}/event-packets"

    /** POST dispatch/pilot/orders/{orderId}/event-reconciliation */
    const val DISPATCH_PILOT_ORDERS_EVENT_RECONCILIATION = "dispatch/pilot/orders/{orderId}/event-reconciliation"

    /** GET dispatch/pilot/orders/{orderId}/event-chains */
    const val DISPATCH_PILOT_ORDERS_EVENT_CHAINS = "dispatch/pilot/orders/{orderId}/event-chains"

    /** GET dispatch/orders/{orderId}/flight */
    const val DISPATCH_ORDERS_FLIGHT = "dispatch/orders/{orderId}/flight"

    /** GET dispatch/orders/{orderId}/flight/track */
    const val DISPATCH_ORDERS_FLIGHT_TRACK = "dispatch/orders/{orderId}/flight/track"

    /** GET dispatch/orders/{orderId}/flight/history */
    const val DISPATCH_ORDERS_FLIGHT_HISTORY = "dispatch/orders/{orderId}/flight/history"

    /** POST dispatch/pilot/orders/{orderId}/live */
    const val DISPATCH_PILOT_ORDERS_LIVE = "dispatch/pilot/orders/{orderId}/live"

    /** POST dispatch/pilot/orders/{orderId}/live/{liveId}/heartbeat */
    const val DISPATCH_PILOT_ORDERS_LIVE_HEARTBEAT = "dispatch/pilot/orders/{orderId}/live/{liveId}/heartbeat"

    /** POST dispatch/pilot/orders/{orderId}/live/{liveId}/stop */
    const val DISPATCH_PILOT_ORDERS_LIVE_STOP = "dispatch/pilot/orders/{orderId}/live/{liveId}/stop"

    /** GET dispatch/orders/{orderId}/live */
    const val DISPATCH_ORDERS_LIVE = "dispatch/orders/{orderId}/live"

    /** POST dispatch/orders/{orderId}/live/{liveId}/watch */
    const val DISPATCH_ORDERS_LIVE_WATCH = "dispatch/orders/{orderId}/live/{liveId}/watch"

    /** 事实源规格 openapi/drone-backend.openapi.json 的 sha256（门禁测试对账）。 */
    const val GENERATED_FROM_SHA256 = "39ab6d5c22fdae6e99c4dfd3df95f0ef1bf2430247a399b0e1c33f883967ac63"
}
