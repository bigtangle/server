/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutputChanges;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 *
 * <p>
 * The following are the tables and field names/types that are assumed:-
 * </p>
 *
 * <p>
 * <b>setting</b> table
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Type (generic)</th>
 * </tr>
 * <tr>
 * <td>name</td>
 * <td>string</td>
 * </tr>
 * <tr>
 * <td>value</td>
 * <td>binary</td>
 * </tr>
 * </table>
 * </p>
 *
 * <p>
 * <br/>
 * <b>headers</b> table
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Type (generic)</th>
 * </tr>
 * <tr>
 * <td>hash</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>chainwork</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>height</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>header</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>wasundoable</td>
 * <td>boolean</td>
 * </tr>
 * </table>
 * </p>
 *
 * <p>
 * <br/>
 * <b>undoableblocks</b> table
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Type (generic)</th>
 * </tr>
 * <tr>
 * <td>hash</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>height</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>txoutchanges</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>transactions</td>
 * <td>binary</td>
 * </tr>
 * </table>
 * </p>
 *
 * <p>
 * <br/>
 * <b>outputs</b> table
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Type (generic)</th>
 * </tr>
 * <tr>
 * <td>hash</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>index</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>height</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>value</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>scriptbytes</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>toaddress</td>
 * <td>string</td>
 * </tr>
 * <tr>
 * <td>addresstargetable</td>
 * <td>integer</td>
 * </tr>
 * <tr>
 * <td>coinbase</td>
 * <td>boolean</td>
 * </tr>
 * <tr>
 * <td>blockhash</td>
 * <td>binary</td>
 * </tr>
 * <tr>
 * <td>tokenid</td>
 * <td>string</td>
 * </tr>
 * <tr>
 * <td>fromaddress</td>
 * <td>string</td>
 * </tr>
 * <tr>
 * <td>description</td>
 * <td>string</td>
 * </tr>
 * </table>
 * </p>
 *
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {
	private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

	private static final String CHAIN_HEAD_SETTING = "chainhead";
	private static final String VERIFIED_CHAIN_HEAD_SETTING = "verifiedchainhead";
	private static final String VERSION_SETTING = "version";

	// Drop table SQL.
	private static final String DROP_SETTINGS_TABLE = "DROP TABLE settings";
	private static final String DROP_HEADERS_TABLE = "DROP TABLE headers";
	private static final String DROP_UNDOABLE_TABLE = "DROP TABLE undoableblocks";
	private static final String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE outputs";
	private static final String DROP_TIPS_TABLE = "DROP TABLE tips";
	private static final String DROP_BLOCKEVALUATION_TABLE = "DROP TABLE blockevaluation";

	// Queries SQL.
	private static final String SELECT_SETTINGS_SQL = "SELECT value FROM settings WHERE name = ?";
	private static final String INSERT_SETTINGS_SQL = "INSERT INTO settings(name, value) VALUES(?, ?)";
	private static final String UPDATE_SETTINGS_SQL = "UPDATE settings SET value = ? WHERE name = ?";

	private static final String SELECT_HEADERS_SQL = "SELECT  height, header, wasundoable,prevblockhash,prevbranchblockhash,mineraddress,tokenid,blocktype FROM headers WHERE hash = ?";
	private static final String SELECT_SOLID_APPROVER_HEADERS_SQL = "SELECT  headers.height, header, wasundoable,prevblockhash,prevbranchblockhash,mineraddress,tokenid,blocktype FROM headers INNER JOIN blockevaluation ON headers.hash=blockevaluation.blockhash WHERE blockevaluation.solid = 1 AND (prevblockhash = ? OR prevbranchblockhash = ?)";
	private static final String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM headers, blockevaluation WHERE headers.hash=blockevaluation.blockhash and blockevaluation.solid = 1 AND (prevblockhash = ? OR prevbranchblockhash = ?)";

	private static final String INSERT_HEADERS_SQL = "INSERT INTO headers(hash,  height, header, wasundoable,prevblockhash,prevbranchblockhash,mineraddress,tokenid,blocktype ) VALUES(?, ?, ?, ?, ?,?, ?, ?, ?)";
	private static final String UPDATE_HEADERS_SQL = "UPDATE headers SET wasundoable=? WHERE hash=?";

	private static final String SELECT_UNDOABLEBLOCKS_SQL = "SELECT txoutchanges, transactions FROM undoableblocks WHERE hash = ?";
	private static final String INSERT_UNDOABLEBLOCKS_SQL = "INSERT INTO undoableblocks(hash, height, txoutchanges, transactions) VALUES(?, ?, ?, ?)";
	private static final String UPDATE_UNDOABLEBLOCKS_SQL = "UPDATE undoableblocks SET txoutchanges=?, transactions=? WHERE hash = ?";
	private static final String DELETE_UNDOABLEBLOCKS_SQL = "DELETE FROM undoableblocks WHERE height <= ?";

	private static final String SELECT_OUTPUTS_COUNT_SQL = "SELECT COUNT(*) FROM outputs WHERE hash = ?";
	private static final String INSERT_OUTPUTS_SQL = "INSERT INTO outputs (hash, `index`, height, value, scriptbytes, toaddress, addresstargetable, coinbase, blockhash,tokenid,fromaddress, description, spent) VALUES (?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?,?)";
	private static final String SELECT_OUTPUTS_SQL = "SELECT height, value, scriptbytes, coinbase, toaddress, addresstargetable,blockhash,tokenid,fromaddress, description,spent FROM outputs WHERE hash = ? AND `index` = ?";
	private static final String DELETE_OUTPUTS_SQL = "DELETE FROM outputs WHERE hash = ? AND `index`= ?";
	private static final String UPDATE_OUTPUTS_SPENT_SQL = "UPDATE outputs SET spent = ? WHERE hash = ? AND `index`= ?";

	private static final String SELECT_TRANSACTION_OUTPUTS_SQL = "SELECT hash, value, scriptbytes, height, `index`, coinbase, toaddress, addresstargetable, blockhash,tokenid,fromaddress, description,spent FROM outputs where toaddress = ?";

	// Dump table SQL (this is just for data sizing statistics).
	private static final String SELECT_DUMP_SETTINGS_SQL = "SELECT name, value FROM settings";
	private static final String SELECT_DUMP_HEADERS_SQL = "SELECT chainwork, header FROM headers";
	private static final String SELECT_DUMP_UNDOABLEBLOCKS_SQL = "SELECT txoutchanges, transactions FROM undoableblocks";
	private static final String SELECT_DUMP_OUTPUTS_SQL = "SELECT value, scriptbytes FROM outputs";

	// Select the balance of an address SQL.
	private static final String SELECT_BALANCE_SQL = "SELECT sum(value) FROM outputs WHERE toaddress = ?";

	// Select the maximum height of all solid blocks
	private static final String SELECT_MAX_SOLID_HEIGHT = "SELECT max(height) FROM blockevaluation WHERE solid = 1";

	// Tables exist SQL.
	private static final String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

	// Compatibility SQL.
	// private static final String SELECT_COMPATIBILITY_COINBASE_SQL = "SELECT
	// coinbase FROM outputs WHERE 1 = 2";
	
	private static final String DELETE_TIP_SQL = "DELETE FROM tips WHERE hash = ?";
	private static final String INSERT_TIP_SQL = "INSERT INTO tips (hash) VALUES (?);";

	private static final String SELECT_BLOCKEVALUATION_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE blockhash = ?";
	private static final String DELETE_BLOCKEVALUATION_SQL = "DELETE FROM blockevaluation WHERE blockhash = ?";
	private static final String INSERT_BLOCKEVALUATION_SQL = "INSERT INTO blockevaluation (blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

	private static final String UPDATE_BLOCKEVALUATION_DEPTH_SQL = "UPDATE blockevaluation SET depth = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL = "UPDATE blockevaluation SET cumulativeweight = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_HEIGHT_SQL = "UPDATE blockevaluation SET height = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = "UPDATE blockevaluation SET milestone = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_MILESTONE_LAST_UPDATE_TIME_SQL = "UPDATE blockevaluation SET milestonelastupdate = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_RATING_SQL = "UPDATE blockevaluation SET rating = ? WHERE blockhash = ?";
	private static final String UPDATE_BLOCKEVALUATION_SOLID_SQL = "UPDATE blockevaluation SET solid = ? WHERE blockhash = ?";
	
	private static final String SELECT_SOLID_BLOCKEVALUATIONS_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE solid = 1";
	private static final String SELECT_NONSOLID_BLOCKS_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE solid = 0";
	private static final String SELECT_BLOCKS_TO_ADD_TO_MILESTONE_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE solid = 1 AND milestone = 0 AND rating >= 75 AND depth >= ?";
	private static final String SELECT_BLOCKS_TO_REMOVE_FROM_MILESTONE_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE solid = 1 AND milestone = 1 AND rating <= 50";
	private static final String SELECT_SOLID_TIPS_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation INNER JOIN tips ON tips.hash=blockevaluation.blockhash WHERE solid = 1";
	private static final String SELECT_SOLID_BLOCKS_OF_HEIGHT_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation WHERE solid = 1 && height = ?";

	private static final String SELECT_OUTPUT_SPENDER_SQL = "SELECT blockhash, rating, depth, cumulativeweight, solid, height, milestone, milestonelastupdate FROM blockevaluation INNER JOIN outputs ON outputs.blockhash=blockevaluation.blockhash WHERE solid = 1 and hash = ? AND `index`= ?";

	private static final String SELECT_MAX_TOKENID_SQL = "select max(tokenid) from headers where blocktype = ?";

	protected Sha256Hash chainHeadHash;
	protected StoredBlock chainHeadBlock;
	protected Sha256Hash verifiedChainHeadHash;
	protected StoredBlock verifiedChainHeadBlock;
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
	 * instead of a hostname and password, and optionally allowing a schema to be
	 * specified.
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
	 *             If there is a failure to connect and/or initialise the database.
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
			// initFromDatabase();
		} catch (SQLException e) {
			throw new BlockStoreException(e);
		}
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
	 * Get the SQL to select the total balance for a given address.
	 * 
	 * @return The SQL prepared statement.
	 */
	protected String getBalanceSelectSQL() {
		return SELECT_BALANCE_SQL;
	}

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

	/**
	 * Get the SQL to drop all the tables (DDL).
	 * 
	 * @return The SQL drop statements.
	 */
	protected List<String> getDropTablesSQL() {
		List<String> sqlStatements = new ArrayList<String>();
		sqlStatements.add(DROP_SETTINGS_TABLE);
		sqlStatements.add(DROP_HEADERS_TABLE);
		sqlStatements.add(DROP_UNDOABLE_TABLE);
		sqlStatements.add(DROP_OPEN_OUTPUT_TABLE);
		sqlStatements.add(DROP_TIPS_TABLE);
		sqlStatements.add(DROP_BLOCKEVALUATION_TABLE);
		return sqlStatements;
	}

	/**
	 * Get the SQL to select a setting value.
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
	 * Get the SQL to update a setting value.
	 * 
	 * @return The SQL update statement.
	 */
	protected String getUpdateSettingsSLQ() {
		return UPDATE_SETTINGS_SQL;
	}

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
	protected String getUpdateHeadersSQL() {
		return UPDATE_HEADERS_SQL;
	}

	/**
	 * Get the SQL to select an undoableblocks record.
	 * 
	 * @return The SQL select statement.
	 */
	protected String getSelectUndoableBlocksSQL() {
		return SELECT_UNDOABLEBLOCKS_SQL;
	}

	/**
	 * Get the SQL to insert a undoableblocks record.
	 * 
	 * @return The SQL insert statement.
	 */
	protected String getInsertUndoableBlocksSQL() {
		return INSERT_UNDOABLEBLOCKS_SQL;
	}

	/**
	 * Get the SQL to update a undoableblocks record.
	 * 
	 * @return The SQL update statement.
	 */
	protected String getUpdateUndoableBlocksSQL() {
		return UPDATE_UNDOABLEBLOCKS_SQL;
	}

	/**
	 * Get the SQL to delete a undoableblocks record.
	 * 
	 * @return The SQL delete statement.
	 */
	protected String getDeleteUndoableBlocksSQL() {
		return DELETE_UNDOABLEBLOCKS_SQL;
	}

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
	 * Get the SQL to select the undoableblocks dump fields for sizing/statistics.
	 * 
	 * @return The SQL select statement.
	 */
	protected String getSelectUndoableblocksDumpSQL() {
		return SELECT_DUMP_UNDOABLEBLOCKS_SQL;
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
	 * If there isn't a connection on the {@link ThreadLocal} then create and store
	 * it.
	 * </p>
	 * <p>
	 * This will also automatically set up the schema if it does not exist within
	 * the DB.
	 * </p>
	 * 
	 * @throws BlockStoreException
	 *             if successful connection to the DB couldn't be made.
	 */
	protected synchronized final void maybeConnect() throws BlockStoreException {
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
	 * This specifically checks for the 'settings' table and if it exists makes an
	 * assumption that the rest of the data structures are present.
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
		Statement s = conn.get().createStatement();
		// create all the database tables
		for (String sql : getCreateTablesSQL()) {
			if (log.isDebugEnabled()) {
				log.debug("DatabaseFullPrunedBlockStore : CREATE table [SQL= {0}]", sql);
			}
			s.executeUpdate(sql);
		}
		// create all the database indexes
		for (String sql : getCreateIndexesSQL()) {
			if (log.isDebugEnabled()) {
				log.debug("DatabaseFullPrunedBlockStore : CREATE index [SQL= {0}]", sql);
			}
			s.executeUpdate(sql);
		}
		s.close();

		// insert the initial settings for this store
		PreparedStatement ps = conn.get().prepareStatement(getInsertSettingsSQL());
		ps.setString(1, CHAIN_HEAD_SETTING);
		ps.setNull(2, Types.BINARY);
		ps.execute();
		ps.setString(1, VERIFIED_CHAIN_HEAD_SETTING);
		ps.setNull(2, Types.BINARY);
		ps.execute();
		ps.setString(1, VERSION_SETTING);
		ps.setBytes(2, "03".getBytes());
		ps.execute();
		ps.close();
		createNewStore(params);
	}

	/**
	 * Create a new store for the given {@link org.bitcoinj.core.NetworkParameters}.
	 * 
	 * @param params
	 *            The network.
	 * @throws BlockStoreException
	 *             If the store couldn't be created.
	 */
	private void createNewStore(NetworkParameters params) throws BlockStoreException {
		try {
			// Set up the genesis block. When we start out fresh, it is by
			// definition the top of the chain.
			StoredBlock storedGenesisHeader = new StoredBlock(params.getGenesisBlock().cloneAsHeader(), 0);
			// The coinbase in the genesis block is not spendable. This is
			// because of how Bitcoin Core inits
			// its database - the genesis transaction isn't actually in the db
			// so its spent flags can never be updated.
			List<Transaction> genesisTransactions = Lists.newLinkedList();
			StoredUndoableBlock storedGenesis = new StoredUndoableBlock(params.getGenesisBlock().getHash(),
					genesisTransactions);
			put(storedGenesisHeader, storedGenesis);
			setChainHead(storedGenesisHeader);
			setVerifiedChainHead(storedGenesisHeader);
		} catch (VerificationException e) {
			throw new RuntimeException(e); // Cannot happen.
		}
	}

	/**
	 * Initialise the store state from the database.
	 * 
	 * @throws java.sql.SQLException
	 *             If there is a database error.
	 * @throws BlockStoreException
	 *             If there is a block store error.
	 */
	public void initFromDatabase() throws SQLException, BlockStoreException {
		PreparedStatement ps = conn.get().prepareStatement(getSelectSettingsSQL());
		ResultSet rs;
		ps.setString(1, CHAIN_HEAD_SETTING);
		rs = ps.executeQuery();
		if (!rs.next()) {
			throw new BlockStoreException("corrupt database block store - no chain head pointer");
		}
		Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
		rs.close();
		this.chainHeadBlock = get(hash);
		this.chainHeadHash = hash;
		if (this.chainHeadBlock == null) {
			throw new BlockStoreException("corrupt database block store - head block not found");
		}
		ps.setString(1, VERIFIED_CHAIN_HEAD_SETTING);
		rs = ps.executeQuery();
		if (!rs.next()) {
			throw new BlockStoreException("corrupt database block store - no verified chain head pointer");
		}
		hash = Sha256Hash.wrap(rs.getBytes(1));
		rs.close();
		ps.close();
		this.verifiedChainHeadBlock = get(hash);
		this.verifiedChainHeadHash = hash;
		if (this.verifiedChainHeadBlock == null) {
			throw new BlockStoreException("corrupt databse block store - verified head block not found");
		}
	}

	protected void putUpdateStoredBlock(StoredBlock storedBlock, boolean wasUndoable) throws SQLException {
		try {
			PreparedStatement s = conn.get().prepareStatement(getInsertHeadersSQL());

			s.setBytes(1, storedBlock.getHeader().getHash().getBytes());
			s.setInt(2, storedBlock.getHeight());
			s.setBytes(3, storedBlock.getHeader().unsafeBitcoinSerialize()); //changed to write full block to db
			s.setBoolean(4, wasUndoable);
			s.setBytes(5, storedBlock.getHeader().getPrevBlockHash().getBytes());
			s.setBytes(6, storedBlock.getHeader().getPrevBranchBlockHash().getBytes());
			s.setBytes(7, storedBlock.getHeader().getMineraddress());
			s.setLong(8, storedBlock.getHeader().getTokenid());
			s.setLong(9, storedBlock.getHeader().getBlocktype());
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			// It is possible we try to add a duplicate StoredBlock if we
			// upgraded
			// In that case, we just update the entry to mark it wasUndoable
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
	public void put(StoredBlock storedBlock) throws BlockStoreException {
		maybeConnect();
		try {
			putUpdateStoredBlock(storedBlock, false);
		} catch (SQLException e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock) throws BlockStoreException {
		maybeConnect();
		// We skip the first 4 bytes because (on mainnet) the minimum target has
		// 4 0-bytes

//		int height = storedBlock.getHeight();
//		byte[] transactions = null;
//		byte[] txOutChanges = null;
//		try {
//			ByteArrayOutputStream bos = new ByteArrayOutputStream();
//			if (undoableBlock.getTxOutChanges() != null) {
//				undoableBlock.getTxOutChanges().serializeToStream(bos);
//				txOutChanges = bos.toByteArray();
//			} else {
//				int numTxn = undoableBlock.getTransactions().size();
//				bos.write(0xFF & numTxn);
//				bos.write(0xFF & (numTxn >> 8));
//				bos.write(0xFF & (numTxn >> 16));
//				bos.write(0xFF & (numTxn >> 24));
//				for (Transaction tx : undoableBlock.getTransactions())
//					tx.bitcoinSerialize(bos);
//				transactions = bos.toByteArray();
//			}
//			bos.close();
//		} catch (IOException e) {
//			throw new BlockStoreException(e);
//		}

//		try {
//			try {
//				PreparedStatement s = conn.get().prepareStatement(getInsertUndoableBlocksSQL());
//				s.setBytes(1, storedBlock.getHeader().getHash().getBytes());
//				s.setInt(2, height);
//				if (transactions == null) {
//					s.setBytes(3, txOutChanges);
//					s.setNull(4, Types.BINARY);
//				} else {
//					s.setNull(3, Types.BINARY);
//					s.setBytes(4, transactions);
//				}
//				s.executeUpdate();
//				s.close();
				try {
					putUpdateStoredBlock(storedBlock, true);
				} catch (SQLException e) {
					throw new BlockStoreException(e);
				}
//			} catch (SQLException e) {
//				if (!e.getSQLState().equals(getDuplicateKeyErrorCode()))
//					throw new BlockStoreException(e);
//
//				// There is probably an update-or-insert statement, but it
//				// wasn't obvious from the docs
//				PreparedStatement s = conn.get().prepareStatement(getUpdateUndoableBlocksSQL());
//				s.setBytes(3, storedBlock.getHeader().getHash().getBytes());
//				if (transactions == null) {
//					s.setBytes(1, txOutChanges);
//					s.setNull(2, Types.BINARY);
//				} else {
//					s.setNull(1, Types.BINARY);
//					s.setBytes(2, transactions);
//				}
//				s.executeUpdate();
//				s.close();
//			}
			
			// Initial blockevaluation
			insertBlockEvaluation(BlockEvaluation.buildInitial(undoableBlock.getHash()));
			
//		} catch (SQLException ex) {
//			throw new BlockStoreException(ex);
//		}
	}

	public StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly) throws BlockStoreException {
		// Optimize for chain head
		if (chainHeadHash != null && chainHeadHash.equals(hash))
			return chainHeadBlock;
		if (verifiedChainHeadHash != null && verifiedChainHeadHash.equals(hash))
			return verifiedChainHeadBlock;
		maybeConnect();
		PreparedStatement s = null;
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
			Block b = params.getDefaultSerializer().makeBlock(results.getBytes(2));
			b.verifyHeader();
			StoredBlock stored = new StoredBlock(b, height);
			return stored;
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
	public StoredUndoableBlock getUndoBlock(Sha256Hash hash) throws BlockStoreException {
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(getSelectUndoableBlocksSQL());

			s.setBytes(1, hash.getBytes());
			ResultSet results = s.executeQuery();
			if (!results.next()) {
				return null;
			}
			// Parse it.
			byte[] txOutChanges = results.getBytes(1);
			byte[] transactions = results.getBytes(2);
			StoredUndoableBlock block;
			if (txOutChanges == null) {
				int offset = 0;
				int numTxn = ((transactions[offset++] & 0xFF)) | ((transactions[offset++] & 0xFF) << 8)
						| ((transactions[offset++] & 0xFF) << 16) | ((transactions[offset++] & 0xFF) << 24);
				List<Transaction> transactionList = new LinkedList<Transaction>();
				for (int i = 0; i < numTxn; i++) {
					Transaction tx = params.getDefaultSerializer().makeTransaction(transactions, offset);
					transactionList.add(tx);
					offset += tx.getMessageSize();
				}
				block = new StoredUndoableBlock(hash, transactionList);
			} else {
				TransactionOutputChanges outChangesObject = new TransactionOutputChanges(
						new ByteArrayInputStream(txOutChanges));
				block = new StoredUndoableBlock(hash, outChangesObject);
			}
			return block;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} catch (NullPointerException e) {
			// Corrupted database.
			throw new BlockStoreException(e);
		} catch (ClassCastException e) {
			// Corrupted database.
			throw new BlockStoreException(e);
		} catch (ProtocolException e) {
			// Corrupted database.
			throw new BlockStoreException(e);
		} catch (IOException e) {
			// Corrupted database.
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
	public StoredBlock getChainHead() throws BlockStoreException {
		return chainHeadBlock;
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
		Sha256Hash hash = chainHead.getHeader().getHash();
		this.chainHeadHash = hash;
		this.chainHeadBlock = chainHead;
		maybeConnect();
		try {
			PreparedStatement s = conn.get().prepareStatement(getUpdateSettingsSLQ());
			s.setString(2, CHAIN_HEAD_SETTING);
			s.setBytes(1, hash.getBytes());
			s.executeUpdate();
			s.close();
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		}
	}

	@Override
	public StoredBlock getVerifiedChainHead() throws BlockStoreException {
		return verifiedChainHeadBlock;
	}

	@Override
	public void setVerifiedChainHead(StoredBlock chainHead) throws BlockStoreException {
		Sha256Hash hash = chainHead.getHeader().getHash();
		this.verifiedChainHeadHash = hash;
		this.verifiedChainHeadBlock = chainHead;
		maybeConnect();
		try {
			PreparedStatement s = conn.get().prepareStatement(getUpdateSettingsSLQ());
			s.setString(2, VERIFIED_CHAIN_HEAD_SETTING);
			s.setBytes(1, hash.getBytes());
			s.executeUpdate();
			s.close();
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		}
		if (this.chainHeadBlock.getHeight() < chainHead.getHeight())
			setChainHead(chainHead);
		removeUndoableBlocksWhereHeightIsLessThan(chainHead.getHeight() - fullStoreDepth);
	}

	private void removeUndoableBlocksWhereHeightIsLessThan(int height) throws BlockStoreException {
		try {
			PreparedStatement s = conn.get().prepareStatement(getDeleteUndoableBlocksSQL());
			s.setInt(1, height);
			if (log.isDebugEnabled())
				log.debug("Deleting undoable undoable block with height <= " + height);
			s.executeUpdate();
			s.close();
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
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
			s.setInt(2, (int) index);
			ResultSet results = s.executeQuery();
			if (!results.next()) {
				return null;
			}
			// Parse it.
			int height = results.getInt(1);
			Coin value = Coin.valueOf(results.getLong(2), results.getLong(8));
			byte[] scriptBytes = results.getBytes(3);
			boolean coinbase = results.getBoolean(4);
			String address = results.getString(5);
			Sha256Hash blockhash = Sha256Hash.wrap(results.getBytes(7));

			String fromaddress = results.getString(9);
			String description = results.getString(10);
			boolean spent = results.getBoolean(11);
			long tokenid = results.getLong("tokenid");
			UTXO txout = new UTXO(hash, index, value, height, coinbase, new Script(scriptBytes), address, blockhash,
					fromaddress, description, tokenid, spent);
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
			s.setInt(2, (int) out.getIndex());
			s.setLong(3, out.getHeight());
			s.setLong(4, out.getValue().value);
			s.setBytes(5, out.getScript().getProgram());
			s.setString(6, out.getAddress());
			s.setInt(7, out.getScript().getScriptType().ordinal());
			s.setBoolean(8, out.isCoinbase());
			s.setBytes(9, out.getBlockhash().getBytes());
			s.setLong(10, out.getValue().tokenid);
			s.setString(11, out.getFromaddress());
			s.setString(12, out.getDescription());
			s.setBoolean(13, out.isSpent());
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException(e);
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
	public boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs) throws BlockStoreException {
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

	@Override
	public int getChainHeadHeight() throws UTXOProviderException {
		try {
			return getVerifiedChainHead().getHeight();
		} catch (BlockStoreException e) {
			throw new UTXOProviderException(e);
		}
	}

	/**
	 * Resets the store by deleting the contents of the tables and reinitialising
	 * them.
	 * 
	 * @throws BlockStoreException
	 *             If the tables couldn't be cleared and initialised.
	 */
	public void resetStore() throws BlockStoreException {
		maybeConnect();
		try {
			deleteStore();
			createTables();
			initFromDatabase();
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
			Statement s = conn.get().createStatement();
			for (String sql : getDropTablesSQL()) {
				s.execute(sql);
			}
			s.close();
		} catch (SQLException ex) {
			log.warn("Warning: deleteStore", ex);
			// throw new RuntimeException(ex);
		}
	}

	/**
	 * Calculate the balance for a coinbase, to-address, or p2sh address.
	 *
	 * <p>
	 * The balance
	 * {@link org.bitcoinj.store.DatabaseFullPrunedBlockStore#getBalanceSelectSQL()}
	 * returns the balance (summed) as an number, then use calculateClientSide=false
	 * </p>
	 *
	 * <p>
	 * The balance
	 * {@link org.bitcoinj.store.DatabaseFullPrunedBlockStore#getBalanceSelectSQL()}
	 * returns the all the outputs as stored in the DB (binary), then use
	 * calculateClientSide=true
	 * </p>
	 *
	 * @param address
	 *            The address to calculate the balance of
	 * @return The balance of the address supplied. If the address has not been
	 *         seen, or there are no outputs open for this address, the return value
	 *         is 0.
	 * @throws BlockStoreException
	 *             If there is an error getting the balance.
	 */
	public BigInteger calculateBalanceForAddress(Address address) throws BlockStoreException {
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(getBalanceSelectSQL());
			s.setString(1, address.toString());
			ResultSet rs = s.executeQuery();
			BigInteger balance = BigInteger.ZERO;
			if (rs.next()) {
				return BigInteger.valueOf(rs.getLong(1));
			}
			return balance;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException("Could not close statement");
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
					Coin amount = Coin.valueOf(rs.getLong(2), rs.getLong(10));
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
					long tokenid = rs.getLong("tokenid");
					UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(scriptBytes), toAddress,
							blockhash, fromaddress, description, tokenid, spent);
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
	 * Dumps information about the size of actual data in the database to standard
	 * output The only truly useless data counted is printed in the form "N in id
	 * indexes" This does not take database indexes into account. 
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

		totalSize += size;
		size = 0;
		count = 0;
		rs = s.executeQuery(getSelectUndoableblocksDumpSQL());
		while (rs.next()) {
			size += 28; // hash
			size += 4; // height
			byte[] txOutChanges = rs.getBytes(1);
			byte[] transactions = rs.getBytes(2);
			if (txOutChanges == null)
				size += transactions.length;
			else
				size += txOutChanges.length;
			// size += the space to represent NULL
			count++;
		}
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

	@Override
	public void insertBlockEvaluation(BlockEvaluation blockEvaluation) throws BlockStoreException {
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(INSERT_BLOCKEVALUATION_SQL);
			preparedStatement.setBytes(1, blockEvaluation.getBlockhash().getBytes());
			preparedStatement.setLong(2, blockEvaluation.getRating());
			preparedStatement.setLong(3, blockEvaluation.getDepth());
			preparedStatement.setLong(4, blockEvaluation.getCumulativeWeight());
			preparedStatement.setBoolean(5, blockEvaluation.isSolid());
			preparedStatement.setLong(6, blockEvaluation.getHeight());
			preparedStatement.setBoolean(7, blockEvaluation.isMilestone());
			preparedStatement.setLong(8, blockEvaluation.getMilestoneLastUpdateTime());
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
	public void removeBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(DELETE_BLOCKEVALUATION_SQL);
			preparedStatement.setBytes(1, hash.getBytes());
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
	public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(SELECT_BLOCKEVALUATION_SQL);
			preparedStatement.setBytes(1, hash.getBytes());

			ResultSet resultSet = preparedStatement.executeQuery();
			if (!resultSet.next()) {
				return null;
			}
			BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)), 
					resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getBoolean(5), 
					resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
						resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
						resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
						resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
						resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
						resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
	public void updateBlockEvaluationCumulativeweight(Sha256Hash blockhash, long cumulativeweight)
			throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL);
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

	@Override
	public void updateBlockEvaluationDepth(Sha256Hash blockhash, long depth) throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_DEPTH_SQL);
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

	@Override
	public void updateBlockEvaluationHeight(Sha256Hash blockhash, long height) throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_HEIGHT_SQL);
			preparedStatement.setLong(1, height);
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
	public void updateBlockEvaluationMilestone(Sha256Hash blockhash, boolean b) throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_MILESTONE_SQL);
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
	public void updateBlockEvaluationRating(Sha256Hash blockhash, long i) throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_RATING_SQL);
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

	@Override
	public void updateBlockEvaluationSolid(Sha256Hash blockhash, boolean b) throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_SOLID_SQL);
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
	public void updateBlockEvaluationMilestoneLastUpdateTime(Sha256Hash blockhash, long now)
			throws BlockStoreException {
		BlockEvaluation blockEvaluation = this.getBlockEvaluation(blockhash);
		if (blockEvaluation == null) {
			throw new BlockStoreException("Could not find blockevaluation to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_MILESTONE_LAST_UPDATE_TIME_SQL);
			preparedStatement.setLong(1, now);
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
					resultSet.getLong(6), resultSet.getBoolean(7), resultSet.getLong(8));
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
	
	@Override
	public void updateTransactionOutputSpent(Sha256Hash prevTxHash, long index, boolean b) throws BlockStoreException {
		UTXO prev = this.getTransactionOutput(prevTxHash, index);
		if (prev == null) {
			throw new BlockStoreException("Could not find UTXO to update");
		}
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = conn.get().prepareStatement(UPDATE_OUTPUTS_SPENT_SQL);
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
            preparedStatement.setLong(1, NetworkParameters.BLOCKTYPE_GENESIS);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return 1;
            }
            return (int) resultSet.getLong(1);
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
