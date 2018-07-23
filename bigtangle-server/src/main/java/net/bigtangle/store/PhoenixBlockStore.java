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
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated, so you can use
 * {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly
 * look up the quantity of bitcoins controlled by that address.
 * </p>
 */

public class PhoenixBlockStore extends DatabaseFullPrunedBlockStore {

    //private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    @Override
    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        return null;
//        List<StoredBlock> storedBlocks = new ArrayList<StoredBlock>();
//        maybeConnect();
//        PreparedStatement s = null;
//        try {
//            String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT  headers.height, header, wasundoable,prevblockhash,"
//                    + "prevbranchblockhash,mineraddress,tokenid,blocktype FROM headers INNER JOIN blockevaluation"
//                    + " ON headers.hash=blockevaluation.hash WHERE blockevaluation.solid = true AND (prevblockhash = ?)"
//                    + afterSelect();
//            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_BLOCKS_SQL);
//            s.setString(1, Utils.HEX.encode(hash.getBytes()));
//            // s.setString(2, Utils.HEX.encode(hash.getBytes()));
//            ResultSet results = s.executeQuery();
//            while (results.next()) {
//                // Parse it.
//                int height = results.getInt(1);
//                Block b = params.getDefaultSerializer().makeBlock(results.getBytes(2));
//                b.verifyHeader();
//                storedBlocks.add(new StoredBlock(b, height));
//            }
//        } catch (SQLException ex) {
//            throw new BlockStoreException(ex);
//        } catch (ProtocolException e) {
//            // Corrupted database.
//            throw new BlockStoreException(e);
//        } catch (VerificationException e) {
//            // Should not be able to happen unless the database contains bad
//            // blocks.
//            throw new BlockStoreException(e);
//        } finally {
//            if (s != null) {
//                try {
//                    s.close();
//                } catch (SQLException e) {
//                    throw new BlockStoreException("Failed to close PreparedStatement");
//                }
//            }
//        }
//        try {
//            String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT  headers.height, header, wasundoable,prevblockhash,"
//                    + "prevbranchblockhash,mineraddress,tokenid,blocktype FROM headers INNER JOIN blockevaluation"
//                    + " ON headers.hash=blockevaluation.hash WHERE blockevaluation.solid = true AND (prevbranchblockhash = ?)"
//                    + afterSelect();
//            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_BLOCKS_SQL);
//            s.setString(1, Utils.HEX.encode(hash.getBytes()));
//            // s.setString(2, Utils.HEX.encode(hash.getBytes()));
//            ResultSet results = s.executeQuery();
//            while (results.next()) {
//                // Parse it.
//                int height = results.getInt(1);
//                Block b = params.getDefaultSerializer().makeBlock(results.getBytes(2));
//                b.verifyHeader();
//                storedBlocks.add(new StoredBlock(b, height));
//            }
//        } catch (SQLException ex) {
//            throw new BlockStoreException(ex);
//        } catch (ProtocolException e) {
//            // Corrupted database.
//            throw new BlockStoreException(e);
//        } catch (VerificationException e) {
//            // Should not be able to happen unless the database contains bad
//            // blocks.
//            throw new BlockStoreException(e);
//        } finally {
//            if (s != null) {
//                try {
//                    s.close();
//                } catch (SQLException e) {
//                    throw new BlockStoreException("Failed to close PreparedStatement");
//                }
//            }
//        }
//        return storedBlocks;
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
    public static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" + "    name varchar(32) not null,\n"
            + "    settingvalue VARBINARY(10000),\n" + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" + ")\n";

    public static final String CREATE_BLOCKS_TABLE = "CREATE TABLE headers (\n" + "    hash BINARY(32) not null,\n"
            + "    height bigint ,\n" + "    header VARBINARY(4000) ,\n" + "    wasundoable boolean ,\n"
            + "    prevblockhash VARCHAR(255) ,\n" + "    prevbranchblockhash VARCHAR(255) ,\n"
            + "    mineraddress VARBINARY(255),\n" + "    tokenid VARBINARY(255),\n" + "    blocktype bigint ,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash)  \n" + ")";

    public static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE undoableblocks (\n"
            + "    hash VARBINARY(32) not null,\n" + "    height bigint ,\n" + "    txoutchanges VARBINARY(4000),\n"
            + "    transactions VARBINARY(4000),\n" + "    CONSTRAINT undoableblocks_pk PRIMARY KEY (hash)  \n" + ")\n";

    public static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" + "    hash binary(32) not null,\n"
            + "    outputindex bigint not null,\n" + "    height bigint ,\n" + "    coinvalue bigint ,\n"
            + "    scriptbytes VARBINARY(4000) ,\n" + "    toaddress varchar(255),\n"
            + "    addresstargetable bigint,\n" + "    coinbase boolean,\n" + "    blockhash  VARBINARY(32)  ,\n"
            + "    tokenid varchar(255),\n" + "    fromaddress varchar(35),\n" + "    description varchar(80),\n"
            + "    spent boolean ,\n" + "    confirmed boolean ,\n" + "    spendpending boolean ,\n"
            + "    spenderblockhash  VARBINARY(32),\n" + "    CONSTRAINT outputs_pk PRIMARY KEY (hash,outputindex)  \n"
            + ")\n";

    public static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n" + "    hash VARBINARY(32) not null,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash)  \n" + ")\n";

    public static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE blockevaluation (\n"
            + "    blockhash BINARY(32) not null,\n" + "    rating bigint ,\n" + "    depth bigint,\n"
            + "    cumulativeweight  bigint ,\n" + "    solid boolean ,\n" + "    height bigint,\n"
            + "    milestone boolean,\n" + "    milestonelastupdate bigint,\n" + "    milestonedepth bigint,\n"
            + "    inserttime bigint,\n" + "    maintained boolean,\n" + "    rewardvalidityassessment boolean,\n"
            + "    CONSTRAINT blockevaluation_pk PRIMARY KEY (blockhash) )\n";

    public static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    tokenid VARBINARY(255) not null ,\n" + "    tokenname varchar(255)  ,\n" + "    amount bigint ,\n"
            + "    description varchar(255),\n" + "    blocktype integer ,\n"
            + "    CONSTRAINT tokenid_pk PRIMARY KEY (tokenid) \n)";

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
        sqlStatements.add(CREATE_BLOCKEVALUATION_TABLE);
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
        return getUpdate() + " blockevaluation (cumulativeweight, blockhash) VALUES (?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return getUpdate() + " blockevaluation (depth, blockhash) VALUES (?, ?)";
    }

  
    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return getUpdate() + " blockevaluation (milestone,milestonelastupdate, hash) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return getUpdate() + " blockevaluation (rating, hash) VALUES (?, ?)";
    }

  

    @Override
    protected String getUpdateBlockEvaluationMilestoneDepthSQL() {
        return getUpdate() + " blockevaluation (milestonedepth, hash) VALUES (?, ?)";
    }

    @Override
    protected String getUpdateBlockEvaluationMaintainedSQL() {
        return getUpdate() + " blockevaluation (maintained, hash) VALUES (?, ?)";
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
        return getUpdate() + " blockevaluation (maintained) VALUES (false)";
    }

}
