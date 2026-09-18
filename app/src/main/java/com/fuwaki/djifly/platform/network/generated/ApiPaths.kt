// GENERATED — DO NOT EDIT.
// 契约路径表：调用点只允许引用 ApiPaths 常量，禁止散落路径字面量。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: 649c3f3e02243ba7e828cd225e84f117f18a498515312774f75bd0f66376f7da)
// 生成命令：cd djifly && node tools/openapi-gen.mjs

package com.fuwaki.djifly.platform.network.generated

/**
 * 后端端点路径常量（与 openapi/drone-backend.openapi.json 同源）。
 *
 * 常量值刻意不带前导斜杠：Retrofit 会把它解析到 baseUrl 之下，
 * 与既有调用点的字面量保持逐字节一致。
 */
object ApiPaths {

    /** POST appUav/add */
    const val APP_UAV_ADD = "appUav/add"

    /** POST api/ws/request */
    const val API_WS_REQUEST = "api/ws/request"

    /** POST user/login */
    const val USER_LOGIN = "user/login"

    /** POST rider/register */
    const val RIDER_REGISTER = "rider/register"

    /** POST user/refresh */
    const val USER_REFRESH = "user/refresh"

    /** GET rider/square */
    const val RIDER_SQUARE = "rider/square"

    /** POST rider/accept */
    const val RIDER_ACCEPT = "rider/accept"

    /** GET rider/my-tasks */
    const val RIDER_MY_TASKS = "rider/my-tasks"

    /** GET rider/my-tasks/history */
    const val RIDER_MY_TASKS_HISTORY = "rider/my-tasks/history"

    /** POST rider/cancel */
    const val RIDER_CANCEL = "rider/cancel"

    /** POST rider/complete */
    const val RIDER_COMPLETE = "rider/complete"

    /** GET rider/task/detail */
    const val RIDER_TASK_DETAIL = "rider/task/detail"

    /** GET chat/session/list */
    const val CHAT_SESSION_LIST = "chat/session/list"

    /** POST chat/session/create */
    const val CHAT_SESSION_CREATE = "chat/session/create"

    /** GET chat/Message/messages/{sessionId} */
    const val CHAT_MESSAGE_MESSAGES = "chat/Message/messages/{sessionId}"

    /** POST chat/Message/send */
    const val CHAT_MESSAGE_SEND = "chat/Message/send"

    /** GET chat/Message/sync */
    const val CHAT_MESSAGE_SYNC = "chat/Message/sync"

    /** GET rider/stats */
    const val RIDER_STATS = "rider/stats"

    /** POST live/rider/req */
    const val LIVE_RIDER_REQ = "live/rider/req"

    /** POST tasks/{taskNum}/attachments/upload-url */
    const val TASKS_ATTACHMENTS_UPLOAD_URL = "tasks/{taskNum}/attachments/upload-url"

    /** GET tasks/{taskNum}/attachments */
    const val TASKS_ATTACHMENTS = "tasks/{taskNum}/attachments"

    /** GET chat/Message/unread/count */
    const val CHAT_MESSAGE_UNREAD_COUNT = "chat/Message/unread/count"

    /** POST chat/Message/read */
    const val CHAT_MESSAGE_READ = "chat/Message/read"

    /** GET rider/drone/list */
    const val RIDER_DRONE_LIST = "rider/drone/list"

    /** POST rider/drone/bind */
    const val RIDER_DRONE_BIND = "rider/drone/bind"

    /** DELETE rider/drone/unbind */
    const val RIDER_DRONE_UNBIND = "rider/drone/unbind"

    /** POST user/register */
    const val USER_REGISTER = "user/register"

    /** 事实源规格 openapi/drone-backend.openapi.json 的 sha256（门禁测试对账）。 */
    const val GENERATED_FROM_SHA256 = "649c3f3e02243ba7e828cd225e84f117f18a498515312774f75bd0f66376f7da"
}
