package com.akashark.agentbuddy.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akashark.agentbuddy.android.core.bridge.UniffiInit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.codex_mobile_client.*
import java.util.UUID

/** Opt-in fixture regression; use adb reverse tcp:18765 tcp:18765 and -e historyFixtureUrl. */
@RunWith(AndroidJUnit4::class)
class MobileHistoryDeviceTest {
    private fun users(snapshot: AppThreadSnapshot?): List<String> =
        snapshot?.hydratedConversationItems.orEmpty().mapNotNull {
            (it.content as? HydratedConversationItemContent.User)?.v1?.text
        }

    @Test
    fun sleepPaginationForkEditAndFailedRevert() = runBlocking {
        val endpoint = InstrumentationRegistry.getArguments().getString("historyFixtureUrl")
        assumeTrue("Start tools/qa/mobile-history-fixture.py and set historyFixtureUrl", !endpoint.isNullOrBlank())
        UniffiInit.ensure(InstrumentationRegistry.getInstrumentation().targetContext)
        val serverId = "qa-history-android-${UUID.randomUUID()}"
        ServerBridge().use { bridge ->
            AppClient().use { client ->
                AppStore().use { store ->
                    bridge.connectRemoteUrlServer(serverId, "History QA", endpoint!!)
                    try {
                        val key = client.readThread(serverId, AppReadThreadRequest("history", false))
                        store.loadThreadTurnsPage(key, null, 1u)
                        var snapshot = store.threadSnapshot(key)
                        assertEquals(listOf("prompt-3"), users(snapshot))
                        while (snapshot?.olderTurnsCursor != null) {
                            store.loadThreadTurnsPage(key, snapshot.olderTurnsCursor, 1u)
                            snapshot = store.threadSnapshot(key)
                        }
                        assertEquals(listOf("prompt-1", "prompt-2", "prompt-3"), users(snapshot))
                        val items = snapshot!!.hydratedConversationItems
                        assertEquals(items.size, items.map { it.id }.toSet().size)
                        assertEquals(3, items.count {
                            (it.content as? HydratedConversationItemContent.Note)?.v1?.body?.contains("2000 ms") == true
                        })
                        val fork = store.forkThreadFromMessage(key, 1u,
                            AppForkThreadFromMessageRequest(null, null, null, null, null, true))
                        assertEquals(listOf("prompt-1", "prompt-2"), users(store.threadSnapshot(fork)))
                        assertEquals(listOf("prompt-1", "prompt-2", "prompt-3"), users(store.threadSnapshot(key)))
                        assertEquals("prompt-2", store.editMessage(key, 1u))
                        assertEquals(listOf("prompt-1"), users(store.threadSnapshot(key)))
                        assertEquals("prompt-1", store.editMessage(key, 0u))
                        assertTrue(users(store.threadSnapshot(key)).isEmpty())
                        assertNull(store.threadSnapshot(key)?.olderTurnsCursor)
                        val failure = client.readThread(serverId, AppReadThreadRequest("history-error", true))
                        try {
                            store.editMessage(failure, 0u)
                            fail("Injected revert error must reach the caller")
                        } catch (error: ClientException) {
                            assertTrue(error.message.orEmpty().contains("Injected failure"))
                        }
                        assertEquals(listOf("prompt-1", "prompt-2", "prompt-3"), users(store.threadSnapshot(failure)))
                    } finally { bridge.disconnectServer(serverId) }
                }
            }
        }
    }
}
