package com.here.xyz.pub.impl;

import com.here.xyz.models.hub.Subscription;
import com.here.xyz.pub.mapper.IPubMsgMapper;
import com.here.xyz.pub.models.PubConfig;
import com.here.xyz.pub.models.PubLogConstants;
import com.here.xyz.pub.models.PubTransactionData;
import com.here.xyz.pub.models.PublishEntryDTO;
import com.here.xyz.pub.util.AwsUtil;
import com.here.xyz.pub.util.MessageUtil;
import com.here.xyz.pub.util.PubUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultSNSBatchPublisher implements IPublisher {
    private static final Logger logger = LogManager.getLogger();

    // Convert and publish transactions to desired SNS Topic
    @Override
    public PublishEntryDTO publishTransactions(final PubConfig pubCfg, final Subscription sub,
                                               final List<PubTransactionData> txnList,
                                               final long lastStoredTxnId, final long lastStoredTxnRecId) throws Exception {
        final String subId = sub.getId();
        final String spaceId = sub.getSource();
        final String snsTopic = PubUtil.getSnsTopicARN(sub);
        final long lotStartTS = System.currentTimeMillis();
        // local counters
        final PublishEntryDTO pubDTO = new PublishEntryDTO(lastStoredTxnId, lastStoredTxnRecId);
        int publishedRecCnt = 0;
        // Variables for batch publish
        final int TXN_LIST_SIZE = txnList.size();
        final int MAX_ALLOWED_BATCH_SIZE = 10;
        final long MAX_ALLOWED_PAYLOAD_SIZE = 240*1024; // keeping some buffer below 256K
        final String MSG_ID_PREFIX = "msg_idx_";
        final List<PublishBatchRequestEntry> batchEntries = new ArrayList<>();
        long aggrBatchPayloadSize = 0; // aggregated batch payload size
        int txnRecCnt = 0;
        int batchEntryCounter = 0;

        try {
            final IPubMsgMapper msgMapper = MessageUtil.getMsgMapperInstance(sub);
            // TODO : Support multi-region based on subscription configuration.
            // We may require region specific publisher job for the respective subscriptions.
            final SnsAsyncClient snsClient = AwsUtil.getSnsAsyncClient(pubCfg.AWS_DEFAULT_REGION);

            // Publish all transactions on SNS Topic (in the same order they were fetched)
            for (final PubTransactionData txnData : txnList) {
                txnRecCnt++;

                // Convert transaction payload into expected publishable format
                final String pubFormat = msgMapper.mapToPublishableFormat(sub, txnData);

                // Prepare SNS Notification message
                final String msg = MessageUtil.compressAndEncodeToString(pubFormat);
                final int msgLength = msg.length();
                final Map<String, MessageAttributeValue> msgAttrMap = populateMessageAttributeMap(txnData, sub, spaceId);

                // Prepare PublishBatchEntry for current message
                final PublishBatchRequestEntry batchEntry = PublishBatchRequestEntry.builder()
                        .message(msg)
                        .messageAttributes(msgAttrMap)
                        .id(MSG_ID_PREFIX + batchEntryCounter)
                        .build();

                // publish batch if payload limit reached
                if (msgLength+aggrBatchPayloadSize > MAX_ALLOWED_PAYLOAD_SIZE && batchEntries.size()>0) {
                    // publish current batch
                    publishBatchEntriesAndCheckResult(batchEntries, snsTopic, snsClient, txnList, subId, publishedRecCnt, MSG_ID_PREFIX, pubCfg, pubDTO);
                    // update batch variables
                    publishedRecCnt += batchEntries.size();
                    batchEntries.clear();
                    aggrBatchPayloadSize = 0;
                    batchEntryCounter = 0;
                }

                // add current message to the batch
                batchEntries.add(batchEntry);
                aggrBatchPayloadSize += msgLength;
                batchEntryCounter++;

                // publish batch if count limit reached
                if ( (batchEntries.size() >= MAX_ALLOWED_BATCH_SIZE)    // batch size exceeded
                    || (txnRecCnt >= TXN_LIST_SIZE)     // this is last record in the list
                ) {
                    // publish current batch
                    publishBatchEntriesAndCheckResult(batchEntries, snsTopic, snsClient, txnList, subId, publishedRecCnt, MSG_ID_PREFIX, pubCfg, pubDTO);
                    // update batch variables
                    publishedRecCnt += batchEntries.size();
                    batchEntries.clear();
                    aggrBatchPayloadSize = 0;
                    batchEntryCounter = 0;
                }
            }
        }
        finally {
            final long lotTimeTaken = System.currentTimeMillis() - lotStartTS;
            logger.info("Transaction publish stats for SNS [{}] [format => eventType,subId,spaceId,msgCount,timeTakenMs,lastTxnId,lastTxnRecId] - {} {} {} {} {} {} {}",
                    snsTopic, PubLogConstants.LOG_EVT_TXN_PUBLISH_STATS, subId, spaceId, publishedRecCnt, lotTimeTaken, pubDTO.getLastTxnId(), pubDTO.getLastTxnRecId());
        }

        return pubDTO;
    }


    private void publishBatchEntriesAndCheckResult(final List<PublishBatchRequestEntry> batchEntries,
                                   final String snsTopic, final SnsAsyncClient snsClient,
                                   final List<PubTransactionData> txnList, final String subId,
                                   final int publishedRecCnt, final String MSG_ID_PREFIX,
                                   final PubConfig pubCfg, final PublishEntryDTO pubDTO) throws Exception {
        // Prepare batch request
        final PublishBatchRequest batchRequest = PublishBatchRequest.builder()
                .topicArn(snsTopic)
                .publishBatchRequestEntries(batchEntries)
                .build();
        // Publish the batch request
        final CompletableFuture<PublishBatchResponse> futureResponse = snsClient.publishBatch(batchRequest);
        final PublishBatchResponse result = futureResponse.join();
        for (int i = 0; i < batchEntries.size(); i++) {
            final String matchStr = MSG_ID_PREFIX + i;
            final int txnRecIdx = publishedRecCnt + i;
            final long msgTxnId = txnList.get(txnRecIdx).getTxnId();
            final long msgTxnRecId = txnList.get(txnRecIdx).getTxnRecId();
            final String featureId = txnList.get(txnRecIdx).getFeatureId();
            final String action = txnList.get(txnRecIdx).getAction();
            boolean resultFound = false;
            // Find in the list of publish-success entries
            if (result.hasSuccessful()) {
                for (final PublishBatchResultEntry success : result.successful()) {
                    if (matchStr.equals(success.id())) {
                        resultFound = true;
                        // Record last successfully published transaction Id's
                        pubDTO.setLastTxnId(msgTxnId);
                        pubDTO.setLastTxnRecId(msgTxnRecId);
                        if (pubCfg.ENABLE_TXN_PUB_DETAILED_LOGGING) {
                            logger.info("Message no. [{}], txnId={}, txnRecId={}, featureId={}, action={}, published to SNS [{}] for subId [{}].",
                                    txnRecIdx+1, msgTxnId, msgTxnRecId, featureId, action, snsTopic, subId);
                        }
                        break;
                    }
                }
            }
            // Find in the list of publish-failed entries
            if (!resultFound && result.hasFailed()) {
                for (final BatchResultErrorEntry error : result.failed()) {
                    if (matchStr.equals(error.id())) {
                        resultFound = true;
                        if (pubCfg.ENABLE_TXN_PUB_DETAILED_LOGGING) {
                            logger.info("Message no. [{}], txnId={}, txnRecId={}, featureId={}, action={}, failed while publishing to SNS [{}] for subId [{}].",
                                    txnRecIdx + 1, msgTxnId, msgTxnRecId, featureId, action, snsTopic, subId);
                        }
                        // Raise error here to stop publishing
                        throw new Exception("Message no. ["+txnRecIdx+1+"], txnId="+msgTxnId+", " +
                                "txnRecId="+msgTxnRecId+", failed while publishing to SNS ["+snsTopic+"] for subId ["+subId+"]");
                    }
                }
            }
            if (!resultFound) {
                // Neither found in success list, nor in failed list
                if (pubCfg.ENABLE_TXN_PUB_DETAILED_LOGGING) {
                    logger.info("No result found for Message no. [{}], txnId={}, txnRecId={}, featureId={}, action={}, while publishing to SNS [{}] for subId [{}].",
                            txnRecIdx + 1, msgTxnId, msgTxnRecId, featureId, action, snsTopic, subId);
                }
                // Raise error here to stop publishing
                throw new Exception("No result found for Message no. ["+txnRecIdx+1+"], txnId="+msgTxnId+", " +
                        "txnRecId="+msgTxnRecId+", while publishing to SNS ["+snsTopic+"] for subId ["+subId+"]");
            }
        }
    }


    private Map<String, MessageAttributeValue> populateMessageAttributeMap(
            final PubTransactionData txnData, final Subscription sub, final String spaceId) {

        final Map<String, MessageAttributeValue> msgAttrMap = new HashMap<>();
        MessageUtil.addToAttributeMap(msgAttrMap, "action", txnData.getAction());
        MessageUtil.addToAttributeMap(msgAttrMap, "space", spaceId);
        MessageUtil.addToAttributeMap(msgAttrMap, "featureId", txnData.getFeatureId());
        // Add other custom attributes
        MessageUtil.addCustomFieldsToAttributeMap(msgAttrMap, sub, txnData.getJsonData());
        return msgAttrMap;
    }


}
