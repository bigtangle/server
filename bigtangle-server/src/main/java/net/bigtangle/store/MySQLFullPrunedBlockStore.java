/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;

/**
 * <p>
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated.
 * </p>
 */

public class MySQLFullPrunedBlockStore extends DatabaseFullPrunedBlockStore {
    
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX =  "jdbc:mysql://"; //"jdbc:log4jdbc:mysql://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" 
            + "    name varchar(32) NOT NULL,\n"
            + "    settingvalue blob,\n" 
            + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" + ")\n";

    // TODO lower the sizes of stuff to their exact size limitations! use NetworkParameters!
    private static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n"
            + "    hash varbinary(32) NOT NULL,\n"
            + "    height bigint NOT NULL,\n"
            + "    block mediumblob NOT NULL,\n"
            + "    wasundoable boolean NOT NULL,\n" 
            + "    prevblockhash  varbinary(32) NOT NULL,\n"
            + "    prevbranchblockhash  varbinary(32) NOT NULL,\n" 
            + "    mineraddress varbinary(255),\n"
            + "    tokenid varbinary(255),\n" 
            + "    blocktype bigint NOT NULL,\n"
            + "    rating bigint ,\n"
            + "    depth bigint,\n"
            + "    cumulativeweight  bigint ,\n" 
            + "    milestone boolean,\n" 
            + "    milestonelastupdate bigint,\n" 
            + "    milestonedepth bigint,\n"
            + "    inserttime bigint,\n" 
            + "    maintained boolean,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash) USING BTREE \n" + ")";

    private static final String CREATE_UNSOLIDBLOCKS_TABLE = "CREATE TABLE unsolidblocks (\n"
            + "    hash varbinary(32) NOT NULL,\n"
            + "    block mediumblob NOT NULL,\n"
            + "    inserttime bigint,\n" 
            + "    reason bigint NOT NULL,\n"
            + "    missingdependency mediumblob NOT NULL,\n"
            + "    CONSTRAINT unsolidblocks_pk PRIMARY KEY (hash) USING BTREE \n" + ")";
            
    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    hash varbinary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n"
            + "    coinvalue bigint NOT NULL,\n" 
            + "    scriptbytes mediumblob NOT NULL,\n"
            + "    toaddress varchar(255),\n" 
            + "    addresstargetable bigint,\n" 
            + "    coinbase boolean,\n"
            + "    blockhash varbinary(32),\n" // confirming blockhash
            + "    tokenid varchar(255),\n"
            + "    fromaddress varchar(255),\n" 
            + "    memo varchar(80),\n" 
            + "    spent boolean NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n" 
            + "    spendpending boolean NOT NULL,\n" // true if there exists a transaction on the Tangle that can spend this output
            + "    spenderblockhash  varbinary(32),\n"
            + "    time bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex) USING BTREE \n" + ")\n";
    
    private static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash varbinary(32) NOT NULL,\n" 
            + "   toheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash varbinary(32),\n"
            + "   eligibility int NOT NULL,\n"
            + "   prevblockhash varbinary(32) NOT NULL,\n"
            + "   nexttxreward bigint NOT NULL,\n"
            + "   PRIMARY KEY (blockhash) )";
    
    private static final String CREATE_ORDER_MATCHING_TABLE = "CREATE TABLE ordermatching (\n"
            + "   blockhash varbinary(32) NOT NULL,\n" 
            + "   toheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash varbinary(32),\n"
            + "   eligibility int NOT NULL,\n"
            + "   prevblockhash varbinary(32) NOT NULL,\n"
            + "   PRIMARY KEY (blockhash) )";
    
    private static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n" 
            + "    hash varbinary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n" 
            + "    toaddress varchar(255) NOT NULL,\n"
            + "    minimumsign bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress) USING BTREE \n" + ")\n";

    private static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n"
            + "    hash varbinary(32) NOT NULL,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash) USING BTREE \n" + ")\n";
    
    private static final String CREATE_CONFIRMATION_DEPENDENCY_TABLE = "CREATE TABLE confirmationdependency (\n"
            + "    blockhash binary(32) NOT NULL,\n"
            + "    dependencyblockhash binary(32) NOT NULL,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (blockhash, dependencyblockhash) USING BTREE \n" + ")";    
    
    private static final String CREATE_ORDERS_TABLE = "CREATE TABLE openorders (\n" 
            + "    blockhash varbinary(32) NOT NULL,\n" // initial issuing block hash
            + "    collectinghash varbinary(32) NOT NULL,\n" // ZEROHASH if confirmed by order blocks, issuing ordermatch blockhash if issued by ordermatch block
            + "    offercoinvalue bigint NOT NULL,\n" // amount of tokens in order (tokens locked in for the order)
            + "    offertokenid varchar(255),\n" // tokenid of the used tokens
            + "    confirmed boolean NOT NULL,\n" // true iff a order block of this order is confirmed
            + "    spent boolean NOT NULL,\n" // true iff used by a confirmed ordermatch block (either returned or used for another orderoutput/output)
            + "    spenderblockhash  varbinary(32),\n" // if confirmed, this is the consuming ordermatch blockhash, else null
            + "    targetcoinvalue bigint,\n" // amount of target tokens wanted
            + "    targettokenid varchar(255),\n" // tokenid of the wanted tokens
            + "    beneficiarypubkey binary(33),\n" // the pubkey that will receive the targettokens on completion or returned tokens on cancels
            + "    validToTime bigint,\n" // order is valid untill this time
            + "    opindex int,\n" // a number used to track operations on the order, e.g. increasing by one when refreshing
            + "    validFromTime bigint,\n" // order is valid after this time
            + "    side varchar(255),\n" //buy or sell
            + "    beneficiaryaddress varchar(255),\n" //public addressl
            + "    CONSTRAINT outputs_pk PRIMARY KEY (blockhash, collectinghash) USING BTREE \n" + ")\n"; 

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    blockhash varchar(255) NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    amount bigint(20) ,\n" 
            + "    tokenname varchar(255) ,\n"
            + "    description varchar(255) ,\n" 
            + "    url varchar(255) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n"
            + "    tokentype int(11),\n" 
            + "    tokenstop boolean,\n"
            + "    prevblockhash varchar(255) NOT NULL,\n"
            + "    spent boolean NOT NULL,\n"
            + "    spenderblockhash  varbinary(32),\n"
            + "    tokenkeyvalues  mediumblob,\n"
            + "    PRIMARY KEY (blockhash) \n)";

    private static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash varchar(255) NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    address varchar(255),\n"
            + "    pubKeyHex varchar(255),\n"
            + "    posIndex int(11),\n"
            + "    PRIMARY KEY (blockhash, tokenid, address) \n)";

    private static final String CREATE_MULTISIGNBY_TABLE = "CREATE TABLE multisignby (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n" 
            + "    PRIMARY KEY (tokenid,tokenindex, address) \n)";

    private static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n"
            + "    blockhash  mediumblob NOT NULL,\n"
            + "    sign int(11) NOT NULL,\n"
            + "    PRIMARY KEY (id) \n)";

    private static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    toaddress varchar(255) NOT NULL,\n"
            + "    blockhash mediumblob NOT NULL,\n"
            + "    amount bigint(20) ,\n"
            + "    minsignnumber bigint(20) ,\n"
            + "    outputHashHex varchar(255) ,\n"
            + "    outputindex bigint ,\n"
            + "    PRIMARY KEY (orderid) \n)";
    
    private static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    pubKey varchar(255),\n"
            + "    sign int(11) NOT NULL,\n"
            + "    signIndex int(11) NOT NULL,\n"
            + "    signInputData mediumblob,\n"
            + "    PRIMARY KEY (orderid, pubKey) \n)";
    
    private static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n" 
            + "    blockhash varbinary(32) NOT NULL,\n"
            + "    dataclassname varchar(255) NOT NULL,\n" 
            + "    data mediumblob NOT NULL,\n"
            + "    pubKey varchar(255),\n" 
            + "    blocktype bigint,\n" 
             + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey) USING BTREE \n" + ")";
    
    private static final String CREATE_VOSEXECUTE_TABLE = "CREATE TABLE vosexecute (\n" 
            + "    vosKey varchar(255) NOT NULL,\n"
            + "    pubKey varchar(255) NOT NULL,\n" 
            + "    execute bigint NOT NULL,\n" 
            + "    data mediumblob NOT NULL,\n"
            + "    startDate datetime NOT NULL,\n"
            + "    endDate datetime NOT NULL,\n"
             + "   CONSTRAINT vosexecute_pk PRIMARY KEY (vosKey, pubKey) USING BTREE \n" + ")";
    
    private static final String CREATE_LOGRESULT_TABLE = "CREATE TABLE logresult (\n" 
            + "    logResultId varchar(255) NOT NULL,\n"
            + "    logContent varchar(255) NOT NULL,\n" 
            + "    submitDate datetime NOT NULL,\n"
             + "   CONSTRAINT vosexecute_pk PRIMARY KEY (logResultId) USING BTREE \n" + ")";
    
    private static final String CREATE_BATCHBLOCK_TABLE = "CREATE TABLE batchblock (\n" 
            + "    hash varbinary(32) NOT NULL,\n"
            + "    block mediumblob NOT NULL,\n"
            + "    inserttime datetime NOT NULL,\n"
             + "   CONSTRAINT batchblock_pk PRIMARY KEY (hash) USING BTREE \n" + ")";
    
    private static final String CREATE_SUBTANGLE_PERMISSION_TABLE = "CREATE TABLE subtangle_permission (\n" 
            + "    pubkey varchar(255) NOT NULL,\n"
            + "    userdataPubkey varchar(255) NOT NULL,\n"
            + "    status varchar(255) NOT NULL,\n"
            + "   CONSTRAINT batchblock_pk PRIMARY KEY (pubkey) USING BTREE \n" + ")";

    // Some indexes to speed up stuff
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_toaddress_idx ON outputs (hash, outputindex, toaddress) USING btree";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING btree";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING btree";
    private static final String CREATE_OUTPUTS_HASH_INDEX = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING btree";

    private static final String CREATE_PREVBRANCH_HASH_INDEX = "CREATE INDEX blocks_prevbranchblockhash_idx ON blocks (prevbranchblockhash) USING btree";
    private static final String CREATE_PREVTRUNK_HASH_INDEX = "CREATE INDEX blocks_prevblockhash_idx ON blocks (prevblockhash) USING btree";
  
    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName + "?useUnicode=true&characterEncoding=UTF-8", fullStoreDepth, username, password,
                null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_SETTINGS_TABLE);
        sqlStatements.add(CREATE_BLOCKS_TABLE);
        sqlStatements.add(CREATE_UNSOLIDBLOCKS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_MULTISIGNBY_TABLE);
        sqlStatements.add(CREATE_MULTISIGN_TABLE);
        sqlStatements.add(CREATE_TX_REWARD_TABLE);
        sqlStatements.add(CREATE_USERDATA_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGN_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_VOSEXECUTE_TABLE);
        sqlStatements.add(CREATE_LOGRESULT_TABLE);
        sqlStatements.add(CREATE_BATCHBLOCK_TABLE);
        sqlStatements.add(CREATE_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(CREATE_ORDERS_TABLE);
        sqlStatements.add(CREATE_ORDER_MATCHING_TABLE);
        sqlStatements.add(CREATE_CONFIRMATION_DEPENDENCY_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_OUTPUTS_ADDRESS_MULTI_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_HASH_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_TOADDRESS_INDEX);
        sqlStatements.add(CREATE_PREVBRANCH_HASH_INDEX);
        sqlStatements.add(CREATE_PREVTRUNK_HASH_INDEX);
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

    @Override
    protected String getUpdateSettingsSLQ() {
        // return UPDATE_SETTINGS_SQL;
        return getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    }

    @Override
    protected String getUpdateHeadersSQL() {
        return UPDATE_BLOCKS_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationCumulativeweightSQL() {
        return UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return UPDATE_BLOCKEVALUATION_DEPTH_SQL;
    }

   
    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return UPDATE_BLOCKEVALUATION_MILESTONE_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return UPDATE_BLOCKEVALUATION_RATING_SQL;
    }

  

    @Override
    protected String getUpdateBlockEvaluationMilestoneDepthSQL() {
        return UPDATE_BLOCKEVALUATION_MILESTONEDEPTH_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationMaintainedSQL() {
        return UPDATE_BLOCKEVALUATION_MAINTAINED_SQL;
    }

  
    @Override
    protected String getUpdateOutputsSpentSQL() {
        return UPDATE_OUTPUTS_SPENT_SQL;
    }

    @Override
    protected String getUpdateOutputsConfirmedSQL() {
        return UPDATE_OUTPUTS_CONFIRMED_SQL;
    }

    @Override
    protected String getUpdateOutputsSpendPendingSQL() {
        return UPDATE_OUTPUTS_SPENDPENDING_SQL;
    }

    @Override
    protected List<String> getDropIndexsSQL() {
        return new ArrayList<String>();
    }

    @Override
    protected String getUpdateBlockevaluationUnmaintainAllSQL() {
        return getUpdate() + " blocks SET maintained = false WHERE maintained = true";
    }
}
