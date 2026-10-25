// GENERATED — DO NOT EDIT.
// 契约路径表：调用点只允许引用 ApiPaths 常量，禁止散落路径字面量。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: c953c4d5b6b6adc327436a587f5cf2ba12d255ee7ca99a9c1ee40bfcb5f27a24)
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

    /** GET rider/my-tasks */
    const val RIDER_MY_TASKS = "rider/my-tasks"

    /** GET rider/task/detail */
    const val RIDER_TASK_DETAIL = "rider/task/detail"

    /** POST appUav/add */
    const val APP_UAV_ADD = "appUav/add"

    /** POST api/ws/request */
    const val API_WS_REQUEST = "api/ws/request"

    /** POST live/rider/req */
    const val LIVE_RIDER_REQ = "live/rider/req"

    /** POST tasks/{taskNum}/attachments/upload-url */
    const val TASKS_ATTACHMENTS_UPLOAD_URL = "tasks/{taskNum}/attachments/upload-url"

    /** POST tasks/{taskNum}/attachments/confirm-upload */
    const val TASKS_ATTACHMENTS_CONFIRM_UPLOAD = "tasks/{taskNum}/attachments/confirm-upload"

    /** GET tasks/{taskNum}/attachments */
    const val TASKS_ATTACHMENTS = "tasks/{taskNum}/attachments"

    /** POST rider/complete */
    const val RIDER_COMPLETE = "rider/complete"

    /** GET rider/drone/list */
    const val RIDER_DRONE_LIST = "rider/drone/list"

    /** POST rider/drone/bind */
    const val RIDER_DRONE_BIND = "rider/drone/bind"

    /** DELETE rider/drone/unbind */
    const val RIDER_DRONE_UNBIND = "rider/drone/unbind"

    /** GET dispatch/pilot/orders */
    const val DISPATCH_PILOT_ORDERS = "dispatch/pilot/orders"

    /** POST dispatch/pilot/orders/{orderId}/flight-connections */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS = "dispatch/pilot/orders/{orderId}/flight-connections"

    /** POST dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/samples/batch */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_SAMPLES_BATCH = "dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/samples/batch"

    /** POST dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/close */
    const val DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_CLOSE = "dispatch/pilot/orders/{orderId}/flight-connections/{connectionId}/close"

    /** 事实源规格 openapi/drone-backend.openapi.json 的 sha256（门禁测试对账）。 */
    const val GENERATED_FROM_SHA256 = "c953c4d5b6b6adc327436a587f5cf2ba12d255ee7ca99a9c1ee40bfcb5f27a24"
}
