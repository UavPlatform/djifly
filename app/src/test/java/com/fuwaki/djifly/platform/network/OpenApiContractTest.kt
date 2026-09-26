package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.platform.network.generated.ApiPaths
import com.fuwaki.djifly.platform.network.generated.ApiTaskVo
import com.fuwaki.djifly.platform.network.generated.ApiUavVo
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * OpenAPI 契约门禁：
 *
 * 1. vendored 规格的 sha256 == openapi/drone-backend.openapi.sha256 == ApiPaths.GENERATED_FROM_SHA256；
 * 2. CALLED_ENDPOINTS 登记表里的每个端点都能在契约里按“占位符结构归一化”找到；
 * 3. 除 generated 包外，app/src/main/java 下不得再有后端路径字面量（防止绕过 ApiPaths）；
 * 4. 生成的契约模型能用 Gson 解析样例 JSON，且 required 字段在 Kotlin 侧是非空类型。
 *
 * 纯 JVM 单测：不依赖 Android / DJI / DataStore。
 */
class OpenApiContractTest {

    /** 本端调用的端点登记表（method, Retrofit 注解字面量，无前导斜杠）。新增调用点必须在此登记。 */
    private val calledEndpoints: List<List<String>> = listOf(
        listOf("post", "appUav/add"),
        listOf("post", "api/ws/request"),
        listOf("post", "user/login"),
        listOf("post", "rider/register"),
        listOf("post", "user/refresh"),
        listOf("get", "rider/my-tasks"),
        listOf("get", "rider/my-tasks/history"),
        listOf("post", "rider/cancel"),
        listOf("post", "rider/complete"),
        listOf("get", "rider/task/detail"),
        listOf("post", "live/rider/req"),
        listOf("post", "tasks/{taskNum}/attachments/upload-url"),
        listOf("get", "tasks/{taskNum}/attachments"),
        listOf("get", "rider/drone/list"),
        listOf("post", "rider/drone/bind"),
        listOf("delete", "rider/drone/unbind"),
    )

    private val specFile: File by lazy { resolveSpecFile() }
    private val repoRoot: File
        get() = requireNotNull(specFile.parentFile?.parentFile) {
            "无法从 " + specFile.absolutePath + " 推断仓根目录（应位于 <repo>/openapi/ 下）"
        }
    private val shaFile: File
        get() = File(repoRoot, "openapi/drone-backend.openapi.sha256").also {
            if (!it.isFile) missing("找不到 sha256 记录文件：" + it.absolutePath)
        }

    // ═══════════════════════════════════════════════════════
    // 1. sha256 三方对账
    // ═══════════════════════════════════════════════════════

    @Test
    fun vendored_spec_sha256_matches_record_and_generated_constant() {
        val actual = sha256Hex(specFile.readBytes())
        val recordedLine = shaFile.readText().trim()
        val recorded = recordedLine.split(Regex("\\s+")).first()

        assertEquals("openapi/*.sha256 与 vendored 规格不一致（规格被就地改动？）", recorded, actual)
        assertEquals("ApiPaths.GENERATED_FROM_SHA256 与 vendored 规格不一致，请重跑 node tools/openapi-gen.mjs", actual, ApiPaths.GENERATED_FROM_SHA256)
        assertTrue("sha256 记录格式应为 \"<sha>  drone-backend.openapi.json\"，实际：" + recordedLine, recordedLine.endsWith("drone-backend.openapi.json"))
        assertEquals("sha256 应为 64 位十六进制", 64, actual.length)
    }

    // ═══════════════════════════════════════════════════════
    // 2. 被调用端点都在契约里
    // ═══════════════════════════════════════════════════════

    @Test
    fun every_called_endpoint_exists_in_the_spec() {
        val paths = specPaths()
        for (endpoint in calledEndpoints) {
            val method = endpoint[0]
            val localPath = endpoint[1]
            val matches = paths.keys.filter { normalized(it) == normalized(localPath) }
            assertEquals(
                "契约里应恰好有一条路径对应 " + localPath + "（按占位符结构匹配），实际：" + matches,
                1,
                matches.size
            )
            val specPath = matches.first()
            val item = paths[specPath] as? Map<*, *>
            assertTrue("契约 " + specPath + " 缺少 " + method.uppercase() + " 方法", item?.containsKey(method) == true)
        }
        assertEquals("端点登记表应覆盖本端全部 16 条调用（新增调用点需同步登记）", 16, calledEndpoints.size)
    }

