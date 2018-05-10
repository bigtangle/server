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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ProtocolException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProviderException;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.script.Script;

/**
 * <p>
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated, so you can use
 * {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly
 * look up the quantity of bitcoins controlled by that address.
 * </p>
 */

public class PhoenixBlockStore extends DatabaseFullPrunedBlockStore {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);
    
    @Override
    protected void putUpdateStoredBlock(StoredBlock storedBlock, boolean wasUndoable) throws SQLException {
        try {
            PreparedStatement s = conn.get().prepareStatement(getInsertHeadersSQL());

            s.setBytes(1, storedBlock.getHeader().getHash().getBytes());
            s.setLong(2, storedBlock.getHeight());
            s.setBytes(3, storedBlock.getHeader().unsafeBitcoinSerialize());
            s.setBoolean(4, wasUndoable);
            s.setString(5, Utils.HEX.encode(storedBlock.getHeader().getPrevBlockHash().getBytes()));
            s.setString(6, Utils.HEX.encode(storedBlock.getHeader().getPrevBranchBlockHash().getBytes()));
            s.setBytes(7, storedBlock.getHeader().getMineraddress());
            s.setBytes(8, storedBlock.getHeader().getTokenid());
            s.setLong(9, storedBlock.getHeader().getBlocktype());
            s.executeUpdate();
            s.close();
            log.info("add block hexStr : " + storedBlock.getHeader().getHash().toString());
        } catch (SQLException e) {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())) || !wasUndoable)
                throw e;
            PreparedStatement s = conn.get().prepareStatement(getUpdateHeadersSQL());
            s.setBoolean(1, true);
            s.setBytes(2, storedBlock.getHeader().getHash().getBytes());
            s.executeUpdate();
            s.close();
        }
    }
    
    @Override
    public List<StoredBlock> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        List<StoredBlock> storedBlocks = new ArrayList<StoredBlock>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HEADERS_SQL);
            s.setString(1, Utils.HEX.encode(hash.getBytes()));
            s.setString(2, Utils.HEX.encode(hash.getBytes()));
            ResultSet results = s.executeQuery();
            while (results.next()) {
                // Parse it.
                int height = results.getInt(1);
                Block b = params.getDefaultSerializer().makeBlock(results.getBytes(2));
                b.verifyHeader();
                storedBlocks.add(new StoredBlock(b, height));
            }
            return storedBlocks;
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
    }
    
    @Override
    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException {
        List<Sha256Hash> storedBlockHash = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HASHES_SQL);
            s.setString(1, Utils.HEX.encode(hash.getBytes()));
            s.setString(2, Utils.HEX.encode(hash.getBytes()));
            ResultSet results = s.executeQuery();
            while (results.next()) {
                storedBlockHash.add(Sha256Hash.wrap(results.getBytes(1)));
            }
            return storedBlockHash;
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
    }
    
    @Override
    public UTXO getTransactionOutput(Sha256Hash hash, long index) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(getSelectOpenoutputsSQL());
            s.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            s.setLong(2, index);
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            long height = results.getLong(1);
            Coin coinvalue = Coin.valueOf(results.getLong(2), Utils.HEX.decode(results.getString(8)));
            byte[] scriptBytes = results.getBytes(3);
            boolean coinbase = results.getBoolean(4);
            String address = results.getString(5);
            Sha256Hash blockhash = Sha256Hash.wrap(results.getBytes(7));

            String fromaddress = results.getString(9);
            String description = results.getString(10);
            boolean spent = results.getBoolean(11);
            boolean confirmed = results.getBoolean(12);
            boolean spendPending = results.getBoolean(13);
            String tokenid = results.getString("tokenid");
            UTXO txout = new UTXO(hash, index, coinvalue, height, coinbase, new Script(scriptBytes), address, blockhash,
                    fromaddress, description, Utils.HEX.decode(tokenid), spent, confirmed, spendPending);
            return txout;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new BlockStoreException(ex);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }
    
    @Override
    public void addUnspentTransactionOutput(UTXO out) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(getInsertOpenoutputsSQL());
            s.setBytes(1, out.getHash().getBytes());
            // index is actually an unsigned int
            s.setLong(2, out.getIndex());
            s.setLong(3, out.getHeight());
            s.setLong(4, out.getValue().value);
            s.setBytes(5, out.getScript().getProgram());
            s.setString(6, out.getAddress());
            s.setLong(7, out.getScript().getScriptType().ordinal());
            s.setBoolean(8, out.isCoinbase());
            s.setBytes(9, out.getBlockhash().getBytes());
            s.setString(10, Utils.HEX.encode(out.getValue().tokenid));
            s.setString(11, out.getFromaddress());
            s.setString(12, out.getDescription());
            s.setBoolean(13, out.isSpent());
            s.setBoolean(14, out.isConfirmed());
            s.setBoolean(15, out.isSpendPending());
            s.executeUpdate();
            s.close();
        } catch (SQLException e) {
            if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
                throw new BlockStoreException(e);
        } finally {
            if (s != null) {
                try {
                    if (s.getConnection() != null)
                        s.close();
                } catch (SQLException e) {
                    // throw new BlockStoreException(e);
                }
            }
        }
    }
    
    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        PreparedStatement s = null;
        List<UTXO> outputs = new ArrayList<UTXO>();
        try {
            maybeConnect();
            s = conn.get().prepareStatement(getTransactionOutputSelectSQL());
            for (Address address : addresses) {
                s.setString(1, address.toString());
                ResultSet rs = s.executeQuery();
                while (rs.next()) {
                    Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
                    Coin amount = Coin.valueOf(rs.getLong(2), Utils.HEX.decode(rs.getString(10)));
                    byte[] scriptBytes = rs.getBytes(3);
                    int height = rs.getInt(4);
                    int index = rs.getInt(5);
                    boolean coinbase = rs.getBoolean(6);
                    String toAddress = rs.getString(7);
                    // addresstargetable =rs.getBytes(8);
                    Sha256Hash blockhash = Sha256Hash.wrap(rs.getBytes(9));

                    String fromaddress = rs.getString(11);
                    String description = rs.getString(12);
                    boolean spent = rs.getBoolean(13);
                    boolean confirmed = rs.getBoolean(14);
                    boolean spendPending = rs.getBoolean(15);
                    String tokenid = rs.getString("tokenid");
                    UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(scriptBytes), toAddress,
                            blockhash, fromaddress, description, Utils.HEX.decode(tokenid), spent, confirmed, spendPending);
                    outputs.add(output);
                }
            }
            return outputs;
        } catch (SQLException ex) {
        	log.error("getOpenTransactionOutputs sql error, ", ex);
            throw new UTXOProviderException(ex);
        } catch (BlockStoreException bse) {
            throw new UTXOProviderException(bse);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) {
                	log.error("Could not close statement", e);
                    throw new UTXOProviderException("Could not close statement", e);
                }
        }
    }
    
    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses, byte[] tokenid00)
            throws UTXOProviderException {
        PreparedStatement s = null;
        List<UTXO> outputs = new ArrayList<UTXO>();
        try {
            maybeConnect();
            s = conn.get().prepareStatement(getTransactionOutputTokenSelectSQL());
            for (Address address : addresses) {
                s.setString(1, address.toString());
                s.setBytes(2, tokenid00);
                ResultSet rs = s.executeQuery();
                while (rs.next()) {
                    Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
                    Coin amount = Coin.valueOf(rs.getLong(2), rs.getBytes(10));
                    byte[] scriptBytes = rs.getBytes(3);
                    int height = rs.getInt(4);
                    int index = rs.getInt(5);
                    boolean coinbase = rs.getBoolean(6);
                    String toAddress = rs.getString(7);
                    // addresstargetable =rs.getBytes(8);
                    Sha256Hash blockhash = Sha256Hash.wrap(rs.getBytes(9));

                    String fromaddress = rs.getString(11);
                    String description = rs.getString(12);
                    boolean spent = rs.getBoolean(13);
                    boolean confirmed = rs.getBoolean(14);
                    boolean spendPending = rs.getBoolean(15);
                    String tokenid = rs.getString("tokenid");
                    UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(scriptBytes), toAddress,
                            blockhash, fromaddress, description, Utils.HEX.decode(tokenid), spent, confirmed, spendPending);
                    outputs.add(output);
                }
            }
            return outputs;
        } catch (SQLException ex) {
            throw new UTXOProviderException(ex);
        } catch (BlockStoreException bse) {
            throw new UTXOProviderException(bse);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new UTXOProviderException("Could not close statement", e);
                }
        }
    }
    
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "org.apache.phoenix.queryserver.client.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:phoenix:thin:url=http://";

    // create table SQL
    public static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" 
            + "    name varchar(32) not null,\n"
            + "    settingvalue VARBINARY(10000),\n"
            + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n"
            + ")\n";

    public static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n"
            + "    hash BINARY(32) not null,\n"
            + "    height bigint ,\n" 
            + "    header VARBINARY(4000) ,\n" 
            + "    wasundoable boolean ,\n"
            + "    prevblockhash VARCHAR(255) ,\n" 
            + "    prevbranchblockhash VARCHAR(255) ,\n"
            + "    mineraddress VARBINARY(255),\n" 
            + "    tokenid VARBINARY(255),\n" 
            + "    blocktype bigint ,\n"
            + "    CONSTRAINT headers_pk PRIMARY KEY (hash)  \n" + ")";

    public static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE undoableblocks (\n"
            + "    hash VARBINARY(32) not null,\n" 
            + "    height bigint ,\n" 
            + "    txoutchanges VARBINARY(4000),\n"
            + "    transactions VARBINARY(4000),\n" 
            + "    CONSTRAINT undoableblocks_pk PRIMARY KEY (hash)  \n" + ")\n";

 
    public static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    hash binary(32) not null,\n"
            + "    outputindex bigint not null,\n" 
            + "    height bigint ,\n" 
            + "    coinvalue bigint ,\n"
            + "    scriptbytes VARBINARY(4000) ,\n" 
            + "    toaddress varchar(255),\n" 
            + "    addresstargetable bigint,\n"
            + "    coinbase boolean,\n" 
            + "    blockhash  VARBINARY(32)  ,\n" 
            + "    tokenid varchar(255),\n"
            + "    fromaddress varchar(35),\n" 
            + "    description varchar(80),\n" 
            + "    spent boolean ,\n"
            + "    confirmed boolean ,\n" 
            + "    spendpending boolean ,\n" 
            + "    spenderblockhash  VARBINARY(32),\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash,outputindex)  \n" + ")\n";

    public static final String CREATE_TIPS_TABLE = "CREATE TABLE tips (\n" 
            + "    hash VARBINARY(32) not null,\n"
            + "    CONSTRAINT tips_pk PRIMARY KEY (hash)  \n" 
            + ")\n";

    public static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE blockevaluation (\n"
            + "    blockhash BINARY(32) not null,\n" 
            + "    rating bigint ,\n" 
            + "    depth bigint,\n"
            + "    cumulativeweight  bigint ,\n" 
            + "    solid boolean ,\n"
            + "    height bigint,\n"
            + "    milestone boolean,\n"
            + "    milestonelastupdate bigint,\n" 
            + "    milestonedepth bigint,\n"
            + "    inserttime bigint,\n" 
            + "    maintained boolean,\n"
            + "    rewardvalidityassessment boolean,\n"
            + "    CONSTRAINT blockevaluation_pk PRIMARY KEY (blockhash) )\n";

    public static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n" 
            + "    tokenid VARBINARY(255) not null ,\n"
            + "    tokenname varchar(255)  ,\n" 
            + "    amount bigint ,\n" 
            + "    description varchar(255),\n"
            + "    blocktype integer ,\n" 
            + "    CONSTRAINT tokenid_pk PRIMARY KEY (tokenid) \n)";

    public static final String CREATE_ORDERPUBLISH_TABLE = "CREATE TABLE orderpublish (\n"
            + "   orderid VARBINARY(255) not null,\n" 
            + "   address VARBINARY(255),\n" 
            + "   tokenid VARBINARY(255),\n"
            + "   type integer,\n" 
            + "   validateto DATE,\n" 
            + "   validatefrom DATE,\n"
            + "   price bigint,\n"
            + "   amount bigint,\n" 
            + "   state integer,\n" 
            + "   market VARBINARY(255),\n"
            + "   CONSTRAINT orderid_pk PRIMARY KEY (orderid) )";

    public static final String CREATE_ORDERMATCH_TABLE = "CREATE TABLE ordermatch (\n"
            + "   matchid varchar(255) not null,\n" 
            + "   restingOrderId varchar(255),\n"
            + "   incomingOrderId varchar(255),\n"
            + "   type integer,\n" 
            + "   price bigint,\n"
            + "   executedQuantity bigint,\n" 
            + "   remainingQuantity bigint,\n"
            + "   CONSTRAINT matchid_pk PRIMARY KEY (matchid) )";

    public static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE exchange (\n"
            + "   orderid varchar(255) not null,\n" 
            + "   fromAddress varchar(255),\n"
            + "   fromTokenHex varchar(255),\n" 
            + "   fromAmount varchar(255),\n"
            + "   toAddress varchar(255),\n"
            + "   toTokenHex varchar(255),\n" 
            + "   toAmount varchar(255),\n" 
            + "   data VARBINARY(5000) ,\n"
            + "   toSign integer,\n" 
            + "   fromSign integer,\n" 
            + "   toOrderId varchar(255),\n"
            + "   fromOrderId varchar(255),\n" 
            + "   CONSTRAINT orderid_pk PRIMARY KEY (orderid) )";

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
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (fromAddress)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (toAddress)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (toSign)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON exchange (fromSign)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON tokens (tokenname)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON tokens (description)");
        sqlStatements.add("CREATE LOCAL INDEX idx_" + (index++) + " ON blockevaluation (maintained)");
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
        System.out.println("bbb > " + Utils.HEX.encode(hash.getBytes()));
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
        return getUpdate() +" blockevaluation (cumulativeweight, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return getUpdate() +" blockevaluation (depth, blockhash) VALUES (?, ?)";
    }
    
    @Override
    public String getUpdateBlockEvaluationHeightSQL() {
        return getUpdate() +" blockevaluation (height, blockhash) VALUES (?, ?)";
    }
    
    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return getUpdate() +" blockevaluation (milestone, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return getUpdate() +" blockevaluation (rating, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationSolidSQL() {
        return getUpdate() +" blockevaluation (solid, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMilestoneLastUpdateTimeSQL() {
        return getUpdate() +" blockevaluation (milestonelastupdate, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMilestoneDepthSQL() {
        return getUpdate() +" blockevaluation (milestonedepth, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationMaintainedSQL() {
        return getUpdate() +" blockevaluation (maintained, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateBlockEvaluationRewardValidItyassessmentSQL() {
        return getUpdate() +" blockevaluation (rewardvalidityassessment, blockhash) VALUES (?, ?)";
    }
    
    @Override
    protected String getUpdateOutputsSpentSQL() {
        return getUpdate() +" outputs (spent, spenderblockhash, hash, outputindex) VALUES (?, ?, ?, ?)";
    }
    
    @Override
    protected String getUpdateOutputsConfirmedSQL() {
        return getUpdate() +" outputs (confirmed, hash, outputindex) VALUES (?, ?, ?)";
    }
    
    @Override
    protected String getUpdateOutputsSpendPendingSQL() {
        return getUpdate() +" outputs (spendpending, hash, outputindex) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpdateBlockevaluationUnmaintainAllSQL() {
        return getUpdate() + " blockevaluation (maintained) VALUES (false)";
    }
    
    @Override
    public void updateUnmaintainAll() throws BlockStoreException {
        maybeConnect();
        List<byte[]> buf = new ArrayList<byte[]>();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "select blockhash from blockevaluation where maintained = true";
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                buf.add(resultSet.getBytes("blockhash"));
            }
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
        finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
        if (buf.isEmpty()) {
            return;
        }
//        PreparedStatement preparedStatement = null;
        try {
            for (byte[] b : buf) {
                String sql = getUpdate() + " blockevaluation (blockhash, maintained) VALUES (?, false)";
                preparedStatement = conn.get().prepareStatement(sql);
                preparedStatement.setBytes(1, b);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }
}
