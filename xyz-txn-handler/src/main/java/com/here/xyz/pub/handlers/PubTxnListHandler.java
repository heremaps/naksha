package com.here.xyz.pub.handlers;

import com.here.xyz.models.hub.Subscription;
import com.here.xyz.pub.db.PubDatabaseHandler;
import com.here.xyz.pub.models.*;
import com.here.xyz.pub.util.PubUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class PubTxnListHandler implements Runnable{
    private static final Logger logger = LogManager.getLogger();

    private List<PubTransactionData> txnList;
    private AtomicReference<Boolean> failureFlagRef;
    private long lastTxnId;
    private long lastTxnRecId;
    private Subscription sub;
    private PubConfig pubCfg;
    private JdbcConnectionParams adminDBConnParams;

    public PubTxnListHandler(final List<PubTransactionData> txnList, final AtomicReference<Boolean> failureFlagRef,
                             final long lastTxnId, final long lastTxnRecId,
                             final Subscription sub, final PubConfig pubCfg, final JdbcConnectionParams adminDBConnParams) {
        this.txnList = txnList;
        this.failureFlagRef = failureFlagRef;
        this.lastTxnId = lastTxnId;
        this.lastTxnRecId = lastTxnRecId;
        this.sub = sub;
        this.pubCfg = pubCfg;
        this.adminDBConnParams = adminDBConnParams;
    }

    // Called once per "txnList" (set of transactions) to be published
    @Override
    public void run() {
        final String subId = sub.getId();
        final String spaceId = sub.getSource();

        try {
            // check if there is already failure registered by previous task execution
            if (failureFlagRef.get().booleanValue() == true) {
                logger.info("Skipping publishing of a set starting with [{}:{}], subId={}, spaceId={}, due to previous error in thread pool.",
                        txnList.get(0).getTxnId(), txnList.get(0).getTxnRecId(), subId, spaceId);
                return;
            }
            // Handover transactions to appropriate Publisher (e.g. DefaultSNSPublisher)
            PublishEntryDTO lastTxn = null;
            try {
                lastTxn = PubUtil.getPubInstance(sub).publishTransactions(pubCfg, sub, txnList, lastTxnId, lastTxnRecId);
            } finally {
                if (lastTxn != null) {
                    // Update last txn_id in AdminDB::xyz_config::xyz_txn_pub table
                    PubDatabaseHandler.saveLastTxnId(adminDBConnParams, subId, lastTxn);
                }
            }
        } catch (Exception ex) {
            logger.error("{} - Exception in publisher job for subId={}, spaceId={}. ",
                    PubLogConstants.LOG_CODE_PUBLISH_ERROR, subId, spaceId, ex);
            failureFlagRef.set(Boolean.TRUE); // set the flag to broadcast to other tasks in the same thread pool
        }
    }

}
