package com.here.xyz.pub.handlers;

import com.here.xyz.models.hub.Subscription;
import com.here.xyz.pub.db.PubDatabaseHandler;
import com.here.xyz.pub.db.PubJdbcConnectionPool;
import com.here.xyz.pub.models.*;
import com.here.xyz.pub.util.CustomLinkedBlockingQueue;
import com.here.xyz.pub.util.PubUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PubSubscriptionHandler implements Runnable{
    private static final Logger logger = LogManager.getLogger();

    private PubConfig pubCfg;
    private JdbcConnectionParams adminDBConnParams;
    private Subscription sub;

    public PubSubscriptionHandler(final PubConfig pubCfg, final JdbcConnectionParams adminDBConnParams, final Subscription sub) {
        this.pubCfg = pubCfg;
        this.adminDBConnParams = adminDBConnParams;
        this.sub = sub;
    }

    // Called once per "active" subscription to be processed
    @Override
    public void run() {
        final String subId = sub.getId();
        final String spaceId = sub.getSource();
        boolean lockAcquired = false;
        Connection lockConn = null;
        Thread.currentThread().setName("pub-job-subId-"+subId);

        logger.debug("Starting publisher for subscription Id [{}], spaceId [{}]...", subId, spaceId);
        try {
            // Acquire distributed lock against subscription Id
            // If unsuccessful, then return gracefully (likely another thread is processing this subscription)
            lockConn = PubJdbcConnectionPool.getConnection(adminDBConnParams);
            lockAcquired = PubDatabaseHandler.advisoryLock(subId, lockConn);
            if (!lockAcquired) {
                logger.debug("Couldn't acquire lock for subscription Id [{}]. Some other thread might be processing the same.", subId);
                return;
            }

            // Fetch last txn_id from AdminDB::xyz_config::xyz_txn_pub table
            // if no entry found, then start with -1
            final PublishEntryDTO lastTxn = PubDatabaseHandler.fetchLastTxnIdForSubId(subId, adminDBConnParams);
            logger.debug("For subscription Id [{}], spaceId [{}], the LastTxnId obtained as [{}]", subId, spaceId, lastTxn);

            // Fetch SpaceDB Connection details from AdminDB::xyz_config::xyz_space and xyz_storage tables
            // if no entry found, then log error and return
            JdbcConnectionParams spaceDBConnParams = PubDatabaseHandler.fetchDBConnParamsForSpaceId(spaceId, adminDBConnParams);
            if (spaceDBConnParams==null) {
                logger.error("Can't process subscription [{}] for spaceId [{}], as SpaceDB details couldn't be found", subId, spaceId);
                return;
            }
            logger.debug("Subscription Id [{}], spaceId [{}], to be processed against database {} with user {}",
                    subId, spaceId, spaceDBConnParams.getDbUrl(), spaceDBConnParams.getUser());

            // Fetch all new transactions (in right order) from SpaceDB::xyz_config::xyz_transactions and space tables
            // if no new transactions found, then return gracefully
            List<PubTransactionData> txnList = null;
            boolean txnFound = false;
            // flag used to broadcast failure of one task across other waiting tasks in a thread pool
            final AtomicReference<Boolean> failureFlagRef = new AtomicReference<>(Boolean.FALSE);
            ExecutorService pubThreadPool = null;
            while (
                (txnList =
                    PubDatabaseHandler.fetchPublishableTransactions(spaceDBConnParams, spaceId, lastTxn, pubCfg.TXN_PUB_FETCH_SIZE)
                ) != null
            ) {
                txnFound = true;
                // Handover publish of txnList to a thread pool asynchronously,
                // so that meanwhile, we can go back and fetch another set from DB
                if (pubThreadPool == null) {
                    pubThreadPool = new ThreadPoolExecutor(1, 1,60, TimeUnit.SECONDS,
                            new CustomLinkedBlockingQueue(2), // blocking queue with predefined capacity
                            new ThreadPoolExecutor.AbortPolicy()); // on reaching queue limit, throw RejectedExecutionException
                }
                if (failureFlagRef.get().booleanValue() == true) {
                    throw new RejectedExecutionException("Skipping publishing of a set starting with [" + txnList.get(0).getTxnId()
                            + ":" + txnList.get(0).getTxnRecId() + "], subId=" + subId
                            + ", spaceId=" + spaceId + ", as error observed in a thread pool.");
                }
                // if queue capacity is reached, then submission will be blocked until there is a room to accept new task
                pubThreadPool.submit(new PubTxnListHandler(txnList, failureFlagRef,
                                                        lastTxn.getLastTxnId(), lastTxn.getLastTxnRecId(),
                                                        sub, pubCfg, adminDBConnParams));
                // Update lastTxn, so we can fetch next set of transactions
                int lotSize = txnList.size();
                lastTxn.setLastTxnId( txnList.get(lotSize-1).getTxnId() );
                lastTxn.setLastTxnRecId( txnList.get(lotSize-1).getTxnRecId() );
            }
            if (!txnFound) {
                logger.debug("No publishable transactions found for subId [{}], space [{}]", subId, spaceId);
                // No transaction found. Make an insert into publisher table with lastTxnId as -1
                PubDatabaseHandler.saveLastTxnId(adminDBConnParams, subId, lastTxn);
            }
            else if (pubThreadPool != null) {
                // Wait for all threads to finish
                pubThreadPool.shutdown();
                while(!pubThreadPool.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
        catch (RejectedExecutionException re) {
            logger.info("Exception submitting a publish task for subscription Id [{}]. Skipping rest of the execution, as it will be attempted again in next scheduled job. ",
                    subId, re);
        }
        catch (Exception ex) {
            logger.error("{} - Exception in publisher job for subscription Id [{}]. ",
                            PubLogConstants.LOG_CODE_PUBLISH_ERROR, subId, ex);
        }
        finally {
            // Release lock against subscription Id (if it was acquired)
            try {
                if (lockAcquired && !PubDatabaseHandler.advisoryUnlock(subId, lockConn)) {
                    logger.warn("Couldn't release lock for subscription Id [{}]. If problem persist, it might need manual intervention.", subId);
                }
                if (lockConn != null) {
                    lockConn.close();
                }
            } catch (SQLException e) {
                logger.warn("Exception while releasing publisher lock for subscription Id [{}]. If problem persist, it might need manual intervention.", subId);
            }
        }
        logger.debug("Publisher job completed for subscription Id [{}], spaceId [{}]...", subId, spaceId);
        return;
    }




}
