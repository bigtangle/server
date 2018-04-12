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
 * <p>A full pruned block store using the MySQL database engine. As an added bonus an address index is calculated,
 * so you can use {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly look up
 * the quantity of bitcoins controlled by that address.</p>
 */
 
public class MySQLFullPrunedBlockStore extends DatabaseFullPrunedBlockStore {
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:mysql://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" +
            "    name varchar(32) NOT NULL,\n" +
            "    value blob,\n" +
            "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" +
            ")\n";

    private static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    header mediumblob NOT NULL,\n" +
            "    wasundoable tinyint(1) NOT NULL,\n" +
            "    prevblockhash  varbinary(32) NOT NULL,\n" +
            "    prevbranchblockhash  varbinary(32) NOT NULL,\n" +
            "    mineraddress varbinary(255),\n" +
            "    tokenid varbinary(255),\n" +
            "    blocktype bigint NOT NULL,\n" +
            "    CONSTRAINT headers_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")";


    private static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE undoableblocks (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    txoutchanges mediumblob,\n" +
            "    transactions mediumblob,\n" +
            "    CONSTRAINT undoableblocks_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")\n";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    `index` integer NOT NULL,\n" +
            "    height bigint NOT NULL,\n" +
            "    value bigint NOT NULL,\n" +
            "    scriptbytes mediumblob NOT NULL,\n" +
            "    toaddress varchar(35),\n" +
            "    addresstargetable tinyint(1),\n" +
            "    coinbase boolean,\n" +
            "    blockhash  varbinary(32)  NOT NULL,\n" +
            "    tokenid varbinary(255),\n" +
            "    fromaddress varchar(35),\n" +
            "    description varchar(80),\n" +
            "    spent tinyint(1) NOT NULL,\n" +
            "    confirmed tinyint(1) NOT NULL,\n" +
            "    spendpending tinyint(1) NOT NULL,\n" +
            "    spenderblockhash  varbinary(32),\n" +
            "    CONSTRAINT outputs_pk PRIMARY KEY (hash, `index`) USING BTREE \n" +
            ")\n";
    
    private static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    CONSTRAINT tips_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")\n";
 
    private static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE blockevaluation (\n" +
            "    blockhash varbinary(32) NOT NULL,\n" +
            "    rating bigint ,\n" +
            "    depth bigint,\n" +
            "    cumulativeweight  bigint ,\n" +
            "    solid tinyint(1) NOT NULL,\n" +
            "    height bigint,\n" +
            "    milestone tinyint(1),\n" +
            "    milestonelastupdate bigint,\n" +
            "    milestonedepth bigint,\n" +
            "    inserttime bigint,\n" +
            "    maintained tinyint(1),\n" +
            "    rewardvalidityassessment tinyint(1),\n" +
            "    CONSTRAINT blockevaluation_pk PRIMARY KEY (blockhash) )\n";
    
    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n" +
            "    tokenid varbinary(255) NOT NULL DEFAULT '0',\n" +
            "    tokenname varchar(255) DEFAULT NULL,\n" + 
            "    amount bigint(20) DEFAULT NULL,\n" +
            "    description varchar(255) DEFAULT NULL,\n" + 
            "    blocktype integer NOT NULL,\n" +
            "    PRIMARY KEY (tokenid) \n)";
    
    
    private static final String CREATE_ORDERPUBLISH_TABLE = "CREATE TABLE orderpublish (\n" +
            "   orderid varchar(255) NOT NULL,\n"+
            "   address varchar(255),\n"+
            "   tokenid varchar(255),\n"+ 
            "   type integer,\n"+ 
            "   validateto datetime,\n"+ 
            "   validatefrom datetime,\n"+ 
            "   price bigint,\n"+ 
            "   amount bigint,\n"+ 
            "   state integer,\n"+ 
            "   PRIMARY KEY (orderid) )";
    
    private static final String CREATE_ORDERMATCH_TABLE = "CREATE TABLE ordermatch (\n" +
            "   matchid varchar(255) NOT NULL,\n"+
            "   restingOrderId varchar(255),\n"+
            "   incomingOrderId varchar(255),\n"+ 
            "   type integer,\n"+ 
            "   price bigint,\n"+ 
            "   executedQuantity bigint,\n"+ 
            "   remainingQuantity bigint,\n"+ 
            "   PRIMARY KEY (matchid) )";
    
    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE exchange (\n" +
            "   orderid varchar(255) NOT NULL,\n"+
            "   fromAddress varchar(255),\n"+
            "   fromTokenHex varchar(255),\n"+ 
            "   fromAmount varchar(255),\n"+ 
            "   toAddress varchar(255),\n"+ 
            "   toTokenHex varchar(255),\n"+ 
            "   toAmount varchar(255),\n"+ 
            "   data varbinary(5000) NOT NULL,\n" +
            "   toSign integer,\n"+ 
            "   fromSign integer,\n"+ 
            "   PRIMARY KEY (orderid) )";
    
    // Some indexes to speed up inserts
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_height_toaddress_idx ON outputs (hash, `index`, height, toaddress) USING btree";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING btree";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING btree";
    private static final String CREATE_OUTPUTS_HASH_INDEX = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING btree";
    private static final String CREATE_UNDOABLE_TABLE_INDEX = "CREATE INDEX undoableblocks_height_idx ON undoableblocks (height) USING btree";
    private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";
    private static final String CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_restingOrderId_idx ON ordermatch (restingOrderId) USING btree";
    private static final String CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_incomingOrderId_idx ON ordermatch (incomingOrderId) USING btree";

      public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
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
        sqlStatements.add(CREATE_UNDOABLE_TABLE_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_ADDRESS_MULTI_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_HASH_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_TOADDRESS_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX);
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
}
