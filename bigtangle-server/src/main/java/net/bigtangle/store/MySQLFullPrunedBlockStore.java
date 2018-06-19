/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;

/**
 * <p>
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated, so you can use
 * {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly
 * look up the quantity of bitcoins controlled by that address.
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

    private static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n"
            + "    hash varbinary(32) NOT NULL,\n"
            + "    height bigint NOT NULL,\n"
            + "    header mediumblob NOT NULL,\n"
            + "    wasundoable boolean NOT NULL,\n" 
            + "    prevblockhash  varbinary(32) NOT NULL,\n"
            + "    prevbranchblockhash  varbinary(32) NOT NULL,\n" 
            + "    mineraddress varbinary(255),\n"
            + "    tokenid varbinary(255),\n" 
            + "    blocktype bigint NOT NULL,\n"
            + "    rating bigint ,\n"
            + "    depth bigint,\n"
            + "    cumulativeweight  bigint ,\n" 
            + "    solid boolean ,\n" 
            + "    milestone boolean,\n" 
            + "    milestonelastupdate bigint,\n" 
            + "    milestonedepth bigint,\n"
            + "    inserttime bigint,\n" 
            + "    maintained boolean,\n"
            + "    rewardvalidityassessment boolean,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash) USING BTREE \n" + ")";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    hash varbinary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n"
            + "    height bigint NOT NULL,\n"
            + "    coinvalue bigint NOT NULL,\n" 
            + "    scriptbytes mediumblob NOT NULL,\n"
            + "    toaddress varchar(255),\n" 
            + "    addresstargetable bigint,\n" 
            + "    coinbase boolean,\n"
            + "    blockhash  varbinary(32)  NOT NULL,\n" 
            + "    tokenid varchar(255),\n"
            + "    fromaddress varchar(255),\n" 
            + "    memo varchar(80),\n" 
            + "    spent boolean NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n" 
            + "    spendpending boolean NOT NULL,\n"
            + "    spenderblockhash  varbinary(32),\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex) USING BTREE \n" + ")\n";
    
    private static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n" 
            + "    hash varbinary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n" 
            + "    toaddress varchar(255) NOT NULL,\n"
            + "    minimumsign bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress) USING BTREE \n" + ")\n";

    private static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n"
            + "    hash varbinary(32) NOT NULL,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash) USING BTREE \n" + ")\n";

 

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    tokenname varchar(255) ,\n"
            + "    description varchar(255) ,\n" 
            + "    url varchar(255) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n"
            + "    multiserial boolean,\n" 
            + "    asmarket boolean,\n" + "   tokenstop boolean,\n"
            + "    PRIMARY KEY (tokenid) \n)";

    private static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    address varchar(255),\n"
            + "    pubKeyHex varchar(255),\n"
            + "    posIndex int(11),\n"
            + "    PRIMARY KEY (tokenid, address) \n)";

    private static final String CREATE_TOKENSERIAL_TABLE = "CREATE TABLE tokenserial (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    amount bigint(20) ,\n" 
            + "    PRIMARY KEY (tokenid, tokenindex) \n)";

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
            + "    outpusHashHex varchar(255) ,\n"
            + "    PRIMARY KEY (orderid) \n)";
    
    private static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    pubKey varchar(255),\n"
            + "    sign int(11) NOT NULL,\n"
            + "    signIndex int(11) NOT NULL,\n"
            + "    signInputData mediumblob,\n"
            + "    PRIMARY KEY (orderid, pubKey) \n)";
    
    private static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash varbinary(32) NOT NULL,\n" 
            + "   rewardamount bigint NOT NULL,\n"
            + "   prevheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   PRIMARY KEY (blockhash) )";
    
    private static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n" 
            + "    blockhash varbinary(32) NOT NULL,\n"
            + "    dataclassname varchar(255) NOT NULL,\n" 
            + "    data mediumblob NOT NULL,\n"
            + "    pubKey varchar(255),\n" 
            + "    blocktype bigint,\n" 
             + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey) USING BTREE \n" + ")";

    // Some indexes to speed up inserts
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_height_toaddress_idx ON outputs (hash, outputindex, height, toaddress) USING btree";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING btree";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING btree";
    private static final String CREATE_OUTPUTS_HASH_INDEX = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING btree";
  
    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName, fullStoreDepth, username, password,
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
        sqlStatements.add(CREATE_HEADERS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_TOKENSERIAL_TABLE);
        sqlStatements.add(CREATE_MULTISIGNBY_TABLE);
        sqlStatements.add(CREATE_MULTISIGN_TABLE);
        sqlStatements.add(CREATE_TX_REWARD_TABLE);
        sqlStatements.add(CREATE_USERDATA_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGN_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGNADDRESS_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_OUTPUTS_ADDRESS_MULTI_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_HASH_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_TOADDRESS_INDEX);
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
        return UPDATE_HEADERS_SQL;
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
    protected String getUpdateBlockEvaluationSolidSQL() {
        return UPDATE_BLOCKEVALUATION_SOLID_SQL;
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
    protected String getUpdateBlockEvaluationRewardValidItyassessmentSQL() {
        return UPDATE_BLOCKEVALUATION_REWARDVALIDITYASSESSMENT_SQL;
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
        return getUpdate() + " headers SET maintained = false WHERE maintained = true";
    }

}
