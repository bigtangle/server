/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;

/**
 * <p>A full pruned block store using the MySQL database engine. As an added bonus an address index is calculated,
 * so you can use {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly look up
 * the quantity of bitcoins controlled by that address.</p>
 */
 
public class CassandraBlockStore extends DatabaseFullPrunedBlockStore {
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "org.apache.cassandra.cql.jdbc.CassandraDriver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:cassandra://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE IF NOT EXISTS  "  + " bigtangle." +"settings (\n" +
            "    name varchar ,\n" +
            "    settingvalue blob,\n" +
            "     PRIMARY KEY (name)  \n" +
            ")\n";

    private static final String CREATE_HEADERS_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"headers (\n" +
            "    hash blob ,\n" +
            "    height bigint ,\n" +
            "    header blob ,\n" +
            "    wasundoable boolean ,\n" +
            "    prevblockhash  blob ,\n" +
            "    prevbranchblockhash  blob ,\n" +
            "    mineraddress blob,\n" +
            "    tokenid blob,\n" +
            "    blocktype bigint ,\n" +
            "     PRIMARY KEY (hash)  \n" +
            ")";


    private static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"undoableblocks (\n" +
            "    hash blob ,\n" +
            "    height bigint ,\n" +
            "    txoutchanges blob,\n" +
            "    transactions blob,\n" +
            "    PRIMARY KEY (hash)  \n" +
            ")\n";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"outputs (\n" +
            "    hash blob ,\n" +
            "    outputindex bigint ,\n" +
            "    height bigint ,\n" +
            "    coinvalue bigint ,\n" +
            "    scriptbytes blob ,\n" +
            "    toaddress text,\n" +
            "    addresstargetable int,\n" +
            "    coinbase boolean,\n" +
            "    blockhash  blob  ,\n" +
            "    tokenid blob,\n" +
            "    fromaddress text,\n" +
            "    description text,\n" +
            "    spent boolean ,\n" +
            "    confirmed boolean ,\n" +
            "    spendpending boolean ,\n" +
            "    spenderblockhash  blob,\n" +
            "    PRIMARY KEY (hash, outputindex)  \n" +
            ")\n";
    
    private static final String CREATE_TIPS_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"tips (\n" +
            "    hash blob ,\n" +
            "    PRIMARY KEY (hash)  \n" +
            ")\n";
 
    private static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"blockevaluation (\n" +
            "    blockhash blob ,\n" +
            "    rating bigint ,\n" +
            "    depth bigint,\n" +
            "    cumulativeweight  bigint ,\n" +
            "    solid boolean ,\n" +
            "    height bigint,\n" +
            "    milestone boolean,\n" +
            "    milestonelastupdate bigint,\n" +
            "    milestonedepth bigint,\n" +
            "    inserttime bigint,\n" +
            "    maintained boolean,\n" +
            "    rewardvalidityassessment boolean,\n" +
            "    PRIMARY KEY (blockhash) )\n";
    
    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"tokens (\n" +
            "    tokenid blob  ,\n" +
            "    tokenname text ,\n" + 
            "    amount bigint ,\n" +
            "    description text ,\n" + 
            "    blocktype bigint ,\n" +
            "    PRIMARY KEY (tokenid) \n)";
    
    
    private static final String CREATE_ORDERPUBLISH_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"orderpublish (\n" +
            "   orderid text ,\n"+
            "   address text,\n"+
            "   tokenid text,\n"+ 
            "   type bigint,\n"+ 
            "   validateto timestamp,\n"+ 
            "   validatefrom timestamp,\n"+ 
            "   price bigint,\n"+ 
            "   amount bigint,\n"+ 
            "   state bigint,\n"+ 
            "   market text,\n"+ 
            "   PRIMARY KEY (orderid) )";
    
