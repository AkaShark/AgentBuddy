package com.akashark.agentbuddy.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.AppPushPlatform
import uniffi.codex_mobile_client.ThreadKey

class TurnCompletionPushTest {

    private val hostId = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c"
    private val serverId = "alleycat:$hostId"

    private fun workerData(kind: String? = "completed") = buildMap {
        put("agentbuddy.notification.serverId", serverId)
        put("agentbuddy.notification.threadId", "thread-1")
        put("agentbuddy.notification.turnId", "turn-1")
        if (kind != null) put("agentbuddy.notification.kind", kind)
        put("agentbuddy.notification.eventId", "evt_0123456789abcdef0123456789abcdef")
    }

    // --- parsePushPayload -----------------------------------------------------

    @Test
    fun `completed payload parses every routing key`() {
        assertEquals(
            PushPayload.TurnTerminal(
                serverId = serverId,
                threadId = "thread-1",
                turnId = "turn-1",
                kind = TurnPushKind.COMPLETED,
                eventId = "evt_0123456789abcdef0123456789abcdef",
            ),
            parsePushPayload(workerData(), hasNotification = true),
        )
    }

    @Test
    fun `failed payload parses kind failed`() {
        val payload = parsePushPayload(workerData("failed"), hasNotification = true)
        assertEquals(TurnPushKind.FAILED, (payload as PushPayload.TurnTerminal).kind)
    }

    @Test
    fun `turn payload without turnId or eventId still parses`() {
        val data = workerData() - "agentbuddy.notification.turnId" - "agentbuddy.notification.eventId"
        val payload = parsePushPayload(data, hasNotification = true) as PushPayload.TurnTerminal
        assertNull(payload.turnId)
        assertNull(payload.eventId)
    }

    @Test
    fun `turn payload missing serverId or threadId is ignored`() {
        assertEquals(
            PushPayload.Ignored,
            parsePushPayload(workerData() - "agentbuddy.notification.serverId", hasNotification = true),
        )
        assertEquals(
            PushPayload.Ignored,
            parsePushPayload(
                workerData() + ("agentbuddy.notification.threadId" to "  "),
                hasNotification = true,
            ),
        )
    }

    @Test
    fun `unknown kind is ignored`() {
        assertEquals(PushPayload.Ignored, parsePushPayload(workerData("approval"), hasNotification = true))
    }

    @Test
    fun `debug alert without kind keeps optional routing`() {
        assertEquals(
            PushPayload.DebugAlert(serverId = serverId, threadId = "thread-1", turnId = "turn-1"),
            parsePushPayload(workerData(kind = null) - "agentbuddy.notification.eventId", hasNotification = true),
        )
        assertEquals(
            PushPayload.DebugAlert(serverId = null, threadId = null, turnId = null),
            parsePushPayload(emptyMap(), hasNotification = true),
        )
    }

    @Test
    fun `debug background is recognised with or without routing`() {
        assertEquals(
            PushPayload.DebugBackground,
            parsePushPayload(mapOf("type" to "debug_background"), hasNotification = false),
        )
        assertEquals(
            PushPayload.DebugBackground,
            parsePushPayload(
                workerData(kind = null) + ("type" to "debug_background"),
                hasNotification = false,
            ),
        )
    }

    @Test
    fun `data-only message without kind and legacy keepalive are ignored`() {
        assertEquals(PushPayload.Ignored, parsePushPayload(emptyMap(), hasNotification = false))
        assertEquals(
            PushPayload.Ignored,
            parsePushPayload(mapOf("type" to "turn_keepalive", "serverId" to "s"), hasNotification = false),
        )
    }

    // --- turnNotificationTag --------------------------------------------------

    @Test
    fun `tag matches the Worker collapse key`() {
        // "t-" + sha256("<hostId>|thread-1|turn-1")[0, 32), as in the Worker's turnCollapseKey.
        assertEquals(
            "t-c40ddcaca6f4df6ccdae6f865e703d72",
            turnNotificationTag(serverId, "thread-1", "turn-1"),
        )
    }

    @Test
    fun `tag is stable and isolates hosts turns and threads`() {
        val tag = turnNotificationTag(serverId, "thread-1", "turn-1")
        assertEquals(tag, turnNotificationTag(serverId, "thread-1", "turn-1"))
        assertNotEquals(tag, turnNotificationTag("alleycat:${"0".repeat(64)}", "thread-1", "turn-1"))
        assertNotEquals(tag, turnNotificationTag(serverId, "thread-1", "turn-2"))
        assertNotEquals(tag, turnNotificationTag(serverId, "thread-2", "turn-1"))
        assertTrue(turnNotificationTag(serverId, "thread-1", null).matches(Regex("t-[0-9a-f]{32}")))
    }

    // --- shouldSuppressTurnNotification ---------------------------------------

    @Test
    fun `suppressed only when foreground and viewing the same thread`() {
        val visible = ThreadKey(serverId = serverId, threadId = "thread-1")
        assertTrue(shouldSuppressTurnNotification(serverId, "thread-1", appInForeground = true, visibleThread = visible))
        assertFalse(shouldSuppressTurnNotification(serverId, "thread-1", appInForeground = false, visibleThread = visible))
        assertFalse(shouldSuppressTurnNotification(serverId, "thread-2", appInForeground = true, visibleThread = visible))
        assertFalse(shouldSuppressTurnNotification(serverId, "thread-1", appInForeground = true, visibleThread = null))
    }

    @Test
    fun `same threadId on another host is not suppressed`() {
        val visible = ThreadKey(serverId = "alleycat:${"0".repeat(64)}", threadId = "thread-1")
        assertFalse(shouldSuppressTurnNotification(serverId, "thread-1", appInForeground = true, visibleThread = visible))
    }

    // --- pushRegistrationFor --------------------------------------------------

    @Test
    fun `registration is built for an enabled device with a token`() {
        val registration = pushRegistrationFor(" fcm-token ", notificationsEnabled = true)!!
        assertEquals(AppPushPlatform.ANDROID, registration.platform)
        assertEquals("fcm-token", registration.token)
        assertNull(registration.apnsEnvironment)
        assertEquals(PUSH_WORKER_BASE_URL, registration.workerBaseUrl)
    }

    @Test
    fun `registration is cleared when notifications are disabled or there is no token`() {
        assertNull(pushRegistrationFor("fcm-token", notificationsEnabled = false))
        assertNull(pushRegistrationFor(null, notificationsEnabled = true))
        assertNull(pushRegistrationFor("   ", notificationsEnabled = true))
    }

    // --- fallback text --------------------------------------------------------

    @Test
    fun `fallback text matches the Worker copy`() {
        assertEquals("任务已完成" to "点击查看结果", turnNotificationFallbackText(TurnPushKind.COMPLETED))
        assertEquals(
            "任务未完成" to "任务失败或已中断，点击查看详情",
            turnNotificationFallbackText(TurnPushKind.FAILED),
        )
    }
}
