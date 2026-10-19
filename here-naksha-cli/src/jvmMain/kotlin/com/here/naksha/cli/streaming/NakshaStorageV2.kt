package com.here.naksha.cli.streaming

import naksha.base.unsupportedOp
import naksha.model.AbstractStorage
import naksha.model.IReadSession
import naksha.model.IStreamSession
import naksha.model.IWriteSession
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaStorage
import kotlin.reflect.KClass

class NakshaStorageV2(override val config: NakshaStorage) : AbstractStorage<NakshaStorage>() {
    override val configKlass: KClass<NakshaStorage>
        get() = NakshaStorage::class

    override fun initStorage(
        config: NakshaStorage,
        create: Boolean?,
        upgrade: Boolean?
    ) {
        // TODO anything?
    }

    override fun afterInit() {
        throw unsupportedOp("afterInit is not supported in NakshaStorageV2")
    }

    override fun shutdownStorage(dropCache: Boolean) {
        throw unsupportedOp("shutdownStorage is not supported in NakshaStorageV2")
    }

    override fun newWriteSession(options: SessionOptions?): IWriteSession {
        throw unsupportedOp("newWriteSession is not supported in NakshaStorageV2")
    }

    override fun newReadSession(options: SessionOptions?): IReadSession {
        throw unsupportedOp("newReadSession is not supported in NakshaStorageV2")
    }

    override fun newStreamSession(options: SessionOptions?): IStreamSession {
        return StreamReadSessionV2(
            this,
            options?.socketTimeout ?: NakshaContext.defaultSocketTimeout.get(),
            options?.stmtTimeout ?: NakshaContext.defaultStmtTimeout.get(),
            options?.lockTimeout ?: NakshaContext.defaultLockTimeout.get(),
            options ?: SessionOptions())
    }
}