package com.fuwaki.djifly.platform.network

/**
 * 交付物上传凭证（t43：POST /tasks/{taskNum}/attachments/upload-url 的 data）。
 * 客户端凭 [uploadUrl] 直传 MinIO（presigned PUT，15 分钟有效），不经过业务后端。
 */
data class AttachmentUploadTicket(
    val objectKey: String? = null,
    val uploadUrl: String? = null,
    val expireSeconds: Long? = null,
    val method: String? = null
)

/**
 * 交付物附件（t43：GET /tasks/{taskNum}/attachments 列表项 / download-url 响应）。
 * downloadUrl 为 presigned GET（15 分钟有效），仅任务所有者与接单飞手可见。
 */
data class AttachmentItem(
    val objectKey: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val createTime: String? = null,
    val downloadUrl: String? = null
)