    private static final String CREATE_ORDERMATCH_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"ordermatch (\n" +
            "   matchid text ,\n"+
            "   restingOrderId text,\n"+
            "   incomingOrderId text,\n"+ 
            "   type bigint,\n"+ 
            "   price bigint,\n"+ 
            "   executedQuantity bigint,\n"+ 
            "   remainingQuantity bigint,\n"+ 
            "   PRIMARY KEY (matchid) )";
    
    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE IF NOT EXISTS  " + " bigtangle." +"exchange (\n" +
            "   orderid text ,\n"+
            "   fromAddress text,\n"+
            "   fromTokenHex text,\n"+ 
            "   fromAmount text,\n"+ 
            "   toAddress text,\n"+ 
            "   toTokenHex text,\n"+ 
            "   toAmount text,\n"+ 
            "   data blob ,\n" +
            "   toSign bigint,\n"+ 
            "   fromSign bigint,\n"+ 
            "   toOrderId text,\n"+ 
            "   fromOrderId text,\n"+ 
            "   PRIMARY KEY (orderid) )";
    
    // Some indexes to speed up inserts
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_height_toaddress_idx ON outputs (hash, outputindex, height, toaddress) USING btree";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING btree";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING btree";
    private static final String CREATE_OUTPUTS_HASH_INDEX = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING btree";
    private static final String CREATE_UNDOABLE_TABLE_INDEX = "CREATE INDEX undoableblocks_height_idx ON undoableblocks (height) USING btree";
    private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";
    private static final String CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_restingOrderId_idx ON ordermatch (restingOrderId) USING btree";
    private static final String CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_incomingOrderId_idx ON ordermatch (incomingOrderId) USING btree";

      public CassandraBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
                                     String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName, fullStoreDepth, username, password, null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_SETTINGS_TABLE);
        sqlStatements.add(CREATE_HEADERS_TABLE);
        sqlStatements.add(CREATE_UNDOABLE_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
        sqlStatements.add(CREATE_BLOCKEVALUATION_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_ORDERPUBLISH_TABLE);
        sqlStatements.add(CREATE_ORDERMATCH_TABLE);
        sqlStatements.add(CREATE_EXCHANGE_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
      
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateSchemeSQL() {
        // do nothing
        return Collections.emptyList();
    }

    @Override
    protected String getDatabaseDriverClass() {
        return DATABASE_DRIVER_CLASS;
    }
   
    // Drop table SQL.
    protected String DROP_SETTINGS_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "settings";
    protected String DROP_HEADERS_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+"headers";
    protected String DROP_UNDOABLE_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "undoableblocks";
    protected String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "outputs";
    protected String DROP_TIPS_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "tips";
    protected String DROP_BLOCKEVALUATION_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "blockevaluation";
    protected String DROP_TOKENS_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "tokens";
    protected String DROP_ORDERPUBLISH_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "orderpublish";
    protected String DROP_ORDERMATCH_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "ordermatch";
    protected String DROP_EXCHANGE_TABLE = "DROP TABLE IF EXISTS " + "bigtangle."+ "exchange";
    
    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
       
    }

   
 
    protected String UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL = getUpdate() +" blockevaluation SET cumulativeweight = ? WHERE blockhash = ?";
     
    protected String UPDATE_BLOCKEVALUATION_REWARDVALIDITYASSESSMENT_SQL = getUpdate() +" blockevaluation SET rewardvalidityassessment = ? WHERE blockhash = ?";
    
    @Override
    protected String getUpdateSettingsSLQ() {
        return getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    }
    
    @Override
    protected String getUpdateHeadersSQL() {
        return getUpdate() +" headers SET wasundoable=? WHERE hash=?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationCumulativeweightSQL() {
        return   getUpdate() +" outputs SET spent = ?, spenderblockhash = ? WHERE hash = ? AND outputindex= ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return getUpdate() +" blockevaluation SET depth = ? WHERE blockhash = ?";
    }
    
    @Override
    public String getUpdateBlockEvaluationHeightSQL() {
        return getUpdate() +" blockevaluation SET height = ? WHERE blockhash = ?";
    }
    
    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return  getUpdate() +" blockevaluation SET milestone = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return getUpdate() +" blockevaluation SET rating = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationSolidSQL() {
        return getUpdate() +" blockevaluation SET solid = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMilestoneLastUpdateTimeSQL() {
        return  getUpdate() +" blockevaluation SET milestonelastupdate = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMilestoneDepthSQL() {
        return  getUpdate() +" blockevaluation SET milestonedepth = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMaintainedSQL() {
        return getUpdate() +" blockevaluation SET maintained = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateBlockEvaluationRewardValidItyassessmentSQL() {
        return getUpdate() +" blockevaluation SET maintained = ? WHERE blockhash = ?";
    }
    
    @Override
    protected String getUpdateOutputsSpentSQL() {
        return getUpdate() +" outputs SET spendpending = ? WHERE hash = ? AND outputindex= ?";
    }
    
    @Override
    protected String getUpdateOutputsConfirmedSQL() {
        return  getUpdate() +" outputs SET confirmed = ? WHERE hash = ? AND outputindex= ?";
    }
    
    @Override
    protected String getUpdateOutputsSpendPendingSQL() {
        return getUpdate() +" outputs SET spendpending = ? WHERE hash = ? AND outputindex= ?";
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        //cassandra is autocommit
    }

}
