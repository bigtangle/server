/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bitcoinj.core.NetworkParameters;

/**
 * <p>A full pruned block store using the MySQL database engine. As an added bonus an address index is calculated,
 * so you can use {@link #calculateBalanceForAddress(org.bitcoinj.core.Address)} to quickly look up
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
            "    tokenid bigint,\n" +
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

    private static final String CREATE_OPEN_OUTPUT_TABLE = "CREATE TABLE outputs (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    `index` integer NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    value bigint NOT NULL,\n" +
            "    scriptbytes mediumblob NOT NULL,\n" +
            "    toaddress varchar(35),\n" +
            "    addresstargetable tinyint(1),\n" +
            "    coinbase boolean,\n" +
            "    blockhash  varbinary(32)  NOT NULL,\n" +
            "    tokenid bigint,\n" +
            "    fromaddress varchar(35),\n" +
            "    description varchar(80),\n" +
            "    spent tinyint(1) NOT NULL,\n" +
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
            "    CONSTRAINT blockevaluation_pk PRIMARY KEY (blockhash) )\n";

    // Some indexes to speed up inserts
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX              = "CREATE INDEX outputs_hash_index_height_toaddress_idx ON outputs (hash, `index`, height, toaddress) USING btree";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX                  = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING btree";
    private static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX          = "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) USING btree";
    private static final String CREATE_OUTPUTS_HASH_INDEX                       = "CREATE INDEX outputs_hash_idx ON outputs (hash) USING btree";
    private static final String CREATE_UNDOABLE_TABLE_INDEX                     = "CREATE INDEX undoableblocks_height_idx ON undoableblocks (height) USING btree";

    // SQL involving index column (table outputs) overridden as it is a reserved word and must be back ticked in MySQL.
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
        sqlStatements.add(CREATE_OPEN_OUTPUT_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
        sqlStatements.add(CREATE_BLOCKEVALUATION_TABLE);
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
