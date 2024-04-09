import com.here.naksha.lib.jbon.SQL_INT16
import com.here.naksha.lib.jbon.SQL_INT32
import com.here.naksha.lib.jbon.SQL_INT64
import com.here.naksha.lib.jbon.SQL_STRING
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.IPlv8Plan
import com.here.naksha.lib.plv8.NakshaSession

internal class NakshaBulkLoaderPlan(
        val partition: Int?,
        val partitionHeadQuoted: String,
        val delCollectionIdQuoted: String,
        val hstCollectionIdQuoted: String,
        val session: NakshaSession) {
    internal var insertHeadPlan: IPlv8Plan? = null

    fun insertHeadPlan(): IPlv8Plan {
        if (insertHeadPlan == null) {
            insertHeadPlan = session.sql.prepare("INSERT INTO $partitionHeadQuoted ($COL_ALL) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)", COL_ALL_TYPES)
        }
        return insertHeadPlan!!
    }

    internal var updateHeadPlan: IPlv8Plan? = null
    fun updateHeadPlan(): IPlv8Plan {
        if (updateHeadPlan == null) {
            updateHeadPlan = session.sql.prepare("""
                UPDATE $partitionHeadQuoted 
                SET $COL_TXN_NEXT=$1, $COL_TXN=$2, $COL_UID=$3, $COL_PTXN=$4,$COL_PUID=$5,$COL_GEO_TYPE=$6,$COL_ACTION=$7,$COL_VERSION=$8,$COL_CREATED_AT=$9,$COL_UPDATE_AT=$10,$COL_AUTHOR_TS=$11,$COL_AUTHOR=$12,$COL_APP_ID=$13,$COL_GEO_GRID=$14,$COL_ID=$15,$COL_TAGS=$16,$COL_GEOMETRY=$17,$COL_FEATURE=$18,$COL_GEO_REF=$19,$COL_TYPE=$20 WHERE $COL_ID=$21
                """.trimIndent(),
                    arrayOf(*COL_ALL_TYPES, SQL_STRING))
        }
        return updateHeadPlan!!
    }

    internal var deleteHeadPlan: IPlv8Plan? = null
    fun deleteHeadPlan(): IPlv8Plan {
        if (deleteHeadPlan == null) {
            deleteHeadPlan = session.sql.prepare("""
                DELETE FROM $partitionHeadQuoted
                WHERE $COL_ID = $1
                """.trimIndent(),
                    arrayOf(SQL_STRING))
        }
        return deleteHeadPlan!!
    }

    internal var insertDelPlan: IPlv8Plan? = null
    fun insertDelPlan(): IPlv8Plan {
        if (insertDelPlan == null) {
            // ptxn + puid = txn + uid (as we generate new state in _del)
            insertDelPlan = session.sql.prepare("""
                INSERT INTO $delCollectionIdQuoted ($COL_ALL) 
                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_GEO_TYPE,$4,$5,$COL_CREATED_AT,$5,$6,$7,$8,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                    FROM $partitionHeadQuoted WHERE $COL_ID = $8""".trimIndent(),
                    arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING))
        }
        return insertDelPlan!!
    }

    internal var copyHeadToHstPlan: IPlv8Plan? = null
    fun copyHeadToHstPlan(): IPlv8Plan {
        if (copyHeadToHstPlan == null) {
            copyHeadToHstPlan = session.sql.prepare("""
            INSERT INTO $hstCollectionIdQuoted ($COL_ALL) 
            SELECT $1,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                FROM $partitionHeadQuoted WHERE $COL_ID = $2
            """.trimIndent(), arrayOf(SQL_INT64, SQL_STRING))
        }
        return copyHeadToHstPlan!!
    }

    internal var copyDelToHstPlan: IPlv8Plan? = null
    fun copyDelToHstPlan(): IPlv8Plan {
        if (copyDelToHstPlan == null) {
            copyDelToHstPlan = session.sql.prepare("INSERT INTO $hstCollectionIdQuoted ($COL_ALL) SELECT $COL_ALL FROM $delCollectionIdQuoted WHERE $COL_ID = $1", arrayOf(SQL_STRING))
        }
        return copyDelToHstPlan!!
    }
}