package com.here.naksha.cli.stream

import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.model.SessionOptions
import naksha.model.streaming.StreamException
import naksha.model.streaming.StreamRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamV2Test {
    @Test
    fun `closing an unread stream preserves recovery and rejects further reads`() {
        val session = mock<StreamReadSessionV2>()
        whenever(session.options).thenReturn(
            SessionOptions(appId = "stream-v2-test", stmtTimeout = 1_000)
        )

        val request = StreamRequest(
            databaseId = Id("naksha"),
            catalogId = Id("public"),
            collectionId = Id("dummy_collection"),
            minVersion = 133_039_218L,
            ignoreTransactions = true,
            chunkSize = 2
        )
        val stream = StreamV2(session, request)

        assertTrue(stream.unacknowledgedChunks.isEmpty())
        assertTrue(stream.hasNext())

        val closeError = assertFailsWith<StreamException> {
            stream.close()
        }

        assertEquals(NakshaError.CLOSED, closeError.error.code)
        assertEquals(request.minVersion, closeError.recoveryRequest.minVersion)
        assertTrue(stream.isClosed())
        assertFalse(stream.hasNext())

        val readError = assertFailsWith<NakshaException> {
            stream.next(100)
        }

        assertEquals(NakshaError.CLOSED, readError.error.code)
    }
}