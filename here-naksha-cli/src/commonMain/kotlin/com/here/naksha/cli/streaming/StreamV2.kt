package com.here.naksha.cli.streaming

import naksha.base.NakshaError
import naksha.base.Version
import naksha.model.IStreamSession
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest

class StreamV2(session: IStreamSession, request: StreamRequest) : Stream(session, request) {
    override val minVersion: Version,
    override val unacknowledgedChunks: Array<StreamChunk>,
    override val acknowledgedChunks: Array<StreamChunk>,
    override val estimatedChunks: Long = -1
    override val estimatedTuples: Long = -1
    override val acknowledgedTuples: Long = 0
    override val unacknowledgedTuples: Long = 0
    override val isRecoverable: Boolean = false
    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun next(): StreamChunk {
        TODO("Not yet implemented")
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        TODO("Not yet implemented")
    }

    override fun acknowledge(chunk: StreamChunk) {
        TODO("Not yet implemented")
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun closeForRecovery(): StreamRequest {
        TODO("Not yet implemented")
    }

    override fun getRecoveryRequest(timeoutMillis: Long): StreamRequest {
        TODO("Not yet implemented")
    }
}