/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.BatchBlock;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.ExchangeMulti;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UnsolidBlock;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.script.Script;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.service.DepthAndWeight;
import net.bigtangle.server.service.Rating;
import net.bigtangle.server.service.SolidityState;
import net.bigtangle.utils.Gzip;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {

    private static final String OPENORDERHASH = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private static final String LIMIT_5000 = " limit 5000 ";

    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    public static final String VERSION_SETTING = "version";

    // Drop table SQL.
    public static String DROP_SETTINGS_TABLE = "DROP TABLE IF EXISTS settings";
    public static String DROP_BLOCKS_TABLE = "DROP TABLE IF EXISTS blocks";
    public static String DROP_UNSOLIDBLOCKS_TABLE = "DROP TABLE IF EXISTS unsolidblocks";
    public static String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE IF EXISTS outputs";
    public static String DROP_OUTPUTSMULTI_TABLE = "DROP TABLE IF EXISTS outputsmulti";
    public static String DROP_TOKENS_TABLE = "DROP TABLE IF EXISTS tokens";
    public static String DROP_MATCHING_TABLE = "DROP TABLE IF EXISTS matching";
    public static String DROP_MULTISIGNADDRESS_TABLE = "DROP TABLE IF EXISTS multisignaddress";
    public static String DROP_MULTISIGNBY_TABLE = "DROP TABLE IF EXISTS multisignby";
    public static String DROP_MULTISIGN_TABLE = "DROP TABLE IF EXISTS multisign";
    public static String DROP_TX_REWARDS_TABLE = "DROP TABLE IF EXISTS txreward";
    public static String DROP_USERDATA_TABLE = "DROP TABLE IF EXISTS userdata";
    public static String DROP_PAYMULTISIGN_TABLE = "DROP TABLE IF EXISTS paymultisign";
    public static String DROP_PAYMULTISIGNADDRESS_TABLE = "DROP TABLE IF EXISTS paymultisignaddress";
    public static String DROP_VOSEXECUTE_TABLE = "DROP TABLE IF EXISTS vosexecute";
    public static String DROP_ordercancel_TABLE = "DROP TABLE IF EXISTS ordercancel";
    public static String DROP_BATCHBLOCK_TABLE = "DROP TABLE IF EXISTS batchblock";
    public static String DROP_SUBTANGLE_PERMISSION_TABLE = "DROP TABLE IF EXISTS subtangle_permission";
    public static String DROP_ORDERS_TABLE = "DROP TABLE IF EXISTS orders";

    public static String DROP_MYSERVERBLOCKS_TABLE = "DROP TABLE IF EXISTS myserverblocks";
    public static String DROP_EXCHANGE_TABLE = "DROP TABLE exchange";
    public static String DROP_EXCHANGEMULTI_TABLE = "DROP TABLE exchange_multisign";
    public static String DROP_ACCESS_PERMISSION_TABLE = "DROP TABLE access_permission";
    public static String DROP_ACCESS_GRANT_TABLE = "DROP TABLE access_grant";

    // Queries SQL.
    protected final String SELECT_SETTINGS_SQL = "SELECT settingvalue FROM settings WHERE name = ?";
    protected final String INSERT_SETTINGS_SQL = getInsert() + "  INTO settings(name, settingvalue) VALUES(?, ?)";

    protected final String SELECT_BLOCKS_SQL = "SELECT  height, block,  prevblockhash,prevbranchblockhash,mineraddress,"
            + "blocktype FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_UNSOLIDBLOCKS_SQL = "SELECT  hash,   block,  inserttime FROM unsolidblocks order by inserttime asc"
            + afterSelect();

    protected final String SELECT_UNSOLIDBLOCKS_FROM_DEPENDENCY_SQL = "SELECT block FROM unsolidblocks WHERE missingDependency = ? "
            + afterSelect();

    // need all blocks from the milestone, even it is not include in milestone
    protected final String SELECT_BLOCKS_MILESTONE_SQL = "SELECT block, height FROM blocks WHERE height "
            + " >= (select min(height) from blocks where  milestone >= ? and  milestone <=?)"
            + " and height <= (select max(height) from blocks where  milestone >= ? and  milestone <=?) "
            + afterSelect() + " order by height asc ";
    protected final String SELECT_BLOCKS_TEMPLATE = "  hash, rating, depth, cumulativeweight, "
            + "  height, milestone, milestonelastupdate,  inserttime,  block, solid, confirmed";

    protected final String SELECT_NOT_INVALID_APPROVER_BLOCKS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE (prevblockhash = ? or prevbranchblockhash = ?) AND solid >= 0 " + afterSelect();
    protected final String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks WHERE (prevblockhash = ? or prevbranchblockhash = ?) AND solid = 2 " + afterSelect();
    protected final String SELECT_APPROVER_BLOCKS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks WHERE prevblockhash = ?  or" + "  prevbranchblockhash = ? " + afterSelect();
    protected final String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM blocks "
            + "WHERE blocks.prevblockhash = ? or blocks.prevbranchblockhash = ?" + afterSelect();

    protected final String INSERT_BLOCKS_SQL = getInsert() + "  INTO blocks(hash,  height, block,  prevblockhash,"
            + "prevbranchblockhash,mineraddress,blocktype, rating, depth, cumulativeweight, "
            + "milestone, milestonelastupdate,  inserttime,  solid, confirmed  )"
            + " VALUES(?, ?, ?, ?, ?,?, ?, ?, ?, ? , ?, ?, ?,  ?, ? )";

    protected final String INSERT_UNSOLIDBLOCKS_SQL = getInsert()
            + "  INTO unsolidblocks(hash,   block,  inserttime  , reason, missingdependency, height, directlymissing)"
            + " VALUES(?, ?, ?, ?, ?, ?, ?)";

    protected final String UPDATE_SET_MISSING_BLOCK_SQL = getUpdate()
            + " unsolidblocks set directlymissing=? WHERE missingdependency=?";

    protected final String INSERT_OUTPUTS_SQL = getInsert()
            + " INTO outputs (hash, outputindex, coinvalue, scriptbytes, toaddress, addresstargetable,"
            + " coinbase, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,time, spendpendingtime, minimumsign)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)";

    protected final String SELECT_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
            + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending , spendpendingtime, minimumsign, time FROM outputs WHERE hash = ? AND outputindex = ? AND blockhash = ? ";

    protected final String SELECT_TRANSACTION_OUTPUTS_SQL = "SELECT " + "outputs.hash, coinvalue, scriptbytes, "
            + " outputs.outputindex, coinbase, " + "  outputs.toaddress  as  toaddress,"
            + " outputsmulti.toaddress  as multitoaddress, " + "  addresstargetable, blockhash, tokenid, "
            + " fromaddress, memo, spent, confirmed, spendpending,spendpendingtime,  minimumsign, time  "
            + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?";

    protected final String SELECT_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress as toaddress , addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime, minimumsign, time "
            + " , outputsmulti.toaddress  as multitoaddress" + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE (outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?) " + " AND tokenid = ?";
    protected final String SELECT_ALL_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress, addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime , minimumsign, time "
            + " FROM outputs  WHERE   confirmed=true and spent= false and tokenid = ?";

    // Tables exist SQL.
    protected final String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

    protected final String DELETE_UNSOLIDBLOCKS_SQL = "DELETE FROM unsolidblocks WHERE hash = ?";
    protected final String DELETE_OLD_UNSOLIDBLOCKS_SQL = "DELETE FROM unsolidblocks WHERE height <= ?";

    protected final String SELECT_BLOCKEVALUATION_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_BLOCKWRAP_SQL = "SELECT hash, rating, depth, cumulativeweight, "
            + " height, milestone, milestonelastupdate,  inserttime, "
            + "  block, solid, confirmed FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_ALL_BLOCKEVALUATIONS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE + "  FROM blocks ";

    protected final String SELECT_NONSOLID_MISSINGBLOCKS_SQL = "select   hash, "
            + "  inserttime,  reason,   missingdependency,   height,\n"
            + "              directlymissing from unsolidblocks where height > ? and height <= ?";

    protected final String SELECT_BLOCKS_TO_CONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks WHERE solid=2 AND milestone = -1 AND confirmed = false AND height > ? AND height <= ? AND rating >= "
            + NetworkParameters.CONFIRMATION_UPPER_THRESHOLD + afterSelect();

    protected final String SELECT_BLOCKS_TO_UNCONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE solid=2 AND milestone = -1 AND confirmed = true AND rating < "
            + NetworkParameters.CONFIRMATION_LOWER_THRESHOLD + afterSelect();

    protected final String SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE milestone >= ? AND milestone <= ?" + afterSelect();

    protected final String SELECT_SOLID_BLOCKS_IN_INTERVAL_SQL = "SELECT blocks.hash, rating, depth, cumulativeweight, "
            + " blocks.height, milestone, milestonelastupdate,  inserttime,  block, solid, confirmed FROM blocks "
            + " WHERE height > ? AND height <= ? AND solid = 2 " + afterSelect();

    protected final String SELECT_BLOCKS_CONFIRMED_AND_NOT_MILESTONE_SQL = "SELECT hash "
            + "FROM blocks WHERE milestone = -1 AND confirmed = 1 " + afterSelect();

    protected final String SELECT_OUTPUT_SPENDER_SQL = "SELECT blocks.hash," + " rating, depth, cumulativeweight, "
            + " blocks.height, milestone, milestonelastupdate, " + " inserttime,  solid, blocks.confirmed "
            + " FROM blocks INNER JOIN outputs ON outputs.spenderblockhash=blocks.hash"
            + " WHERE outputs.hash = ? AND outputindex= ? AND outputs.blockhash = ? " + afterSelect();

    protected final String UPDATE_ORDER_SPENT_SQL = getUpdate() + " orders SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";
    protected final String UPDATE_ORDER_CONFIRMED_SQL = getUpdate() + " orders SET confirmed = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";

    protected final String ORDER_TEMPLATE = "  blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, "
            + "beneficiarypubkey, validToTime, validFromTime, side , beneficiaryaddress";
    protected final String SELECT_ORDERS_BY_ISSUER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE collectinghash = ?";

    protected final String SELECT_ORDER_SPENT_SQL = "SELECT spent FROM orders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_CONFIRMED_SQL = "SELECT confirmed FROM orders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_SPENDER_SQL = "SELECT spenderblockhash FROM orders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE blockhash = ? AND collectinghash = ?";
    protected final String INSERT_ORDER_SQL = getInsert()
            + "  INTO orders (blockhash, collectinghash, offercoinvalue, offertokenid, confirmed, spent, spenderblockhash, "
            + "targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, validFromTime, side, beneficiaryaddress) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,  ?,?,?,?)";

    protected final String INSERT_OrderCancel_SQL = getInsert()
            + " INTO ordercancel (blockhash, orderblockhash, confirmed, spent, spenderblockhash,time) "
            + " VALUES (?, ?, ?, ?, ?,?)";

    protected final String INSERT_TOKENS_SQL = getInsert()
            + " INTO tokens (blockhash, confirmed, tokenid, tokenindex, amount, "
            + "tokenname, description, domainname, signnumber,tokentype, tokenstop,"
            + " prevblockhash, spent, spenderblockhash, tokenkeyvalues, revoked,language,classification, decimals, domainpredblockhash) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?,?)";

    protected String SELECT_TOKENS_SQL_TEMPLATE = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, domainname, signnumber,tokentype, tokenstop ,"
            + "tokenkeyvalues, revoked,language,classification,decimals, domainpredblockhash ";

    protected final String SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL = "SELECT spent FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_ANY_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    protected final String SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    protected final String SELECT_DOMAIN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenname = ? AND domainpredblockhash = ? AND tokenindex = ? AND confirmed = true";

    protected final String SELECT_DOMAIN_DESCENDANT_CONFIRMED_BLOCKS_SQL = "SELECT blockhash FROM tokens WHERE domainpredblockhash = ? AND confirmed = true";

    protected final String SELECT_TOKEN_SPENDER_SQL = "SELECT spenderblockhash FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_PREVBLOCKHASH_SQL = "SELECT prevblockhash FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKENID_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE tokenid = ?";

    protected final String UPDATE_TOKEN_SPENT_SQL = getUpdate() + " tokens SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ?";

    protected final String UPDATE_TOKEN_CONFIRMED_SQL = getUpdate() + " tokens SET confirmed = ? "
            + " WHERE blockhash = ?";

    protected final String SELECT_CONFIRMED_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE confirmed = true";

    protected final String SELECT_MARKET_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE tokentype = 1 and confirmed = true";

    protected final String SELECT_TOKENS_ACOUNT_MAP_SQL = "SELECT tokenid, amount  as amount "
            + "FROM tokens WHERE confirmed = true ";

    protected final String COUNT_TOKENSINDEX_SQL = "SELECT blockhash, tokenindex FROM tokens"
            + " WHERE tokenid = ? AND confirmed = true ORDER BY tokenindex DESC limit 1";

    protected final String SELECT_TOKENS_BY_DOMAINNAME_SQL = "SELECT blockhash, tokenid FROM tokens WHERE blockhash = ? limit 1";

    protected final String SELECT_TOKENS_BY_DOMAINNAME_SQL0 = "SELECT blockhash, tokenid "
            + "FROM tokens WHERE tokenname = ?  AND confirmed = true limit 1";

    protected final String UPDATE_SETTINGS_SQL = getUpdate() + " settings SET settingvalue = ? WHERE name = ?";

    protected final String UPDATE_OUTPUTS_SPENT_SQL = getUpdate()
            + " outputs SET spent = ?, spenderblockhash = ? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    protected final String UPDATE_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    protected final String UPDATE_ALL_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE blockhash = ?";

    protected final String UPDATE_OUTPUTS_SPENDPENDING_SQL = getUpdate()
            + " outputs SET spendpending = ?, spendpendingtime=? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    protected final String UPDATE_BLOCKEVALUATION_DEPTH_SQL = getUpdate() + " blocks SET depth = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL = getUpdate()
            + " blocks SET cumulativeweight = ?, depth = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL = getUpdate()
            + " blocks SET cumulativeweight = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_HEIGHT_SQL = getUpdate() + " blocks SET height = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = ?, milestonelastupdate= ?  WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_CONFIRMED_SQL = getUpdate()
            + " blocks SET confirmed = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_RATING_SQL = getUpdate() + " blocks SET rating = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_SOLID_SQL = getUpdate() + " blocks SET solid = ? WHERE hash = ?";

    protected final String UPDATE_UNSET_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = -1, milestonelastupdate= ?  WHERE milestone = ?";

    protected final String UPDATE_SET_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = ?, milestonelastupdate= ?  WHERE milestone = -1 AND confirmed = 1";

    protected final String SELECT_MULTISIGNADDRESS_SQL = "SELECT blockhash, tokenid, address, pubKeyHex, posIndex, tokenHolder FROM multisignaddress WHERE tokenid = ? AND blockhash = ?";
    protected final String INSERT_MULTISIGNADDRESS_SQL = "INSERT INTO multisignaddress (tokenid, address, pubKeyHex, posIndex,blockhash,tokenHolder) VALUES (?, ?, ?, ?,?,?)";
    protected final String DELETE_MULTISIGNADDRESS_SQL = "DELETE FROM multisignaddress WHERE tokenid = ? AND address = ?";
    protected final String COUNT_MULTISIGNADDRESS_SQL = "SELECT COUNT(*) as count FROM multisignaddress WHERE tokenid = ?";

    protected final String INSERT_MULTISIGNBY_SQL = "INSERT INTO multisignby (tokenid, tokenindex, address) VALUES (?, ?, ?)";
    protected final String SELECT_MULTISIGNBY_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String SELECT_MULTISIGNBY0_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ?";

    protected final String SELECT_MULTISIGN_ADDRESS_ALL_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign,(select count(ms1.sign) from multisign ms1 where ms1.tokenid=tokenid and tokenindex=ms1.tokenindex and ms1.sign!=0 ) as count FROM multisign  WHERE 1=1 ";
    protected final String SELECT_MULTISIGN_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE address = ? ORDER BY tokenindex ASC";
    protected final String SELECT_MULTISIGN_TOKENID_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE tokenid = ? and address = ? ORDER BY tokenindex ASC";

    protected final String INSERT_MULTISIGN_SQL = "INSERT INTO multisign (tokenid, tokenindex, address, blockhash, sign, id) VALUES (?, ?, ?, ?, ?, ?)";
    protected final String UPDATE_MULTISIGN_SQL = "UPDATE multisign SET blockhash = ?, sign = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String UPDATE_MULTISIGN0_SQL = "UPDATE multisign SET blockhash = ?, sign = 0 WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String UPDATE_MULTISIGN1_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ?";
    protected final String SELECT_COUNT_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String DELETE_MULTISIGN_SQL = "DELETE FROM multisign WHERE tokenid = ?";

    protected final String SELECT_COUNT_MULTISIGN_SIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND sign = ?";

    /* REWARD BLOCKS */
    protected final String INSERT_TX_REWARD_SQL = getInsert()
            + "  INTO txreward (blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength) VALUES (?, ?, ?, ?, ?, ?, ?)";
    protected final String SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=(SELECT MAX(chainlength) FROM txreward WHERE confirmed=1)";
    protected final String SELECT_TX_REWARD_CONFIRMED_AT_HEIGHT_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=?";
    protected final String SELECT_TX_REWARD_MAX_SOLID_REWARD_SQL = "SELECT blockhash, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, txreward.difficulty, txreward.chainlength FROM txreward"
            + "  JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1 AND chainlength="
            + "(SELECT MAX(chainlength) FROM txreward JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1)";
    protected final String SELECT_TX_REWARD_ALL_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, "
            + "spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward "
            + "WHERE confirmed = 1 order by chainlength ";

    protected final String SELECT_TX_REWARD_CONFIRMED_SQL = "SELECT confirmed " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_CHAINLENGTH_SQL = "SELECT chainlength "
            + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_DIFFICULTY_SQL = "SELECT difficulty " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_SPENT_SQL = "SELECT spent " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_SPENDER_SQL = "SELECT spenderblockhash "
            + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_PREVBLOCKHASH_SQL = "SELECT prevblockhash "
            + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_REWARD_WHERE_PREV_HASH_SQL = "SELECT blockhash "
            + "FROM txreward WHERE prevblockhash = ?";
    protected final String UPDATE_TX_REWARD_CONFIRMED_SQL = "UPDATE txreward SET confirmed = ? WHERE blockhash = ?";
    protected final String UPDATE_TX_REWARD_SPENT_SQL = "UPDATE txreward SET spent = ?, spenderblockhash = ? WHERE blockhash = ?";

    /* MATCHING EVENTS */
    protected final String INSERT_MATCHING_EVENT_SQL = getInsert()
            + " INTO matching (txhash, tokenid,  price, executedQuantity, inserttime) VALUES (?, ?, ?, ?, ?)";
    protected final String SELECT_MATCHING_EVENT = "SELECT txhash, tokenid,  price, executedQuantity, inserttime "
            + "FROM matching ";
    protected final String DELETE_MATCHING_EVENT_BY_HASH = "DELETE FROM matching WHERE txhash = ?";

    /* OTHER */
    protected final String INSERT_OUTPUTSMULTI_SQL = "insert into outputsmulti (hash, toaddress, outputindex) values (?, ?, ?)";
    protected final String SELECT_OUTPUTSMULTI_SQL = "select hash, toaddress, outputindex from outputsmulti where hash=? and outputindex=?";

    protected final String SELECT_USERDATA_SQL = "SELECT blockhash, dataclassname, data, pubKey, blocktype FROM userdata WHERE dataclassname = ? and pubKey = ?";
    protected final String INSERT_USERDATA_SQL = "INSERT INTO userdata (blockhash, dataclassname, data, pubKey, blocktype) VALUES (?, ?, ?, ?, ?)";
    protected final String UPDATE_USERDATA_SQL = "UPDATE userdata SET blockhash = ?, data = ? WHERE dataclassname = ? and pubKey = ?";

    protected final String INSERT_BATCHBLOCK_SQL = "INSERT INTO batchblock (hash, block, inserttime) VALUE (?, ?, ?)";
    protected final String DELETE_BATCHBLOCK_SQL = "DELETE FROM batchblock WHERE hash = ?";
    protected final String SELECT_BATCHBLOCK_SQL = "SELECT hash, block, inserttime FROM batchblock order by inserttime ASC";
    protected final String INSERT_SUBTANGLE_PERMISSION_SQL = "INSERT INTO  subtangle_permission (pubkey, userdataPubkey , status) VALUE (?, ?, ?)";

    protected final String DELETE_SUBTANGLE_PERMISSION_SQL = "DELETE FROM  subtangle_permission WHERE pubkey=?";
    protected final String UPATE_ALL_SUBTANGLE_PERMISSION_SQL = "UPDATE   subtangle_permission set status=? ,userdataPubkey=? WHERE  pubkey=? ";

    protected final String SELECT_ALL_SUBTANGLE_PERMISSION_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission ";

    protected final String SELECT_SUBTANGLE_PERMISSION_BY_PUBKEYS_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission WHERE 1=1 ";

    protected final String SELECT_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders ORDER BY blockhash, collectinghash";

    protected final String SELECT_OPEN_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE confirmed=1 AND spent=0 ";

    protected final String SELECT_BEST_OPEN_SELL_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=0 AND offertokenid=? " + " ORDER BY targetcoinvalue / offercoinvalue ASC"
            + " LIMIT ?";
    protected final String SELECT_BEST_OPEN_BUY_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=0 AND targettokenid=? " + " ORDER BY offercoinvalue / targetcoinvalue DESC"
            + " LIMIT ?";

    protected final String SELECT_MY_REMAINING_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? ";
    protected final String SELECT_MY_INITIAL_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress=? AND collectinghash=" + OPENORDERHASH
            + " AND blockhash IN ( SELECT blockhash FROM orders "
            + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? )";
    // TODO remove test
    protected final String SELECT_AVAILABLE_UTXOS_SORTED_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress, "
            + "addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,spendpendingtime, minimumsign, time, hash, outputindex "
            + " FROM outputs WHERE confirmed=1 AND spent=0 ORDER BY hash, outputindex";

    protected String INSERT_EXCHANGE_SQL = getInsert()
            + "  INTO exchange (orderid, fromAddress, fromTokenHex, fromAmount,"
            + " toAddress, toTokenHex, toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market,memo) VALUES (?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected String DELETE_EXCHANGE_SQL = "DELETE FROM exchange WHERE orderid=?";
    protected String SELECT_EXCHANGE_ORDERID_SQL = "SELECT orderid,"
            + " fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex,"
            + " toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market,signInputData FROM exchange WHERE orderid = ?";

    protected String INSERT_EXCHANGEMULTI_SQL = getInsert()
            + "  INTO exchange_multisign (orderid, pubkey,sign) VALUES (?, ?,?)";

    protected final String SELECT_ORDERCANCEL_SQL = "SELECT blockhash, orderblockhash, confirmed, spent, spenderblockhash,time FROM ordercancel WHERE 1 = 1";
    protected String SELECT_EXCHANGE_SQL_A = "SELECT DISTINCT orderid, fromAddress, "
            + "fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, "
            + "data, toSign, fromSign, toOrderId, fromOrderId, market,memo "
            + "FROM exchange e WHERE (toSign = false OR fromSign = false) AND "
            + "(fromAddress = ? OR toAddress = ? OR toOrderId in(SELECT DISTINCT em.orderid FROM exchange_multisign em WHERE pubkey=?)) "
            + afterSelect();
    protected NetworkParameters params;
    protected ThreadLocal<Connection> conn;
    protected LinkedBlockingQueue<Connection> allConnections;
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
        this.allConnections = new LinkedBlockingQueue<Connection>();
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
            } else {
                log.info("setting table   Exists");
            }
        } catch (Exception e) {
            log.warn("create table error", e);
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
        sqlStatements.add(DROP_BLOCKS_TABLE);
        sqlStatements.add(DROP_UNSOLIDBLOCKS_TABLE);
        sqlStatements.add(DROP_OPEN_OUTPUT_TABLE);
        sqlStatements.add(DROP_OUTPUTSMULTI_TABLE);
        sqlStatements.add(DROP_TOKENS_TABLE);
        sqlStatements.add(DROP_MATCHING_TABLE);
        sqlStatements.add(DROP_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(DROP_MULTISIGNBY_TABLE);
        sqlStatements.add(DROP_MULTISIGN_TABLE);
        sqlStatements.add(DROP_TX_REWARDS_TABLE);
        sqlStatements.add(DROP_USERDATA_TABLE);
        sqlStatements.add(DROP_PAYMULTISIGN_TABLE);
        sqlStatements.add(DROP_PAYMULTISIGNADDRESS_TABLE);
        sqlStatements.add(DROP_VOSEXECUTE_TABLE);
        sqlStatements.add(DROP_ordercancel_TABLE);
        sqlStatements.add(DROP_BATCHBLOCK_TABLE);
        sqlStatements.add(DROP_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(DROP_ORDERS_TABLE);
        sqlStatements.add(DROP_MYSERVERBLOCKS_TABLE);
        sqlStatements.add(DROP_EXCHANGE_TABLE);
        sqlStatements.add(DROP_EXCHANGEMULTI_TABLE);
        sqlStatements.add(DROP_ACCESS_PERMISSION_TABLE);
        sqlStatements.add(DROP_ACCESS_GRANT_TABLE);
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
    protected void maybeConnect() throws BlockStoreException {
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

            // set the schema if one is needed
            synchronized (this) {
                if (schemaName != null) {
                    Statement s = conn.get().createStatement();
                    for (String sql : getCreateSchemeSQL()) {
                        s.execute(sql);
                    }
                }
            }
            logStack();

        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    private void logStack() {

    }

    @Override
    public void close() {
        synchronized (this) {
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
    private synchronized void createTables() throws SQLException, BlockStoreException {
        try {
            // beginDatabaseBatchWrite();
            // create all the database tables
            for (String sql : getCreateTablesSQL()) {

                log.debug("DatabaseFullPrunedBlockStore : CREATE table " + sql);

                Statement s = conn.get().createStatement();
                try {
                    s.execute(sql);
                } catch (Exception e) {
                    log.debug("DatabaseFullPrunedBlockStore : CREATE table " + sql, e);

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

                } catch (Exception e) {
                    log.debug("DatabaseFullPrunedBlockStore : CREATE index " + sql, e);

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

        } catch (Exception e) {
            log.error("", e);
            // this.abortDatabaseBatchWrite();
        }
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

            saveNewStore(params.getGenesisBlock());
            saveGenesisTransactionOutput(params.getGenesisBlock());

            // Just fill the tables with some valid data
            // Reward output table
            insertReward(params.getGenesisBlock().getHash(), Sha256Hash.ZERO_HASH,
                    Utils.encodeCompactBits(params.getMaxTargetReward()), 0);
            updateRewardConfirmed(params.getGenesisBlock().getHash(), true);

            // create bigtangle Token output table
            Token bigtangle = Token.genesisToken(params);
            insertToken(bigtangle.getBlockHash(), bigtangle);
            updateTokenConfirmed(params.getGenesisBlock().getHash(), true);

        } catch (VerificationException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    private void saveNewStore(Block b) throws BlockStoreException {
        put(b);

        updateBlockEvaluationMilestone(b.getHash(), 0);

        updateBlockEvaluationSolid(b.getHash(), 2);
        updateBlockEvaluationConfirmed(b.getHash(), true);

    }

    public void saveGenesisTransactionOutput(Block block) throws BlockStoreException {

        for (TransactionOutput out : block.getTransactions().get(0).getOutputs()) {
            // For each output, add it to the set of unspent outputs so
            // it can be consumed
            // in future.
            Script script = new Script(out.getScriptBytes());
            int minsignnumber = 1;
            if (script.isSentToMultiSig()) {
                minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
            }

            UTXO newOut = new UTXO(block.getTransactions().get(0).getHash(), out.getIndex(), out.getValue(), true,
                    script, script.getToAddress(params, true).toString(), block.getHash(), "",
                    block.getTransactions().get(0).getMemo(), Utils.HEX.encode(out.getValue().getTokenid()), false,
                    true, false, minsignnumber, 0, block.getTimeSeconds());
            addUnspentTransactionOutput(newOut);

            if (script.isSentToMultiSig()) {

                for (ECKey ecKey : script.getPubKeys()) {
                    String toaddress = ecKey.toAddress(params).toBase58();
                    OutputsMulti outputsMulti = new OutputsMulti(newOut.getTxHash(), toaddress, newOut.getIndex());
                    this.insertOutputsMulti(outputsMulti);
                }
            }

        }
    }

    protected void putUpdateStoredBlock(Block block, BlockEvaluation blockEvaluation) throws SQLException {
        try {

            PreparedStatement s = conn.get().prepareStatement(INSERT_BLOCKS_SQL);
            s.setBytes(1, block.getHash().getBytes());
            s.setLong(2, block.getHeight());
            s.setBytes(3, Gzip.compress(block.unsafeBitcoinSerialize()));

            s.setBytes(4, block.getPrevBlockHash().getBytes());
            s.setBytes(5, block.getPrevBranchBlockHash().getBytes());
            s.setBytes(6, block.getMinerAddress());
            s.setLong(7, block.getBlockType().ordinal());

            s.setLong(8, blockEvaluation.getRating());
            s.setLong(9, blockEvaluation.getDepth());
            s.setLong(10, blockEvaluation.getCumulativeWeight());

            int j = 4;
            s.setLong(j + 7, blockEvaluation.getMilestone());
            s.setLong(j + 8, blockEvaluation.getMilestoneLastUpdateTime());

            s.setLong(j + 9, blockEvaluation.getInsertTime());

            s.setLong(j + 10, blockEvaluation.getSolid());
            s.setBoolean(j + 11, blockEvaluation.isConfirmed());

            s.executeUpdate();
            s.close();
            // log.info("add block hexStr : " + block.getHash().toString());
        } catch (SQLException e) {
            // It is possible we try to add a duplicate Block if we
            // upgraded
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
                throw e;
        }
    }

    @Override
    public void put(Block block) throws BlockStoreException {
        maybeConnect();

        try {

            BlockEvaluation blockEval = BlockEvaluation.buildInitial(block);

            putUpdateStoredBlock(block, blockEval);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }

    }

    public Block get(Sha256Hash hash) throws BlockStoreException {

        maybeConnect();
        PreparedStatement s = null;
        // log.info("find block hexStr : " + hash.toString());
        try {
            s = conn.get().prepareStatement(SELECT_BLOCKS_SQL);
            s.setBytes(1, hash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.

            return params.getDefaultSerializer().makeZippedBlock(results.getBytes(2));

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

    public List<byte[]> blocksFromChainLength(long start, long end) throws BlockStoreException {
        // Optimize for chain head
        List<byte[]> re = new ArrayList<byte[]>();
        maybeConnect();
        PreparedStatement s = null;
        // log.info("find block hexStr : " + hash.toString());
        try {
            s = conn.get().prepareStatement(SELECT_BLOCKS_MILESTONE_SQL);
            s.setLong(1, start);
            s.setLong(2, end);
            s.setLong(3, start);
            s.setLong(4, end);
            ResultSet results = s.executeQuery();
            while (results.next()) {
                re.add(Gzip.decompress(results.getBytes("block")));
            }
            return re;
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
        return re;
    }

    public void streamBlocks(long height, KafkaMessageProducer kafkaMessageProducer, String serveraddress)
            throws BlockStoreException {
        // Optimize for chain head

        maybeConnect();
        PreparedStatement s = null;
        // log.info("find block hexStr : " + hash.toString());
        try {
            s = conn.get().prepareStatement(SELECT_BLOCKS_MILESTONE_SQL);
            s.setLong(1, height);
            ResultSet results = s.executeQuery();
            long count = 0;
            while (results.next()) {

                kafkaMessageProducer.sendMessage(results.getBytes(1), serveraddress);
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

    public List<BlockWrap> getApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        List<BlockWrap> storedBlocks = new ArrayList<BlockWrap>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_APPROVER_BLOCKS_SQL);
            s.setBytes(1, hash.getBytes());
            s.setBytes(2, hash.getBytes());
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    storedBlocks.add(new BlockWrap(block, blockEvaluation, params));
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

    private boolean verifyHeader(Block block) {
        try {
            block.verifyHeader();
            return true;
        } catch (VerificationException e) {
            return false;
        }
    }

    public List<BlockWrap> getNotInvalidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        List<BlockWrap> storedBlocks = new ArrayList<BlockWrap>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_NOT_INVALID_APPROVER_BLOCKS_SQL);
            s.setBytes(1, hash.getBytes());
            s.setBytes(2, hash.getBytes());
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    storedBlocks.add(new BlockWrap(block, blockEvaluation, params));
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

    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        List<BlockWrap> storedBlocks = new ArrayList<BlockWrap>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_SOLID_APPROVER_BLOCKS_SQL);
            s.setBytes(1, hash.getBytes());
            s.setBytes(2, hash.getBytes());
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    storedBlocks.add(new BlockWrap(block, blockEvaluation, params));
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
    public boolean getOutputConfirmation(Sha256Hash blockHash, Sha256Hash hash, long index) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(
                    "SELECT  confirmed " + "FROM outputs WHERE hash = ? AND outputindex = ? AND blockhash = ? ");
            s.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            s.setLong(2, index);
            s.setBytes(3, blockHash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return false;
            }
            return results.getBoolean("confirmed");

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
    public UTXO getTransactionOutput(Sha256Hash blockHash, Sha256Hash hash, long index) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_OUTPUTS_SQL);
            s.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            s.setLong(2, index);
            s.setBytes(3, blockHash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            UTXO txout = setUTXO(hash, index, results);
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

    private UTXO setUTXO(Sha256Hash hash, long index, ResultSet results) throws SQLException {
        // Parse it.
        Coin coinvalue = new Coin(new BigInteger(results.getBytes("coinvalue")), results.getString("tokenid"));
        byte[] scriptBytes = results.getBytes("scriptbytes");
        boolean coinbase = results.getBoolean("coinbase");
        String address = results.getString("toaddress");
        Sha256Hash blockhash = Sha256Hash.wrap(results.getBytes("blockhash"));

        String fromaddress = results.getString("fromaddress");
        String memo = results.getString("memo");
        boolean spent = results.getBoolean("spent");
        boolean confirmed = results.getBoolean("confirmed");
        boolean spendPending = results.getBoolean("spendpending");
        long spendPendingTime = results.getLong("spendpendingtime");
        String tokenid = results.getString("tokenid");
        long minimumsign = results.getLong("minimumsign");
        long time = results.getLong("time");
        if (minimumsign > 1) {
            address = results.getString("multitoaddress");
        }
        UTXO txout = new UTXO(hash, index, coinvalue, coinbase, new Script(scriptBytes), address, blockhash,
                fromaddress, memo, tokenid, spent, confirmed, spendPending, minimumsign, spendPendingTime, time);
        return txout;
    }

    @Override
    public void addUnspentTransactionOutput(List<UTXO> utxos) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(INSERT_OUTPUTS_SQL);
            for (UTXO out : utxos) {
                s.setBytes(1, out.getTxHash().getBytes());
                // index is actually an unsigned int
                s.setLong(2, out.getIndex());
                s.setBytes(3, out.getValue().getValue().toByteArray());
                s.setBytes(4, out.getScript().getProgram());
                s.setString(5, out.getAddress());
                s.setLong(6, out.getScript().getScriptType().ordinal());
                s.setBoolean(7, out.isCoinbase());
                s.setBytes(8, out.getBlockHash() != null ? out.getBlockHash().getBytes() : null);
                s.setString(9, Utils.HEX.encode(out.getValue().getTokenid()));
                s.setString(10, out.getFromaddress());
                s.setString(11, out.getMemo());
                s.setBoolean(12, out.isSpent());
                s.setBoolean(13, out.isConfirmed());
                s.setBoolean(14, out.isSpendPending());
                s.setLong(15, out.getTime());
                s.setLong(16, out.getSpendPendingTime());
                s.setLong(17, out.getMinimumsign());
                s.addBatch();
            }
            s.executeBatch();
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
    public void addUnspentTransactionOutput(UTXO out) throws BlockStoreException {
        List<UTXO> a = new ArrayList<UTXO>();
        a.add(out);
        addUnspentTransactionOutput(a);
    }

    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();

        try {
            conn.get().setAutoCommit(false);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
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
    public void defaultDatabaseBatchWrite() throws BlockStoreException {
        try {
            if (!conn.get().getAutoCommit()) {
                conn.get().setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new BlockStoreException(e);
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
        defaultDatabaseBatchWrite();
        try {
            deleteStore();
            createTables();
        } catch (SQLException ex) {
            log.warn("Warning: deleteStore", ex);
            throw new RuntimeException(ex);
        }
    }

    public void resetStore(DatabaseStoreCallback databaseStoreCallback) throws BlockStoreException {
        this.resetStore();
        try {
            databaseStoreCallback.callback();
        } catch (Exception e) {
            log.error("databaseStoreCallback", e);
            throw new RuntimeException(e);
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

            for (String sql : getDropTablesSQL()) {
                Statement s = conn.get().createStatement();
                try {

                    log.info("drop table : " + sql);
                    s.execute(sql);
                } catch (Exception e) {
                    log.info("drop table : " + sql, e);
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
    public List<UTXO> getOpenAllOutputs(String tokenid) throws UTXOProviderException {

        PreparedStatement s = null;
        List<UTXO> outputs = new ArrayList<UTXO>();
        try {
            maybeConnect();
            s = conn.get().prepareStatement(SELECT_ALL_OUTPUTS_TOKEN_SQL);
            s.setString(1, tokenid);
            ResultSet results = s.executeQuery();
            while (results.next()) {
                outputs.add(
                        setUTXO(Sha256Hash.wrap(results.getBytes("hash")), results.getLong("outputindex"), results));
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
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        PreparedStatement s = null;
        List<UTXO> outputs = new ArrayList<UTXO>();
        try {
            maybeConnect();
            s = conn.get().prepareStatement(SELECT_TRANSACTION_OUTPUTS_SQL);
            for (Address address : addresses) {
                s.setString(1, address.toString());
                s.setString(2, address.toString());
                ResultSet results = s.executeQuery();
                while (results.next()) {
                    outputs.add(setUTXO(Sha256Hash.wrap(results.getBytes("hash")), results.getLong("outputindex"),
                            results));
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
                ResultSet results = s.executeQuery();
                while (results.next()) {
                    outputs.add(setUTXO(Sha256Hash.wrap(results.getBytes("hash")), results.getLong("outputindex"),
                            results));

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
    public BlockWrap getBlockWrap(Sha256Hash hash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKWRAP_SQL);
            preparedStatement.setBytes(1, hash.getBytes());

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

            Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
            return new BlockWrap(block, blockEvaluation, params);
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
            BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

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
    public List<UnsolidBlock> getNonSolidMissingBlocks(long cutoffHeight, long maxHeight) throws BlockStoreException {
        List<UnsolidBlock> storedBlockHashes = new ArrayList<UnsolidBlock>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_NONSOLID_MISSINGBLOCKS_SQL);
            preparedStatement.setLong(1, cutoffHeight);
            preparedStatement.setLong(2, maxHeight);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                storedBlockHashes.add(new UnsolidBlock(resultSet.getBytes("hash"), resultSet.getLong("inserttime"),
                        resultSet.getLong("reason"), resultSet.getBytes("missingdependency"),
                        resultSet.getLong("height"), resultSet.getBoolean("directlymissing")));
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
    public List<UTXO> getOutputsHistory(String fromaddress, String toaddress, Long starttime, Long endtime)
            throws BlockStoreException {
        List<UTXO> outputs = new ArrayList<UTXO>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "SELECT hash,outputindex, coinvalue, scriptbytes, toaddress ,"
                    + " addresstargetable, coinbase,blockhash, tokenid, fromaddress, memo, spent, confirmed, "
                    + "spendpending,time,spendpendingtime, minimumsign FROM outputs WHERE  spent=false ";

            if (fromaddress != null && !"".equals(fromaddress.trim())) {
                sql += " AND fromaddress=?";
            }
            if (toaddress != null && !"".equals(toaddress.trim())) {
                sql += " AND toaddress=?";
            }
            if (starttime != null) {
                sql += " AND time>=?";
            }
            if (endtime != null) {
                sql += " AND time<=?";
            }
            preparedStatement = conn.get().prepareStatement(sql);
            int i = 1;
            if (fromaddress != null && !"".equals(fromaddress.trim())) {
                preparedStatement.setString(i++, fromaddress);
            }
            if (toaddress != null && !"".equals(toaddress.trim())) {
                preparedStatement.setString(i++, fromaddress);
            }
            if (starttime != null) {
                preparedStatement.setLong(i++, starttime);
            }
            if (endtime != null) {
                preparedStatement.setLong(i++, starttime);
            }
            ResultSet results = preparedStatement.executeQuery();
            while (results.next()) {
                outputs.add(
                        setUTXO(Sha256Hash.wrap(results.getBytes("hash")), results.getLong("outputindex"), results));

            }
            return outputs;
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
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

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
    public TreeSet<BlockWrap> getBlocksToConfirm(long cutoffHeight, long maxHeight) throws BlockStoreException {
        Comparator<BlockWrap> comparator = Comparator.comparingLong((BlockWrap b) -> b.getBlock().getHeight())
                .thenComparing((BlockWrap b) -> b.getBlock().getHash());
        TreeSet<BlockWrap> storedBlockHashes = new TreeSet<>(comparator);
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_TO_CONFIRM_SQL);
            preparedStatement.setLong(1, cutoffHeight);
            preparedStatement.setLong(2, maxHeight);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    storedBlockHashes.add(new BlockWrap(block, blockEvaluation, params));
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
    public HashSet<BlockEvaluation> getBlocksToUnconfirm() throws BlockStoreException {
        HashSet<BlockEvaluation> storedBlockHashes = new HashSet<BlockEvaluation>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_TO_UNCONFIRM_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

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
    public PriorityQueue<BlockWrap> getSolidBlocksInIntervalDescending(long cutoffHeight, long maxHeight)
            throws BlockStoreException {
        PriorityQueue<BlockWrap> blocksByDescendingHeight = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_SOLID_BLOCKS_IN_INTERVAL_SQL);
            preparedStatement.setLong(1, cutoffHeight);
            preparedStatement.setLong(2, maxHeight);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    blocksByDescendingHeight.add(new BlockWrap(block, blockEvaluation, params));
            }
            return blocksByDescendingHeight;
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
    public List<BlockWrap> getBlocksInMilestoneInterval(long minMilestone, long maxMilestone)
            throws BlockStoreException {
        List<BlockWrap> storedBlockHashes = new ArrayList<>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL);
            preparedStatement.setLong(1, minMilestone);
            preparedStatement.setLong(2, maxMilestone);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    storedBlockHashes.add(new BlockWrap(block, blockEvaluation, params));
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
    public List<BlockWrap> getEntryPoints() throws BlockStoreException {
        long currChainLength = getMaxConfirmedReward().getChainLength();
        long minChainLength = Math.max(0, currChainLength - NetworkParameters.MILESTONE_CUTOFF);
        List<BlockWrap> resultQueue = new ArrayList<BlockWrap>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL);
            preparedStatement.setLong(1, minChainLength);
            preparedStatement.setLong(2, currChainLength);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    resultQueue.add(new BlockWrap(block, blockEvaluation, params));
            }
            return resultQueue;
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
    public PriorityQueue<BlockWrap> getEntryPointsAscending() throws BlockStoreException {
        long currChainLength = getMaxConfirmedReward().getChainLength();
        long minChainLength = Math.max(0, currChainLength - NetworkParameters.MILESTONE_CUTOFF);
        PriorityQueue<BlockWrap> resultQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL);
            preparedStatement.setLong(1, minChainLength);
            preparedStatement.setLong(2, currChainLength);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);

                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));
                if (verifyHeader(block))
                    resultQueue.add(new BlockWrap(block, blockEvaluation, params));
            }
            return resultQueue;
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

    private BlockEvaluation setBlockEvaluation(ResultSet resultSet) throws SQLException {
        BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes("hash")),
                resultSet.getLong("rating"), resultSet.getLong("depth"), resultSet.getLong("cumulativeweight"),
                resultSet.getLong("height"), resultSet.getLong("milestone"), resultSet.getLong("milestonelastupdate"),
                resultSet.getLong("inserttime"), resultSet.getLong("solid"), resultSet.getBoolean("confirmed"));
        return blockEvaluation;
    }

    protected abstract String getUpdateBlockEvaluationCumulativeweightSQL();

    @Override
    public void updateBlockEvaluationCumulativeWeight(Sha256Hash blockhash, long cumulativeweight)
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

    @Override
    public void updateBlockEvaluationWeightAndDepth(List<DepthAndWeight> depthAndWeight) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL);
            for (DepthAndWeight d : depthAndWeight) {
                preparedStatement.setLong(1, d.getWeight());
                preparedStatement.setLong(2, d.getDepth());
                preparedStatement.setBytes(3, d.getBlockHash().getBytes());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
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
    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, long b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationMilestoneSQL());
            preparedStatement.setLong(1, b);
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

    @Override
    public void updateAllConfirmedToMilestone(long milestoneNumber) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_SET_MILESTONE_SQL);
            preparedStatement.setLong(1, milestoneNumber);
            preparedStatement.setLong(2, System.currentTimeMillis());
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
    public void updateMissingBlock(Sha256Hash hash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_SET_MISSING_BLOCK_SQL);
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
    public void updateUnsetMilestone(long milestoneNumber) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_UNSET_MILESTONE_SQL);
            preparedStatement.setLong(1, System.currentTimeMillis());
            preparedStatement.setLong(2, milestoneNumber);
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
    public void updateBlockEvaluationConfirmed(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();

        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_CONFIRMED_SQL);
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

    protected abstract String getUpdateBlockEvaluationRatingSQL();

    @Override
    public void updateBlockEvaluationRating(List<Rating> ratings) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateBlockEvaluationRatingSQL());
            for (Rating r : ratings) {
                preparedStatement.setLong(1, r.getRating());
                preparedStatement.setBytes(2, r.getBlockhash().getBytes());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
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
    public void updateBlockEvaluationSolid(Sha256Hash blockhash, long solid) throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_SOLID_SQL);
            preparedStatement.setLong(1, solid);
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
    public void deleteUnsolid(Sha256Hash blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_UNSOLIDBLOCKS_SQL);
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
    public void deleteOldUnsolid(long height) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_OLD_UNSOLIDBLOCKS_SQL);
            preparedStatement.setLong(1, height);
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
    public HashSet<Block> getUnsolidBlocks(byte[] dep) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        HashSet<Block> resultSet = new HashSet<>();
        try {
            // Since both waiting reasons are hashes, we can simply look for the
            // hashes
            s = conn.get().prepareStatement(SELECT_UNSOLIDBLOCKS_FROM_DEPENDENCY_SQL);
            s.setBytes(1, dep);
            ResultSet results = s.executeQuery();
            while (results.next()) {
                Block block = params.getDefaultSerializer().makeZippedBlock(results.getBytes(1));
                if (verifyHeader(block))
                    resultSet.add(block);
            }
            return resultSet;
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

    public long getHeightTransactions(List<Sha256Hash> txHashs) throws BlockStoreException {

        PreparedStatement s = null;
        maybeConnect();
        long re = 0l;
        try {
            s = conn.get().prepareStatement(SELECT_BLOCKS_SQL);
            for (Sha256Hash hash : txHashs) {
                s.setBytes(1, hash.getBytes());

                ResultSet results = s.executeQuery();
                if (results.next()) {

                    re = Math.max(re, results.getLong("height"));
                }
            }
            return re;
        } catch (SQLException e) {
            throw new BlockStoreException(e);
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
    public void insertUnsolid(Block block, SolidityState solidityState) throws BlockStoreException {
        if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
            return;
        }
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_UNSOLIDBLOCKS_SQL);
            preparedStatement.setBytes(1, block.getHash().getBytes());
            preparedStatement.setBytes(2, Gzip.compress(block.bitcoinSerialize()));
            preparedStatement.setLong(3, block.getTimeSeconds());
            preparedStatement.setLong(4, solidityState.getState().ordinal());

            preparedStatement.setLong(6, block.getHeight());
            switch (solidityState.getState()) {
            case MissingCalculation:
            case MissingPredecessor:
                preparedStatement.setBytes(5, solidityState.getMissingDependency().getBytes());
                preparedStatement.setBoolean(7, solidityState.isDirectlyMissing());
                break;
            case Success:
                throw new RuntimeException("Should not happen");
            case Invalid:
                throw new RuntimeException("Should not happen");
            default:
                throw new RuntimeException("Not Implemented");

            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
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
    public BlockEvaluation getTransactionOutputSpender(Sha256Hash blockHash, Sha256Hash txHash, long index)
            throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_OUTPUT_SPENDER_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setLong(2, index);
            preparedStatement.setBytes(3, blockHash.getBytes());

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);
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
    public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, Sha256Hash prevTxHash, long index, boolean b,
            @Nullable Sha256Hash spenderBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsSpentSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlockHash != null ? spenderBlockHash.getBytes() : null);
            preparedStatement.setBytes(3, prevTxHash.getBytes());
            preparedStatement.setLong(4, index);
            preparedStatement.setBytes(5, prevBlockHash.getBytes());
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
    public void updateTransactionOutputConfirmed(Sha256Hash prevBlockHash, Sha256Hash prevTxHash, long index, boolean b)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsConfirmedSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, prevTxHash.getBytes());
            preparedStatement.setLong(3, index);
            preparedStatement.setBytes(4, prevBlockHash.getBytes());
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
    public void updateAllTransactionOutputsConfirmed(Sha256Hash prevBlockHash, boolean b) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ALL_OUTPUTS_CONFIRMED_SQL);
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, prevBlockHash.getBytes());
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
    public void updateTransactionOutputSpendPending(List<UTXO> utxos) throws BlockStoreException {
        maybeConnect();

        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsSpendPendingSQL());
            for (UTXO u : utxos) {
                preparedStatement.setBoolean(1, true);
                preparedStatement.setLong(2, System.currentTimeMillis());
                preparedStatement.setBytes(3, u.getTxHash().getBytes());
                preparedStatement.setLong(4, u.getIndex());
                preparedStatement.setBytes(5, u.getBlockHash().getBytes());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
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
    public List<Token> getTokensList(Set<String> tokenids) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_CONFIRMED_TOKENS_SQL;
            if (tokenids != null && !tokenids.isEmpty()) {
                sql += "  and tokenid in ( " + buildINList(tokenids) + " )";
            }
            sql += LIMIT_5000;
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {

                Token tokens = new Token();
                setToken(resultSet, tokens);
                list.add(tokens);
            }
            return list;
        } catch (Exception ex) {
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
    public List<Token> getMarketTokenList() throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MARKET_TOKENS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                Token tokens = new Token();
                setToken(resultSet, tokens);
                list.add(tokens);
            }
            return list;
        } catch (Exception ex) {

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

    public Map<String, BigInteger> getTokenAmountMap() throws BlockStoreException {
        Map<String, BigInteger> map = new HashMap<String, BigInteger>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(SELECT_TOKENS_ACOUNT_MAP_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                BigInteger id = map.get(resultSet.getString("tokenid"));
                if (id == null) {
                    map.put(resultSet.getString("tokenid"), new BigInteger(resultSet.getBytes("amount")));
                } else {
                    map.put(resultSet.getString("tokenid"), id.add(new BigInteger(resultSet.getBytes("amount"))));

                }
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
    public List<Token> getTokensListFromDomain(String domainname) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_CONFIRMED_TOKENS_SQL;
            if (domainname != null && !"".equals(domainname.trim())) {
                sql += " AND (domainname = '" + domainname + "' )";
            }
            sql += LIMIT_5000;
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Token tokens = new Token();
                setToken(resultSet, tokens);
                list.add(tokens);
            }
            return list;
        } catch (Exception ex) {

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
    public List<Token> getTokensList(String name) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_CONFIRMED_TOKENS_SQL;
            if (name != null && !"".equals(name.trim())) {
                sql += " AND (tokenname LIKE '%" + name + "%' OR description LIKE '%" + name + "%')";
            }
            sql += LIMIT_5000;
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Token tokens = new Token();
                setToken(resultSet, tokens);
                list.add(tokens);
            }
            return list;
        } catch (Exception ex) {

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

    private void setToken(ResultSet resultSet, Token tokens)
            throws SQLException, JsonParseException, JsonMappingException, IOException {
        tokens.setBlockHash(Sha256Hash.wrap(resultSet.getBytes("blockhash")));
        tokens.setConfirmed(resultSet.getBoolean("confirmed"));
        tokens.setTokenid(resultSet.getString("tokenid"));
        tokens.setTokenindex(resultSet.getInt("tokenindex"));
        tokens.setAmount(new BigInteger(resultSet.getBytes("amount")));
        tokens.setTokenname(resultSet.getString("tokenname"));
        tokens.setDescription(resultSet.getString("description"));

        tokens.setSignnumber(resultSet.getInt("signnumber"));

        tokens.setTokentype(resultSet.getInt("tokentype"));
        tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
        tokens.setDomainName(resultSet.getString("domainname"));
        tokens.setDecimals(resultSet.getInt("decimals"));
        tokens.setRevoked(resultSet.getBoolean("revoked"));
        tokens.setLanguage(resultSet.getString("language"));
        tokens.setClassification(resultSet.getString("classification"));
        tokens.setDomainNameBlockHash(resultSet.getString("domainpredblockhash"));
        byte[] buf = resultSet.getBytes("tokenkeyvalues");
        if (buf != null) {
            tokens.setTokenKeyValues(TokenKeyValues.parse(buf));
        }
    }

    @Override
    public void insertToken(Sha256Hash blockhash, Token token) throws BlockStoreException {
        boolean confirmed = false;
        String tokenid = token.getTokenid();
        long tokenindex = token.getTokenindex();
        String tokenname = token.getTokenname();
        String description = token.getDescription();

        int signnumber = token.getSignnumber();

        int tokentype = token.getTokentype();
        boolean tokenstop = token.isTokenstop();
        Sha256Hash prevblockhash = token.getPrevblockhash();
        byte[] tokenkeyvalues = null;
        if (token.getTokenKeyValues() != null) {
            tokenkeyvalues = token.getTokenKeyValues().toByteArray();
        }
        this.insertToken(blockhash, confirmed, tokenid, tokenindex, token.getAmount(), tokenname, description,
                token.getDomainName(), signnumber, tokentype, tokenstop, prevblockhash, tokenkeyvalues,
                token.getRevoked(), token.getLanguage(), token.getClassification(), token.getDecimals(),
                token.getDomainNameBlockHash());
    }

    public void insertToken(Sha256Hash blockhash, boolean confirmed, String tokenid, long tokenindex, BigInteger amount,
            String tokenname, String description, String domainname, int signnumber, int tokentype, boolean tokenstop,
            Sha256Hash prevblockhash, byte[] tokenkeyvalues, Boolean revoked, String language, String classification,
            int decimals, String domainNameBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(INSERT_TOKENS_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            preparedStatement.setBoolean(2, confirmed);
            preparedStatement.setString(3, tokenid);
            preparedStatement.setLong(4, tokenindex);
            preparedStatement.setBytes(5, amount.toByteArray());
            preparedStatement.setString(6, tokenname);
            preparedStatement.setString(7, description);
            preparedStatement.setString(8, domainname);
            preparedStatement.setInt(9, signnumber);

            preparedStatement.setInt(10, tokentype);
            preparedStatement.setBoolean(11, tokenstop);
            preparedStatement.setBytes(12, prevblockhash == null ? null : prevblockhash.getBytes());
            preparedStatement.setBoolean(13, false);
            preparedStatement.setString(14, null);
            preparedStatement.setBytes(15, tokenkeyvalues);
            preparedStatement.setBoolean(16, revoked);
            preparedStatement.setString(17, language);
            preparedStatement.setString(18, classification);
            preparedStatement.setLong(19, decimals);
            preparedStatement.setString(20, domainNameBlockHash);
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
    public Sha256Hash getTokenPrevblockhash(Sha256Hash blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_PREVBLOCKHASH_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return Sha256Hash.wrap(resultSet.getBytes(1));
            } else {
                return null;
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

    @Override
    public Sha256Hash getTokenSpender(String blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_SPENDER_SQL);
            preparedStatement.setString(1, blockhash);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getBytes(1) == null ? null : Sha256Hash.wrap(resultSet.getBytes(1));
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
    public boolean getTokenSpent(Sha256Hash blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getBoolean(1);
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
    public boolean getTokenConfirmed(Sha256Hash blockHash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_CONFIRMED_SQL);
            preparedStatement.setBytes(1, blockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getBoolean(1);
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
    public boolean getTokenAnyConfirmed(String tokenid, long tokenIndex) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_ANY_CONFIRMED_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenIndex);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
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
    public boolean getTokennameAndDomain(String tokenname, String domainpre) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            String sql = "SELECT confirmed FROM tokens WHERE tokenname = ? AND domainpredblockhash = ?  ";
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, tokenname);
            preparedStatement.setString(2, domainpre);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getBoolean("confirmed");
            } else {
                return false;
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

    @Override
    public BlockWrap getTokenIssuingConfirmedBlock(String tokenid, long tokenIndex) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setLong(2, tokenIndex);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            return getBlockWrap(Sha256Hash.wrap(resultSet.getBytes(1)));
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
    public BlockWrap getDomainIssuingConfirmedBlock(String tokenName, String domainPred, long index)
            throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_DOMAIN_ISSUING_CONFIRMED_BLOCK_SQL);
            preparedStatement.setString(1, tokenName);
            preparedStatement.setString(2, domainPred);
            preparedStatement.setLong(3, index);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            return getBlockWrap(Sha256Hash.wrap(resultSet.getBytes(1)));
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
    public List<String> getDomainDescendantConfirmedBlocks(String domainPred) throws BlockStoreException {
        List<String> storedBlocks = new ArrayList<String>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_DOMAIN_DESCENDANT_CONFIRMED_BLOCKS_SQL);
            preparedStatement.setString(1, domainPred);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                storedBlocks.add(Utils.HEX.encode(resultSet.getBytes("blockhash")));
            }
            return storedBlocks;
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
    public void updateTokenSpent(Sha256Hash blockhash, boolean b, Sha256Hash spenderBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(UPDATE_TOKEN_SPENT_SQL);
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlockHash == null ? null : spenderBlockHash.getBytes());
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

    @Override
    public void updateTokenConfirmed(Sha256Hash blockHash, boolean confirmed) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(UPDATE_TOKEN_CONFIRMED_SQL);
            preparedStatement.setBoolean(1, confirmed);
            preparedStatement.setBytes(2, blockHash.getBytes());
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
    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address) throws BlockStoreException {
        if (address.isEmpty()) {
            return new ArrayList<BlockEvaluation>();
        }
        String sql = "SELECT blocks.* FROM outputs LEFT JOIN blocks ON outputs.blockhash = blocks.blockhash WHERE outputs.toaddress in ";
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
                BlockEvaluation blockEvaluation = setBlockEvaluation(resultSet);
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
    public List<BlockEvaluationDisplay> getSearchBlockEvaluations(List<String> address, String lastestAmount,
            long height, long maxblocks) throws BlockStoreException {

        String sql = "";
        StringBuffer stringBuffer = new StringBuffer();
        if (!"0".equalsIgnoreCase(lastestAmount) && !"".equalsIgnoreCase(lastestAmount)) {
            sql += "SELECT hash, rating, depth, cumulativeweight, "
                    + " height, milestone, milestonelastupdate,  inserttime,  blocktype, solid, confirmed "
                    + "  FROM  blocks ";
            sql += " where height >= " + height;
            sql += " ORDER BY insertTime desc ";
            Long a = Long.valueOf(lastestAmount);
            if (a > maxblocks) {
                a = maxblocks;
            }
            sql += " LIMIT " + a;
        } else {
            sql += "SELECT blocks.hash, rating, depth, cumulativeweight, "
                    + " blocks.height, milestone, milestonelastupdate,  inserttime,  blocktype, solid, blocks.confirmed"
                    + " FROM outputs JOIN blocks " + "ON outputs.blockhash = blocks.hash  ";
            sql += " where height >= " + height;
            sql += " and  outputs.toaddress in ";
            for (String str : address)
                stringBuffer.append(",").append("'" + str + "'");
            sql += "(" + stringBuffer.substring(1).toString() + ")";

            sql += " ORDER BY insertTime desc ";
        }
        List<BlockEvaluationDisplay> result = new ArrayList<BlockEvaluationDisplay>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluationDisplay blockEvaluation = BlockEvaluationDisplay.build(
                        Sha256Hash.wrap(resultSet.getBytes("hash")), resultSet.getLong("rating"),
                        resultSet.getLong("depth"), resultSet.getLong("cumulativeweight"), resultSet.getLong("height"),
                        resultSet.getLong("milestone"), resultSet.getLong("milestonelastupdate"),
                        resultSet.getLong("inserttime"), resultSet.getInt("blocktype"), resultSet.getLong("solid"),
                        resultSet.getBoolean("confirmed"));
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
    public List<BlockEvaluationDisplay> getSearchBlockEvaluationsByhashs(List<String> blockhashs)
            throws BlockStoreException {

        List<BlockEvaluationDisplay> result = new ArrayList<BlockEvaluationDisplay>();
        if (blockhashs == null || blockhashs.isEmpty()) {
            return result;
        }
        String sql = "";

        sql += "SELECT hash, rating, depth, cumulativeweight, "
                + " height, milestone, milestonelastupdate,  inserttime,  blocktype, solid, confirmed "
                + "  FROM  blocks WHERE hash = ? ";

        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            for (String hash : blockhashs) {

                preparedStatement = conn.get().prepareStatement(sql);
                preparedStatement.setBytes(1, Utils.HEX.decode(hash));
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    BlockEvaluationDisplay blockEvaluation = BlockEvaluationDisplay.build(
                            Sha256Hash.wrap(resultSet.getBytes(1)), resultSet.getLong(2), resultSet.getLong(3),
                            resultSet.getLong(4), resultSet.getLong(5), resultSet.getLong(6), resultSet.getLong(7),
                            resultSet.getLong(8), resultSet.getInt(9), resultSet.getLong("solid"),
                            resultSet.getBoolean("confirmed"));
                    result.add(blockEvaluation);
                }
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
    public List<MultiSignAddress> getMultiSignAddressListByTokenidAndBlockHashHex(String tokenid,
            Sha256Hash prevblockhash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<MultiSignAddress> list = new ArrayList<MultiSignAddress>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setBytes(2, prevblockhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String tokenid0 = resultSet.getString("tokenid");
                String address = resultSet.getString("address");
                String pubKeyHex = resultSet.getString("pubKeyHex");
                MultiSignAddress multiSignAddress = new MultiSignAddress(tokenid0, address, pubKeyHex);
                int posIndex = resultSet.getInt("posIndex");
                multiSignAddress.setPosIndex(posIndex);
                int tokenHolder = resultSet.getInt("tokenHolder");
                multiSignAddress.setTokenHolder(tokenHolder);
                // TODO if(multiSignAddress.getTokenHolder() > 0)
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
            preparedStatement.setInt(4, multiSignAddress.getPosIndex());
            preparedStatement.setBytes(5, multiSignAddress.getBlockhash().getBytes());
            preparedStatement.setInt(6, multiSignAddress.getTokenHolder());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
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
    public Token getCalMaxTokenIndex(String tokenid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(COUNT_TOKENSINDEX_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            Token tokens = new Token();
            if (resultSet.next()) {
                tokens.setBlockHash(Sha256Hash.wrap(resultSet.getBytes("blockhash")));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                return tokens;
            } else {
                // tokens.setBlockhash("");
                tokens.setTokenindex(-1);
            }
            return tokens;
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
    public Token getTokenByBlockHash(Sha256Hash blockhash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_SQL);
            preparedStatement.setBytes(1, blockhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            Token tokens = null;
            if (resultSet.next()) {
                tokens = new Token();
                setToken(resultSet, tokens);
            }
            return tokens;
        } catch (Exception ex) {
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
    public List<Token> getTokenID(String tokenid) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKENID_SQL);
            preparedStatement.setString(1, tokenid);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Token tokens = new Token();
                setToken(resultSet, tokens);
                list.add(tokens);
            }
            return list;
        } catch (Exception ex) {

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
    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGN_ADDRESS_SQL);
            preparedStatement.setString(1, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                setMultisign(list, resultSet);
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

    private void setMultisign(List<MultiSign> list, ResultSet resultSet) throws SQLException {
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
        multiSign.setBlockbytes(blockhash);
        multiSign.setSign(sign);

        list.add(multiSign);
    }

    public List<MultiSign> getMultiSignListByTokenidAndAddress(final String tokenid, String address)
            throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGN_TOKENID_ADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, address);

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                setMultisign(list, resultSet);
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
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, int tokenindex, Set<String> addresses,
            boolean isSign) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        String sql = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE 1 = 1 ";
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND address IN( " + buildINList(addresses) + " ) ";
        }
        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND tokenid=? AND tokenindex = ? ";
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
                preparedStatement.setInt(1, tokenindex);
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                setMultisign(list, resultSet);
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

    private String buildINList(Collection<String> datalist) {
        if (datalist == null || datalist.isEmpty())
            return "";
        StringBuffer stringBuffer = new StringBuffer();
        for (String str : datalist)
            stringBuffer.append(",").append("'" + str + "'");
        return stringBuffer.substring(1).toString();
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

        if (multiSign.getTokenid() == null || "".equals(multiSign.getTokenid())) {
            return;
        }

        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MULTISIGN_SQL);
            preparedStatement.setString(1, multiSign.getTokenid());
            preparedStatement.setLong(2, multiSign.getTokenindex());
            preparedStatement.setString(3, multiSign.getAddress());
            preparedStatement.setBytes(4, multiSign.getBlockbytes());
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
    public void updateMultiSign(String tokenid, long tokenIndex, String address, byte[] blockhash, int sign)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_MULTISIGN_SQL);
            preparedStatement.setBytes(1, blockhash);
            preparedStatement.setInt(2, sign);
            preparedStatement.setString(3, tokenid);
            preparedStatement.setLong(4, tokenIndex);
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
                setMultisign(list, resultSet);
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
    public boolean getRewardSpent(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_SPENT_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
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
    public Sha256Hash getRewardSpender(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_SPENDER_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getBytes(1) == null ? null : Sha256Hash.wrap(resultSet.getBytes(1));
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
    public Sha256Hash getRewardPrevBlockHash(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_PREVBLOCKHASH_SQL);
            preparedStatement.setBytes(1, blockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return Sha256Hash.wrap(resultSet.getBytes(1));
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
    public long getRewardDifficulty(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_DIFFICULTY_SQL);
            preparedStatement.setBytes(1, blockHash.getBytes());
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
    public long getRewardChainLength(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_CHAINLENGTH_SQL);
            preparedStatement.setBytes(1, blockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong(1);
            } else {
                return -1;
            }

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
    public boolean getRewardConfirmed(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_CONFIRMED_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
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
    public void insertReward(Sha256Hash hash, Sha256Hash prevBlockHash, long difficulty, long chainLength)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TX_REWARD_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            preparedStatement.setBoolean(2, false);
            preparedStatement.setBoolean(3, false);
            preparedStatement.setBytes(4, null);
            preparedStatement.setBytes(5, prevBlockHash.getBytes());
            preparedStatement.setLong(6, difficulty);
            preparedStatement.setLong(7, chainLength);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
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
    public void updateRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException {
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
    public void updateRewardSpent(Sha256Hash hash, boolean b, @Nullable Sha256Hash spenderBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_TX_REWARD_SPENT_SQL);
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlockHash == null ? null : spenderBlockHash.getBytes());
            preparedStatement.setBytes(3, hash.getBytes());
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
    public TXReward getRewardConfirmedAtHeight(long chainlength) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_CONFIRMED_AT_HEIGHT_REWARD_SQL);
            preparedStatement.setLong(1, chainlength);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return setReward(resultSet);
            } else
                return null;

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
    public TXReward getMaxConfirmedReward() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {

                return setReward(resultSet);
            } else
                return null;

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
    public TXReward getMaxSolidReward() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_MAX_SOLID_REWARD_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {

                return setReward(resultSet);
            } else
                return null;

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
    public List<TXReward> getAllConfirmedReward() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_ALL_CONFIRMED_REWARD_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<TXReward> list = new ArrayList<TXReward>();
            while (resultSet.next()) {
                list.add(setReward(resultSet));
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

    private TXReward setReward(ResultSet resultSet) throws SQLException {
        return new TXReward(Sha256Hash.wrap(resultSet.getBytes("blockhash")), resultSet.getBoolean("confirmed"),
                resultSet.getBoolean("spent"), Sha256Hash.wrap(resultSet.getBytes("prevblockhash")),
                Sha256Hash.wrap(resultSet.getBytes("spenderblockhash")), resultSet.getLong("difficulty"),
                resultSet.getLong("chainlength"));
    }

    @Override
    public List<Sha256Hash> getRewardBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_REWARD_WHERE_PREV_HASH_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            List<Sha256Hash> list = new ArrayList<Sha256Hash>();
            while (resultSet.next()) {
                list.add(Sha256Hash.wrap(resultSet.getBytes(1)));
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
    public void insertOutputsMulti(OutputsMulti outputsMulti) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_OUTPUTSMULTI_SQL);
            preparedStatement.setBytes(1, outputsMulti.getHash().getBytes());
            preparedStatement.setString(2, outputsMulti.getToAddress());
            preparedStatement.setLong(3, outputsMulti.getOutputIndex());

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
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

    public List<OutputsMulti> queryOutputsMultiByHashAndIndex(byte[] hash, long index) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<OutputsMulti> list = new ArrayList<OutputsMulti>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_OUTPUTSMULTI_SQL);
            preparedStatement.setBytes(1, hash);
            preparedStatement.setLong(2, index);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Sha256Hash sha256Hash = Sha256Hash.of(resultSet.getBytes("hash"));
                String address = resultSet.getString("toaddress");
                long outputindex = resultSet.getLong("outputindex");

                OutputsMulti outputsMulti = new OutputsMulti(sha256Hash, address, outputindex);
                list.add(outputsMulti);
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
    public UserData queryUserDataWithPubKeyAndDataclassname(String dataclassname, String pubKey)
            throws BlockStoreException {
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
            Sha256Hash blockhash = resultSet.getBytes("blockhash") != null
                    ? Sha256Hash.wrap(resultSet.getBytes("blockhash"))
                    : null;
            userData.setBlockhash(blockhash);
            userData.setData(resultSet.getBytes("data"));
            userData.setDataclassname(resultSet.getString("dataclassname"));
            userData.setPubKey(resultSet.getString("pubKey"));
            userData.setBlocktype(resultSet.getLong("blocktype"));
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
            preparedStatement.setLong(5, userData.getBlocktype());
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
    public List<UserData> getUserDataListWithBlocktypePubKeyList(int blocktype, List<String> pubKeyList)
            throws BlockStoreException {
        if (pubKeyList.isEmpty()) {
            return new ArrayList<UserData>();
        }
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "select blockhash, dataclassname, data, pubKey, blocktype from userdata where blocktype = ? and pubKey in ";
            StringBuffer stringBuffer = new StringBuffer();
            for (String str : pubKeyList)
                stringBuffer.append(",'").append(str).append("'");
            sql += "(" + stringBuffer.substring(1) + ")";

            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setLong(1, blocktype);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<UserData> list = new ArrayList<UserData>();
            while (resultSet.next()) {
                UserData userData = new UserData();
                Sha256Hash blockhash = resultSet.getBytes("blockhash") != null
                        ? Sha256Hash.wrap(resultSet.getBytes("blockhash"))
                        : null;
                userData.setBlockhash(blockhash);
                userData.setData(resultSet.getBytes("data"));
                userData.setDataclassname(resultSet.getString("dataclassname"));
                userData.setPubKey(resultSet.getString("pubKey"));
                userData.setBlocktype(resultSet.getLong("blocktype"));
                list.add(userData);
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
        String sql = "insert into paymultisign (orderid, tokenid, toaddress, blockhash, amount, minsignnumber,"
                + " outputHashHex,  outputindex) values (?, ?, ?, ?, ?, ?, ?,?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, payMultiSign.getOrderid());
            preparedStatement.setString(2, payMultiSign.getTokenid());
            preparedStatement.setString(3, payMultiSign.getToaddress());
            preparedStatement.setBytes(4, payMultiSign.getBlockhash());
            preparedStatement.setBytes(5, payMultiSign.getAmount().toByteArray());
            preparedStatement.setLong(6, payMultiSign.getMinsignnumber());
            preparedStatement.setString(7, payMultiSign.getOutputHashHex());
            preparedStatement.setLong(8, payMultiSign.getOutputindex());
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
        String sql = "insert into paymultisignaddress (orderid, pubKey, sign, signInputData, signIndex) values (?, ?, ?, ?, ?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, payMultiSignAddress.getOrderid());
            preparedStatement.setString(2, payMultiSignAddress.getPubKey());
            preparedStatement.setInt(3, payMultiSignAddress.getSign());
            preparedStatement.setBytes(4, payMultiSignAddress.getSignInputData());
            preparedStatement.setInt(5, payMultiSignAddress.getSignIndex());
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
    public void updatePayMultiSignAddressSign(String orderid, String pubKey, int sign, byte[] signInputData)
            throws BlockStoreException {
        String sql = "update paymultisignaddress set sign = ?, signInputData = ? where orderid = ? and pubKey = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setInt(1, sign);
            preparedStatement.setBytes(2, signInputData);
            preparedStatement.setString(3, orderid);
            preparedStatement.setString(4, pubKey);
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
    public int getMaxPayMultiSignAddressSignIndex(String orderid) throws BlockStoreException {
        String sql = "SELECT MAX(signIndex) AS signIndex FROM paymultisignaddress WHERE orderid = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, orderid);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getInt(1);
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
    public PayMultiSign getPayMultiSignWithOrderid(String orderid) throws BlockStoreException {
        String sql = "select orderid, tokenid, toaddress, blockhash, amount, minsignnumber, outputHashHex, outputindex from paymultisign where orderid = ?";
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
            payMultiSign.setAmount(new BigInteger(resultSet.getBytes("amount")));
            payMultiSign.setBlockhash(resultSet.getBytes("blockhash"));
            payMultiSign.setMinsignnumber(resultSet.getLong("minsignnumber"));
            payMultiSign.setOrderid(resultSet.getString("orderid"));
            payMultiSign.setToaddress(resultSet.getString("toaddress"));
            payMultiSign.setTokenid(resultSet.getString("tokenid"));
            payMultiSign.setBlockhashHex(Utils.HEX.encode(payMultiSign.getBlockhash()));
            payMultiSign.setOutputHashHex(resultSet.getString("outputHashHex"));
            payMultiSign.setOutputindex(resultSet.getLong("outputindex"));
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
        String sql = "select orderid, pubKey, sign, signInputData, signIndex from paymultisignaddress where orderid = ? ORDER BY signIndex ASC";
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
                payMultiSignAddress.setSignInputData(resultSet.getBytes("signInputData"));
                payMultiSignAddress.setSignIndex(resultSet.getInt("signIndex"));
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

    @Override
    public List<PayMultiSign> getPayMultiSignList(List<String> pubKeys) throws BlockStoreException {
        if (pubKeys.isEmpty()) {
            return new ArrayList<PayMultiSign>();
        }
        String sql = "SELECT paymultisign.orderid, tokenid, toaddress, blockhash, amount, minsignnumber, outputHashHex,"
                + "outputindex, sign,(select count(1) from  paymultisignaddress t where t.orderid=paymultisign.orderid AND sign!=0) as signcount "
                + " FROM paymultisign LEFT JOIN paymultisignaddress ON paymultisign.orderid = paymultisignaddress.orderid "
                + " WHERE paymultisignaddress.pubKey ";
        StringBuffer stringBuffer = new StringBuffer();
        for (String pubKey : pubKeys)
            stringBuffer.append(",'").append(pubKey).append("'");
        sql += " in (" + stringBuffer.substring(1) + ")";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<PayMultiSign> list = new ArrayList<PayMultiSign>();
            while (resultSet.next()) {
                PayMultiSign payMultiSign = new PayMultiSign();
                payMultiSign.setAmount(new BigInteger(resultSet.getBytes("amount")));
                payMultiSign.setBlockhash(resultSet.getBytes("blockhash"));
                payMultiSign.setMinsignnumber(resultSet.getLong("minsignnumber"));
                payMultiSign.setOrderid(resultSet.getString("orderid"));
                payMultiSign.setToaddress(resultSet.getString("toaddress"));
                payMultiSign.setTokenid(resultSet.getString("tokenid"));
                payMultiSign.setBlockhashHex(Utils.HEX.encode(payMultiSign.getBlockhash()));
                payMultiSign.setOutputHashHex(resultSet.getString("outputHashHex"));
                payMultiSign.setOutputindex(resultSet.getLong("outputindex"));
                payMultiSign.setSign(resultSet.getInt("sign"));
                payMultiSign.setSigncount(resultSet.getInt("signcount"));
                list.add(payMultiSign);
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
    public int getCountPayMultiSignAddressStatus(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(
                    "select count(*) as count from paymultisignaddress where orderid = ? and sign = 1");
            preparedStatement.setString(1, orderid);
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
    public UTXO getOutputsWithHexStr(byte[] hash, long outputindex) throws BlockStoreException {
        String sql = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
                + " addresstargetable, blockhash, tokenid, fromaddress, memo, minimumsign, time, spent, confirmed, "
                + " spendpending, spendpendingtime FROM outputs WHERE hash = ? and outputindex = ?";

        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setBytes(1, hash);
            preparedStatement.setLong(2, outputindex);
            ResultSet results = preparedStatement.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            Coin amount = new Coin(new BigInteger(results.getBytes("coinvalue")), results.getString("tokenid"));
            byte[] scriptBytes = results.getBytes("scriptbytes");
            boolean coinbase = results.getBoolean("coinbase");
            String address = results.getString("toaddress");
            Sha256Hash blockhash = Sha256Hash.wrap(results.getBytes("blockhash"));

            String fromaddress = results.getString("fromaddress");
            String memo = results.getString("memo");
            boolean spent = results.getBoolean("spent");
            boolean confirmed = results.getBoolean("confirmed");
            boolean spendPending = results.getBoolean("spendpending");
            String tokenid = results.getString("tokenid");

            // long outputindex = results.getLong("outputindex");

            UTXO utxo = new UTXO(Sha256Hash.wrap(hash), outputindex, amount, coinbase, new Script(scriptBytes), address,
                    blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, 0,
                    results.getLong("spendpendingtime"), results.getLong("time"));
            return utxo;
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
    public List<VOSExecute> getVOSExecuteList(String vosKey) throws BlockStoreException {
        String sql = "SELECT vosKey, pubKey, execute, data, startDate, endDate FROM vosexecute WHERE vosKey = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, vosKey);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<VOSExecute> list = new ArrayList<VOSExecute>();
            while (resultSet.next()) {
                VOSExecute vosExecute = new VOSExecute();
                vosExecute.setVosKey(resultSet.getString("vosKey"));
                vosExecute.setPubKey(resultSet.getString("pubKey"));
                vosExecute.setExecute(resultSet.getLong("execute"));
                vosExecute.setData(resultSet.getBytes("data"));
                vosExecute.setStartDate(resultSet.getDate("startDate"));
                vosExecute.setEndDate(resultSet.getDate("endDate"));
                list.add(vosExecute);
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
    public VOSExecute getVOSExecuteWith(String vosKey, String pubKey) throws BlockStoreException {
        String sql = "SELECT vosKey, pubKey, execute, data, startDate, endDate FROM vosexecute WHERE vosKey = ? AND pubKey = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, vosKey);
            preparedStatement.setString(2, pubKey);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            VOSExecute vosExecute = new VOSExecute();
            vosExecute.setVosKey(resultSet.getString("vosKey"));
            vosExecute.setPubKey(resultSet.getString("pubKey"));
            vosExecute.setExecute(resultSet.getLong("execute"));
            vosExecute.setData(resultSet.getBytes("data"));
            vosExecute.setStartDate(resultSet.getDate("startDate"));
            vosExecute.setEndDate(resultSet.getDate("endDate"));
            return vosExecute;
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
    public void insertVOSExecute(VOSExecute vosExecute) throws BlockStoreException {
        String sql = "INSERT INTO vosexecute (vosKey, pubKey, execute, data, startDate, endDate) VALUES (?, ?, ?, ?, ?, ?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, vosExecute.getVosKey());
            preparedStatement.setString(2, vosExecute.getPubKey());
            preparedStatement.setLong(3, vosExecute.getExecute());
            preparedStatement.setBytes(4, vosExecute.getData());
            preparedStatement.setTimestamp(5, new java.sql.Timestamp(vosExecute.getStartDate().getTime()));
            preparedStatement.setTimestamp(6, new java.sql.Timestamp(vosExecute.getEndDate().getTime()));
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
    public void updateVOSExecute(VOSExecute vosExecute) throws BlockStoreException {
        String sql = "UPDATE vosexecute SET execute = ?, data = ?, startDate = ?, endDate = ? WHERE vosKey = ? AND pubKey = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setLong(1, vosExecute.getExecute());
            preparedStatement.setBytes(2, vosExecute.getData());
            preparedStatement.setTimestamp(3, new java.sql.Timestamp(vosExecute.getStartDate().getTime()));
            preparedStatement.setTimestamp(4, new java.sql.Timestamp(vosExecute.getEndDate().getTime()));

            preparedStatement.setString(5, vosExecute.getVosKey());
            preparedStatement.setString(6, vosExecute.getPubKey());
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
    public byte[] getSettingValue(String name) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(getSelectSettingsSQL());
            preparedStatement.setString(1, name);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getBytes(1);
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
    public void insertBatchBlock(Block block) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_BATCHBLOCK_SQL);
            preparedStatement.setBytes(1, block.getHash().getBytes());
            preparedStatement.setBytes(2, Gzip.compress(block.bitcoinSerialize()));
            preparedStatement.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
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
    public void deleteBatchBlock(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_BATCHBLOCK_SQL);
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
    public List<BatchBlock> getBatchBlockList() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BATCHBLOCK_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<BatchBlock> list = new ArrayList<BatchBlock>();
            while (resultSet.next()) {
                BatchBlock batchBlock = new BatchBlock();
                batchBlock.setHash(Sha256Hash.wrap(resultSet.getBytes("hash")));
                batchBlock.setBlock(Gzip.decompress(resultSet.getBytes("block")));
                batchBlock.setInsertTime(resultSet.getDate("inserttime"));
                list.add(batchBlock);
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
    public void insertSubtanglePermission(String pubkey, String userdatapubkey, String status)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_SUBTANGLE_PERMISSION_SQL);
            preparedStatement.setString(1, pubkey);
            preparedStatement.setString(2, userdatapubkey);
            preparedStatement.setString(3, status);
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
    public void deleteSubtanglePermission(String pubkey) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_SUBTANGLE_PERMISSION_SQL);
            preparedStatement.setString(1, pubkey);
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
    public void updateSubtanglePermission(String pubkey, String userdataPubkey, String status)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPATE_ALL_SUBTANGLE_PERMISSION_SQL);
            preparedStatement.setString(1, status);
            preparedStatement.setString(2, userdataPubkey);
            preparedStatement.setString(3, pubkey);
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
    public List<Map<String, String>> getAllSubtanglePermissionList() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ALL_SUBTANGLE_PERMISSION_SQL);

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("pubkey", resultSet.getString("pubkey"));
                map.put("userdataPubkey", resultSet.getString("userdataPubkey"));
                map.put("status", resultSet.getString("status"));
                list.add(map);
            }
            return list;
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
    public List<Map<String, String>> getSubtanglePermissionListByPubkey(String pubkey) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ALL_SUBTANGLE_PERMISSION_SQL);
            preparedStatement.setString(1, pubkey);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("pubkey", resultSet.getString("pubkey"));
                map.put("userdataPubkey", resultSet.getString("userdataPubkey"));
                map.put("status", resultSet.getString("status"));
                list.add(map);
            }
            return list;
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
    public List<Map<String, String>> getSubtanglePermissionListByPubkeys(List<String> pubkeys)
            throws BlockStoreException {
        String sql = SELECT_SUBTANGLE_PERMISSION_BY_PUBKEYS_SQL + " AND pubkey ";
        StringBuffer stringBuffer = new StringBuffer();
        for (String pubKey : pubkeys)
            stringBuffer.append(",'").append(pubKey).append("'");
        sql += " in (" + stringBuffer.substring(1) + ")";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("pubkey", resultSet.getString("pubkey"));
                map.put("userdataPubkey", resultSet.getString("userdataPubkey"));
                map.put("status", resultSet.getString("status"));
                list.add(map);
            }
            return list;
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
    public boolean getOrderConfirmed(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_CONFIRMED_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setBytes(2, issuingMatcherBlockHash.getBytes());
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
    public Sha256Hash getOrderSpender(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_SPENDER_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setBytes(2, issuingMatcherBlockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getBytes(1) == null ? null : Sha256Hash.wrap(resultSet.getBytes(1));
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
    public boolean getOrderSpent(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_SPENT_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setBytes(2, issuingMatcherBlockHash.getBytes());
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
    public HashMap<Sha256Hash, OrderRecord> getOrderMatchingIssuedOrders(Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        HashMap<Sha256Hash, OrderRecord> result = new HashMap<>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDERS_BY_ISSUER_SQL);
            preparedStatement.setBytes(1, issuingMatcherBlockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                result.put(Sha256Hash.wrap(resultSet.getBytes(1)), setOrder(resultSet));
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
    public OrderRecord getOrder(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setBytes(2, issuingMatcherBlockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return null;

            return setOrder(resultSet);
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
    public void insertCancelOrder(OrderCancel orderCancel) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_OrderCancel_SQL);
            preparedStatement.setBytes(1, orderCancel.getBlockHash().getBytes());
            preparedStatement.setBytes(2, orderCancel.getOrderBlockHash().getBytes());
        } catch (SQLException e) {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
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
    public void insertOrder(Collection<OrderRecord> records) throws BlockStoreException {
        if (records == null)
            return;

        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_ORDER_SQL);
            for (OrderRecord record : records) {
                preparedStatement.setBytes(1, record.getBlockHash().getBytes());
                preparedStatement.setBytes(2, record.getIssuingMatcherBlockHash().getBytes());
                preparedStatement.setLong(3, record.getOfferValue());
                preparedStatement.setString(4, record.getOfferTokenid());
                preparedStatement.setBoolean(5, record.isConfirmed());
                preparedStatement.setBoolean(6, record.isSpent());
                preparedStatement.setBytes(7,
                        record.getSpenderBlockHash() != null ? record.getSpenderBlockHash().getBytes() : null);
                preparedStatement.setLong(8, record.getTargetValue());
                preparedStatement.setString(9, record.getTargetTokenid());
                preparedStatement.setBytes(10, record.getBeneficiaryPubKey());
                preparedStatement.setLong(11, record.getValidToTime());
                preparedStatement.setLong(12, record.getValidFromTime());
                preparedStatement.setString(13, record.getSide() == null ? null : record.getSide().name());
                preparedStatement.setString(14, record.getBeneficiaryAddress());

                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();

        } catch (SQLException e) {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
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
    public void updateOrderConfirmed(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, boolean confirmed)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_CONFIRMED_SQL);
            preparedStatement.setBoolean(1, confirmed);
            preparedStatement.setBytes(2, initialBlockHash.getBytes());
            preparedStatement.setBytes(3, issuingMatcherBlockHash.getBytes());
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
    public void updateOrderConfirmed(Collection<OrderRecord> orderRecords, boolean confirm) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_CONFIRMED_SQL);
            for (OrderRecord o : orderRecords) {
                preparedStatement.setBoolean(1, confirm);
                preparedStatement.setBytes(2, o.getBlockHash().getBytes());
                preparedStatement.setBytes(3, o.getIssuingMatcherBlockHash().getBytes());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
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
    public void updateOrderSpent(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, boolean spent,
            Sha256Hash spenderBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_SPENT_SQL);
            preparedStatement.setBoolean(1, spent);
            preparedStatement.setBytes(2, spenderBlockHash != null ? spenderBlockHash.getBytes() : null);
            preparedStatement.setBytes(3, initialBlockHash.getBytes());
            preparedStatement.setBytes(4, issuingMatcherBlockHash.getBytes());
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
    public void updateOrderSpent(Set<OrderRecord> orderRecords) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_SPENT_SQL);
            for (OrderRecord o : orderRecords) {
                preparedStatement.setBoolean(1, o.isSpent());
                preparedStatement.setBytes(2,
                        o.getSpenderBlockHash() != null ? o.getSpenderBlockHash().getBytes() : null);
                preparedStatement.setBytes(3, o.getBlockHash().getBytes());
                preparedStatement.setBytes(4, o.getIssuingMatcherBlockHash().getBytes());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
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
    public List<OrderRecord> getAllOrdersSorted() throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_ORDERS_SORTED_SQL);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<OrderRecord> getAllOpenOrdersSorted(List<String> addresses, String tokenid) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        String sql = SELECT_OPEN_ORDERS_SORTED_SQL;
        String orderby = " ORDER BY blockhash, collectinghash";

        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND (offertokenid=? or targettokenid=?)";
        }
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND beneficiaryaddress in (";

            sql += buildINList(addresses) + ")";
        }
        sql += orderby;
        PreparedStatement s = null;
        try {
            // log.debug(sql);
            s = conn.get().prepareStatement(sql);
            int i = 1;

            if (tokenid != null && !tokenid.trim().isEmpty()) {
                s.setString(i++, tokenid);
                s.setString(i++, tokenid);
            }
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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

    private OrderRecord setOrder(ResultSet resultSet) throws SQLException {
        return new OrderRecord(Sha256Hash.wrap(resultSet.getBytes("blockhash")),
                Sha256Hash.wrap(resultSet.getBytes("collectinghash")), resultSet.getLong("offercoinvalue"),
                resultSet.getString("offertokenid"), resultSet.getBoolean("confirmed"), resultSet.getBoolean("spent"),
                resultSet.getBytes("spenderblockhash") == null ? null
                        : Sha256Hash.wrap(resultSet.getBytes("spenderblockhash")),
                resultSet.getLong("targetcoinvalue"), resultSet.getString("targetTokenid"),
                resultSet.getBytes("beneficiarypubkey"), resultSet.getLong("validToTime"),
                resultSet.getLong("validFromTime"), resultSet.getString("side"),
                resultSet.getString("beneficiaryaddress"));
    }

    @Override
    public List<OrderRecord> getBestOpenSellOrders(String tokenId, int count) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_BEST_OPEN_SELL_ORDERS_SORTED_SQL);
            s.setString(1, tokenId);
            s.setInt(2, count);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<OrderRecord> getBestOpenBuyOrders(String tokenId, int count) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_BEST_OPEN_BUY_ORDERS_SORTED_SQL);
            s.setString(1, tokenId);
            s.setInt(2, count);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<OrderRecord> getMyClosedOrders(List<String> addresses) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        if (addresses == null || addresses.isEmpty())
            return new ArrayList<OrderRecord>();

        maybeConnect();
        PreparedStatement s = null;
        try {

            String myaddress = " in (" + buildINList(addresses) + ")";

            String sql = "SELECT " + ORDER_TEMPLATE + " FROM orders "
                    + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress" + myaddress + " AND collectinghash="
                    + OPENORDERHASH + " AND blockhash NOT IN ( SELECT blockhash FROM orders "
                    + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress" + myaddress + ")";

            s = conn.get().prepareStatement(sql);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<OrderRecord> getMyRemainingOpenOrders(String address) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_MY_REMAINING_OPEN_ORDERS_SQL);
            s.setString(1, address);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<OrderRecord> getMyInitialOpenOrders(String address) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_MY_INITIAL_OPEN_ORDERS_SQL + LIMIT_5000);
            s.setString(1, address);
            s.setString(2, address);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = setOrder(resultSet);
                result.add(order);
            }
            return result;
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
    public List<UTXO> getAllAvailableUTXOsSorted() throws BlockStoreException {
        List<UTXO> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_AVAILABLE_UTXOS_SORTED_SQL);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                // Parse it.
                Coin amount = new Coin(new BigInteger(resultSet.getBytes("coinvalue")), resultSet.getString("tokenid"));

                byte[] scriptBytes = resultSet.getBytes(2);
                boolean coinbase = resultSet.getBoolean(3);
                String address = resultSet.getString(4);
                Sha256Hash blockhash = resultSet.getBytes(6) != null ? Sha256Hash.wrap(resultSet.getBytes(6)) : null;

                String fromaddress = resultSet.getString(8);
                String memo = resultSet.getString(9);
                boolean spent = resultSet.getBoolean(10);
                boolean confirmed = resultSet.getBoolean(11);
                boolean spendPending = resultSet.getBoolean(12);
                String tokenid = resultSet.getString("tokenid");
                byte[] hash = resultSet.getBytes("hash");
                long index = resultSet.getLong("outputindex");
                UTXO txout = new UTXO(Sha256Hash.wrap(hash), index, amount, coinbase, new Script(scriptBytes), address,
                        blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, 0,
                        resultSet.getLong("spendpendingtime"), resultSet.getLong("time"));
                result.add(txout);
            }
            return result;
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
    public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get()
                    .prepareStatement(" insert into myserverblocks (prevhash, hash, inserttime) values (?,?,?) ");
            preparedStatement.setBytes(1, prevhash.getBytes());
            preparedStatement.setBytes(2, hash.getBytes());
            preparedStatement.setLong(3, inserttime);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
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
    public void deleteMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {
        // delete only one, but anyone
        maybeConnect();
        PreparedStatement preparedStatement = null;
        PreparedStatement p2 = null;
        try {
            preparedStatement = conn.get().prepareStatement(" select hash from myserverblocks where prevhash = ?");
            preparedStatement.setBytes(1, prevhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                byte[] hash = resultSet.getBytes(1);
                p2 = conn.get().prepareStatement(" delete  from  myserverblocks  where prevhash = ?  and hash =?");
                p2.setBytes(1, prevhash.getBytes());
                p2.setBytes(2, hash);
                p2.executeUpdate();
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
            if (p2 != null) {
                try {
                    p2.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }

    }

    @Override
    public boolean existMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {

        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(" select hash from myserverblocks where prevhash = ?");
            preparedStatement.setBytes(1, prevhash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
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
    public void insertMatchingEvent(MatchResult match) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MATCHING_EVENT_SQL);
            preparedStatement.setString(1, match.getTxhash());
            preparedStatement.setString(2, match.getTokenid());
            preparedStatement.setLong(3, match.getPrice());
            preparedStatement.setLong(4, match.getExecutedQuantity());
            preparedStatement.setLong(5, match.getInserttime());
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
    public List<MatchResult> getLastMatchingEvents(Set<String> tokenIds, int count) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_MATCHING_EVENT;
            if (tokenIds == null || tokenIds.isEmpty()) {
                sql += " ORDER BY inserttime DESC " + "LIMIT  " + count;

            } else {
                sql += " where tokenid IN (" + buildINList(tokenIds) + " )" + "  ORDER BY inserttime DESC " + "LIMIT   "
                        + count;
            }
            preparedStatement = conn.get().prepareStatement(sql);

            ResultSet resultSet = preparedStatement.executeQuery();
            List<MatchResult> list = new ArrayList<>();
            while (resultSet.next()) {
                list.add(new MatchResult(resultSet.getString(1), resultSet.getString(2),

                        resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5)));
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
    public void deleteMatchingEvents(String hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MATCHING_EVENT_BY_HASH);
            preparedStatement.setString(1, Utils.HEX.encode(hash.getBytes()));
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
    public Token queryDomainnameToken(Sha256Hash domainNameBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKENS_BY_DOMAINNAME_SQL);
            preparedStatement.setBytes(1, domainNameBlockHash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Token tokens = new Token();
                tokens.setBlockHash(Sha256Hash.wrap(resultSet.getBytes("blockhash")));
                tokens.setTokenid(resultSet.getString("tokenid"));
                return tokens;
            }
            return null;
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
    public Token getTokensByDomainname(String domainname) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKENS_BY_DOMAINNAME_SQL0);
            preparedStatement.setString(1, domainname);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Token tokens = new Token();
                tokens.setBlockHash(Sha256Hash.wrap(resultSet.getBytes("blockhash")));
                tokens.setTokenid(resultSet.getString("tokenid"));
                return tokens;
            }
            return null;
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
        PreparedStatement sub_preparedStatement = null;
        String sql = "SELECT orderid,pubkey,sign,signInputData FROM exchange_multisign WHERE orderid=?";
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

            exchange.getSigs().add(resultSet.getBytes("signInputData"));
            sub_preparedStatement = conn.get().prepareStatement(sql);
            sub_preparedStatement.setString(1, exchange.getToOrderId());
            ResultSet sub_resultSet = sub_preparedStatement.executeQuery();
            List<ExchangeMulti> list = new ArrayList<ExchangeMulti>();

            while (sub_resultSet.next()) {
                exchange.getSigs().add(sub_resultSet.getBytes("signInputData"));
                list.add(new ExchangeMulti(exchange.getToOrderId(), sub_resultSet.getString("pubkey"),
                        sub_resultSet.getBytes("signInputData"), sub_resultSet.getInt("sign")));
            }
            exchange.setExchangeMultis(list);
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
    public List<Exchange> getExchangeListWithAddressA(String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Exchange> list = new ArrayList<Exchange>();
        String sql = SELECT_EXCHANGE_SQL_A;

        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            preparedStatement.setString(3, address);
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
                exchange.setMemo(resultSet.getString("memo"));
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
            preparedStatement.setString(14, exchange.getMemo());
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
    public void deleteExchange(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_EXCHANGE_SQL);
            preparedStatement.setString(1, orderid);
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
    public void updateExchangeSignData(String orderid, byte[] data) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "UPDATE exchange SET toSign = 1, signInputData = ? WHERE orderid = ?";

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
    public List<Sha256Hash> getWhereConfirmedNotMilestone() throws BlockStoreException {
        List<Sha256Hash> storedBlockHashes = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_CONFIRMED_AND_NOT_MILESTONE_SQL);
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
    public List<OrderCancel> getOrderCancelByOrderBlockHash(HashSet<String> orderBlockHashs)
            throws BlockStoreException {
        if (orderBlockHashs.isEmpty()) {
            return new ArrayList<OrderCancel>();
        }
        List<OrderCancel> orderCancels = new ArrayList<OrderCancel>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            StringBuffer sql = new StringBuffer();
            for (String s : orderBlockHashs) {
                sql.append(",'").append(s).append("'");
            }
            preparedStatement = conn.get()
                    .prepareStatement(SELECT_ORDERCANCEL_SQL + " AND orderblockhash IN (" + sql.substring(1) + ")");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                OrderCancel orderCancel = new OrderCancel();
                orderCancel.setBlockHash(Sha256Hash.wrap(resultSet.getBytes("blockhash")));
                orderCancel.setOrderBlockHash(Sha256Hash.wrap(resultSet.getBytes("orderblockhash")));
                orderCancel.setConfirmed(resultSet.getBoolean("confirmed"));
                orderCancel.setSpent(resultSet.getBoolean("spent"));
                orderCancel.setSpenderBlockHash(Sha256Hash.wrap(resultSet.getBytes("spenderblockhash")));
                orderCancel.setTime(resultSet.getLong("time"));
                orderCancels.add(orderCancel);
            }
            return orderCancels;
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
    public List<MatchResult> getTimeBetweenMatchingEvents(Set<String> tokenIds, Long startDate, Long endDate, int count)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_MATCHING_EVENT;
            if (tokenIds == null || tokenIds.isEmpty()) {
                sql += " ORDER BY inserttime DESC " + "LIMIT  " + count;

            } else {
                sql += " where tokenid IN (" + buildINList(tokenIds) + " ) AND inserttime >= " + startDate
                        + " AND inserttime <=" + endDate + "  ORDER BY inserttime DESC " + "LIMIT   " + count;
            }
            preparedStatement = conn.get().prepareStatement(sql);

            ResultSet resultSet = preparedStatement.executeQuery();
            List<MatchResult> list = new ArrayList<>();
            while (resultSet.next()) {
                list.add(new MatchResult(resultSet.getString(1), resultSet.getString(2),

                        resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5)));
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
 
    public void insertAccessPermission(String pubKey, String accessToken) throws BlockStoreException {
        String sql = "insert into access_permission (pubKey, accessToken, refreshTime) value (?,?,?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, pubKey);
            preparedStatement.setString(2, accessToken);
            preparedStatement.setLong(3, System.currentTimeMillis());
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
    public int getCountAccessPermissionByPubKey(String pubKey, String accessToken) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement("select count(1) as count from access_permission where pubKey = ? and accessToken = ?");
            preparedStatement.setString(1, pubKey);
            preparedStatement.setString(2, accessToken);
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
    public void insertAccessGrant(String address) throws BlockStoreException {
        String sql = "insert into access_grant (address, createTime) value (?,?)";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, address);
            preparedStatement.setLong(2, System.currentTimeMillis());
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
    public void deleteAccessGrant(String address) throws BlockStoreException {
        String sql = "delete from access_grant where address = ?";
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, address);
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
    public int getCountAccessGrantByAddress(String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement("select count(1) as count from access_grant where address = ?");
            preparedStatement.setString(1, address);
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
 
    public List<Block> findRetryBlocks(long minHeigth) throws BlockStoreException {

        String sql = "SELECT hash, rating, depth, cumulativeweight, "
                + " height, milestone, milestonelastupdate,  inserttime,  blocktype, solid, confirmed , block"
                + "  FROM   blocks ";
        sql += " where solid=true and confirmed=false and height >= " + minHeigth;
        sql += " ORDER BY insertTime desc ";
        List<Block> result = new ArrayList<Block>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Block block = params.getDefaultSerializer().makeZippedBlock(resultSet.getBytes("block"));

                result.add(block);
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
 
}
