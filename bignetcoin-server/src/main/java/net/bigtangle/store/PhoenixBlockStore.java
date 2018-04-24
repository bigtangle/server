/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;

/**
 * <p>
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated, so you can use
 * {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly
 * look up the quantity of bitcoins controlled by that address.
 * </p>
 */

public class PhoenixBlockStore extends DatabaseFullPrunedBlockStore {
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "org.apache.phoenix.queryserver.client.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:phoenix:thin:url=http://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" + "    name varchar(32) not null,\n"
            + "    settingvalue binary(1000),\n" + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" + ")\n";

    private static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n" + "    hash binary(32) not null,\n"
            + "    height bigint ,\n" + "    header binary(4000) ,\n" + "    wasundoable boolean ,\n"
            + "    prevblockhash  binary(32) ,\n" + "    prevbranchblockhash  binary(32) ,\n"
            + "    mineraddress binary(255),\n" + "    tokenid binary(255),\n" + "    blocktype bigint ,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash)  \n" + ")";

    private static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE undoableblocks (\n"
            + "    hash binary(32) not null,\n" + "    height bigint ,\n" + "    txoutchanges binary(4000),\n"
            + "    transactions binary(4000),\n" + "    CONSTRAINT undoableblocks_pk PRIMARY KEY (hash)  \n" + ")\n";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" + "    hash binary(32) not null,\n"
            + "    outputindex integer not null,\n" + "    height bigint ,\n" + "    coinvalue bigint ,\n"
            + "    scriptbytes binary(4000) ,\n" + "    toaddress binary(35),\n" + "    addresstargetable boolean,\n"
            + "    coinbase boolean,\n" + "    blockhash  binary(32)  ,\n" + "    tokenid binary(255),\n"
            + "    fromaddress binary(35),\n" + "    description binary(80),\n" + "    spent boolean ,\n"
            + "    confirmed boolean ,\n" + "    spendpending boolean ,\n" + "    spenderblockhash  binary(32),\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash,outputindex)  \n" + ")\n";

    private static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n" + "    hash binary(32) not null,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash)  \n" + ")\n";

    private static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE blockevaluation (\n"
            + "    blockhash binary(32) not null,\n" + "    rating bigint ,\n" + "    depth bigint,\n"
            + "    cumulativeweight  bigint ,\n" + "    solid boolean ,\n" + "    height bigint,\n"
            + "    milestone boolean,\n" + "    milestonelastupdate bigint,\n" + "    milestonedepth bigint,\n"
            + "    inserttime bigint,\n" + "    maintained boolean,\n" + "    rewardvalidityassessment boolean,\n"
            + "    CONSTRAINT blockevaluation_pk PRIMARY KEY (blockhash) )\n";

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n" + "    tokenid binary(255) not null ,\n"
            + "    tokenname binary(255)  ,\n" + "    amount bigint ,\n" + "    description varchar(255),\n"
            + "    blocktype integer ,\n" + "    CONSTRAINT tokenid_pk PRIMARY KEY (tokenid) \n)";

    private static final String CREATE_ORDERPUBLISH_TABLE = "CREATE TABLE orderpublish (\n"
            + "   orderid binary(255) not null,\n" + "   address binary(255),\n" + "   tokenid binary(255),\n"
            + "   type integer,\n" + "   validateto DATE,\n" + "   validatefrom DATE,\n" + "   price bigint,\n"
            + "   amount bigint,\n" + "   state integer,\n" + "   market binary(255),\n"
            + "   CONSTRAINT orderid_pk PRIMARY KEY (orderid) )";

    private static final String CREATE_ORDERMATCH_TABLE = "CREATE TABLE ordermatch (\n"
            + "   matchid varchar(255) not null,\n" + "   restingOrderId varchar(255),\n"
            + "   incomingOrderId varchar(255),\n" + "   type integer,\n" + "   price bigint,\n"
            + "   executedQuantity bigint,\n" + "   remainingQuantity bigint,\n"
            + "   CONSTRAINT matchid_pk PRIMARY KEY (matchid) )";

    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE exchange (\n"
            + "   orderid varchar(255) not null,\n" + "   fromAddress varchar(255),\n"
            + "   fromTokenHex varchar(255),\n" + "   fromAmount varchar(255),\n" + "   toAddress varchar(255),\n"
            + "   toTokenHex varchar(255),\n" + "   toAmount varchar(255),\n" + "   data varchar(5000) ,\n"
            + "   toSign integer,\n" + "   fromSign integer,\n" + "   toOrderId varchar(255),\n"
            + "   fromOrderId varchar(255),\n" + "   CONSTRAINT orderid_pk PRIMARY KEY (orderid) )";

    // Some indexes to speed up inserts
    /*
     * private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX =
     * "CREATE INDEX outputs_hash_index_height_toaddress_idx ON outputs (hash, outputindex, height, toaddress) "
     * ; private static final String CREATE_OUTPUTS_TOADDRESS_INDEX =
     * "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) "; private
     * static final String CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX =
     * "CREATE INDEX outputs_addresstargetable_idx ON outputs (addresstargetable) "
     * ; private static final String CREATE_OUTPUTS_HASH_INDEX =
     * "CREATE INDEX outputs_hash_idx ON outputs (hash) "; private static final
     * String CREATE_UNDOABLE_TABLE_INDEX =
     * "CREATE INDEX undoableblocks_height_idx ON undoableblocks (height) ";
     * private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX =
     * "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) ";
     * private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX =
     * "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) "; private
     * static final String CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX =
     * "CREATE INDEX ordermatch_restingOrderId_idx ON ordermatch (restingOrderId) "
     * ; private static final String
     * CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX =
     * "CREATE INDEX ordermatch_incomingOrderId_idx ON ordermatch (incomingOrderId) "
     * ;
     */
    public PhoenixBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + ";serialization=PROTOBUF", fullStoreDepth, username,
                password, null);
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
        /*
         * sqlStatements.add(CREATE_UNDOABLE_TABLE_INDEX);
         * sqlStatements.add(CREATE_OUTPUTS_ADDRESS_MULTI_INDEX);
         * sqlStatements.add(CREATE_OUTPUTS_ADDRESSTARGETABLE_INDEX);
         * sqlStatements.add(CREATE_OUTPUTS_HASH_INDEX);
         * sqlStatements.add(CREATE_OUTPUTS_TOADDRESS_INDEX);
         * sqlStatements.add(CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
         * sqlStatements.add(CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX);
         * sqlStatements.add(CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX);
         * sqlStatements.add(CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX);
         */
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

    protected String getInsert() {
        return "upsert ";
    }
    protected String getUpdate() {
        return "UPSERT ";
    }
    
    protected String getUpdateHeadersSQL() {
        return INSERT_HEADERS_SQL;
    }
    protected String getUpdateSettingsSLQ() {
        return INSERT_SETTINGS_SQL;
    }

    @Override
    public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        Sha256Hash hash = chainHead.getHeader().getHash();
        this.chainHeadHash = hash;
        this.chainHeadBlock = chainHead;
        maybeConnect();
        try {
            PreparedStatement s = conn.get().prepareStatement(getUpdateSettingsSLQ());
            s.setString(1, CHAIN_HEAD_SETTING);
            s.setBytes(2, hash.getBytes());
            s.executeUpdate();
            s.close();
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }
    @Override
    public void setVerifiedChainHead(StoredBlock chainHead) throws BlockStoreException {
        Sha256Hash hash = chainHead.getHeader().getHash();
        this.verifiedChainHeadHash = hash;
        this.verifiedChainHeadBlock = chainHead;
        maybeConnect();
        try {
            PreparedStatement s = conn.get().prepareStatement(getUpdateSettingsSLQ());
            s.setString(1, VERIFIED_CHAIN_HEAD_SETTING);
            s.setBytes(2, hash.getBytes());
            s.executeUpdate();
            s.close();
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
        if (this.chainHeadBlock.getHeight() < chainHead.getHeight())
            setChainHead(chainHead);
        removeUndoableBlocksWhereHeightIsLessThan(chainHead.getHeight() - fullStoreDepth);
    }
    
    @Override
    protected synchronized void maybeConnect() throws BlockStoreException {
        super.maybeConnect();
        Connection connection = this.getConnection().get();
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
