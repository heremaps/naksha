package com.here.xyz.pub.handlers;

import com.here.xyz.models.hub.Subscription;
import com.here.xyz.pub.db.PubDatabaseHandler;
import com.here.xyz.pub.models.JdbcConnectionParams;
import com.here.xyz.pub.models.PubConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PubJobHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger();

    private PubConfig pubCfg;
    private JdbcConnectionParams adminDBConnParams;
    // Subscription handling Thread Pool
    private static ThreadPoolExecutor subHandlingPool;



    public PubJobHandler(final PubConfig pubCfg, final JdbcConnectionParams adminDBConnParams) {
        this.pubCfg = pubCfg;
        this.adminDBConnParams = adminDBConnParams;
    }

    // Called once per job execution, to fetch and process all "active" subscriptions
    @Override
    public void run() {
        Thread.currentThread().setName("pub-job");
        logger.debug("Starting publisher job...");
        try {

            // Fetch all active subscriptions from AdminDB::xyz_config::xyz_subscription table
            List<Subscription> subList = PubDatabaseHandler.fetchAllSubscriptions(adminDBConnParams);
            if (subList == null || subList.isEmpty()) {
                logger.debug("No active subscriptions to be processed.");
                return;
            }

            logger.debug("{} active subscriptions to be processed.", subList.size());

            // Distribute subscriptions amongst thread pool to perform parallel publish (configurable poolSize e.g. 10 threads)
            distributeSubscriptionProcessing(pubCfg, subList);

            logger.debug("All subscription processing completed");
        }
        catch (Exception ex) {
            logger.error("Exception while running Publisher job. ", ex);
        }
        logger.debug("Publisher job finished!");
    }



    // Blocking function which distributes subscriptions to a thread pool and waits for all threads to complete
    private void distributeSubscriptionProcessing(final PubConfig pubCfg, final List<Subscription> subList) throws InterruptedException, ExecutionException {
        // create thread pool (if doesn't exist already)
        if (subHandlingPool == null) {
            subHandlingPool = new ThreadPoolExecutor(pubCfg.TXN_PUB_TPOOL_CORE_SIZE,
                    pubCfg.TXN_PUB_TPOOL_MAX_SIZE,
                    pubCfg.TXN_PUB_TPOOL_KEEP_ALIVE_SEC,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(), // queue with zero capacity
                    new ThreadPoolExecutor.CallerRunsPolicy()); // on reaching queue limit, caller thread itself is used for execution
        }
        // distribute subscriptions to thread pool
        final List<Future> fList = new ArrayList<Future>(subList.size());
        for (final Subscription sub : subList) {
            final Future f = subHandlingPool.submit(new PubSubscriptionHandler(pubCfg, adminDBConnParams, sub));
            fList.add(f);
        }
        // NOTE : We should not wait for completion of all threads, otherwise one buzy/long thread
        // can hold up restart of the entire job (thereby delaying other subscriptions as well)
        /*
        for (Future f : fList) {
            f.get();
        }*/
    }

}
