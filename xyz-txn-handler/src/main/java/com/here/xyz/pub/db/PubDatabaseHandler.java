package com.here.xyz.pub.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.mapcreator.ext.naksha.NPsqlPoolConfig;
import com.here.xyz.XyzSerializable;
import com.here.xyz.models.hub.Subscription;
import com.here.mapcreator.ext.naksha.NPsqlConnectorParams;
import com.here.xyz.pub.models.*;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PubDatabaseHandler {
    private static final Logger logger = LogManager.getLogger();

    final private static String LOCK_SQL
            = "SELECT pg_try_advisory_lock( ('x' || md5('%s') )::bit(60)::bigint ) AS success";

    final private static String UNLOCK_SQL
            = "SELECT pg_advisory_unlock( ('x' || md5('%s') )::bit(60)::bigint ) AS success";

    final private static String FETCH_ALL_SUBSCRIPTIONS =
            "SELECT s.id, s.source, s.config " +
                    "FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_subscription s " +
                    "WHERE s.status->>'state' = 'ACTIVE'";

    final private static String FETCH_TXN_ID_FOR_SUBSCRIPTION =
            "SELECT t.last_txn_id, t.last_txn_rec_id FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_txn_pub t WHERE t.subscription_id = ?";

    final private static String FETCH_CONN_DETAILS_FOR_SPACE =
            "SELECT " +
            "  st.id AS id, " + // 1
            "  COALESCE(sp.config->'storage'->'params'->>'tableName', sp.id) AS tableName " + // 2
            "  st.config->'params' AS params " + // 3
            "FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_storage st, "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_space sp " +
            "WHERE st.id = sp.config->'storage'->>'id' AND sp.id = ? ";

    // TODO : LIMIT can split transactional data into multiple fetches.
    //      It may require a fix in future, if it needs to be transactional batch publish.
    final private static String SCHEMA_STR = "{{SCHEMA}}";
    final private static String TABLE_STR = "{{TABLE}}";
    final private static String FETCH_TXNS_FROM_SPACEDB =
            "SELECT t.id, h.i, h.jsondata, h.jsondata->'properties'->'@ns:com:here:xyz'->>'action' AS action, h.jsondata->>'id' AS featureId " +
            "FROM "+SCHEMA_STR+".\""+TABLE_STR+"\" h, "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".transactions t " +
            "WHERE "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".naksha_json_txn(h.jsondata) = t.txn " +
            "AND "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".naksha_json_txn_ts(h.jsondata) = "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".naksha_uuid_ts("+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".naksha_uuid_to_bytes(t.txn)) " +
            "AND t.space = ? AND (t.id > ? OR (t.id = ? AND h.i > ?)) " +
            "ORDER BY t.id ASC, h.i ASC " +
            "LIMIT 50";

    final private static String UPDATE_PUB_TXN_ID =
            "UPDATE "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_txn_pub " +
            "SET last_txn_id = ? , last_txn_rec_id = ? , updated_at = now() " +
            "WHERE subscription_id = ? ";

    final private static String INSERT_PUB_TXN_ID =
            "INSERT INTO "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_txn_pub (subscription_id, last_txn_id, last_txn_rec_id, updated_at) " +
            "VALUES (? , ? , ? , now()) ";

    final private static String FETCH_CONNECTORS_AND_SPACES =
            "SELECT " +
            "  st.id AS connectorId, " + // 1
            "  st.config->'params' AS params, " + // 2
            "  array_agg(sp.id) AS spaceIds, "+ // 3
            "  array_agg(COALESCE(sp.config->'storage'->'params'->>'tableName', sp.id)) AS tableNames " + // 4
            "FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_space sp, "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".xyz_storage st " +
            "WHERE sp.config->'storage'->>'id' = st.id AND st.id != ? " +
            "GROUP BY connectorId";

    final private static String SPACE_UNION_CLAUSE_STR = "{{SPACE_UNION_CLAUSE}}";
    final private static String UPDATE_TXN_SEQUENCE =
            "WITH sel AS ( " +
            "       "+ SPACE_UNION_CLAUSE_STR +" " +
            "    ), " +
            "    ranked_seq AS ( " +
            "        SELECT i, rank() OVER (ORDER BY txn ASC, i ASC) seq " +
            "        FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".transactions " +
            "        WHERE id IS NULL ORDER BY txn ASC, i ASC " +
            "    ) " +
            "UPDATE "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".transactions t " +
            "SET space = sel.sname, " +
            "    id = (SELECT COALESCE(max(id),0) FROM "+PubConfig.XYZ_ADMIN_DB_CFG_SCHEMA+".transactions)+ranked_seq.seq, " +
            "    ts = now() " +
            "FROM sel, ranked_seq " +
            "WHERE t.\"schema\" = sel.skima AND t.\"table\" = sel.tname " +
            "AND t.i = ranked_seq.i AND t.id IS null ";




    private static boolean _advisory(final String lockName, final Connection connection, final boolean lock) throws SQLException {
        boolean success = false;
        final boolean autoCommitFlag = connection.getAutoCommit();
        connection.setAutoCommit(true);

        try(final Statement stmt = connection.createStatement();
            final ResultSet rs = stmt.executeQuery(String.format(Locale.US, lock? LOCK_SQL : UNLOCK_SQL, lockName));) {
            if (rs.next()) {
                success = rs.getBoolean("success");
            }
        }
        connection.setAutoCommit(autoCommitFlag);
        return success;
    }

    public static boolean advisoryLock(final String lockName, final Connection conn) throws SQLException {
        return _advisory(lockName, conn,true);
    }

    public static boolean advisoryUnlock(final String lockName, final Connection conn) throws SQLException {
        return _advisory(lockName, conn,false);
    }

    // Fetch all active subscriptions from AdminDB::xyz_config::xyz_subscription table
    public static List<Subscription> fetchAllSubscriptions(final JdbcConnectionParams dbConnParams) throws SQLException {
        List<Subscription> subList = null;

        try (final Connection conn = PubJdbcConnectionPool.getConnection(dbConnParams);
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(FETCH_ALL_SUBSCRIPTIONS);
            ) {
            while (rs.next()) {
                final String cfgJsonStr = rs.getString("config");
                final Subscription sub = new Subscription();
                sub.setConfig(Json.decodeValue(cfgJsonStr, Subscription.SubscriptionConfig.class));
                sub.setId(rs.getString("id"));
                sub.setSource(rs.getString("source"));
                if (subList == null) {
                    subList = new ArrayList<>();
                }
                subList.add(sub);
            }
        }
        return subList;
    }

    // Fetch last_txn_id, last_txn_rec_id from AdminDB::xyz_config::xyz_txn_pub table
    public static PublishEntryDTO fetchLastTxnIdForSubId(final String subId, final JdbcConnectionParams dbConnParams) throws SQLException {
        final PublishEntryDTO dto = new PublishEntryDTO(-1, -1);

        try (final Connection conn = PubJdbcConnectionPool.getConnection(dbConnParams);
             final PreparedStatement stmt = conn.prepareStatement(FETCH_TXN_ID_FOR_SUBSCRIPTION);
        ) {
            stmt.setString(1, subId);
            final ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                dto.setLastTxnId(rs.getLong("last_txn_id"));
                dto.setLastTxnRecId(rs.getLong("last_txn_rec_id"));
            }
            rs.close();
        }
        return dto;
    }

    // Fetch DB Connection details for a given spaceId
    public static JdbcConnectionParams fetchDBConnParamsForSpaceId(final String spaceId,
                           final JdbcConnectionParams dbConnParams) throws SQLException, GeneralSecurityException {
        JdbcConnectionParams spaceDBConnParams = null;
        try (final Connection conn = PubJdbcConnectionPool.getConnection(dbConnParams);
             final PreparedStatement stmt = conn.prepareStatement(FETCH_CONN_DETAILS_FOR_SPACE);
        ) {
            stmt.setString(1, spaceId);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final String id = rs.getString(1);
                    final String tableName = rs.getString(2);
                    try {
                        final String rawParams = rs.getString(3);
                        final NPsqlConnectorParams params = XyzSerializable.deserialize(rawParams, NPsqlConnectorParams.class);
                        if (params == null)
                            throw new NullPointerException("params");
                        final NPsqlPoolConfig dbConfig = params.getDbConfig();
                        spaceDBConnParams = new JdbcConnectionParams();
                        spaceDBConnParams.setSpaceId(spaceId);
                        spaceDBConnParams.setDbUrl(dbConfig.url);
                        spaceDBConnParams.setUser(dbConfig.user);
                        spaceDBConnParams.setPswd(dbConfig.password);
                        spaceDBConnParams.setSchema(params.getSpaceSchema());
                        spaceDBConnParams.setTableName(tableName);
                    } catch (JsonProcessingException | NullPointerException e) {
                        logger.error("Failed to parse the configuration of connector {}", id);
                    }
                }
            }
        }
        return spaceDBConnParams;
    }

    public static List<PubTransactionData> fetchPublishableTransactions(
            final JdbcConnectionParams spaceDBConnParams, final String spaceId, final PublishEntryDTO lastTxn) throws SQLException {
        List<PubTransactionData> txnList = null;

        try (final Connection conn = PubJdbcConnectionPool.getConnection(spaceDBConnParams);) {
            final String SEL_STMT_STR = FETCH_TXNS_FROM_SPACEDB.replace(SCHEMA_STR, spaceDBConnParams.getSchema())
                                            .replace(TABLE_STR, spaceDBConnParams.getTableName()+"_hst");
            logger.debug("Fetch Transactions statement for spaceId [{}] is [{}]", spaceId, SEL_STMT_STR);

            final long startTS = System.currentTimeMillis();
            int rowCnt = 0;
            try (final PreparedStatement stmt = conn.prepareStatement(SEL_STMT_STR)) {
                stmt.setString(1, spaceId);
                stmt.setLong(2, lastTxn.getLastTxnId());
                stmt.setLong(3, lastTxn.getLastTxnId());
                stmt.setLong(4, lastTxn.getLastTxnRecId());
                final ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    final PubTransactionData pubTxnData = new PubTransactionData();
                    pubTxnData.setTxnId(rs.getLong("id"));
                    pubTxnData.setTxnRecId(rs.getLong("i"));
                    pubTxnData.setJsonData(rs.getString("jsonData"));
                    pubTxnData.setAction(rs.getString("action"));
                    pubTxnData.setFeatureId(rs.getString("featureId"));
                    // add to the list
                    if (txnList == null) {
                        txnList = new ArrayList<>();
                    }
                    txnList.add(pubTxnData);
                    rowCnt++;
                }
                rs.close();
            }
            final long duration = System.currentTimeMillis() - startTS;
            if (duration > TimeUnit.SECONDS.toMillis(5) || logger.isDebugEnabled()) {
                logger.info("Publisher took {}ms to fetch {} publishable transactions for space {}.",
                        duration, rowCnt, spaceId);
            }
        }
        return txnList;
    }


    public static void saveLastTxnId(
            final JdbcConnectionParams spaceDBConnParams, final String subId, final PublishEntryDTO lastTxn) throws SQLException {
        int rowCnt = 0;

        // UPDATE or INSERT into xyz_txn_pub table
        try (final Connection conn = PubJdbcConnectionPool.getConnection(spaceDBConnParams)) {
            // First try UPDATE
            try (final PreparedStatement stmt = conn.prepareStatement(UPDATE_PUB_TXN_ID)) {
                stmt.setLong(1, lastTxn.getLastTxnId());
                stmt.setLong(2, lastTxn.getLastTxnRecId());
                stmt.setString(3, subId);
                rowCnt = stmt.executeUpdate();
            }
            if (rowCnt > 0) {
                // UPDATE was successful, return from here
                conn.commit();
                return;
            }

            // UPDATE was unsuccessful, try INSERT
            try (final PreparedStatement stmt = conn.prepareStatement(INSERT_PUB_TXN_ID)) {
                stmt.setString(1, subId);
                stmt.setLong(2, lastTxn.getLastTxnId());
                stmt.setLong(3, lastTxn.getLastTxnRecId());
                stmt.executeUpdate();
            }
            conn.commit();
        }
    }


    public static List<ConnectorDTO> fetchConnectorsAndSpaces(final JdbcConnectionParams spaceDBConnParams,
            final String exclConnectorId) throws SQLException, GeneralSecurityException {

        List<ConnectorDTO> connectorList = null;
        try (final Connection conn = PubJdbcConnectionPool.getConnection(spaceDBConnParams);
             final PreparedStatement stmt = conn.prepareStatement(FETCH_CONNECTORS_AND_SPACES);
        ) {
            stmt.setString(1, exclConnectorId);
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final ConnectorDTO dto;
                final String connectorId = rs.getString(1);
                try {
                    final String rawParams = rs.getString(2);
                    final NPsqlConnectorParams params = XyzSerializable.deserialize(rawParams, NPsqlConnectorParams.class);
                    if (params == null) throw new NullPointerException("params");
                    final NPsqlPoolConfig dbConfig = params.getDbConfig();
                    dto = new ConnectorDTO();
                    dto.setDbUrl(dbConfig.url);
                    dto.setUser(dbConfig.user);
                    dto.setPswd(dbConfig.password);
                    dto.setSchema(params.getSpaceSchema());
                } catch (JsonProcessingException|NullPointerException e) {
                    logger.error("Failed to parse the configuration of connector {}", connectorId);
                    continue;
                }
                dto.setId(connectorId);
                final String[] spaceIds = (String[]) rs.getArray(3).getArray();
                dto.setSpaceIds(Arrays.asList(spaceIds));
                final String[] tableNames = (String[]) rs.getArray(4).getArray();
                dto.setTableNames(Arrays.asList(tableNames));
                // add to the list
                if (connectorList == null) {
                    connectorList = new ArrayList<>();
                }
                connectorList.add(dto);
            }
            rs.close();
        }
        return connectorList;
    }


    /*
    ** Function updates space, id and ts in xyz_config.transactions table for newer entries (where id is null).
    **      - Space is updated by matching respective "schema" and "table"
    **      - Id is updated in a sequence number in order of txn
    */
    public static void updateTransactionSequence(final JdbcConnectionParams spaceDBConnParams,
                                                 final SeqJobRequest seqJobRequest) throws SQLException {
        int rowCnt = 0;

        try (final Connection conn = PubJdbcConnectionPool.getConnection(spaceDBConnParams)) {
            // Prepare union clause out of all spaces available in present request, like:
            //      SELECT 'xyz_spaces' skima, '39ZVK252_table' tname, '39ZVK252' sname
            //      UNION ALL SELECT 'xyz_spaces' skima, 'naksha_test_table' tname, 'naksha_test' sname
            //      ....
            final List<String> spaceIdList = seqJobRequest.getSpaceIds();
            final List<String> tableNameList = seqJobRequest.getTableNames();
            final List<String> schemaList = seqJobRequest.getSchemas();
            final StringBuilder strBuilder = new StringBuilder("");
            for (int i=0; i < spaceIdList.size(); i++) {
                if (i>0) {
                    strBuilder.append("UNION ALL ");
                }
                strBuilder.append("SELECT '");
                strBuilder.append(schemaList.get(i));
                strBuilder.append("' AS skima, '");
                strBuilder.append(tableNameList.get(i));
                strBuilder.append("' AS tname, '");
                strBuilder.append(spaceIdList.get(i));
                strBuilder.append("' AS sname \n");
            }
            final String UPD_STMT_STR = UPDATE_TXN_SEQUENCE.replace(SPACE_UNION_CLAUSE_STR, strBuilder.toString());
            logger.debug("Transaction Sequencer statement for DB [{}] is [{}]", spaceDBConnParams.getDbUrl(), UPD_STMT_STR);

            final long startTS = System.currentTimeMillis();
            try (final PreparedStatement stmt = conn.prepareStatement(UPD_STMT_STR)) {
                rowCnt = stmt.executeUpdate();
            }
            if (rowCnt > 0) {
                conn.commit();
            }
            final long duration = System.currentTimeMillis() - startTS;
            if (duration > TimeUnit.SECONDS.toMillis(1) || logger.isDebugEnabled()) {
                logger.info("Transaction Sequencer DB update for [{}] took {}ms and updated {} records",
                        spaceDBConnParams.getDbUrl(), duration, rowCnt);
            }
            return;
        }
    }

}
