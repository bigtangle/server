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
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:mysql://"; // "jdbc:log4jdbc:mysql://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" 
            + "    name varchar(32) NOT NULL,\n"
            + "    settingvalue blob,\n" 
            + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" 
            + ")\n";

    // TODO lower the sizes of stuff to their exact size limitations! use
    // NetworkParameters!
    private static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    height bigint NOT NULL,\n" 
            + "    block mediumblob NOT NULL,\n"
            + "    wasundoable boolean NOT NULL,\n" 
            + "    prevblockhash  binary(32) NOT NULL,\n"
            + "    prevbranchblockhash  binary(32) NOT NULL,\n" 
            + "    mineraddress binary(20) NOT NULL,\n"
            + "    blocktype bigint NOT NULL,\n" 
            + "    rating bigint NOT NULL,\n"
            + "    depth bigint NOT NULL,\n" 
            + "    cumulativeweight bigint NOT NULL,\n" 
            + "    milestone bigint NOT NULL,\n"
            + "    milestonelastupdate bigint NOT NULL,\n" 
            + "    milestonedepth bigint NOT NULL,\n" 
            + "    inserttime bigint NOT NULL,\n"
            + "    maintained boolean NOT NULL,\n" 
            + "    solid bigint NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n"
            + "    CONSTRAINT blocks_pk PRIMARY KEY (hash) \n" + ")";

    private static final String CREATE_UNSOLIDBLOCKS_TABLE = "CREATE TABLE unsolidblocks (\n"
            + "    hash binary(32) NOT NULL,\n" 
            + "    block mediumblob NOT NULL,\n" 
            + "    inserttime bigint NOT NULL,\n"
            + "    reason bigint NOT NULL,\n" 
            + "    missingdependency mediumblob NOT NULL,\n" 
            + "    height bigint ,\n"
            + "    directlymissing boolean NOT NULL,\n" 
            + "    CONSTRAINT unsolidblocks_pk PRIMARY KEY (hash) \n" + ")";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n" 
            + "    coinvalue mediumblob NOT NULL,\n"
            + "    scriptbytes mediumblob NOT NULL,\n" 
            + "    toaddress varchar(255),\n"
            + "    addresstargetable bigint,\n" 
            + "    coinbase boolean,\n" 
            + "    tokenid varchar(255),\n" 
            + "    fromaddress varchar(255),\n" 
            + "    memo MEDIUMTEXT,\n"
            + "    spent boolean NOT NULL,\n" 
            + "    confirmed boolean NOT NULL,\n"
            + "    spendpending boolean NOT NULL,\n" 
            + "    spendpendingtime bigint,\n" 
            + "    spenderblockhash  binary(32),\n" 
            + "    time bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (blockhash, hash, outputindex) \n" + ")\n";

    private static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   toheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n" 
            + "   prevblockhash binary(32) NOT NULL,\n" 
            + "   difficulty bigint NOT NULL,\n" 
            + "   chainlength bigint NOT NULL,\n" 
            + "   PRIMARY KEY (blockhash) )";

    private static final String CREATE_ORDER_MATCHING_TABLE = "CREATE TABLE ordermatching (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   toheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n" 
            + "   prevblockhash binary(32) NOT NULL,\n" 
            + "   PRIMARY KEY (blockhash) )";

    private static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n"
            + "    hash binary(32) NOT NULL,\n" 
            + "    outputindex bigint NOT NULL,\n"
            + "    toaddress varchar(255) NOT NULL,\n" 
            + "    minimumsign bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress) \n" + ")\n";

    private static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash) USING HASH \n" + ")\n";

    private static final String CREATE_CONFIRMATION_DEPENDENCY_TABLE = "CREATE TABLE confirmationdependency (\n"
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    dependencyblockhash binary(32) NOT NULL,\n"
            + "    CONSTRAINT confirmationdependency_pk PRIMARY KEY (blockhash, dependencyblockhash) \n"
            + ")";

    private static final String CREATE_ORDERS_TABLE = "CREATE TABLE openorders (\n"
            + "    blockhash binary(32) NOT NULL,\n" // initial issuing block
                                                        // hash
            + "    collectinghash binary(32) NOT NULL,\n" // ZEROHASH if
                                                             // confirmed by
                                                             // order blocks,
                                                             // issuing
                                                             // ordermatch
                                                             // blockhash if
                                                             // issued by
                                                             // ordermatch block
            + "    offercoinvalue bigint NOT NULL,\n" // amount of tokens in
                                                      // order (tokens locked in
                                                      // for the order)
            + "    offertokenid varchar(255),\n" // tokenid of the used tokens
            + "    confirmed boolean NOT NULL,\n" // true iff a order block of
                                                  // this order is confirmed
            + "    spent boolean NOT NULL,\n" // true iff used by a confirmed
                                              // ordermatch block (either
                                              // returned or used for another
                                              // orderoutput/output)
            + "    spenderblockhash  binary(32),\n" // if confirmed, this is
                                                       // the consuming
                                                       // ordermatch blockhash,
                                                       // else null
            + "    targetcoinvalue bigint,\n" // amount of target tokens wanted
            + "    targettokenid varchar(255),\n" // tokenid of the wanted
                                                  // tokens
            + "    beneficiarypubkey binary(33),\n" // the pubkey that will
                                                    // receive the targettokens
                                                    // on completion or returned
                                                    // tokens on cancels
            + "    validToTime bigint,\n" // order is valid untill this time
            + "    opindex int,\n" // a number used to track operations on the
                                   // order, e.g. increasing by one when
                                   // refreshing
            + "    validFromTime bigint,\n" // order is valid after this time
            + "    side varchar(255),\n" // buy or sell
            + "    beneficiaryaddress varchar(255),\n" // public addressl
            + "    CONSTRAINT openorders_pk PRIMARY KEY (blockhash, collectinghash) USING HASH \n" + ")\n";

    private static final String CREATE_MATCHING_TABLE = "CREATE TABLE matching (\n"
            + "    id bigint NOT NULL AUTO_INCREMENT,\n" + "    txhash varchar(255) NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL,\n" + "    price bigint NOT NULL,\n"
            + "    executedQuantity bigint NOT NULL,\n" + "    inserttime bigint NOT NULL,\n"
            + "    PRIMARY KEY (id) \n" + ")\n";

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    blockhash varchar(255) NOT NULL,\n" 
    		+ "    confirmed boolean NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
    		+ "    tokenindex bigint NOT NULL   ,\n"
            + "    amount mediumblob ,\n" 
            + "    tokenname varchar(60) ,\n" 
            + "    description varchar(500) ,\n"
            + "    domainname varchar(100) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n" 
            + "    tokentype int(11),\n"
            + "    tokenstop boolean,\n" 
            + "    prevblockhash varchar(255) NOT NULL,\n"
            + "    spent boolean NOT NULL,\n" 
            + "    spenderblockhash  binary(32),\n"
            + "    tokenkeyvalues  mediumblob,\n" 
            + "    revoked boolean   ,\n" 
            + "    language char(2)   ,\n"
            + "    classification varchar(255)   ,\n"
            + "    domainpredblockhash varchar(255) NOT NULL,\n"
            + "    decimals int ,\n" 
            + "    PRIMARY KEY (blockhash) \n)";

    // Helpers
    private static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash varchar(255) NOT NULL,\n" + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    address varchar(255),\n" + "    pubKeyHex varchar(255),\n" + "    posIndex int(11),\n"
            + "    tokenHolder int(11) NOT NULL DEFAULT 0,\n"
            + "    PRIMARY KEY (blockhash, tokenid, pubKeyHex) \n)";

    private static final String CREATE_MULTISIGNBY_TABLE = "CREATE TABLE multisignby (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n" + "    PRIMARY KEY (tokenid,tokenindex, address) \n)";

    private static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255) NOT NULL  ,\n" + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    tokenindex bigint NOT NULL   ,\n" + "    address varchar(255),\n"
            + "    blockhash  mediumblob NOT NULL,\n" + "    sign int(11) NOT NULL,\n" + "    PRIMARY KEY (id) \n)";

    private static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    toaddress varchar(255) NOT NULL,\n" + "    blockhash mediumblob NOT NULL,\n"
            + "    amount mediumblob ,\n" + "    minsignnumber bigint(20) ,\n" + "    outputHashHex varchar(255) ,\n"
            + "    outputindex bigint ,\n" + "    PRIMARY KEY (orderid) \n)";

    private static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" + "    pubKey varchar(255),\n" + "    sign int(11) NOT NULL,\n"
            + "    signIndex int(11) NOT NULL,\n" + "    signInputData mediumblob,\n"
            + "    PRIMARY KEY (orderid, pubKey) \n)";

    private static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n"
            + "    blockhash binary(32) NOT NULL,\n" + "    dataclassname varchar(255) NOT NULL,\n"
            + "    data mediumblob NOT NULL,\n" + "    pubKey varchar(255),\n" + "    blocktype bigint,\n"
            + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey) USING BTREE \n" + ")";

    private static final String CREATE_VOSEXECUTE_TABLE = "CREATE TABLE vosexecute (\n"
            + "    vosKey varchar(255) NOT NULL,\n" + "    pubKey varchar(255) NOT NULL,\n"
            + "    execute bigint NOT NULL,\n" + "    data mediumblob NOT NULL,\n"
            + "    startDate datetime NOT NULL,\n" + "    endDate datetime NOT NULL,\n"
            + "   CONSTRAINT vosexecute_pk PRIMARY KEY (vosKey, pubKey) USING BTREE \n" + ")";

    private static final String CREATE_LOGRESULT_TABLE = "CREATE TABLE logresult (\n"
            + "    logResultId varchar(255) NOT NULL,\n" + "    logContent varchar(255) NOT NULL,\n"
            + "    submitDate datetime NOT NULL,\n"
            + "   CONSTRAINT logresult_pk PRIMARY KEY (logResultId) USING BTREE \n" + ")";

    private static final String CREATE_BATCHBLOCK_TABLE = "CREATE TABLE batchblock (\n"
            + "    hash binary(32) NOT NULL,\n" + "    block mediumblob NOT NULL,\n"
            + "    inserttime datetime NOT NULL,\n" + "   CONSTRAINT batchblock_pk PRIMARY KEY (hash) USING BTREE \n"
            + ")";

    private static final String CREATE_SUBTANGLE_PERMISSION_TABLE = "CREATE TABLE subtangle_permission (\n"
            + "    pubkey varchar(255) NOT NULL,\n" + "    userdataPubkey varchar(255) NOT NULL,\n"
            + "    status varchar(255) NOT NULL,\n"
            + "   CONSTRAINT subtangle_permission_pk PRIMARY KEY (pubkey) USING BTREE \n" + ")";

    private static final String CREATE_MYSERVERBLOCKS_TABLE = "CREATE TABLE myserverblocks (\n"
            + "    prevhash binary(32) NOT NULL,\n" + " hash binary(32) NOT NULL,\n" + "    inserttime bigint,\n"
            + "    CONSTRAINT myserverblocks_pk PRIMARY KEY (prevhash, hash) USING BTREE \n" + ")";
    
    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE exchange (\n"
            + "   orderid varchar(255) NOT NULL,\n" 
            + "   fromAddress varchar(255),\n"
            + "   fromTokenHex varchar(255),\n" 
            + "   fromAmount varchar(255),\n" 
            + "   toAddress varchar(255),\n"
            + "   toTokenHex varchar(255),\n" 
            + "   toAmount varchar(255),\n" 
            + "   data varbinary(5000) NOT NULL,\n"
            + "   toSign boolean,\n" 
            + "   fromSign integer,\n" 
            + "   toOrderId varchar(255),\n"
            + "   fromOrderId varchar(255),\n" 
            + "   market varchar(255),\n" 
            + "   signInputData varbinary(5000),\n"
            + "   PRIMARY KEY (orderid) )";
    
    private static final String CREATE_EXCHANGE_MULTISIGN_TABLE = "CREATE TABLE exchange_multisign (\n"
//          + "   id varchar(255) NOT NULL,\n"
          + "   orderid varchar(255) ,\n" 
          + "   pubkey varchar(255),\n"
          + "   signInputData varbinary(5000),\n"
          + "   sign integer\n"
          + "    )";

    // Some indexes to speed up stuff
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_toaddress_idx ON outputs (hash, outputindex, toaddress) USING HASH";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING HASH";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING HASH";
    private static final String CREATE_OUTPUTS_HASH_INDEX = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING HASH";

    private static final String CREATE_PREVBRANCH_HASH_INDEX = "CREATE INDEX blocks_prevbranchblockhash_idx ON blocks (prevbranchblockhash) USING HASH";
    private static final String CREATE_PREVTRUNK_HASH_INDEX = "CREATE INDEX blocks_prevblockhash_idx ON blocks (prevblockhash) USING HASH";
    private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";

    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params,
                DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName + "?useUnicode=true&characterEncoding=UTF-8",
                fullStoreDepth, username, password, null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();

        sqlStatements.add(CREATE_BLOCKS_TABLE);
        sqlStatements.add(CREATE_UNSOLIDBLOCKS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_MATCHING_TABLE);
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
        sqlStatements.add(CREATE_MYSERVERBLOCKS_TABLE);
        sqlStatements.add(CREATE_SETTINGS_TABLE);
        sqlStatements.add(CREATE_EXCHANGE_TABLE);
        sqlStatements.add(CREATE_EXCHANGE_MULTISIGN_TABLE);
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
        sqlStatements.add(CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX);
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
