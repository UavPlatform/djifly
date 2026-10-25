// GENERATED — DO NOT EDIT.
// 契约路径表：调用点只允许引用 ApiPaths 常量，禁止散落路径字面量。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: fefef9267c01b64dcfc041bc556a3b4c4ce269de597cb561ef257cc3a892afa9)
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

    /** POST appUav/add */
    const val APP_UAV_ADD = "appUav/add"

    /** POST api/ws/request */
    const val API_WS_REQUEST = "api/ws/request"

    /** POST live/rider/req */
    const val LIVE_RIDER_REQ = "live/rider/req"

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

    /** POST dispatch/pilot/orders/{orderId}/flight-connections */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS = "dispatch/pilot/orders/{orderId}/flight-connections"

    /** POST dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/samples/batch */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_SAMPLES_BATCH = "dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/samples/batch"

    /** POST dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/close */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_CLOSE = "dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/close"

    /** 事实源规格 openapi/drone-backend.openapi.json 的 sha256（门禁测试对账）。 */
    const val GENERATED_FROM_SHA256 = "fefef9267c01b64dcfc041bc556a3b4c4ce269de597cb561ef257cc3a892afa9"
}
