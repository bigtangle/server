/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ProtocolException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;

/**
 * <p>
 *   
 * </p>
 */

public class PhoenixBlockStore extends DatabaseFullPrunedBlockStore {

    //private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    @Override
    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        return null;

    }

    @Override
    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException {
        List<Sha256Hash> storedBlockHash = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT headers.hash FROM headers INNER JOIN"
                    + " blockevaluation ON headers.hash=blockevaluation.hash "
                    + "WHERE blockevaluation.solid = true AND (headers.prevblockhash = ?)" + afterSelect();
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HASHES_SQL);
            s.setString(1, Utils.HEX.encode(hash.getBytes()));
            // s.setString(2, Utils.HEX.encode(hash.getBytes()));
            ResultSet results = s.executeQuery();
            while (results.next()) {
                storedBlockHash.add(Sha256Hash.wrap(results.getBytes(1)));
            }
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);
        } catch (VerificationException e) {
            // Should not be able to happen unless the database contains bad
            // blocks.
            throw new BlockStoreException(e);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
        try {
            String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT headers.hash FROM headers INNER JOIN"
                    + " blockevaluation ON headers.hash=blockevaluation.hash "
                    + "WHERE blockevaluation.solid = true AND (headers.prevbranchblockhash = ?)" + afterSelect();
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HASHES_SQL);
            s.setString(1, Utils.HEX.encode(hash.getBytes()));
            // s.setString(2, Utils.HEX.encode(hash.getBytes()));
            ResultSet results = s.executeQuery();
            while (results.next()) {
                storedBlockHash.add(Sha256Hash.wrap(results.getBytes(1)));
            }
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);
        } catch (VerificationException e) {
            // Should not be able to happen unless the database contains bad
            // blocks.
            throw new BlockStoreException(e);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
        return storedBlockHash;
    }

    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "org.apache.phoenix.queryserver.client.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:log4jdbc:phoenix:thin:url=http://";

    // create table SQL
    // create table SQL
    public static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" 
            + "    name varchar(32) NOT NULL,\n"
            + "    settingvalue  varbinary(800000),\n" 
            + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" + ")\n";

    public static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n"
            + "    hash binary(32) NOT NULL,\n"
            + "    height bigint NOT NULL,\n"
            + "    block varbinary(800000) NOT NULL,\n"
            + "    wasundoable boolean NOT NULL,\n" 
            + "    prevblockhash  binary(32) NOT NULL,\n"
            + "    prevbranchblockhash  binary(32) NOT NULL,\n" 
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
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash)  \n" + ")";

    public static final String CREATE_UNSOLIDBLOCKS_TABLE = "CREATE TABLE unsolidblocks (\n"
            + "    hash binary(32) NOT NULL,\n"
            + "    block varbinary(800000) NOT NULL,\n"
            + "    inserttime bigint,\n" 
            + "    CONSTRAINT unsolidblocks_pk PRIMARY KEY (hash)  \n" + ")";
            
    public static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n"
            + "    height bigint NOT NULL,\n"
            + "    coinvalue bigint NOT NULL,\n" 
            + "    scriptbytes varbinary(800000) NOT NULL,\n"
            + "    toaddress varchar(255),\n" 
            + "    addresstargetable bigint,\n" 
            + "    coinbase boolean,\n"
            + "    blockhash  binary(32) NOT NULL,\n" 
            + "    tokenid varchar(255),\n"
            + "    fromaddress varchar(255),\n" 
            + "    memo varchar(80),\n" 
            + "    spent boolean NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n" 
            + "    spendpending boolean NOT NULL,\n"
            + "    spenderblockhash  binary(32),\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex)  \n" + ")\n";
    
    public static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   prevheight bigint NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n"
            + "   eligibility boolean NOT NULL,\n"
            + "   prevblockhash binary(32),\n"
            + "   PRIMARY KEY (blockhash) )";
    
    public static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n" 
            + "    toaddress varchar(255) NOT NULL,\n"
            + "    minimumsign bigint NOT NULL,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress)  \n" + ")\n";

    public static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n"
            + "    hash binary(32) NOT NULL,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash)  \n" + ")\n";

    public static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    blockhash varchar(255) NOT NULL,\n"
            + "    confirmed boolean NOT NULL,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    amount bigint(20) ,\n" 
            + "    tokenname varchar(255) ,\n"
            + "    description varchar(255) ,\n" 
            + "    url varchar(255) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n"
            + "    multiserial boolean,\n" 
            + "    tokentype int(11),\n" 
            + "    tokenstop boolean,\n"
            + "    prevblockhash varchar(255) NOT NULL,\n"
            + "    spent boolean NOT NULL,\n"
            + "    spenderblockhash  binary(32),\n"
            + "    PRIMARY KEY (blockhash) \n)";

    //update on confirm
    public static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash varchar(255) NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    address varchar(255),\n"
            + "    pubKeyHex varchar(255),\n"
            + "    posIndex int(11),\n"
            + "    PRIMARY KEY (blockhash, tokenid, address) \n)";

    public static final String CREATE_MULTISIGNBY_TABLE = "CREATE TABLE multisignby (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n" 
            + "    PRIMARY KEY (tokenid,tokenindex, address) \n)";
    
    public static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n"
            + "    blockhash  varbinary(800000) NOT NULL,\n"
            + "    sign int(11) NOT NULL,\n"
            + "    PRIMARY KEY (id) \n)";

    public static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    toaddress varchar(255) NOT NULL,\n"
            + "    blockhash varbinary(800000) NOT NULL,\n"
            + "    amount bigint(20) ,\n"
            + "    minsignnumber bigint(20) ,\n"
            + "    outpusHashHex varchar(255) ,\n"
            + "    PRIMARY KEY (orderid) \n)";
    
    public static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    pubKey varchar(255),\n"
            + "    sign int(11) NOT NULL,\n"
            + "    signIndex int(11) NOT NULL,\n"
            + "    signInputData varbinary(800000),\n"
            + "    PRIMARY KEY (orderid, pubKey) \n)";
    
    public static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n" 
            + "    blockhash binary(32) NOT NULL,\n"
            + "    dataclassname varchar(255) NOT NULL,\n" 
            + "    data varbinary(800000) NOT NULL,\n"
            + "    pubKey varchar(255),\n" 
            + "    blocktype bigint,\n" 
             + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey)  \n" + ")";
    
    public static final String CREATE_VOSEXECUTE_TABLE = "CREATE TABLE vosexecute (\n" 
            + "    vosKey varchar(255) NOT NULL,\n"
            + "    pubKey varchar(255) NOT NULL,\n" 
            + "    execute bigint NOT NULL,\n" 
            + "    data varbinary(800000) NOT NULL,\n"
            + "    startDate datetime NOT NULL,\n"
            + "    endDate datetime NOT NULL,\n"
             + "   CONSTRAINT vosexecute_pk PRIMARY KEY (vosKey, pubKey)  \n" + ")";
    
    public static final String CREATE_LOGRESULT_TABLE = "CREATE TABLE logresult (\n" 
            + "    logResultId varchar(255) NOT NULL,\n"
            + "    logContent varchar(255) NOT NULL,\n" 
            + "    submitDate datetime NOT NULL,\n"
             + "   CONSTRAINT vosexecute_pk PRIMARY KEY (logResultId)  \n" + ")";
    
    public static final String CREATE_BATCHBLOCK_TABLE = "CREATE TABLE batchblock (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    block varbinary(800000) NOT NULL,\n"
            + "    inserttime datetime NOT NULL,\n"
             + "   CONSTRAINT batchblock_pk PRIMARY KEY (hash)  \n" + ")";


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
        sqlStatements.add(CREATE_BLOCKS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_TIPS_TABLE);
    
        sqlStatements.add(CREATE_TOKENS_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        int index = new Random().nextInt(1000);
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON headers (prevblockhash)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON headers (prevbranchblockhash)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON outputs (toaddress)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON outputs (tokenid)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (solid)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (milestone)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (rating)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (depth)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (milestonedepth)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (height)");
//        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (fromAddress)");
//        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (toAddress)");
//        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (toSign)");
//        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (fromSign)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON tokens (tokenname)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON tokens (description)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (maintained)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON orderpublish (orderid)");
        return sqlStatements;
    }

    @Override
    protected List<String> getDropIndexsSQL() {
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

    protected String getInsert() {
        return "upsert ";
    }

    protected String getUpdate() {
        return "UPSERT INTO ";
    }

    protected String getUpdateHeadersSQL() {
        return INSERT_BLOCKS_SQL;
    }

    protected String getUpdateSettingsSLQ() {
        return INSERT_SETTINGS_SQL;
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

    @Override
    protected String getUpdateBlockEvaluationCumulativeweightSQL() {
        return getUpdate() + " blocks (cumulativeweight, blockhash) VALUES (?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return getUpdate() + " blocks (depth, blockhash) VALUES (?, ?)";
    }

  
    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return getUpdate() + " blocks (milestone,milestonelastupdate, hash) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return getUpdate() + " blocks (rating, hash) VALUES (?, ?)";
    }

  

    @Override
    protected String getUpdateBlockEvaluationMilestoneDepthSQL() {
        return getUpdate() + " blocks (milestonedepth, hash) VALUES (?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationMaintainedSQL() {
        return getUpdate() + " blocks (maintained, hash) VALUES (?, ?)";
    }

   

    @Override
    protected String getUpdateOutputsSpentSQL() {
        return getUpdate() + " outputs (spent, spenderblockhash, hash, outputindex) VALUES (?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateOutputsConfirmedSQL() {
        return getUpdate() + " outputs (confirmed, hash, outputindex) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpdateOutputsSpendPendingSQL() {
        return getUpdate() + " outputs (spendpending, hash, outputindex) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpdateBlockevaluationUnmaintainAllSQL() {
        return getUpdate() + " blocks (maintained) VALUES (false)";
    }

}