    // ═══════════════════════════════════════════════════════
    // 3. 无残留路径字面量（generated 包除外）
    // ═══════════════════════════════════════════════════════

    @Test
    fun no_backend_path_literals_outside_the_generated_package() {
        val forbidden = specPaths().keys.map { normalized(it) }.toSet()
        val sourceRoot = File(repoRoot, "app/src/main/java")
        if (!sourceRoot.isDirectory) missing("找不到源码根目录：" + sourceRoot.absolutePath)
        val generatedRoot = File(sourceRoot, "com/fuwaki/djifly/platform/network/generated").absolutePath

        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.absolutePath.startsWith(generatedRoot) }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    STRING_LITERAL.findAll(line).forEach { match ->
                        val literal = match.groupValues[1]
                        if (normalized(literal) in forbidden) {
                            offenders += file.relativeTo(repoRoot).path + ":" + (index + 1) + "  \"" + literal + "\""
                        }
                    }
                }
            }

        assertTrue(
            "以下位置仍在直接书写后端路径字面量，请改引用 ApiPaths 常量：\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    // ═══════════════════════════════════════════════════════
    // 4. 生成模型可用
    // ═══════════════════════════════════════════════════════

    @Test
    fun generated_models_parse_sample_payloads() {
        val gson = Gson()

        val uav = gson.fromJson(
            "{\"id\":7,\"uavName\":\"SN-1\",\"djiId\":\"SN-1\",\"controllerModel\":\"RC\",\"isAvailable\":\"1\"}",
            ApiUavVo::class.java
        )
        assertEquals(7L, uav.id)
        assertEquals("SN-1", uav.uavName)

        // 撮合子状态字段（TASK-DJIFLY-002 执飞门禁的契约来源）
        val task = gson.fromJson(
            "{\"taskNum\":\"T-1\",\"taskStatus\":\"IN_PROGRESS\",\"matchStatus\":\"CONFIRMED\"}",
            ApiTaskVo::class.java
        )
        assertEquals("T-1", task.taskNum)
        assertEquals("IN_PROGRESS", task.taskStatus)
        assertEquals("CONFIRMED", task.matchStatus)
    }

    // ═══════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════

    @Suppress("UNCHECKED_CAST")
    private fun specPaths(): Map<String, Any?> {
        val root = Gson().fromJson(specFile.readText(), Map::class.java) as? Map<String, Any?>
            ?: missing("vendored 规格不是合法 JSON 对象：" + specFile.absolutePath)
        return (root["paths"] as? Map<String, Any?>)
            ?: missing("vendored 规格缺少 paths 节点：" + specFile.absolutePath)
    }

    private fun resolveSpecFile(): File {
        System.getProperty("djifly.repoRoot")
            ?.takeIf { it.isNotBlank() }
            ?.let { configured ->
                val candidate = File(configured, SPEC_RELATIVE)
                if (candidate.isFile) return candidate
                missing("djifly.repoRoot=" + configured + " 下找不到 " + SPEC_RELATIVE + "（查找路径：" + candidate.absolutePath + "）")
            }

        val tried = mutableListOf<String>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, SPEC_RELATIVE), File(dir, "djifly/" + SPEC_RELATIVE))) {
                tried += candidate.absolutePath
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        missing(
            "找不到 vendored OpenAPI 契约 " + SPEC_RELATIVE + "。\n" +
                "请先运行：cd djifly && node tools/openapi-sync.mjs\n已尝试路径：\n" + tried.joinToString("\n")
        )
    }

    /** 占位符结构归一化：{msgId} 与 {sessionId} 视为同一模板位置，并去掉前导斜杠。 */
    private fun normalized(path: String): String =
        path.replace(TEMPLATE_REGEX, "{}").removePrefix("/")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun missing(message: String): Nothing = throw AssertionError(message)

    private companion object {
        const val SPEC_RELATIVE = "openapi/drone-backend.openapi.json"
        val TEMPLATE_REGEX = Regex("\\{[^}]*\\}")
        val STRING_LITERAL = Regex("\"([^\"]*)\"")

    }
}