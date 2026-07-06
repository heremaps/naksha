package com.here.xyz.pub.impl;

import com.here.xyz.models.hub.Subscription;
import com.here.xyz.pub.models.PubConfig;
import com.here.xyz.pub.models.PubTransactionData;
import com.here.xyz.pub.models.PublishEntryDTO;

import java.util.List;

public interface IPublisher {
    void publishTransactions(final PubConfig pubCfg, final Subscription sub,
                                        final List<PubTransactionData> txnList,
                                        final PublishEntryDTO pubDTO) throws Exception;

}
