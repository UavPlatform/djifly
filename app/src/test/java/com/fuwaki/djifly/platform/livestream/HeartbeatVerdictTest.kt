package com.fuwaki.djifly.platform.livestream

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归：订单直播心跳响应分类。
 *
 * 后端 heartbeat 是唯一续期 lease 的入口（有效期 20 秒）。因此：
 * - 2xx 才算续期；
 * - 4xx 表示平台已结束该会话，必须收尾且不得复活；
 * - 5xx 或没有响应**不能**续期，也**不能**当作会话结束——短暂抖动应能恢复，
 *   否则一次 502 就会把正在进行的订单直播打断。
 */
class HeartbeatVerdictTest {

    @Test
    fun successfulResponseRenewsTheLease() {
        assertEquals(HeartbeatVerdict.RENEWED, HeartbeatVerdict.of(200, true))
        assertEquals(HeartbeatVerdict.RENEWED, HeartbeatVerdict.of(204, true))
    }

    @Test
    fun clientErrorsMeanTheServerEndedTheSession() {
        assertEquals(HeartbeatVerdict.ENDED, HeartbeatVerdict.of(400, false))
        assertEquals(HeartbeatVerdict.ENDED, HeartbeatVerdict.of(401, false))
        assertEquals(HeartbeatVerdict.ENDED, HeartbeatVerdict.of(404, false))
        assertEquals(HeartbeatVerdict.ENDED, HeartbeatVerdict.of(409, false))
    }

    @Test
    fun serverErrorsAndMissingResponsesDoNotRenewAndDoNotEnd() {
        assertEquals(HeartbeatVerdict.NOT_RENEWED, HeartbeatVerdict.of(500, false))
        assertEquals(HeartbeatVerdict.NOT_RENEWED, HeartbeatVerdict.of(502, false))
        assertEquals(HeartbeatVerdict.NOT_RENEWED, HeartbeatVerdict.of(503, false))
        // No response at all (timeout / IOException) is reported as a null code.
        assertEquals(HeartbeatVerdict.NOT_RENEWED, HeartbeatVerdict.of(null, false))
        assertEquals(HeartbeatVerdict.NOT_RENEWED, HeartbeatVerdict.of(null, true))
    }
}
