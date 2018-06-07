/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;

import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.ProtocolException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredBlockBinary;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProviderException;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.script.Script;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {
    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    protected String VERSION_SETTING = "version";

    // Drop table SQL.
    public static String DROP_SETTINGS_TABLE = "DROP TABLE settings";
    public static String DROP_HEADERS_TABLE = "DROP TABLE headers";
    public static String DROP_UNDOABLE_TABLE = "DROP TABLE undoableblocks";
    public static String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE outputs";
    public static String DROP_OUTPUTSMULTI_TABLE = "DROP TABLE outputsmulti";
    public static String DROP_TIPS_TABLE = "DROP TABLE tips";
    public static String DROP_TOKENS_TABLE = "DROP TABLE tokens";
    public static String DROP_EXCHANGE_TABLE = "DROP TABLE exchange";
    public static String DROP_MULTISIGNADDRESS_TABLE = "DROP TABLE multisignaddress";
    public static String DROP_TOKENSERIAL_TABLE = "DROP TABLE tokenserial";
    public static String DROP_MULTISIGNBY_TABLE = "DROP TABLE multisignby";
    public static String DROP_MULTISIGN_TABLE = "DROP TABLE multisign";
    public static String DROP_TX_REWARDS_TABLE = "DROP TABLE txreward";
    public static String DROP_USERDATA_TABLE = "DROP TABLE userdata";
    public static String DROP_PAYMULTISIGN_TABLE = "DROP TABLE paymultisign";
    public static String DROP_PAYMULTISIGNADDRESS_TABLE = "DROP TABLE paymultisignaddress";

    // Queries SQL.
    protected String SELECT_SETTINGS_SQL = "SELECT settingvalue FROM settings WHERE name = ?";
    protected String INSERT_SETTINGS_SQL = getInsert() + "  INTO settings(name, settingvalue) VALUES(?, ?)";

    protected String SELECT_HEADERS_SQL = "SELECT  height, header, wasundoable,prevblockhash,prevbranchblockhash,mineraddress,"
            + "blocktype FROM headers WHERE hash = ?" + afterSelect();
    protected String SELECT_HEADERS_HEIGHT_SQL = "SELECT header FROM headers WHERE height >= ?" + afterSelect()
            + " order by height asc ";
    protected String SELECT_SOLID_APPROVER_HEADERS_SQL = "SELECT  height, header, wasundoable,prevblockhash,"
            + "prevbranchblockhash,mineraddress,blocktype FROM headers "
            + " WHERE solid = true AND (prevblockhash = ? OR prevbranchblockhash = ?)" + afterSelect();
    protected String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM headers " + " "
            + "WHERE solid = true AND (headers.prevblockhash = ? OR headers.prevbranchblockhash = ?)" + afterSelect();

    protected String INSERT_HEADERS_SQL = getInsert()
            + "  INTO headers(hash,  height, header, wasundoable,prevblockhash,"
            + "prevbranchblockhash,mineraddress,blocktype " + "  , rating, depth, cumulativeweight, solid, "
            + "milestone, milestonelastupdate, milestonedepth, inserttime, maintained, rewardvalidityassessment )"
            + " VALUES(?, ?, ?, ?, ?,?,  ?, ?, ?, ?, ?, ?, ?,  ?, ?, ?, ?, ?)";

    protected String SELECT_OUTPUTS_COUNT_SQL = "SELECT COUNT(*) FROM outputs WHERE hash = ?";
    protected String INSERT_OUTPUTS_SQL = getInsert()
            + " INTO outputs (hash, outputindex, height, coinvalue, scriptbytes, toaddress, addresstargetable,"
            + " coinbase, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?)";

    protected String SELECT_OUTPUTS_SQL = "SELECT height, coinvalue, scriptbytes, coinbase, toaddress,"
            + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending FROM outputs WHERE hash = ? AND outputindex = ?";

    protected String DELETE_OUTPUTS_SQL = "DELETE FROM outputs WHERE hash = ? AND outputindex= ?";

    // protected String SELECT_TRANSACTION_OUTPUTS_SQL = "SELECT hash,
    // coinvalue, scriptbytes, height, "
    // + "outputindex, coinbase, toaddress, addresstargetable, blockhash,
    // tokenid, "
    // + "fromaddress, memo, spent, confirmed, spendpending FROM outputs where
    // toaddress = ?";

    protected String SELECT_TRANSACTION_OUTPUTS_SQL = "SELECT " + "outputs.hash, coinvalue, scriptbytes, height, "
            + " outputs.outputindex, coinbase, " + "case  when outputs.toaddress ='' then outputsmulti.toaddress "
            + "when outputs.toaddress is null then outputsmulti.toaddress "
            + " else outputs.toaddress end, addresstargetable, blockhash, tokenid, "
            + " fromaddress, memo, spent, confirmed, spendpending, outputsmulti.minimumsign AS minimumsign "
            + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?";

    // protected String SELECT_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT hash,
    // coinvalue, "
    // + "scriptbytes, height, outputindex, coinbase, toaddress,
    // addresstargetable,"
    // + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending
    // "
    // + "FROM outputs where toaddress = ? and tokenid = ?";

    protected String SELECT_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, height, outputs.outputindex, coinbase, outputs.toaddress, addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending "
            + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE (outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?) " + " AND tokenid = ?";

    // Dump table SQL (this is just for data sizing statistics).
    protected String SELECT_DUMP_SETTINGS_SQL = "SELECT name, coinvalue FROM settings";
    protected String SELECT_DUMP_HEADERS_SQL = "SELECT chainwork, header FROM headers";

    protected String SELECT_DUMP_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes FROM outputs";

    // Select the maximum height of all solid blocks
    protected String SELECT_MAX_SOLID_HEIGHT = "SELECT max(height) FROM headers WHERE solid = true" + afterSelect();

    // Tables exist SQL.
    protected String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

    // Compatibility SQL.
    // protected String SELECT_COMPATIBILITY_COINBASE_SQL = "SELECT
    // coinbase FROM outputs WHERE 1 = 2";

    protected String DELETE_TIP_SQL = "DELETE FROM tips WHERE hash = ?";
    protected String INSERT_TIP_SQL = getInsert() + "  INTO tips (hash) VALUES (?)";

    protected String SELECT_BLOCKEVALUATION_SQL = "SELECT hash, rating, depth, cumulativeweight, "
            + "solid, height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained,"
            + " rewardvalidityassessment FROM headers WHERE hash = ?" + afterSelect();

    protected String SELECT_COUNT_MILESTONE_SQL = "SELECT COUNT(*) as count FROM headers WHERE milestone = true AND height >= ? AND height <= ?";
    protected String SELECT_SOLID_BLOCKEVALUATIONS_SQL = "SELECT  hash, rating, depth, "
            + "cumulativeweight, solid, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, rewardvalidityassessment " + "FROM headers WHERE solid = true"
            + afterSelect();
    protected String SELECT_ALL_BLOCKEVALUATIONS_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight, solid, height, milestone,"
            + " milestonelastupdate, milestonedepth, inserttime, maintained, "
            + "rewardvalidityassessment FROM headers ";
    protected String SELECT_NONSOLID_BLOCKS_SQL = "SELECT hash, rating, "
            + "depth, cumulativeweight, solid, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, rewardvalidityassessment FROM headers WHERE solid = false"
            + afterSelect();
    protected String SELECT_BLOCKS_TO_ADD_TO_MILESTONE_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, rewardvalidityassessment "
            + "FROM headers WHERE solid = true AND milestone = false AND rating >= "
            + NetworkParameters.MILESTONE_UPPER_THRESHOLD + " AND depth >= ?" + afterSelect();
    protected String SELECT_BLOCKS_IN_MILESTONEDEPTH_INTERVAL_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, rewardvalidityassessment "
            + "FROM headers WHERE milestone = true AND milestonedepth >= ? AND milestonedepth <= ?" + afterSelect();
    protected String SELECT_BLOCKS_TO_REMOVE_FROM_MILESTONE_SQL = "SELECT hash, rating, depth, "
            + "cumulativeweight, solid, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, rewardvalidityassessment"
            + " FROM headers WHERE solid = true AND milestone = true AND rating <= "
            + NetworkParameters.MILESTONE_LOWER_THRESHOLD + afterSelect();
    protected String SELECT_SOLID_TIPS_SQL = "SELECT headers.hash, rating, depth, cumulativeweight, "
            + "solid, height, milestone, milestonelastupdate, milestonedepth, inserttime,"
            + "maintained, rewardvalidityassessment FROM headers "
            + "INNER JOIN tips ON tips.hash=headers.hash WHERE solid = true" + afterSelect();
    protected String SELECT_SOLID_BLOCKS_OF_HEIGHT_SQL = "SELECT hash, rating, depth, "
            + "cumulativeweight, solid, height, milestone, milestonelastupdate, "
            + "milestonedepth, inserttime, maintained, rewardvalidityassessment "
            + "FROM headers WHERE solid = true && height = ?" + afterSelect();
    protected String SELECT_OUTPUT_SPENDER_SQL = "SELECT headers.hash," + " rating, depth, cumulativeweight, solid,  "
            + "headers.height, milestone, milestonelastupdate, "
            + "milestonedepth, inserttime, maintained, rewardvalidityassessment "
            + "FROM headers INNER JOIN outputs ON outputs.spenderblockhash=headers.hash"
            + " WHERE solid = true and headers.hash = ? AND outputindex= ?" + afterSelect();
    protected String SELECT_MAX_IMPORT_TIME_SQL = "SELECT MAX(inserttime) " + "FROM headers WHERE solid = true";

    protected String SELECT_MAX_TOKENID_SQL = "select max(tokenid) from tokens";
    protected String INSERT_TOKENS_SQL = getInsert()
            + "  INTO tokens (tokenid, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    protected String UPDATE_TOKENS_SQL = getUpdate()
            + "  tokens set tokenname = ?, description = ?, url = ?, signnumber = ?, multiserial = ?, asmarket = ?, tokenstop = ? WHERE tokenid = ?";

    protected String SELECT_TOKENS_SQL = "select tokenid, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop from tokens";

    protected String SELECT_MARKET_TOKENS_SQL = "select tokenid, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop from tokens WHERE asmarket=true";

    protected String SELECT_TOKENS_ACOUNT_MAP_SQL = "select tokenid,sum(amount) as amount from tokenserial GROUP BY tokenid";

    protected String SELECT_TOKENS_INFO_SQL = "select tokenid, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop from tokens where tokenid = ?";

    protected String INSERT_EXCHANGE_SQL = getInsert()
            + "  INTO exchange (orderid, fromAddress, fromTokenHex, fromAmount,"
            + " toAddress, toTokenHex, toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected String SELECT_EXCHANGE_SQL = "SELECT orderid, fromAddress, "
            + "fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, "
            + "data, toSign, fromSign, toOrderId, fromOrderId, market "
            + "FROM exchange WHERE (fromAddress = ? OR toAddress = ?) AND (toSign = false OR fromSign = false)"
            + afterSelect();
    protected String SELECT_EXCHANGE_ORDERID_SQL = "SELECT orderid,"
            + " fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex,"
            + " toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market FROM exchange WHERE orderid = ?";

    protected String UPDATE_SETTINGS_SQL = getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    protected String UPDATE_HEADERS_SQL = getUpdate() + " headers SET wasundoable=? WHERE hash=?";

    protected String UPDATE_UNDOABLEBLOCKS_SQL = getUpdate()
            + " undoableblocks SET txoutchanges=?, transactions=? WHERE hash = ?";

    protected String UPDATE_OUTPUTS_SPENT_SQL = getUpdate()
            + " outputs SET spent = ?, spenderblockhash = ? WHERE hash = ? AND outputindex= ?";

    protected String UPDATE_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE hash = ? AND outputindex= ?";

    protected String UPDATE_OUTPUTS_SPENDPENDING_SQL = getUpdate()
            + " outputs SET spendpending = ? WHERE hash = ? AND outputindex= ?";

    protected String UPDATE_BLOCKEVALUATION_DEPTH_SQL = getUpdate() + " headers SET depth = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL = getUpdate()
            + " headers SET cumulativeweight = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_HEIGHT_SQL = getUpdate() + " headers SET height = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = getUpdate()
            + " headers SET milestone = ?,milestonelastupdate= ?  WHERE hash = ?";

    // protected String UPDATE_BLOCKEVALUATION_MILESTONE_LAST_UPDATE_TIME_SQL =
    // getUpdate()
    // + " headers SET milestonelastupdate = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_RATING_SQL = getUpdate() + " headers SET rating = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_SOLID_SQL = getUpdate() + " headers SET solid = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_MILESTONEDEPTH_SQL = getUpdate()
            + " headers SET milestonedepth = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_MAINTAINED_SQL = getUpdate() + " headers SET maintained = ? WHERE hash = ?";

    protected String UPDATE_BLOCKEVALUATION_REWARDVALIDITYASSESSMENT_SQL = getUpdate()
            + " headers SET rewardvalidityassessment = ? WHERE hash = ?";

    protected abstract String getUpdateBlockevaluationUnmaintainAllSQL();

    protected String SELECT_MULTISIGNADDRESS_SQL = "SELECT tokenid, address, pubKeyHex FROM multisignaddress WHERE tokenid = ?";
    protected String INSERT_MULTISIGNADDRESS_SQL = "INSERT INTO multisignaddress (tokenid, address, pubKeyHex) VALUES (?, ?, ?)";
    protected String DELETE_MULTISIGNADDRESS_SQL = "DELETE FROM multisignaddress WHERE tokenid = ? AND address = ?";
    protected String COUNT_MULTISIGNADDRESS_SQL = "SELECT COUNT(*) as count FROM multisignaddress WHERE tokenid = ?";
    protected String SELECT_MULTISIGNADDRESSINFO_SQL = "SELECT tokenid, address FROM multisignaddress WHERE tokenid = ? AND address = ?";
    protected String DELETE_MULTISIGNADDRESS_SQL0 = "DELETE FROM multisignaddress WHERE tokenid = ?";

    protected String INSERT_TOKENSERIAL_SQL = "INSERT INTO tokenserial (tokenid, tokenindex, amount) VALUES (?, ?, ?)";
    protected String UPDATE_TOKENSERIAL_SQL = "UPDATE tokenserial SET amount = ? WHERE tokenid = ? AND tokenindex = ?";
    protected String SELECT_TOKENSERIAL_SQL = "SELECT tokenid, tokenindex, amount FROM tokenserial WHERE tokenid = ? AND tokenindex = ?";
    protected String SELECT_TOKENSERIAL0_SQL = "SELECT tokenid, tokenindex, amount FROM tokenserial WHERE tokenid = ?";
    protected String SELECT_SEARCH_TOKENSERIAL_SQL = "SELECT tokenid, tokenindex, amount FROM tokenserial ";
    protected String COUNT_TOKENSERIAL0_SQL = "SELECT COUNT(*) as count FROM tokenserial WHERE tokenid = ?";

    protected String SELECT_SEARCH_TOKENSERIAL_ALL_SQL = "SELECT ts.tokenid as tokenid, tokenindex, amount,signnumber,"
            + "(select count(address) FROM multisign ms WHERE ts.tokenid=ms.tokenid and ts.tokenindex=ms.tokenindex AND ms.sign=1) as count "
            + "FROM tokenserial ts  " + "LEFT JOIN multisignaddress msa ON ts.tokenid=msa.tokenid "
            + "LEFT JOIN tokens t ON ts.tokenid=t.tokenid WHERE 1=1 ";

    protected String INSERT_MULTISIGNBY_SQL = "INSERT INTO multisignby (tokenid, tokenindex, address) VALUES (?, ?, ?)";
    protected String SELECT_MULTISIGNBY_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected String SELECT_MULTISIGNBY0_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ?";
    protected String SELECT_MULTISIGNBY000_SQL = "SELECT tokenid, tokenindex, address FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";

    protected String SELECT_MULTISIGN_ADDRESS_ALL_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign,(select count(ms1.sign) from multisign ms1 where ms1.tokenid=tokenid and tokenindex=ms1.tokenindex and ms1.sign!=0 ) as count FROM multisign  WHERE 1=1 ";
    protected String SELECT_MULTISIGN_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE address = ? ORDER BY tokenindex ASC";
    protected String INSERT_MULTISIGN_SQL = "INSERT INTO multisign (tokenid, tokenindex, address, blockhash, sign, id) VALUES (?, ?, ?, ?, ?, ?)";
    protected String UPDATE_MULTISIGN_SQL = "UPDATE multisign SET blockhash = ?, sign = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected String UPDATE_MULTISIGN0_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected String UPDATE_MULTISIGN1_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ?";
    protected String SELECT_COUNT_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected String DELETE_MULTISIGN_SQL = "DELETE FROM multisign WHERE tokenid = ?";

    protected String SELECT_COUNT_MULTISIGN_SIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND sign = ?";

    protected String INSERT_TX_REWARD_SQL = getInsert()
            + "  INTO txreward (blockhash, rewardamount, prevheight, confirmed) VALUES (?, ?, ?, ?)";
    protected String SELECT_TX_REWARD_SQL = "SELECT rewardamount " + "FROM txreward WHERE blockhash = ?";
    protected String SELECT_MAX_TX_REWARD_HEIGHT_SQL = "SELECT MAX(prevheight) "
            + "FROM txreward WHERE confirmed = true";
    protected String SELECT_TX_REWARD_CONFIRMED_SQL = "SELECT confirmed " + "FROM txreward WHERE blockhash = ?";
    protected String UPDATE_TX_REWARD_CONFIRMED_SQL = "UPDATE txreward SET confirmed = ? WHERE blockhash = ?";

    protected String INSERT_OUTPUTSMULTI_SQL = "insert into outputsmulti (hash, toaddress, outputindex, minimumsign) values (?, ?, ?, ?)";

    protected String SELECT_USERDATA_SQL = "SELECT blockhash, dataclassname, data, pubKey FROM userdata WHERE dataclassname = ? and pubKey = ?";
    protected String INSERT_USERDATA_SQL = "INSERT INTO userdata (blockhash, dataclassname, data, pubKey) VALUES (?, ?, ?, ?)";
    protected String UPDATE_USERDATA_SQL = "UPDATE userdata SET blockhash = ?, data = ? WHERE dataclassname = ? and pubKey = ?";
    
    protected NetworkParameters params;
    protected ThreadLocal<Connection> conn;
    protected List<Connection> allConnections;
    protected String connectionURL;
    protected int fullStoreDepth;
    protected String username;
    protected String password;
    protected String schemaName;

    public ThreadLocal<Connection> getConnection() {
        return this.conn;
    }

    /**
     * <p>
     * Create a new DatabaseFullPrunedBlockStore, using the full connection URL
     * instead of a hostname and password, and optionally allowing a schema to
     * be specified.
     * </p>
     *
     * @param params
     *            A copy of the NetworkParameters used.
     * @param connectionURL
     *            The jdbc url to connect to the database.
     * @param fullStoreDepth
     *            The number of blocks of history stored in full (something like
     *            1000 is pretty safe).
     * @param username
     *            The database username.
     * @param password
     *            The password to the database.
     * @param schemaName
     *            The name of the schema to put the tables in. May be null if no
     *            schema is being used.
     * @throws BlockStoreException
     *             If there is a failure to connect and/or initialise the
     *             database.
     */
    public DatabaseFullPrunedBlockStore(NetworkParameters params, String connectionURL, int fullStoreDepth,
            @Nullable String username, @Nullable String password, @Nullable String schemaName)
            throws BlockStoreException {
        this.params = params;
        this.fullStoreDepth = fullStoreDepth;
        this.connectionURL = connectionURL;
        this.schemaName = schemaName;
        this.username = username;
        this.password = password;
        this.conn = new ThreadLocal<Connection>();
        this.allConnections = new LinkedList<Connection>();
        create();
    }

    public void create() throws BlockStoreException {
        try {
            Class.forName(getDatabaseDriverClass());
            log.info(getDatabaseDriverClass() + " loaded. ");
        } catch (ClassNotFoundException e) {
            log.error("check CLASSPATH for database driver jar ", e);
        }
        maybeConnect();

        try {
            // Create tables if needed
            if (!tablesExists()) {
                createTables();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BlockStoreException(e);
        }
    }

    protected String afterSelect() {
        return "";
    }

    protected String getInsert() {
        return "insert ";
    }

    protected String getUpdate() {
        return "update ";
    }

    /**
     * Get the database driver class,
     * <p>
     * i.e org.postgresql.Driver.
     * </p>
     * 
     * @return The fully qualified database driver class.
     */
    protected abstract String getDatabaseDriverClass();

    /**
     * Get the SQL statements that create the schema (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateSchemeSQL();

    /**
     * Get the SQL statements that create the tables (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateTablesSQL();

    /**
     * Get the SQL statements that create the indexes (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateIndexesSQL();

    /**
     * Get the database specific error code that indicated a duplicate key error
     * when inserting a record.
     * <p>
     * This is the code returned by {@link java.sql.SQLException#getSQLState()}
     * </p>
     * 
     * @return The database duplicate error code.
     */
    protected abstract String getDuplicateKeyErrorCode();

    /**
     * Get the SQL statement that checks if tables exist.
     * 
     * @return The SQL prepared statement.
     */
    protected String getTablesExistSQL() {
        return SELECT_CHECK_TABLES_EXIST_SQL;
    }

    /**
     * Get the SQL to select the transaction outputs for a given address.
     * 
     * @return The SQL prepared statement.
     */
    protected String getTransactionOutputSelectSQL() {
        return SELECT_TRANSACTION_OUTPUTS_SQL;
    }

    protected String getTransactionOutputTokenSelectSQL() {
        return SELECT_TRANSACTION_OUTPUTS_TOKEN_SQL;
    }

    /**
     * Get the SQL to drop all the tables (DDL).
     * 
     * @return The SQL drop statements.
     */
    protected List<String> getDropTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(DROP_SETTINGS_TABLE);
        sqlStatements.add(DROP_HEADERS_TABLE);
        sqlStatements.add(DROP_OPEN_OUTPUT_TABLE);
        sqlStatements.add(DROP_OUTPUTSMULTI_TABLE);
        sqlStatements.add(DROP_TIPS_TABLE);
        sqlStatements.add(DROP_TOKENS_TABLE);
        sqlStatements.add(DROP_EXCHANGE_TABLE);
        sqlStatements.add(DROP_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(DROP_TOKENSERIAL_TABLE);
        sqlStatements.add(DROP_MULTISIGNBY_TABLE);
        sqlStatements.add(DROP_MULTISIGN_TABLE);
        sqlStatements.add(DROP_TX_REWARDS_TABLE);
        sqlStatements.add(DROP_USERDATA_TABLE);
        sqlStatements.add(DROP_PAYMULTISIGN_TABLE);
        sqlStatements.add(DROP_PAYMULTISIGNADDRESS_TABLE);
        return sqlStatements;
    }

    /**
     * Get the SQL to select a setting coinvalue.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectSettingsSQL() {
        return SELECT_SETTINGS_SQL;
    }

    /**
     * Get the SQL to insert a settings record.
     * 
     * @return The SQL insert statement.
     */
    protected String getInsertSettingsSQL() {
        return INSERT_SETTINGS_SQL;
    }

    /**
     * Get the SQL to update a setting coinvalue.
     * 
     * @return The SQL update statement.
     */
    protected abstract String getUpdateSettingsSLQ();

    /**
     * Get the SQL to select a headers record.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectHeadersSQL() {
        return SELECT_HEADERS_SQL;
    }

    /**
     * Get the SQL to insert a headers record.
     * 
     * @return The SQL insert statement.
     */
    protected String getInsertHeadersSQL() {
        return INSERT_HEADERS_SQL;
    }

    /**
     * Get the SQL to update a headers record.
     * 
     * @return The SQL update statement.
     */
    protected abstract String getUpdateHeadersSQL();

    /**
     * Get the SQL to select a outputs record.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectOpenoutputsSQL() {
        return SELECT_OUTPUTS_SQL;
    }

    /**
     * Get the SQL to select count of outputs.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectOpenoutputsCountSQL() {
        return SELECT_OUTPUTS_COUNT_SQL;
    }

    /**
     * Get the SQL to insert a outputs record.
     * 
     * @return The SQL insert statement.
     */
    protected String getInsertOpenoutputsSQL() {
        return INSERT_OUTPUTS_SQL;
    }

    /**
     * Get the SQL to delete a outputs record.
     * 
     * @return The SQL delete statement.
     */
    protected String getDeleteOpenoutputsSQL() {
        return DELETE_OUTPUTS_SQL;
    }

    /**
     * Get the SQL to select the setting dump fields for sizing/statistics.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectSettingsDumpSQL() {
        return SELECT_DUMP_SETTINGS_SQL;
    }

    /**
     * Get the SQL to select the headers dump fields for sizing/statistics.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectHeadersDumpSQL() {
        return SELECT_DUMP_HEADERS_SQL;
    }

    /**
     * Get the SQL to select the openoutouts dump fields for sizing/statistics.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectopenoutputsDumpSQL() {
        return SELECT_DUMP_OUTPUTS_SQL;
    }

    protected String getSelectMaxTokenIdSQL() {
        return SELECT_MAX_TOKENID_SQL;
    }

    /**
     * <p>
     * If there isn't a connection on the {@link ThreadLocal} then create and
     * store it.
     * </p>
     * <p>
     * This will also automatically set up the schema if it does not exist
     * within the DB.
     * </p>
     * 
     * @throws BlockStoreException
     *             if successful connection to the DB couldn't be made.
     */
    protected synchronized void maybeConnect() throws BlockStoreException {
        try {
            if (conn.get() != null && !conn.get().isClosed())
                return;

            if (username == null || password == null) {
                conn.set(DriverManager.getConnection(connectionURL));
            } else {
                Properties props = new Properties();
                props.setProperty("user", this.username);
                props.setProperty("password", this.password);
                conn.set(DriverManager.getConnection(connectionURL, props));
            }
            allConnections.add(conn.get());
            Connection connection = conn.get();
            // set the schema if one is needed
            if (schemaName != null) {
                Statement s = connection.createStatement();
                for (String sql : getCreateSchemeSQL()) {
                    s.execute(sql);
                }
            }
            log.info("Made a new connection to database " + connectionURL);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    @Override
    public synchronized void close() {
        for (Connection conn : allConnections) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
                conn.close();
                if (conn == this.conn.get()) {
                    this.conn.set(null);
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        allConnections.clear();
    }

    /**
     * <p>
     * Check if a tables exists within the database.
     * </p>
     *
     * <p>
     * This specifically checks for the 'settings' table and if it exists makes
     * an assumption that the rest of the data structures are present.
     * </p>
     *
     * @return If the tables exists.
     * @throws java.sql.SQLException
     */
    private boolean tablesExists() throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.get().prepareStatement(getTablesExistSQL());
            ResultSet results = ps.executeQuery();
            results.close();
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            if (ps != null && !ps.isClosed()) {
                ps.close();
            }
        }
    }

    /**
     * Create the tables/block store in the database and
     * 
     * @throws java.sql.SQLException
     *             If there is a database error.
     * @throws BlockStoreException
     *             If the block store could not be created.
     */
    private void createTables() throws SQLException, BlockStoreException {
        // create all the database tables
        for (String sql : getCreateTablesSQL()) {
            if (log.isDebugEnabled()) {
                log.debug("DatabaseFullPrunedBlockStore : CREATE table " + sql);
            }
            Statement s = conn.get().createStatement();
            try {
                s.execute(sql);
            } finally {
                s.close();
            }
        }
        // create all the database indexes
        for (String sql : getCreateIndexesSQL()) {
            if (log.isDebugEnabled()) {
                log.debug("DatabaseFullPrunedBlockStore : CREATE index " + sql);
            }
            Statement s = conn.get().createStatement();
            try {
                s.execute(sql);
            } finally {
                s.close();
            }
        }
        // insert the initial settings for this store
        PreparedStatement ps = conn.get().prepareStatement(getInsertSettingsSQL());
        ps.setString(1, VERSION_SETTING);
        ps.setBytes(2, "03".getBytes());
        ps.execute();
        ps.close();
        createNewStore(params);

        ECKey ecKey = new ECKey();
        this.saveTokens(ecKey.getPublicKeyAsHex(), "default market", "default market", "http://localhost:8089/", 0,
                false, true, true);
    }

    /**
     * Create a new store for the given
     * {@link net.bigtangle.core.NetworkParameters}.
     * 
     * @param params
     *            The network.
     * @throws BlockStoreException
     *             If the store couldn't be created.
     */
    private void createNewStore(NetworkParameters params) throws BlockStoreException {
        try {
            // Set up the genesis block.
            StoredBlock storedGenesisHeader = new StoredBlock(params.getGenesisBlock(), 0);
            List<Transaction> genesisTransactions = Lists.newLinkedList();
            StoredUndoableBlock storedGenesis = new StoredUndoableBlock(params.getGenesisBlock().getHash(),
                    genesisTransactions);
            put(storedGenesisHeader, storedGenesis);
            saveGenesisTransactionOutput(params.getGenesisBlock());
            updateBlockEvaluationMilestone(params.getGenesisBlock().getHash(), true);
            updateBlockEvaluationSolid(params.getGenesisBlock().getHash(), true);
            updateBlockEvaluationRewardValid(params.getGenesisBlock().getHash(), true);
            insertTip(params.getGenesisBlock().getHash());
            insertTxReward(params.getGenesisBlock().getHash(), NetworkParameters.INITIAL_TX_REWARD,
                    -NetworkParameters.REWARD_HEIGHT_INTERVAL);
            updateTxRewardConfirmed(params.getGenesisBlock().getHash(), true);
        } catch (VerificationException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }


    public void saveGenesisTransactionOutput(Block block) throws BlockStoreException {

        for (TransactionOutput out : block.getTransactions().get(0).getOutputs()) {
            // For each output, add it to the set of unspent outputs so
            // it can be consumed
            // in future.
            Script script = new Script(out.getScriptBytes());
            UTXO newOut = new UTXO(block.getTransactions().get(0).getHash(), out.getIndex(), out.getValue(), 0, true,
                    script, script.getToAddress(params, true).toString(), block.getHash(), out.getFromaddress(),
                    block.getTransactions().get(0).getMemo(), Utils.HEX.encode(out.getValue().getTokenid()), false,
                    true, false, 0);
            addUnspentTransactionOutput(newOut);

            if (script.isSentToMultiSig()) {
                int minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                for (ECKey ecKey : script.getPubKeys()) {
                    String toaddress = ecKey.toAddress(params).toBase58();
                    OutputsMulti outputsMulti = new OutputsMulti(newOut.getHash(), toaddress, newOut.getIndex(),
                            minsignnumber);
                    this.insertOutputsMulti(outputsMulti);
                }
            }

        }
    }

    protected void putUpdateStoredBlock(StoredBlock storedBlock, boolean wasUndoable, BlockEvaluation blockEval)
            throws SQLException {

        putBinary(new StoredBlockBinary(storedBlock.getHeader().bitcoinSerialize(), storedBlock.getHeight()),
                blockEval);
    }

    protected void putBinary(StoredBlockBinary r, BlockEvaluation blockEvaluation) throws SQLException {
        try {
            Block block = params.getDefaultSerializer().makeBlock(r.getBlockBytes());

            PreparedStatement s = conn.get().prepareStatement(getInsertHeadersSQL());
            s.setBytes(1, block.getHash().getBytes());
            s.setLong(2, r.getHeight());
            s.setBytes(3, block.unsafeBitcoinSerialize());
            s.setBoolean(4, false);
            s.setBytes(5, block.getPrevBlockHash().getBytes());
            s.setBytes(6, block.getPrevBranchBlockHash().getBytes());
            s.setBytes(7, block.getMineraddress());

            s.setLong(8, block.getBlocktype());
            int j = 7;
            s.setLong(j + 2, blockEvaluation.getRating());
            s.setLong(j + 3, blockEvaluation.getDepth());
            s.setLong(j + 4, blockEvaluation.getCumulativeWeight());
            s.setBoolean(j + 5, blockEvaluation.isSolid());
            j = 6;
            s.setBoolean(j + 7, blockEvaluation.isMilestone());
            s.setLong(j + 8, blockEvaluation.getMilestoneLastUpdateTime());
            s.setLong(j + 9, blockEvaluation.getMilestoneDepth());
            s.setLong(j + 10, blockEvaluation.getInsertTime());
            s.setBoolean(j + 11, blockEvaluation.isMaintained());
            s.setBoolean(j + 12, blockEvaluation.isRewardValid());

            s.executeUpdate();
            s.close();
            log.info("add block hexStr : " + block.getHash().toString());
        } catch (SQLException e) {
            // It is possible we try to add a duplicate StoredBlock if we
            // upgraded
            // In that case, we just update the entry to mark it wasUndoable
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
                throw e;
            Block block = params.getDefaultSerializer().makeBlock(r.getBlockBytes());
            PreparedStatement s = conn.get().prepareStatement(getUpdateHeadersSQL());
            s.setBoolean(1, true);

            s.setBytes(2, block.getHash().getBytes());
            s.executeUpdate();
            s.close();
        }
    }

    @Override
    public void put(StoredBlock storedBlock) throws BlockStoreException {
        maybeConnect();
        try {
            BlockEvaluation blockEval = BlockEvaluation.buildInitial(storedBlock.getHeader());
            putUpdateStoredBlock(storedBlock, false, blockEval);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock) throws BlockStoreException {
        maybeConnect();

        try {

            BlockEvaluation blockEval = BlockEvaluation.buildInitial(storedBlock.getHeader());

            putUpdateStoredBlock(storedBlock, true, blockEval);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
        // } catch (SQLException e) {
        // if (!e.getSQLState().equals(getDuplicateKeyErrorCode()))
        // throw new BlockStoreException(e);
        //
        // // There is probably an update-or-insert statement, but it
        // // wasn't obvious from the docs
        // PreparedStatement s =
        // conn.get().prepareStatement(getUpdateUndoableBlocksSQL());
        // s.setBytes(3, storedBlock.getHeader().getHash().getBytes());
        // if (transactions == null) {
        // s.setBytes(1, txOutChanges);
        // s.setNull(2, Types.BINARY);
        // } else {
        // s.setNull(1, Types.BINARY);
        // s.setBytes(2, transactions);
        // }
        // s.executeUpdate();
        // s.close();
        // }

        // } catch (SQLException ex) {
        // throw new BlockStoreException(ex);
        // }
    }

    public StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly) throws BlockStoreException {
        StoredBlockBinary r = getBinary(hash, wasUndoableOnly);
        if (r == null)
            return null;
        Block b = params.getDefaultSerializer().makeBlock(r.getBlockBytes());
        b.verifyHeader();
        return new StoredBlock(b, r.getHeight());
    }

    @Cacheable(cacheNames = "Blocks")
    public StoredBlockBinary getBinary(Sha256Hash hash, boolean wasUndoableOnly) throws BlockStoreException {

        maybeConnect();
        PreparedStatement s = null;
        // log.info("find block hexStr : " + hash.toString());
        try {
            s = conn.get().prepareStatement(getSelectHeadersSQL());
            s.setBytes(1, hash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            if (wasUndoableOnly && !results.getBoolean(3))
                return null;

            int height = results.getInt(1);

            return new StoredBlockBinary(results.getBytes(2), height);

        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            e.printStackTrace();
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

    public void streamBlocks(long height, KafkaMessageProducer kafkaMessageProducer) throws BlockStoreException {
        // Optimize for chain head

        maybeConnect();
        PreparedStatement s = null;
        // log.info("find block hexStr : " + hash.toString());
        try {
            s = conn.get().prepareStatement(SELECT_HEADERS_HEIGHT_SQL);
            s.setLong(1, height);
            ResultSet results = s.executeQuery();
            long count = 0;
            while (results.next()) {
                kafkaMessageProducer.sendMessage(results.getBytes(1));
                count += 1;
            }
            log.info(" streamBlocks count= " + count + " from height " + height + " to kafka:"
                    + kafkaMessageProducer.producerConfig());
        } catch (Exception ex) {
            log.warn("", ex);
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

    public List<StoredBlock> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        List<StoredBlock> storedBlocks = new ArrayList<StoredBlock>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HEADERS_SQL);
            s.setBytes(1, hash.getBytes());
            s.setBytes(2, hash.getBytes());
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

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException {
        List<Sha256Hash> storedBlockHash = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_HASHES_SQL);
            s.setBytes(1, hash.getBytes());
            s.setBytes(2, hash.getBytes());
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
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        return get(hash, false);
    }

    @Override
    public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash) throws BlockStoreException {
        return get(hash, true);
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
            Coin coinvalue = Coin.valueOf(results.getLong(2), results.getString(8));
            byte[] scriptBytes = results.getBytes(3);
            boolean coinbase = results.getBoolean(4);
            String address = results.getString(5);
            Sha256Hash blockhash = Sha256Hash.wrap(results.getBytes(7));

            String fromaddress = results.getString(9);
            String memo = results.getString(10);
            boolean spent = results.getBoolean(11);
            boolean confirmed = results.getBoolean(12);
            boolean spendPending = results.getBoolean(13);
            String tokenid = results.getString("tokenid");
            UTXO txout = new UTXO(hash, index, coinvalue, height, coinbase, new Script(scriptBytes), address, blockhash,
                    fromaddress, memo, tokenid, spent, confirmed, spendPending, 0);
            return txout;
        } catch (SQLException ex) {
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
            s.setString(12, out.getMemo());
            s.setBoolean(13, out.isSpent());
            s.setBoolean(14, out.isConfirmed());
            s.setBoolean(15, out.isSpendPending());
            s.executeUpdate();
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
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
    public void removeUnspentTransactionOutput(Sha256Hash prevTxHash, long index) throws BlockStoreException {
        maybeConnect();
        if (getTransactionOutput(prevTxHash, index) == null)
            throw new BlockStoreException(
                    "Tried to remove a UTXO from DatabaseFullPrunedBlockStore that it didn't have!");
        try {
            PreparedStatement s = conn.get().prepareStatement(getDeleteOpenoutputsSQL());
            s.setBytes(1, prevTxHash.getBytes());
            // index is actually an unsigned int
            s.setInt(2, (int) index);
            s.executeUpdate();
            s.close();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Starting database batch write with connection: " + conn.get().toString());
        try {
            conn.get().setAutoCommit(false);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Committing database batch write with connection: " + conn.get().toString());
        try {
            if (!conn.get().getAutoCommit())
                conn.get().commit();
            conn.get().setAutoCommit(true);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void abortDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Rollback database batch write with connection: " + conn.get().toString());
        try {
            if (!conn.get().getAutoCommit()) {
                conn.get().rollback();
                conn.get().setAutoCommit(true);
            } else {
                log.warn("Warning: Rollback attempt without transaction");
            }
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public boolean hasUnspentOutputs(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(getSelectOpenoutputsCountSQL());
            s.setBytes(1, hash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                throw new BlockStoreException("Got no results from a COUNT(*) query");
            }
            int count = results.getInt(1);
            return count != 0;
        } catch (SQLException ex) {
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
    public NetworkParameters getParams() {
        return params;
    }

    /**
     * Resets the store by deleting the contents of the tables and
     * reinitialising them.
     * 
     * @throws BlockStoreException
     *             If the tables couldn't be cleared and initialised.
     */
    public void resetStore() throws BlockStoreException {
        maybeConnect();
        try {
            deleteStore();
            createTables();
        } catch (SQLException ex) {
            log.warn("Warning: deleteStore", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes the store by deleting the tables within the database.
     * 
     * @throws BlockStoreException
     *             If tables couldn't be deleted.
     */
    public void deleteStore() throws BlockStoreException {
        maybeConnect();
        try {
            /*
             * for (String sql : getDropIndexsSQL()) { Statement s =
             * conn.get().createStatement(); try { log.info("drop index : " +
             * sql); s.execute(sql); } finally { s.close(); } }
             */
            for (String sql : getDropTablesSQL()) {
                Statement s = conn.get().createStatement();
                try {
                    log.info("drop table : " + sql);
                    s.execute(sql);
                } finally {
                    s.close();
                }
            }
        } catch (Exception ex) {
            log.warn("Warning: deleteStore", ex);
            // throw new RuntimeException(ex);
        }
    }

    protected abstract List<String> getDropIndexsSQL();

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        PreparedStatement s = null;
        List<UTXO> outputs = new ArrayList<UTXO>();
        try {
            maybeConnect();
            s = conn.get().prepareStatement(getTransactionOutputSelectSQL());
            for (Address address : addresses) {
                s.setString(1, address.toString());
                s.setString(2, address.toString());
                ResultSet rs = s.executeQuery();
                while (rs.next()) {
                    Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
                    Coin amount = Coin.valueOf(rs.getLong(2), rs.getString(10));
                    byte[] scriptBytes = rs.getBytes(3);
                    int height = rs.getInt(4);
                    int index = rs.getInt(5);
                    boolean coinbase = rs.getBoolean(6);
                    String toAddress = rs.getString(7);
                    // addresstargetable =rs.getBytes(8);
                    Sha256Hash blockhash = Sha256Hash.wrap(rs.getBytes(9));

                    String fromaddress = rs.getString(11);
                    String memo = rs.getString(12);
                    boolean spent = rs.getBoolean(13);
                    boolean confirmed = rs.getBoolean(14);
                    boolean spendPending = rs.getBoolean(15);
                    String tokenid = rs.getString("tokenid");
                    long minimumsign = rs.getLong("minimumsign");
                    UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(scriptBytes), toAddress,
                            blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, minimumsign);
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
                s.setString(2, address.toString());
                s.setBytes(3, tokenid00);
                ResultSet rs = s.executeQuery();
                while (rs.next()) {
                    Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
                    Coin amount = Coin.valueOf(rs.getLong(2), rs.getString(10));
                    byte[] scriptBytes = rs.getBytes(3);
                    int height = rs.getInt(4);
                    int index = rs.getInt(5);
                    boolean coinbase = rs.getBoolean(6);
                    String toAddress = rs.getString(7);
                    // addresstargetable =rs.getBytes(8);
                    Sha256Hash blockhash = Sha256Hash.wrap(rs.getBytes(9));

                    String fromaddress = rs.getString(11);
                    String memo = rs.getString(12);
                    boolean spent = rs.getBoolean(13);
                    boolean confirmed = rs.getBoolean(14);
                    boolean spendPending = rs.getBoolean(15);
                    String tokenid = rs.getString("tokenid");
                    long minimumsign = rs.getLong("minimumsign");
                    UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(scriptBytes), toAddress,
                            blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, minimumsign);
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

    /**
     * Dumps information about the size of actual data in the database to
     * standard output The only truly useless data counted is printed in the
     * form "N in id indexes" This does not take database indexes into account.
     */
    public void dumpSizes() throws SQLException, BlockStoreException {
        maybeConnect();
        Statement s = conn.get().createStatement();
        long size = 0;
        long totalSize = 0;
        int count = 0;
        ResultSet rs = s.executeQuery(getSelectSettingsDumpSQL());
        while (rs.next()) {
            size += rs.getString(1).length();
            size += rs.getBytes(2).length;
            count++;
        }
        rs.close();
        System.out.printf(Locale.US, "Settings size: %d, count: %d, average size: %f%n", size, count,
                (double) size / count);

        totalSize += size;
        size = 0;
        count = 0;
        rs = s.executeQuery(getSelectHeadersDumpSQL());
        while (rs.next()) {
            size += 28; // hash
            size += rs.getBytes(1).length;
            size += 4; // height
            size += rs.getBytes(2).length;
            count++;
        }
        rs.close();
        System.out.printf(Locale.US, "Headers size: %d, count: %d, average size: %f%n", size, count,
                (double) size / count);

        rs.close();
        System.out.printf(Locale.US, "Undoable Blocks size: %d, count: %d, average size: %f%n", size, count,
                (double) size / count);

        totalSize += size;
        size = 0;
        count = 0;
        long scriptSize = 0;
        rs = s.executeQuery(getSelectopenoutputsDumpSQL());
        while (rs.next()) {
            size += 32; // hash
            size += 4; // index
            size += 4; // height
            size += rs.getBytes(1).length;
            size += rs.getBytes(2).length;
            scriptSize += rs.getBytes(2).length;
            count++;
        }
        rs.close();
        System.out.printf(Locale.US,
                "Open Outputs size: %d, count: %d, average size: %f, average script size: %f (%d in id indexes)%n",
                size, count, (double) size / count, (double) scriptSize / count, count * 8);

        totalSize += size;
        log.debug("Total Size: " + totalSize);

        s.close();
    }

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKEVALUATION_SQL);
            preparedStatement.setBytes(1, hash.getBytes());

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                    resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                    resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                    resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
            return blockEvaluation;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
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

    @Override
    public long getMaxSolidHeight() throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MAX_SOLID_HEIGHT);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return 0;
            }
            return resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
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

    @Override
    public List<Sha256Hash> getNonSolidBlocks() throws BlockStoreException {
        List<Sha256Hash> storedBlockHashes = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_NONSOLID_BLOCKS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                storedBlockHashes.add(Sha256Hash.wrap(resultSet.getBytes(1)));
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<BlockEvaluation> getSolidBlockEvaluations() throws BlockStoreException {
        List<BlockEvaluation> result = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_SOLID_BLOCKEVALUATIONS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                result.add(blockEvaluation);
            }
            return result;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException {
        List<BlockEvaluation> result = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ALL_BLOCKEVALUATIONS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                result.add(blockEvaluation);
            }
            return result;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public HashSet<BlockEvaluation> getBlocksToAddToMilestone(long minDepth) throws BlockStoreException {
        HashSet<BlockEvaluation> storedBlockHashes = new HashSet<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_TO_ADD_TO_MILESTONE_SQL);
            preparedStatement.setLong(1, minDepth);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                storedBlockHashes.add(blockEvaluation);
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<BlockEvaluation> getBlocksInMilestoneDepthInterval(long minDepth, long maxDepth)
            throws BlockStoreException {
        List<BlockEvaluation> storedBlockHashes = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONEDEPTH_INTERVAL_SQL);
            preparedStatement.setLong(1, minDepth);
            preparedStatement.setLong(2, maxDepth);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                storedBlockHashes.add(blockEvaluation);
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException {
        HashSet<BlockEvaluation> storedBlockHashes = new HashSet<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_TO_REMOVE_FROM_MILESTONE_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                storedBlockHashes.add(blockEvaluation);
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<BlockEvaluation> getSolidTips() throws BlockStoreException {
        List<BlockEvaluation> storedBlockHashes = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_SOLID_TIPS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                storedBlockHashes.add(blockEvaluation);
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) throws BlockStoreException {
        List<BlockEvaluation> storedBlockHashes = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_SOLID_BLOCKS_OF_HEIGHT_SQL);
            preparedStatement.setLong(1, currentHeight);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                storedBlockHashes.add(blockEvaluation);
            }
            return storedBlockHashes;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    protected abstract String getUpdateBlockEvaluationCumulativeweightSQL();

    @Override
    public void updateBlockEvaluationCumulativeweight(Sha256Hash blockhash, long cumulativeweight)
            throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationCumulativeweightSQL());
            preparedStatement.setLong(1, cumulativeweight);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationDepthSQL();

    @Override
    public void updateBlockEvaluationDepth(Sha256Hash blockhash, long depth) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationDepthSQL());
            preparedStatement.setLong(1, depth);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationMilestoneSQL();

    @Override
    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationMilestoneSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setLong(2, System.currentTimeMillis());
            preparedStatement.setBytes(3, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationRatingSQL();

    @Override
    public void updateBlockEvaluationRating(Sha256Hash blockhash, long i) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationRatingSQL());
            preparedStatement.setLong(1, i);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationSolidSQL();

    @Override
    public void updateBlockEvaluationSolid(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationSolidSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationMilestoneDepthSQL();

    @Override
    public void updateBlockEvaluationMilestoneDepth(Sha256Hash blockhash, long i) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationMilestoneDepthSQL());
            preparedStatement.setLong(1, i);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationMaintainedSQL();

    @Override
    public void updateBlockEvaluationMaintained(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationMaintainedSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateBlockEvaluationRewardValidItyassessmentSQL();

    @Override
    public void updateBlockEvaluationRewardValid(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationRewardValidItyassessmentSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    @Override
    public void deleteTip(Sha256Hash blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_TIP_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    @Override
    public void insertTip(Sha256Hash blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TIP_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            preparedStatement.executeUpdate();
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

    @Override
    public BlockEvaluation getTransactionOutputSpender(Sha256Hash prevTxHash, long index) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_OUTPUT_SPENDER_SQL);
            preparedStatement.setBytes(1, prevTxHash.getBytes());
            preparedStatement.setLong(2, index);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                    resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                    resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                    resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
            return blockEvaluation;
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

    protected abstract String getUpdateOutputsSpentSQL();

    @Override
    public void updateTransactionOutputSpent(Sha256Hash prevTxHash, long index, boolean b, Sha256Hash spenderBlock)
            throws BlockStoreException {
        UTXO prev = this.getTransactionOutput(prevTxHash, index);
        if (prev == null) {
            throw new BlockStoreException("Could not find UTXO to update");
        }
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsSpentSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlock.getBytes());
            preparedStatement.setBytes(3, prevTxHash.getBytes());
            preparedStatement.setLong(4, index);
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateOutputsConfirmedSQL();

    @Override
    public void updateTransactionOutputConfirmed(Sha256Hash prevTxHash, long index, boolean b)
            throws BlockStoreException {
        UTXO prev = this.getTransactionOutput(prevTxHash, index);
        if (prev == null) {
            throw new BlockStoreException("Could not find UTXO to update");
        }
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsConfirmedSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, prevTxHash.getBytes());
            preparedStatement.setLong(3, index);
            preparedStatement.executeUpdate();
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

    protected abstract String getUpdateOutputsSpendPendingSQL();

    @Override
    public void updateTransactionOutputSpendPending(Sha256Hash prevTxHash, long index, boolean b)
            throws BlockStoreException {
        UTXO prev = this.getTransactionOutput(prevTxHash, index);
        if (prev == null) {
            throw new BlockStoreException("Could not find UTXO to update");
        }
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsSpendPendingSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, prevTxHash.getBytes());
            preparedStatement.setLong(3, index);
            preparedStatement.executeUpdate();
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

    @Override
    public int getMaxTokenId() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getSelectMaxTokenIdSQL());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return 1;
            }
            int tokenid = (int) resultSet.getLong(1);
            return tokenid == 0 ? 1 : tokenid;
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

    @Override
    public List<Tokens> getTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_TOKENS_SQL + " WHERE asmarket=false ";
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Tokens tokens = new Tokens();
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setAsmarket(resultSet.getBoolean("asmarket"));
                tokens.setSignnumber(resultSet.getLong("signnumber"));
                tokens.setMultiserial(resultSet.getBoolean("multiserial"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                tokens.setUrl(resultSet.getString("url"));
                list.add(tokens);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<Tokens> getMarketTokenList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MARKET_TOKENS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Tokens tokens = new Tokens();
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setAsmarket(resultSet.getBoolean("asmarket"));
                tokens.setSignnumber(resultSet.getLong("signnumber"));
                tokens.setMultiserial(resultSet.getBoolean("multiserial"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                tokens.setUrl(resultSet.getString("url"));
                list.add(tokens);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    public Map<String, Long> getTokenAmountMap(String name) throws BlockStoreException {
        Map<String, Long> map = new HashMap<String, Long>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(SELECT_TOKENS_ACOUNT_MAP_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                map.put(resultSet.getString("tokenid"), resultSet.getLong("amount"));

            }
            return map;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<Tokens> getTokensList(String name) throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_TOKENS_SQL + " WHERE 1=1";
            if (name != null && !"".equals(name.trim())) {
                sql += " AND (tokenname LIKE '%" + name + "%' OR description LIKE '%" + name + "%')";
            }
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Tokens tokens = new Tokens();
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setAsmarket(resultSet.getBoolean("asmarket"));
                tokens.setSignnumber(resultSet.getLong("signnumber"));
                tokens.setMultiserial(resultSet.getBoolean("multiserial"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                tokens.setUrl(resultSet.getString("url"));
                list.add(tokens);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void saveTokens(Tokens tokens) throws BlockStoreException {
        this.saveTokens(tokens.getTokenid(), tokens.getTokenname(), tokens.getDescription(), tokens.getUrl(),
                tokens.getSignnumber(), tokens.isMultiserial(), tokens.isAsmarket(), tokens.isTokenstop());
    }

    @Override
    public void saveTokens(String tokenid, String tokenname, String description, String url, long signnumber,
            boolean multiserial, boolean asmarket, boolean tokenstop) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TOKENS_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, tokenname);
            preparedStatement.setString(3, description);
            preparedStatement.setString(4, url);
            preparedStatement.setLong(5, signnumber);
            preparedStatement.setBoolean(6, multiserial);
            preparedStatement.setBoolean(7, asmarket);
            preparedStatement.setBoolean(8, tokenstop);
            preparedStatement.executeUpdate();
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

    @Override
    public void updateTokens(Tokens tokens) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_TOKENS_SQL);
            preparedStatement.setString(1, tokens.getTokenname());
            preparedStatement.setString(2, tokens.getDescription());
            preparedStatement.setString(3, tokens.getUrl());
            preparedStatement.setLong(4, tokens.getSignnumber());
            preparedStatement.setBoolean(5, tokens.isMultiserial());
            preparedStatement.setBoolean(6, tokens.isAsmarket());
            preparedStatement.setBoolean(7, tokens.isTokenstop());
            preparedStatement.setString(8, tokens.getTokenid());
            preparedStatement.executeUpdate();
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

    @Override
    public void saveExchange(Exchange exchange) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_EXCHANGE_SQL);
            preparedStatement.setString(1, exchange.getOrderid());
            preparedStatement.setString(2, exchange.getFromAddress());
            preparedStatement.setString(3, exchange.getFromTokenHex());
            preparedStatement.setString(4, exchange.getFromAmount());
            preparedStatement.setString(5, exchange.getToAddress());
            preparedStatement.setString(6, exchange.getToTokenHex());
            preparedStatement.setString(7, exchange.getToAmount());
            preparedStatement.setBytes(8, exchange.getData());
            preparedStatement.setInt(9, exchange.getToSign());
            preparedStatement.setInt(10, exchange.getFromSign());
            preparedStatement.setString(11, exchange.getToOrderId());
            preparedStatement.setString(12, exchange.getFromOrderId());
            preparedStatement.setString(13, exchange.getMarket());
            preparedStatement.executeUpdate();
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

    @Override
    public List<Exchange> getExchangeListWithAddress(String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Exchange> list = new ArrayList<Exchange>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_EXCHANGE_SQL);
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Exchange exchange = new Exchange();
                exchange.setOrderid(resultSet.getString("orderid"));
                exchange.setFromAddress(resultSet.getString("fromAddress"));
                exchange.setFromTokenHex(resultSet.getString("fromTokenHex"));
                exchange.setFromAmount(resultSet.getString("fromAmount"));
                exchange.setToAddress(resultSet.getString("toAddress"));
                exchange.setToTokenHex(resultSet.getString("toTokenHex"));
                exchange.setToAmount(resultSet.getString("toAmount"));
                exchange.setData(resultSet.getBytes("data"));
                exchange.setToSign(resultSet.getInt("toSign"));
                exchange.setFromSign(resultSet.getInt("fromSign"));
                exchange.setToOrderId(resultSet.getString("toOrderId"));
                exchange.setFromOrderId(resultSet.getString("fromOrderId"));
                exchange.setMarket(resultSet.getString("market"));
                list.add(exchange);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "";
            if (signtype.equals("to")) {
                sql = "UPDATE exchange SET toSign = 1, data = ? WHERE orderid = ?";
            } else {
                sql = "UPDATE exchange SET fromSign = 1, data = ? WHERE orderid = ?";
            }
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(2, orderid);
            preparedStatement.setBytes(1, data);
            preparedStatement.executeUpdate();
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

    @Override
    public void updateUnmaintainAll() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockevaluationUnmaintainAllSQL());
            preparedStatement.executeUpdate();
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

    @Override
    public Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_EXCHANGE_ORDERID_SQL);
            preparedStatement.setString(1, orderid);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            Exchange exchange = new Exchange();
            exchange.setOrderid(resultSet.getString("orderid"));
            exchange.setFromAddress(resultSet.getString("fromAddress"));
            exchange.setFromTokenHex(resultSet.getString("fromTokenHex"));
            exchange.setFromAmount(resultSet.getString("fromAmount"));
            exchange.setToAddress(resultSet.getString("toAddress"));
            exchange.setToTokenHex(resultSet.getString("toTokenHex"));
            exchange.setToAmount(resultSet.getString("toAmount"));
            exchange.setData(resultSet.getBytes("data"));
            exchange.setToSign(resultSet.getInt("toSign"));
            exchange.setFromSign(resultSet.getInt("fromSign"));
            exchange.setToOrderId(resultSet.getString("toOrderId"));
            exchange.setFromOrderId(resultSet.getString("fromOrderId"));
            exchange.setMarket(resultSet.getString("market"));
            return exchange;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address) throws BlockStoreException {
        if (address.isEmpty()) {
            return new ArrayList<BlockEvaluation>();
        }
        String sql = "SELECT headers.* FROM outputs LEFT JOIN headers ON outputs.blockhash = headers.blockhash WHERE outputs.toaddress in ";
        StringBuffer stringBuffer = new StringBuffer();
        for (String str : address)
            stringBuffer.append(",").append("'" + str + "'");
        sql += "(" + stringBuffer.substring(1).toString() + ")";
        sql += " ORDER BY insertTime desc ";
        List<BlockEvaluation> result = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                result.add(blockEvaluation);
            }
            return result;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }

    }

    @Override
    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address, String lastestAmount)
            throws BlockStoreException {

        String sql = "";
        StringBuffer stringBuffer = new StringBuffer();
        if (!"0".equalsIgnoreCase(lastestAmount) && !"".equalsIgnoreCase(lastestAmount)) {
            sql +=   "SELECT hash, rating, depth, cumulativeweight, "
                    + "solid, height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained,"
                    + " rewardvalidityassessment" + "  FROM  headers ";
            sql += " ORDER BY insertTime desc ";
            Integer a = Integer.valueOf(lastestAmount);
            if (a > 1000) {
                a = 2000;
            }
            sql += " LIMIT " + a;
        } else {
            sql += "SELECT headers.hash, rating, depth, cumulativeweight, "
                            + "solid, headers.height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained,"
                            + " rewardvalidityassessment " 
                    + " FROM outputs LEFT JOIN headers " + "ON outputs.blockhash = headers.hash  ";
            sql += "WHERE outputs.toaddress in ";
            for (String str : address)
                stringBuffer.append(",").append("'" + str + "'");
            sql += "(" + stringBuffer.substring(1).toString() + ")";
        }
        List<BlockEvaluation> result = new ArrayList<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5),
                        resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getLong(10), resultSet.getBoolean(11), resultSet.getBoolean(12));
                result.add(blockEvaluation);
            }
            return result;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<MultiSignAddress> getMultiSignAddressListByTokenid(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<MultiSignAddress> list = new ArrayList<MultiSignAddress>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String tokenid0 = resultSet.getString("tokenid");
                String address = resultSet.getString("address");
                String pubKeyHex = resultSet.getString("pubKeyHex");
                MultiSignAddress multiSignAddress = new MultiSignAddress(tokenid0, address, pubKeyHex);
                list.add(multiSignAddress);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void insertMultiSignAddress(MultiSignAddress multiSignAddress) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, multiSignAddress.getTokenid());
            preparedStatement.setString(2, multiSignAddress.getAddress());
            preparedStatement.setString(3, multiSignAddress.getPubKeyHex());
            preparedStatement.executeUpdate();
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

    @Override
    public void deleteMultiSignAddress(String tokenid, String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, address);
            preparedStatement.executeUpdate();
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

    @Override
    public void insertTokenSerial(TokenSerial tokenSerial) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TOKENSERIAL_SQL);
            preparedStatement.setString(1, tokenSerial.getTokenid());
            preparedStatement.setLong(2, tokenSerial.getTokenindex());
            preparedStatement.setLong(3, tokenSerial.getAmount());
            preparedStatement.executeUpdate();
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

    @Override
    public void insertMultisignby(MultiSignBy multisignby) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MULTISIGNBY_SQL);
            preparedStatement.setString(1, multisignby.getTokenid());
            preparedStatement.setLong(2, multisignby.getTokenindex());
            preparedStatement.setString(3, multisignby.getAddress());
            preparedStatement.executeUpdate();
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

    @Override
    public int getCountMultiSignAddress(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(COUNT_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
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

    @Override
    public Tokens getTokensInfo(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKENS_INFO_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            Tokens tokens = null;
            if (resultSet.next()) {
                tokens = new Tokens();
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setAsmarket(resultSet.getBoolean("asmarket"));
                tokens.setSignnumber(resultSet.getLong("signnumber"));
                tokens.setMultiserial(resultSet.getBoolean("multiserial"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                tokens.setUrl(resultSet.getString("url"));
            }
            return tokens;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    public List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String> addresses)
            throws BlockStoreException {
        List<TokenSerial> list = new ArrayList<TokenSerial>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        String sql = SELECT_SEARCH_TOKENSERIAL_ALL_SQL;
        if (addresses != null && !addresses.isEmpty()) {
            String addressString = " AND address IN(";
            for (String address : addresses) {
                addressString += "'" + address + "',";
            }
            addressString = addressString.substring(0, addressString.length() - 1) + ") ";
            sql += addressString;
        }
        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND tokenid=?";
        }
        sql += " ORDER BY tokenid,tokenindex";

        try {
            preparedStatement = conn.get().prepareStatement(sql);
            if (tokenid != null && !tokenid.trim().isEmpty()) {
                preparedStatement.setString(1, tokenid);
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String tokenid0 = resultSet.getString("tokenid");
                long tokenindex = resultSet.getLong("tokenindex");
                long amount = resultSet.getLong("amount");
                TokenSerial tokenSerial = new TokenSerial(tokenid0, tokenindex, amount, resultSet.getLong("signnumber"),
                        resultSet.getLong("count"));
                list.add(tokenSerial);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public TokenSerial getTokenSerialInfo(String tokenid, long tokenindex) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKENSERIAL_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenindex);
            ResultSet resultSet = preparedStatement.executeQuery();
            TokenSerial tokenSerial = null;
            if (resultSet.next()) {
                // String tokenid = resultSet.getString("tokenid");
                // long tokenindex = resultSet.getLong("tokenindex");
                long amount = resultSet.getLong("amount");
                tokenSerial = new TokenSerial(tokenid, tokenindex, amount);
            }
            return tokenSerial;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public MultiSignAddress getMultiSignAddressInfo(String tokenid, String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNADDRESSINFO_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            MultiSignAddress multiSignAddress = null;
            if (resultSet.next()) {
                String tokenid0 = resultSet.getString("tokenid");
                String address0 = resultSet.getString("address");
                String pubKeyHex = resultSet.getString("pubKeyHex");
                multiSignAddress = new MultiSignAddress(tokenid0, address0, pubKeyHex);
            }
            return multiSignAddress;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public int getCountMultiSignByTokenIndexAndAddress(String tokenid, long tokenindex, String address)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNBY_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenindex);
            preparedStatement.setString(3, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public int getCountMultiSignByAlready(String tokenid, long tokenindex) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNBY0_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenindex);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void updateTokenSerial(TokenSerial tokenSerial0) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_TOKENSERIAL_SQL);
            preparedStatement.setLong(1, tokenSerial0.getAmount());
            preparedStatement.setString(2, tokenSerial0.getTokenid());
            preparedStatement.setLong(3, tokenSerial0.getTokenindex());
            preparedStatement.executeUpdate();
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

    /*
     * @Override public MultiSignBy getMultiSignByInfo(String tokenid, long
     * tokenindex, String address) throws BlockStoreException { maybeConnect();
     * PreparedStatement preparedStatement = null; try { preparedStatement =
     * conn.get().prepareStatement(SELECT_MULTISIGNBY000_SQL);
     * preparedStatement.setString(1, tokenid); preparedStatement.setLong(2,
     * tokenindex); preparedStatement.setString(3, address); ResultSet resultSet
     * = preparedStatement.executeQuery(); MultiSignBy multiSignBy = null; if
     * (resultSet.next()) { String tokenid0 = resultSet.getString("tokenid");
     * String address0 = resultSet.getString("address"); Long tokenindex0 =
     * resultSet.getLong("tokenindex"); multiSignBy = new MultiSignBy(tokenid0,
     * tokenindex0, address0); } return multiSignBy; } catch (SQLException ex) {
     * throw new BlockStoreException(ex); } finally { if (preparedStatement !=
     * null) { try { preparedStatement.close(); } catch (SQLException e) { throw
     * new BlockStoreException("Failed to close PreparedStatement"); } } } }
     */

    @Override
    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGN_ADDRESS_SQL);
            preparedStatement.setString(1, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String tokenid = resultSet.getString("tokenid");
                Long tokenindex = resultSet.getLong("tokenindex");
                String address0 = resultSet.getString("address");
                byte[] blockhash = resultSet.getBytes("blockhash");
                int sign = resultSet.getInt("sign");

                MultiSign multiSign = new MultiSign();
                multiSign.setId(id);
                multiSign.setTokenindex(tokenindex);
                multiSign.setTokenid(tokenid);
                multiSign.setAddress(address0);
                multiSign.setBlockhash(blockhash);
                multiSign.setSign(sign);

                list.add(multiSign);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, List<String> addresses, boolean isSign)
            throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        String sql = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE 1 = 1 ";
        if (addresses != null && !addresses.isEmpty()) {
            String addressString = " AND address IN(";
            for (String address : addresses) {
                addressString += "'" + address + "',";
            }
            addressString = addressString.substring(0, addressString.length() - 1) + ") ";
            sql += addressString;
        }
        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND tokenid=?";
        }
        if (!isSign) {
            sql += " AND sign = 0";
        }
        sql += " ORDER BY tokenid,tokenindex DESC";
        try {
            log.info("sql : " + sql);
            preparedStatement = conn.get().prepareStatement(sql);
            if (tokenid != null && !tokenid.isEmpty()) {
                preparedStatement.setString(1, tokenid.trim());
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String tokenid0 = resultSet.getString("tokenid");
                Long tokenindex = resultSet.getLong("tokenindex");
                String address0 = resultSet.getString("address");
                byte[] blockhash = resultSet.getBytes("blockhash");
                int sign = resultSet.getInt("sign");

                MultiSign multiSign = new MultiSign();
                multiSign.setId(id);
                multiSign.setTokenindex(tokenindex);
                multiSign.setTokenid(tokenid0);
                multiSign.setAddress(address0);
                multiSign.setBlockhash(blockhash);
                multiSign.setSign(sign);
                list.add(multiSign);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public long getCountMilestoneBlocksInInterval(long fromHeight, long toHeight) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_COUNT_MILESTONE_SQL);
            preparedStatement.setLong(1, fromHeight);
            preparedStatement.setLong(2, toHeight);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public int getCountMultiSignAlready(String tokenid, long tokenindex, String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_COUNT_MULTISIGN_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenindex);
            preparedStatement.setString(3, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    public int getCountMultiSignNoSign(String tokenid, long tokenindex, int sign) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_COUNT_MULTISIGN_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenindex);
            preparedStatement.setInt(3, sign);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void saveMultiSign(MultiSign multiSign) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MULTISIGN_SQL);
            preparedStatement.setString(1, multiSign.getTokenid());
            preparedStatement.setLong(2, multiSign.getTokenindex());
            preparedStatement.setString(3, multiSign.getAddress());
            preparedStatement.setBytes(4, multiSign.getBlockhash());
            preparedStatement.setInt(5, 0);
            preparedStatement.setString(6, multiSign.getId());
            preparedStatement.executeUpdate();
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

    @Override
    public void updateMultiSign(String tokenid, int tokenindex, String address, byte[] blockhash, int sign)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_MULTISIGN_SQL);
            preparedStatement.setBytes(1, blockhash);
            preparedStatement.setInt(2, sign);
            preparedStatement.setString(3, tokenid);
            preparedStatement.setLong(4, tokenindex);
            preparedStatement.setString(5, address);
            preparedStatement.executeUpdate();
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

    @Override
    public void updateMultiSignBlockHash(String tokenid, long tokenindex, String address, byte[] blockhash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_MULTISIGN0_SQL);
            preparedStatement.setBytes(1, blockhash);
            preparedStatement.setString(2, tokenid);
            preparedStatement.setLong(3, tokenindex);
            preparedStatement.setString(4, address);
            preparedStatement.executeUpdate();
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

    @Override
    public void deleteMultiSignAddressByTokenid(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MULTISIGNADDRESS_SQL0);
            preparedStatement.setString(1, tokenid);
            preparedStatement.executeUpdate();
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

    @Override
    public int getCountTokenSerialNumber(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(COUNT_TOKENSERIAL0_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
            return 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        String sql = SELECT_MULTISIGN_ADDRESS_ALL_SQL + " AND tokenid=? AND tokenindex = ?";
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, tokenid.trim());
            preparedStatement.setLong(2, tokenindex);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String tokenid0 = resultSet.getString("tokenid");
                Long tokenindex0 = resultSet.getLong("tokenindex");
                String address0 = resultSet.getString("address");
                byte[] blockhash = resultSet.getBytes("blockhash");
                int sign = resultSet.getInt("sign");
                MultiSign multiSign = new MultiSign();
                multiSign.setId(id);
                multiSign.setTokenindex(tokenindex0);
                multiSign.setTokenid(tokenid0);
                multiSign.setAddress(address0);
                multiSign.setBlockhash(blockhash);
                multiSign.setSign(sign);
                list.add(multiSign);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void deleteMultiSign(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MULTISIGN_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.executeUpdate();
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

    @Override
    public long getTxRewardValue(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public long getMaxPrevTxRewardHeight() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MAX_TX_REWARD_HEIGHT_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public boolean getTxRewardConfirmed(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_CONFIRMED_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getBoolean(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void insertTxReward(Sha256Hash hash, long nextPerTxReward, long prevHeight) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TX_REWARD_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            preparedStatement.setLong(2, nextPerTxReward);
            preparedStatement.setLong(3, prevHeight);
            preparedStatement.setBoolean(4, false);
            preparedStatement.executeUpdate();
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

    @Override
    public void updateTxRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_TX_REWARD_CONFIRMED_SQL);
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, hash.getBytes());
            preparedStatement.executeUpdate();
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

    @Override
    public void updateMultiSignBlockBitcoinSerialize(String tokenid, long tokenindex, byte[] bytes)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_MULTISIGN1_SQL);
            preparedStatement.setBytes(1, bytes);
            preparedStatement.setString(2, tokenid);
            preparedStatement.setLong(3, tokenindex);
            preparedStatement.executeUpdate();
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

    @Override
    public long getMaxImportTime() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MAX_IMPORT_TIME_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void insertOutputsMulti(OutputsMulti outputsMulti) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_OUTPUTSMULTI_SQL);
            preparedStatement.setBytes(1, outputsMulti.getHash().getBytes());
            preparedStatement.setString(2, outputsMulti.getToaddress());
            preparedStatement.setLong(3, outputsMulti.getOutputindex());
            preparedStatement.setLong(4, outputsMulti.getMinimumsign());
            preparedStatement.executeUpdate();
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

    @Override
    public UserData getUserDataByPrimaryKey(String dataclassname, String pubKey) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_USERDATA_SQL);
            preparedStatement.setString(1, dataclassname);
            preparedStatement.setString(2, pubKey);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            UserData userData = new UserData();
            Sha256Hash blockhash = Sha256Hash.wrap(resultSet.getBytes("blockhash"));
            userData.setBlockhash(blockhash);
            userData.setData(resultSet.getBytes("data"));
            userData.setDataclassname(resultSet.getString("dataclassname"));
            userData.setPubKey(resultSet.getString("pubKey"));
            return userData;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void insertUserData(UserData userData) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_USERDATA_SQL);
            preparedStatement.setBytes(1, userData.getBlockhash().getBytes());
            preparedStatement.setString(2, userData.getDataclassname());
            preparedStatement.setBytes(3, userData.getData());
            preparedStatement.setString(4, userData.getPubKey());
            preparedStatement.executeUpdate();
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

    @Override
    public void updateUserData(UserData userData) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_USERDATA_SQL);
            preparedStatement.setBytes(1, userData.getBlockhash().getBytes());
            preparedStatement.setBytes(2, userData.getData());
            preparedStatement.setString(3, userData.getDataclassname());
            preparedStatement.setString(4, userData.getPubKey());
            preparedStatement.executeUpdate();
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

    @Override
    public void insertPayPayMultiSign(PayMultiSign payMultiSign) throws BlockStoreException {
        String sql = "insert into paymultisign (orderid, tokenid, toaddress, outputhash, blockhash, amount, minsignnumber) values (?, ?, ?, ?, ?, ?, ?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, payMultiSign.getOrderid());
            preparedStatement.setString(2, payMultiSign.getTokenid());
            preparedStatement.setString(3, payMultiSign.getToaddress());
            preparedStatement.setString(4, payMultiSign.getOutputhash());
            preparedStatement.setBytes(5, payMultiSign.getBlockhash());
            preparedStatement.setLong(6, payMultiSign.getAmount());
            preparedStatement.setLong(7, payMultiSign.getMinsignnumber());
            preparedStatement.executeUpdate();
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
    
    @Override
    public void insertPayMultiSignAddress(PayMultiSignAddress payMultiSignAddress) throws BlockStoreException {
        String sql = "insert into paymultisignaddress (orderid, pubKey, signature, sign) values (?, ?, ?, ?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, payMultiSignAddress.getOrderid());
            preparedStatement.setString(2, payMultiSignAddress.getPubKey());
            preparedStatement.setString(3, payMultiSignAddress.getSignature());
            preparedStatement.setInt(4, payMultiSignAddress.getSign());
            preparedStatement.executeUpdate();
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

    @Override
    public void updatePayMultiSignAddressSign(String orderid, String pubKey, int sign) throws BlockStoreException {
        String sql = "update paymultisignaddress set sign = ? where orderid = ? and pubKey = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setInt(1, sign);
            preparedStatement.setString(2, orderid);
            preparedStatement.setString(3, pubKey);
            preparedStatement.executeUpdate();
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

    @Override
    public PayMultiSign getPayMultiSignWithOrderid(String orderid) throws BlockStoreException {
        String sql = "select orderid, tokenid, toaddress, outputhash, blockhash, amount, minsignnumber from paymultisign where orderid = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, orderid.trim());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            PayMultiSign payMultiSign = new PayMultiSign();
            payMultiSign.setAmount(resultSet.getLong("amount"));
            payMultiSign.setBlockhash(resultSet.getBytes("blockhash"));
            payMultiSign.setMinsignnumber(resultSet.getLong("minsignnumber"));
            payMultiSign.setOrderid(resultSet.getString("orderid"));
            payMultiSign.setOutputhash(resultSet.getString("outputhash"));
            payMultiSign.setToaddress(resultSet.getString("toaddress"));
            payMultiSign.setTokenid(resultSet.getString("tokenid"));
            return payMultiSign;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public List<PayMultiSignAddress> getPayMultiSignAddressWithOrderid(String orderid) throws BlockStoreException {
        String sql = "select orderid, pubKey, signature, sign from paymultisignaddress where orderid = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, orderid.trim());
            ResultSet resultSet = preparedStatement.executeQuery();
            List<PayMultiSignAddress> list = new ArrayList<PayMultiSignAddress>();
            while (resultSet.next()) {
                PayMultiSignAddress payMultiSignAddress = new PayMultiSignAddress();
                payMultiSignAddress.setOrderid(resultSet.getString("orderid"));
                payMultiSignAddress.setPubKey(resultSet.getString("pubKey"));
                payMultiSignAddress.setSign(resultSet.getInt("sign"));
                payMultiSignAddress.setSignature(resultSet.getString("signature"));
                list.add(payMultiSignAddress);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void updatePayMultiSignBlockhash(String orderid, byte[] blockhash) throws BlockStoreException {
        String sql = "update paymultisign set blockhash = ? where orderid = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setBytes(1, blockhash);
            preparedStatement.setString(2, orderid);
            preparedStatement.executeUpdate();
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
