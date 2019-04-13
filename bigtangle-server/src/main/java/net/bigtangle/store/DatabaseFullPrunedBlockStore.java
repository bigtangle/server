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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.BatchBlock;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredBlockBinary;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.script.Script;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Match;
import net.bigtangle.server.service.Eligibility;
import net.bigtangle.server.service.SolidityState;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {

    private static final String LIMIT_5000 = " limit 5000 ";

    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    public static final String VERSION_SETTING = "version";

    // Drop table SQL.
    public static String DROP_SETTINGS_TABLE = "DROP TABLE IF EXISTS settings";
    public static String DROP_BLOCKS_TABLE = "DROP TABLE IF EXISTS blocks";
    public static String DROP_UNSOLIDBLOCKS_TABLE = "DROP TABLE IF EXISTS unsolidblocks";
    public static String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE IF EXISTS outputs";
    public static String DROP_OUTPUTSMULTI_TABLE = "DROP TABLE IF EXISTS outputsmulti";
    public static String DROP_TIPS_TABLE = "DROP TABLE IF EXISTS tips";
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
    public static String DROP_LOGRESULT_TABLE = "DROP TABLE IF EXISTS logresult";
    public static String DROP_BATCHBLOCK_TABLE = "DROP TABLE IF EXISTS batchblock";
    public static String DROP_SUBTANGLE_PERMISSION_TABLE = "DROP TABLE IF EXISTS subtangle_permission";
    public static String DROP_ORDERS_TABLE = "DROP TABLE IF EXISTS openorders";
    public static String DROP_ORDER_MATCHING_TABLE = "DROP TABLE IF EXISTS ordermatching";
    public static String DROP_CONFIRMATION_DEPENDENCY_TABLE = "DROP TABLE IF EXISTS confirmationdependency";
    public static String DROP_MYSERVERBLOCKS_TABLE = "DROP TABLE IF EXISTS myserverblocks";

    // Queries SQL.
    protected final String SELECT_SETTINGS_SQL = "SELECT settingvalue FROM settings WHERE name = ?";
    protected final String INSERT_SETTINGS_SQL = getInsert() + "  INTO settings(name, settingvalue) VALUES(?, ?)";

    protected final String SELECT_BLOCKS_SQL = "SELECT  height, block, wasundoable,prevblockhash,prevbranchblockhash,mineraddress,"
            + "blocktype FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_UNSOLIDBLOCKS_SQL = "SELECT  hash,   block,  inserttime FROM unsolidblocks order by inserttime asc"
            + afterSelect();

    protected final String SELECT_UNSOLIDBLOCKS_FROM_DEPENDENCY_SQL = "SELECT block FROM unsolidblocks WHERE missingDependency = ? "
            + afterSelect();

    protected final String SELECT_BLOCKS_HEIGHT_SQL = "SELECT block FROM blocks WHERE height >= ?" + afterSelect()
            + " order by height asc ";
    protected final String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT hash, rating, depth, cumulativeweight, "
            + "  height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained,"
            + "   block FROM blocks" + " WHERE (prevblockhash = ? OR prevbranchblockhash = ?)" + afterSelect();
    protected final String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM blocks " + " "
            + "WHERE  (blocks.prevblockhash = ? OR blocks.prevbranchblockhash = ?)" + afterSelect();

    protected final String INSERT_BLOCKS_SQL = getInsert()
            + "  INTO blocks(hash,  height, block, wasundoable,prevblockhash,"
            + "prevbranchblockhash,mineraddress,blocktype " + "  , rating, depth, cumulativeweight, "
            + "milestone, milestonelastupdate, milestonedepth, inserttime, maintained )"
            + " VALUES(?, ?, ?, ?, ?,?,  ?, ?, ?, ?, ?, ?, ?,  ?, ?, ?)";

    protected final String INSERT_UNSOLIDBLOCKS_SQL = getInsert()
            + "  INTO unsolidblocks(hash,   block,  inserttime  , reason, missingdependency)"
            + " VALUES(?, ?, ?, ?, ? )";

    protected final String SELECT_OUTPUTS_COUNT_SQL = "SELECT COUNT(*) FROM outputs WHERE hash = ?";
    protected final String INSERT_OUTPUTS_SQL = getInsert()
            + " INTO outputs (hash, outputindex, coinvalue, scriptbytes, toaddress, addresstargetable,"
            + " coinbase, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,time)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    protected final String SELECT_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
            + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending FROM outputs WHERE hash = ? AND outputindex = ?";

    protected final String SELECT_OUTPUT_CONFIRMING_BLOCK_SQL = "SELECT blockhash FROM outputs WHERE hash = ? AND outputindex = ?";

    protected final String SELECT_OUTPUTS_HISTORY_SQL = "SELECT hash,outputindex, coinvalue, scriptbytes, toaddress ,"
            + " addresstargetable, coinbase,blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending,time FROM outputs WHERE 1=1";

    protected final String DELETE_OUTPUTS_SQL = "DELETE FROM outputs WHERE hash = ? AND outputindex= ?";

    protected final String SELECT_TRANSACTION_OUTPUTS_SQL = "SELECT " + "outputs.hash, coinvalue, scriptbytes, "
            + " outputs.outputindex, coinbase, " + "case  when outputs.toaddress ='' then outputsmulti.toaddress "
            + "when outputs.toaddress is null then outputsmulti.toaddress "
            + " else outputs.toaddress end, addresstargetable, blockhash, tokenid, "
            + " fromaddress, memo, spent, confirmed, spendpending, outputsmulti.minimumsign AS minimumsign "
            + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?";

    protected final String SELECT_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress, addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending "
            + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE (outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?) " + " AND tokenid = ?";

    protected final String SELECT_DUMP_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes FROM outputs";

    // Tables exist SQL.
    protected final String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

    protected final String DELETE_UNSOLIDBLOCKS_SQL = "DELETE FROM unsolidblocks WHERE hash = ?";
    protected final String DELETE_OLD_UNSOLIDBLOCKS_SQL = "DELETE FROM unsolidblocks WHERE inserttime <= ?";
    protected final String DELETE_TIP_SQL = "DELETE FROM tips WHERE hash = ?";
    protected final String INSERT_TIP_SQL = getInsert() + "  INTO tips (hash) VALUES (?)";

    protected final String INSERT_DEPENDENTS_SQL = getInsert()
            + " INTO confirmationdependency (blockhash, dependencyblockhash) VALUES (?, ?)";
    protected final String SELECT_DEPENDENTS_SQL = "SELECT blockhash FROM confirmationdependency WHERE dependencyblockhash = ?"
            + afterSelect();
    protected final String REMOVE_DEPENDENCIES_SQL = "DELETE FROM confirmationdependency WHERE blockhash = ?"
            + afterSelect();

    protected final String SELECT_BLOCKEVALUATION_SQL = "SELECT hash, rating, depth, cumulativeweight, "
            + " height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained"
            + "  FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_BLOCKWRAP_SQL = "SELECT hash, rating, depth, cumulativeweight, "
            + " height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained,"
            + "  block FROM blocks WHERE hash = ?" + afterSelect();

    protected final String SELECT_COUNT_MILESTONE_SQL = "SELECT COUNT(*) as count FROM blocks WHERE milestone = true AND height >= ? AND height <= ?";

    protected final String SELECT_ALL_BLOCKEVALUATIONS_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight,  height, milestone,"
            + " milestonelastupdate, milestonedepth, inserttime, maintained " + " FROM blocks ";

    protected final String SELECT_NONSOLID_BLOCKS_SQL = "select hash, block, inserttime from unsolidblocks order by inserttime asc ";

    protected final String SELECT_BLOCKWRAPS_TO_ADD_TO_MILESTONE_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight, height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, block " + "FROM blocks WHERE  milestone = false AND rating >= "
            + NetworkParameters.MILESTONE_UPPER_THRESHOLD + " AND depth >= ?" + afterSelect();
    protected final String SELECT_MAINTAINED_BLOCKS_SQL = "SELECT hash " + "FROM blocks WHERE maintained = true"
            + afterSelect();
    protected final String SELECT_BLOCKS_IN_MILESTONEDEPTH_INTERVAL_SQL = "SELECT hash, "
            + "rating, depth, cumulativeweight,  height, milestone, milestonelastupdate,"
            + " milestonedepth, inserttime, maintained, block "
            + "FROM blocks WHERE milestone = true AND milestonedepth >= ? AND milestonedepth <= ?" + afterSelect();
    protected final String SELECT_BLOCKS_TO_REMOVE_FROM_MILESTONE_SQL = "SELECT hash, rating, depth, "
            + "cumulativeweight,  height, milestone, milestonelastupdate," + " milestonedepth, inserttime, maintained"
            + " FROM blocks WHERE  milestone = true AND rating <= " + NetworkParameters.MILESTONE_LOWER_THRESHOLD
            + afterSelect();
    protected final String SELECT_SOLID_TIPS_SQL = "SELECT blocks.hash, rating, depth, cumulativeweight, "
            + " height, milestone, milestonelastupdate, milestonedepth, inserttime," + "maintained, block FROM blocks "
            + "INNER JOIN tips ON tips.hash=blocks.hash" + afterSelect();
    protected final String SELECT_SOLID_BLOCKS_OF_HEIGHT_SQL = "SELECT hash, rating, depth, "
            + "cumulativeweight, height, milestone, milestonelastupdate, " + "milestonedepth, inserttime, maintained "
            + "FROM blocks WHERE  height = ?" + afterSelect();
    protected final String SELECT_CONFIRMED_BLOCKS_OF_HEIGHT_HIGHER_THAN_SQL = "SELECT hash "
            + "FROM blocks WHERE height >= ? AND milestone = 1 " + afterSelect();
    protected final String SELECT_BLOCKS_OF_INSERTTIME_HIGHER_THAN_SQL = "SELECT hash "
            + "FROM blocks WHERE inserttime >= ? " + afterSelect();

    protected final String SELECT_OUTPUT_SPENDER_SQL = "SELECT blocks.hash," + " rating, depth, cumulativeweight,   "
            + "blocks.height, milestone, milestonelastupdate, " + "milestonedepth, inserttime, maintained "
            + "FROM blocks INNER JOIN outputs ON outputs.spenderblockhash=blocks.hash"
            + " WHERE  outputs.hash = ? AND outputindex= ?" + afterSelect();
    protected final String SELECT_MAX_IMPORT_TIME_SQL = "SELECT MAX(inserttime) " + "FROM blocks";

    protected final String UPDATE_ORDER_SPENT_SQL = getUpdate() + " openorders SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";
    protected final String UPDATE_ORDER_CONFIRMED_SQL = getUpdate() + " openorders SET confirmed = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDERS_BY_ISSUER_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, confirmed, spent, "
            + "spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex, validFromTime, side, beneficiaryaddress"
            + " FROM openorders WHERE collectinghash = ?";
    protected final String SELECT_LOST_ORDERS_BEFORE_SQL = "SELECT blockhash"
            + " FROM blocks INNER JOIN openorders ON openorders.blockhash=blocks.hash"
            + " WHERE blocks.height <= ? AND blocks.milestone = 1 AND openorders.spent = 0" + afterSelect();
    protected final String SELECT_ORDER_SPENT_SQL = "SELECT spent FROM openorders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_CONFIRMED_SQL = "SELECT confirmed FROM openorders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_SPENDER_SQL = "SELECT spenderblockhash FROM openorders WHERE blockhash = ? AND collectinghash = ?";
    protected final String SELECT_ORDER_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, confirmed, "
            + "spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex, validFromTime, side, beneficiaryaddress"
            + " FROM openorders WHERE blockhash = ? AND collectinghash = ?";
    protected final String INSERT_ORDER_SQL = getInsert()
            + "  INTO openorders (blockhash, collectinghash, offercoinvalue, offertokenid, confirmed, spent, spenderblockhash, "
            + "targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex,validFromTime, side, beneficiaryaddress) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?)";

    protected final String INSERT_TOKENS_SQL = getInsert()
            + " INTO tokens (blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, url, signnumber,tokentype, tokenstop, prevblockhash, spent, spenderblockhash, tokenkeyvalues) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    protected final String SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL = "SELECT spent FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_ANY_SPENT_BY_TOKEN_SQL = "SELECT spent FROM tokens WHERE tokenid = ? AND tokenindex = ? AND spent = true";

    protected final String SELECT_TOKEN_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_ANY_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    protected final String SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    protected final String SELECT_TOKEN_SPENDER_SQL = "SELECT spenderblockhash FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_PREVBLOCKHASH_SQL = "SELECT prevblockhash FROM tokens WHERE blockhash = ?";

    protected final String SELECT_TOKEN_SQL = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, url, signnumber,tokentype, tokenstop, tokenkeyvalues"
            + " FROM tokens WHERE blockhash = ?";

    protected final String UPDATE_TOKEN_SPENT_SQL = getUpdate() + " tokens SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ?";

    protected final String UPDATE_TOKEN_CONFIRMED_SQL = getUpdate() + " tokens SET confirmed = ? "
            + " WHERE blockhash = ?";

    protected final String SELECT_CONFIRMED_TOKENS_SQL = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, url, signnumber,tokentype, tokenstop , tokenkeyvalues"
            + " FROM tokens WHERE confirmed = true";

    protected final String SELECT_MARKET_TOKENS_SQL = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, url, signnumber,tokentype, tokenstop, tokenkeyvalues "
            + " FROM tokens WHERE tokentype = 1 and confirmed = true";

    protected final String SELECT_TOKENS_ACOUNT_MAP_SQL = "SELECT tokenid, SUM(amount) as amount FROM tokens WHERE confirmed = true GROUP BY tokenid";

    protected final String COUNT_TOKENSINDEX_SQL = "SELECT blockhash, tokenindex FROM tokens WHERE tokenid = ? AND confirmed = true ORDER BY tokenindex DESC limit 1";

    protected final String UPDATE_SETTINGS_SQL = getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    protected final String UPDATE_BLOCKS_SQL = getUpdate() + " blocks SET wasundoable=? WHERE hash=?";

    protected final String UPDATE_OUTPUTS_SPENT_SQL = getUpdate()
            + " outputs SET spent = ?, spenderblockhash = ? WHERE hash = ? AND outputindex= ?";

    protected final String UPDATE_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE hash = ? AND outputindex= ?";

    protected final String UPDATE_OUTPUTS_SPENDPENDING_SQL = getUpdate()
            + " outputs SET spendpending = ? WHERE hash = ? AND outputindex= ?";

    protected final String UPDATE_OUTPUTS_CONFIRMING_BLOCK_SQL = getUpdate()
            + " outputs SET blockhash = ? WHERE hash = ? AND outputindex= ?";

    protected final String UPDATE_BLOCKEVALUATION_DEPTH_SQL = getUpdate() + " blocks SET depth = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL = getUpdate()
            + " blocks SET cumulativeweight = ?, depth = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL = getUpdate()
            + " blocks SET cumulativeweight = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_HEIGHT_SQL = getUpdate() + " blocks SET height = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = ?,milestonelastupdate= ?  WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_RATING_SQL = getUpdate() + " blocks SET rating = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_MILESTONEDEPTH_SQL = getUpdate()
            + " blocks SET milestonedepth = ? WHERE hash = ?";

    protected final String UPDATE_BLOCKEVALUATION_MAINTAINED_SQL = getUpdate()
            + " blocks SET maintained = ? WHERE hash = ?";
    protected final String UPDATE_ALL_BLOCKS_MAINTAINED_SQL = getUpdate() + " blocks SET maintained = 1 ";

    protected abstract String getUpdateBlockevaluationUnmaintainAllSQL();

    protected final String SELECT_MULTISIGNADDRESS_SQL = "SELECT blockhash, tokenid, address, pubKeyHex, posIndex FROM multisignaddress WHERE tokenid = ? AND blockhash = ?";
    protected final String INSERT_MULTISIGNADDRESS_SQL = "INSERT INTO multisignaddress (tokenid, address, pubKeyHex, posIndex,blockhash) VALUES (?, ?, ?, ?,?)";
    protected final String DELETE_MULTISIGNADDRESS_SQL = "DELETE FROM multisignaddress WHERE tokenid = ? AND address = ?";
    protected final String COUNT_MULTISIGNADDRESS_SQL = "SELECT COUNT(*) as count FROM multisignaddress WHERE tokenid = ?";
    protected final String SELECT_MULTISIGNADDRESSINFO_SQL = "SELECT tokenid, address FROM multisignaddress WHERE tokenid = ? AND address = ?";
    protected final String DELETE_MULTISIGNADDRESS_SQL0 = "DELETE FROM multisignaddress WHERE tokenid = ? AND blockhash = ?";

    protected final String SELECT_SEARCH_TOKENSERIAL_ALL_SQL = "SELECT ts.tokenid as tokenid, tokenindex, amount,signnumber,"
            + "(select count(address) FROM multisign ms WHERE ts.tokenid=ms.tokenid and ts.tokenindex=ms.tokenindex AND ms.sign=1) as count "
            + "FROM tokenserial ts  " + "LEFT JOIN multisignaddress msa ON ts.tokenid=msa.tokenid "
            + "LEFT JOIN tokens t ON ts.tokenid=t.tokenid WHERE 1=1 ";

    protected final String INSERT_MULTISIGNBY_SQL = "INSERT INTO multisignby (tokenid, tokenindex, address) VALUES (?, ?, ?)";
    protected final String SELECT_MULTISIGNBY_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String SELECT_MULTISIGNBY0_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ?";
    protected final String SELECT_MULTISIGNBY000_SQL = "SELECT tokenid, tokenindex, address FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";

    protected final String SELECT_MULTISIGN_ADDRESS_ALL_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign,(select count(ms1.sign) from multisign ms1 where ms1.tokenid=tokenid and tokenindex=ms1.tokenindex and ms1.sign!=0 ) as count FROM multisign  WHERE 1=1 ";
    protected final String SELECT_MULTISIGN_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE address = ? ORDER BY tokenindex ASC";
    protected final String INSERT_MULTISIGN_SQL = "INSERT INTO multisign (tokenid, tokenindex, address, blockhash, sign, id) VALUES (?, ?, ?, ?, ?, ?)";
    protected final String UPDATE_MULTISIGN_SQL = "UPDATE multisign SET blockhash = ?, sign = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String UPDATE_MULTISIGN0_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String UPDATE_MULTISIGN1_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ?";
    protected final String SELECT_COUNT_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    protected final String DELETE_MULTISIGN_SQL = "DELETE FROM multisign WHERE tokenid = ?";

    protected final String SELECT_COUNT_MULTISIGN_SIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND sign = ?";

    /* REWARD BLOCKS */
    protected final String INSERT_TX_REWARD_SQL = getInsert()
            + "  INTO txreward (blockhash, toheight, confirmed, spent, spenderblockhash, eligibility, prevblockhash, nexttxreward) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    protected final String SELECT_TX_REWARD_NEXT_TX_REWARD_SQL = "SELECT nexttxreward FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_TOHEIGHT_SQL = "SELECT toheight FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL = "SELECT blockhash FROM txreward WHERE confirmed = 1 AND toheight=(SELECT MAX(toheight) FROM txreward WHERE confirmed=1)";
    protected final String SELECT_TX_REWARD_CONFIRMED_SQL = "SELECT confirmed " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_ELIGIBLE_SQL = "SELECT eligibility " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_SPENT_SQL = "SELECT spent " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_SPENDER_SQL = "SELECT spenderblockhash "
            + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_PREVBLOCKHASH_SQL = "SELECT prevblockhash "
            + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_REWARD_WHERE_PREV_HASH_SQL = "SELECT blockhash "
            + "FROM txreward WHERE prevblockhash = ?";
    protected final String UPDATE_TX_REWARD_CONFIRMED_SQL = "UPDATE txreward SET confirmed = ? WHERE blockhash = ?";
    protected final String UPDATE_TX_REWARD_SPENT_SQL = "UPDATE txreward SET spent = ?, spenderblockhash = ? WHERE blockhash = ?";
    protected final String UPDATE_TX_REWARD_NEXT_TX_REWARD_SQL = "UPDATE txreward SET nexttxreward = ? WHERE blockhash = ?";

    /* ORDER MATCHING BLOCKS */
    protected final String INSERT_ORDER_MATCHING_SQL = getInsert()
            + "  INTO ordermatching (blockhash, toheight, confirmed, spent, spenderblockhash, eligibility, prevblockhash) VALUES (?, ?, ?, ?, ?, ?, ?)";
    protected final String SELECT_ORDER_MATCHING_TOHEIGHT_SQL = "SELECT toheight FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_FROMHEIGHT_SQL = "SELECT fromheight FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_MAX_CONFIRMED_SQL = "SELECT blockhash FROM ordermatching WHERE confirmed = 1 AND toheight=(SELECT MAX(toheight) FROM ordermatching WHERE confirmed=1)";
    protected final String SELECT_ORDER_MATCHING_CONFIRMED_SQL = "SELECT confirmed "
            + "FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_ELIGIBLE_SQL = "SELECT eligibility "
            + "FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_SPENT_SQL = "SELECT spent " + "FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_SPENDER_SQL = "SELECT spenderblockhash "
            + "FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_PREVBLOCKHASH_SQL = "SELECT prevblockhash "
            + "FROM ordermatching WHERE blockhash = ?";
    protected final String SELECT_ORDER_MATCHING_WHERE_PREV_HASH_SQL = "SELECT blockhash "
            + "FROM ordermatching WHERE prevblockhash = ?";
    protected final String UPDATE_ORDER_MATCHING_CONFIRMED_SQL = "UPDATE ordermatching SET confirmed = ? WHERE blockhash = ?";
    protected final String UPDATE_ORDER_MATCHING_SPENT_SQL = "UPDATE ordermatching SET spent = ?, spenderblockhash = ? WHERE blockhash = ?";
    protected final String UPDATE_ORDER_MATCHING_NEXT_TX_REWARD_SQL = "UPDATE ordermatching SET nexttxreward = ? WHERE blockhash = ?";
    
    /* MATCHING EVENTS */
    protected final String INSERT_MATCHING_EVENT_SQL = getInsert()
            + " INTO matching (txhash, tokenid, restingOrderId, incomingOrderId, incomingBuy, price, executedQuantity, remainingQuantity, inserttime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected final String SELECT_MATCHING_EVENT_BY_TIME_SQL = "SELECT txhash, tokenid, restingOrderId, incomingOrderId, incomingBuy, price, executedQuantity, remainingQuantity, inserttime "
            + "FROM matching "
            + "WHERE tokenid = ? "
            + "ORDER BY inserttime DESC "
            + "LIMIT ? ";
    protected final String DELETE_MATCHING_EVENT_BY_HASH = "DELETE FROM matching WHERE txhash = ?";

    /* OTHER */
    protected final String INSERT_OUTPUTSMULTI_SQL = "insert into outputsmulti (hash, toaddress, outputindex, minimumsign) values (?, ?, ?, ?)";
    protected final String SELECT_OUTPUTSMULTI_SQL = "select hash, toaddress, outputindex, minimumsign from outputsmulti where hash=? and outputindex=?";

    protected final String SELECT_USERDATA_SQL = "SELECT blockhash, dataclassname, data, pubKey, blocktype FROM userdata WHERE dataclassname = ? and pubKey = ?";
    protected final String INSERT_USERDATA_SQL = "INSERT INTO userdata (blockhash, dataclassname, data, pubKey, blocktype) VALUES (?, ?, ?, ?, ?)";
    protected final String UPDATE_USERDATA_SQL = "UPDATE userdata SET blockhash = ?, data = ? WHERE dataclassname = ? and pubKey = ?";

    protected final String INSERT_BATCHBLOCK_SQL = "INSERT INTO batchblock (hash, block, inserttime) VALUE (?, ?, ?)";
    protected final String DELETE_BATCHBLOCK_SQL = "DELETE FROM batchblock WHERE hash = ?";
    protected final String SELECT_BATCHBLOCK_SQL = "SELECT hash, block, inserttime FROM batchblock order by inserttime ASC";
    protected final String INSERT_SUBTANGLE_PERMISSION_SQL = "INSERT INTO  subtangle_permission (pubkey, userdataPubkey , status) VALUE (?, ?, ?)";

    protected final String DELETE_SUBTANGLE_PERMISSION_SQL = "DELETE FROM  subtangle_permission WHERE pubkey=?";
    protected final String UPATE_ALL_SUBTANGLE_PERMISSION_SQL = "UPDATE   subtangle_permission set status=? ,userdataPubkey=? WHERE  pubkey=? ";
    protected final String UPATE_SUBTANGLE_PERMISSION_SQL = "UPDATE   subtangle_permission set status=? WHERE  pubkey=? AND userdataPubkey=?";
    protected final String UPATE_SUBTANGLE_PERMISSION_A_SQL = "UPDATE   subtangle_permission set status=? WHERE  pubkey=? ";

    protected final String SELECT_ALL_SUBTANGLE_PERMISSION_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission ";

    protected final String SELECT_SUBTANGLE_PERMISSION_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission WHERE pubkey=?";

    protected final String SELECT_SUBTANGLE_PERMISSION_BY_PUBKEYS_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission WHERE 1=1 ";

    // Tests
    protected final String SELECT_ORDERS_SORTED_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex, validFromTime, side, beneficiaryaddress "
            + " FROM openorders ORDER BY blockhash, collectinghash";
    protected final String SELECT_UTXOS_SORTED_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress, "
            + "addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, hash, outputindex "
            + " FROM outputs ORDER BY hash, outputindex";
    protected final String SELECT_AVAILABLE_ORDERS_SORTED_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex ,validFromTime, side , beneficiaryaddress"
            + " FROM openorders WHERE confirmed=1 AND spent=? ORDER BY blockhash, collectinghash";
    protected final String SELECT_MY_AVAILABLE_ORDERS_SORTED_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex ,validFromTime, side , beneficiaryaddress"
            + " FROM openorders WHERE confirmed=1 AND spent=? ";

    // TODO index
    protected final String SELECT_BEST_OPEN_SELL_ORDERS_SORTED_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex ,validFromTime, side , beneficiaryaddress"
            + " FROM openorders " + " WHERE confirmed=1 AND spent=0 AND offertokenid=? "
            + " ORDER BY targetcoinvalue / offercoinvalue ASC" + " LIMIT ?";
    protected final String SELECT_BEST_OPEN_BUY_ORDERS_SORTED_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, opindex ,validFromTime, side , beneficiaryaddress"
            + " FROM openorders " + " WHERE confirmed=1 AND spent=0 AND targettokenid=? "
            + " ORDER BY offercoinvalue / targetcoinvalue DESC" + " LIMIT ?";

    protected final String SELECT_MY_CLOSED_ORDERS_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + " confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, "
            + " validToTime, opindex ,validFromTime, side , beneficiaryaddress" + " FROM openorders "
            + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress=? AND collectinghash=0x0000000000000000000000000000000000000000000000000000000000000000 "
            + " AND blockhash NOT IN ( SELECT blockhash FROM openorders "
            + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? )";
    protected final String SELECT_MY_REMAINING_OPEN_ORDERS_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + " confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, "
            + " validToTime, opindex ,validFromTime, side , beneficiaryaddress" + " FROM openorders "
            + " WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? ";
    protected final String SELECT_MY_INITIAL_OPEN_ORDERS_SQL = "SELECT blockhash, collectinghash, offercoinvalue, offertokenid, "
            + " confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, beneficiarypubkey, "
            + " validToTime, opindex ,validFromTime, side , beneficiaryaddress" + " FROM openorders "
            + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress=? AND collectinghash=0x0000000000000000000000000000000000000000000000000000000000000000  "
            + " AND blockhash IN ( SELECT blockhash FROM openorders "
            + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? )";

    protected final String SELECT_AVAILABLE_UTXOS_SORTED_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress, "
            + "addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, hash, outputindex "
            + " FROM outputs WHERE confirmed=1 AND spent=0 ORDER BY hash, outputindex";

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
        // try {
        // beginDatabaseBatchWrite();
        // create();
        // commitDatabaseBatchWrite();
        // } catch (Exception e) {
        // log.error("", e);
        // this.abortDatabaseBatchWrite();
        // }
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
        sqlStatements.add(DROP_BLOCKS_TABLE);
        sqlStatements.add(DROP_UNSOLIDBLOCKS_TABLE);
        sqlStatements.add(DROP_OPEN_OUTPUT_TABLE);
        sqlStatements.add(DROP_OUTPUTSMULTI_TABLE);
        sqlStatements.add(DROP_TIPS_TABLE);
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
        sqlStatements.add(DROP_LOGRESULT_TABLE);
        sqlStatements.add(DROP_BATCHBLOCK_TABLE);
        sqlStatements.add(DROP_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(DROP_ORDERS_TABLE);
        sqlStatements.add(DROP_ORDER_MATCHING_TABLE);
        sqlStatements.add(DROP_CONFIRMATION_DEPENDENCY_TABLE);
        sqlStatements.add(DROP_MYSERVERBLOCKS_TABLE);
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
     * Get the SQL to select a blocks record.
     * 
     * @return The SQL select statement.
     */
    protected String getSelectHeadersSQL() {
        return SELECT_BLOCKS_SQL;
    }

    /**
     * Get the SQL to insert a blocks record.
     * 
     * @return The SQL insert statement.
     */
    protected String getInsertHeadersSQL() {
        return INSERT_BLOCKS_SQL;
    }

    /**
     * Get the SQL to update a blocks record.
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
            log.info("Made a new connection to database " + connectionURL);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
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

            // ECKey ecKey = new ECKey();
            // this.saveTokens(ecKey.getPublicKeyAsHex(), "default market",
            // "default
            // market", "http://localhost:8089/", 0,
            // false, true, true);
            // this.commitDatabaseBatchWrite();

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
            // Set up the genesis block and initial state of tables
            StoredBlock storedGenesisHeader = new StoredBlock(params.getGenesisBlock(), 0);
            List<Transaction> genesisTransactions = Lists.newLinkedList();
            StoredUndoableBlock storedGenesis = new StoredUndoableBlock(params.getGenesisBlock().getHash(),
                    genesisTransactions);
            put(storedGenesisHeader, storedGenesis);
            saveGenesisTransactionOutput(params.getGenesisBlock());
            updateBlockEvaluationMilestone(params.getGenesisBlock().getHash(), true);

            // Just fill the tables with some valid data
            // Reward output table
            insertReward(params.getGenesisBlock().getHash(), 0, Eligibility.ELIGIBLE, Sha256Hash.ZERO_HASH,
                    NetworkParameters.REWARD_INITIAL_TX_REWARD);
            insertOrderMatching(params.getGenesisBlock().getHash(), 0, Eligibility.ELIGIBLE, Sha256Hash.ZERO_HASH);
            updateRewardConfirmed(params.getGenesisBlock().getHash(), true);
            updateOrderMatchingConfirmed(params.getGenesisBlock().getHash(), true);

            // Token output table
            Token tokens = Token.buildSimpleTokenInfo(true, "", NetworkParameters.BIGTANGLE_TOKENID_STRING, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                    "BigTangle currency", 1, 0, 0, true);
            insertToken(params.getGenesisBlock().getHashAsString(), tokens);
            updateTokenConfirmed(params.getGenesisBlock().getHashAsString(), true);

            // Tip table
            insertTip(params.getGenesisBlock().getHash());
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
            UTXO newOut = new UTXO(block.getTransactions().get(0).getHash(), out.getIndex(), out.getValue(), true,
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
            s.setBytes(7, block.getMinerAddress());

            s.setLong(8, block.getBlockType().ordinal());
            int j = 7;
            s.setLong(j + 2, blockEvaluation.getRating());
            s.setLong(j + 3, blockEvaluation.getDepth());
            s.setLong(j + 4, blockEvaluation.getCumulativeWeight());

            j = 5;
            s.setBoolean(j + 7, blockEvaluation.isMilestone());
            s.setLong(j + 8, blockEvaluation.getMilestoneLastUpdateTime());
            s.setLong(j + 9, blockEvaluation.getMilestoneDepth());
            s.setLong(j + 10, blockEvaluation.getInsertTime());
            s.setBoolean(j + 11, blockEvaluation.isMaintained());

            s.executeUpdate();
            s.close();
            // log.info("add block hexStr : " + block.getHash().toString());
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

    }

    public StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly) throws BlockStoreException {
        StoredBlockBinary r = getBinary(hash, wasUndoableOnly);
        if (r == null)
            return null;
        Block b = params.getDefaultSerializer().makeBlock(r.getBlockBytes());
        b.verifyHeader();
        return new StoredBlock(b, r.getHeight());
    }

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
            s = conn.get().prepareStatement(SELECT_BLOCKS_HEIGHT_SQL);
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
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

                Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
                block.verifyHeader();
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
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        return get(hash, false);
    }

    @Override
    public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash) throws BlockStoreException {
        return get(hash, true);
    }

    @Override
    public Sha256Hash getTransactionOutputConfirmingBlock(Sha256Hash hash, long index) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_OUTPUT_CONFIRMING_BLOCK_SQL);
            s.setBytes(1, hash.getBytes());
            s.setLong(2, index);
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            return results.getBytes(1) != null ? Sha256Hash.wrap(results.getBytes(1)) : null;
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
            Coin coinvalue = Coin.valueOf(results.getLong(1), results.getString(7));
            byte[] scriptBytes = results.getBytes(2);
            boolean coinbase = results.getBoolean(3);
            String address = results.getString(4);
            Sha256Hash blockhash = results.getBytes(6) != null ? Sha256Hash.wrap(results.getBytes(6)) : null;

            String fromaddress = results.getString(8);
            String memo = results.getString(9);
            boolean spent = results.getBoolean(10);
            boolean confirmed = results.getBoolean(11);
            boolean spendPending = results.getBoolean(12);
            String tokenid = results.getString("tokenid");
            UTXO txout = new UTXO(hash, index, coinvalue, coinbase, new Script(scriptBytes), address, blockhash,
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
            s.setLong(3, out.getValue().getValue());
            s.setBytes(4, out.getScript().getProgram());
            s.setString(5, out.getAddress());
            s.setLong(6, out.getScript().getScriptType().ordinal());
            s.setBoolean(7, out.isCoinbase());
            s.setBytes(8, out.getBlockhash() != null ? out.getBlockhash().getBytes() : null);
            s.setString(9, Utils.HEX.encode(out.getValue().getTokenid()));
            s.setString(10, out.getFromaddress());
            s.setString(11, out.getMemo());
            s.setBoolean(12, out.isSpent());
            s.setBoolean(13, out.isConfirmed());
            s.setBoolean(14, out.isSpendPending());
            s.setLong(15, out.getTime());
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
        // if (log.isDebugEnabled())
        // log.debug("Starting database batch write with connection: " +
        // conn.get().toString());
        try {
            conn.get().setAutoCommit(false);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        // if (log.isDebugEnabled())
        // log.debug("Committing database batch write with connection: " +
        // conn.get().toString());
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
                    Coin amount = Coin.valueOf(rs.getLong(2), rs.getString(9));
                    byte[] scriptBytes = rs.getBytes(3);
                    int index = rs.getInt(4);
                    boolean coinbase = rs.getBoolean(5);
                    String toAddress = rs.getString(6);
                    // addresstargetable =rs.getBytes(7);
                    Sha256Hash blockhash = rs.getBytes(8) != null ? Sha256Hash.wrap(rs.getBytes(8)) : null;

                    String fromaddress = rs.getString(10);
                    String memo = rs.getString(11);
                    boolean spent = rs.getBoolean(12);
                    boolean confirmed = rs.getBoolean(13);
                    boolean spendPending = rs.getBoolean(14);
                    String tokenid = rs.getString("tokenid");
                    long minimumsign = rs.getLong("minimumsign");
                    UTXO output = new UTXO(hash, index, amount, coinbase, new Script(scriptBytes), toAddress, blockhash,
                            fromaddress, memo, tokenid, spent, confirmed, spendPending, minimumsign);
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
                    Coin amount = Coin.valueOf(rs.getLong(2), rs.getString(9));
                    byte[] scriptBytes = rs.getBytes(3);
                    int index = rs.getInt(4);
                    boolean coinbase = rs.getBoolean(5);
                    String toAddress = rs.getString(6);
                    // addresstargetable =rs.getBytes(7);
                    Sha256Hash blockhash = rs.getBytes(8) != null ? Sha256Hash.wrap(rs.getBytes(8)) : null;

                    String fromaddress = rs.getString(10);
                    String memo = rs.getString(11);
                    boolean spent = rs.getBoolean(12);
                    boolean confirmed = rs.getBoolean(13);
                    boolean spendPending = rs.getBoolean(14);
                    String tokenid = rs.getString("tokenid");
                    long minimumsign = rs.getLong("minimumsign");
                    UTXO output = new UTXO(hash, index, amount, coinbase, new Script(scriptBytes), toAddress, blockhash,
                            fromaddress, memo, tokenid, spent, confirmed, spendPending, minimumsign);
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
            BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                    resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                    resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                    resultSet.getBoolean(10));

            Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
            block.verifyHeader();
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
            BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                    resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                    resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                    resultSet.getBoolean(10));

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
    public List<Block> getNonSolidBlocks() throws BlockStoreException {
        List<Block> storedBlockHashes = new ArrayList<Block>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_NONSOLID_BLOCKS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                storedBlockHashes.add(params.getDefaultSerializer().makeBlock(resultSet.getBytes("block")));
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
        List<UTXO> UTXOs = new ArrayList<UTXO>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_OUTPUTS_HISTORY_SQL + " AND spent=false ";
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
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                Sha256Hash hash = Sha256Hash.wrap(rs.getBytes("hash"));
                Coin amount = Coin.valueOf(rs.getLong("coinvalue"), rs.getString("tokenid"));
                byte[] scriptBytes = rs.getBytes("scriptbytes");
                int index = rs.getInt("outputindex");
                boolean coinbase = rs.getBoolean("coinbase");
                String toAddress = rs.getString("toaddress");
                Sha256Hash blockhash = rs.getBytes("blockhash") != null ? Sha256Hash.wrap(rs.getBytes("blockhash"))
                        : null;

                String fromaddress1 = rs.getString("fromaddress");
                String memo = rs.getString("memo");
                boolean spent = rs.getBoolean("spent");
                boolean confirmed = rs.getBoolean("confirmed");
                boolean spendPending = rs.getBoolean("spendpending");
                String tokenid = rs.getString("tokenid");
                long minimumsign = 0;
                UTXO output = new UTXO(hash, index, amount, coinbase, new Script(scriptBytes), toAddress, blockhash,
                        fromaddress1, memo, tokenid, spent, confirmed, spendPending, minimumsign);
                output.setTime(rs.getLong("time"));
                UTXOs.add(output);
            }
            return UTXOs;
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
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

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
    public HashSet<BlockWrap> getBlocksToAddToMilestone() throws BlockStoreException {
        HashSet<BlockWrap> storedBlockHashes = new HashSet<>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKWRAPS_TO_ADD_TO_MILESTONE_SQL);
            preparedStatement.setLong(1, 0);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

                Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
                block.verifyHeader();
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
    public List<BlockWrap> getBlocksInMilestoneDepthInterval(long minDepth, long maxDepth) throws BlockStoreException {
        List<BlockWrap> storedBlockHashes = new ArrayList<>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONEDEPTH_INTERVAL_SQL);
            preparedStatement.setLong(1, minDepth);
            preparedStatement.setLong(2, maxDepth);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

                Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
                block.verifyHeader();
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
    public HashSet<Sha256Hash> getMaintainedBlockHashes() throws BlockStoreException {
        HashSet<Sha256Hash> storedBlockHashes = new HashSet<>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MAINTAINED_BLOCKS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next())
                storedBlockHashes.add(Sha256Hash.wrap(resultSet.getBytes(1)));
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
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

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
    public PriorityQueue<BlockWrap> getSolidTipsDescending() throws BlockStoreException {
        PriorityQueue<BlockWrap> blocksByDescendingHeight = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_SOLID_TIPS_SQL);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

                Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
                block.verifyHeader();
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
    public PriorityQueue<BlockWrap> getRatingEntryPointsAscending() throws BlockStoreException {
        PriorityQueue<BlockWrap> resultQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_IN_MILESTONEDEPTH_INTERVAL_SQL);
            preparedStatement.setLong(1, 0);
            preparedStatement.setLong(2, NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

                Block block = params.getDefaultSerializer().makeBlock(resultSet.getBytes(11));
                block.verifyHeader();
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
    public List<Sha256Hash> getBlocksOfTimeHigherThan(long time) throws BlockStoreException {
        List<Sha256Hash> storedBlockHashes = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_BLOCKS_OF_INSERTTIME_HIGHER_THAN_SQL);
            preparedStatement.setLong(1, time);
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
    public List<Sha256Hash> getConfirmedBlocksOfHeightHigherThan(long fromHeight) throws BlockStoreException {
        List<Sha256Hash> storedBlockHashes = new ArrayList<Sha256Hash>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_CONFIRMED_BLOCKS_OF_HEIGHT_HIGHER_THAN_SQL);
            preparedStatement.setLong(1, fromHeight);
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
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));

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
    public void updateBlockEvaluationWeightAndDepth(Sha256Hash blockhash, long weight, long depth)
            throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL);
            preparedStatement.setLong(1, weight);
            preparedStatement.setLong(2, depth);
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

    @Override
    public void updateAllBlocksMaintained() throws BlockStoreException {

        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ALL_BLOCKS_MAINTAINED_SQL);
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
    public void deleteOldUnsolid(long time) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_OLD_UNSOLIDBLOCKS_SQL);
            preparedStatement.setLong(1, time);
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
                Block block = params.getDefaultSerializer().makeBlock(results.getBytes(1));
                block.verifyHeader();
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
            preparedStatement.setBytes(2, block.bitcoinSerialize());
            preparedStatement.setLong(3, block.getTimeSeconds());
            preparedStatement.setLong(4, solidityState.getState().ordinal());
            switch (solidityState.getState()) {
            case MissingPredecessor:
            case MissingTransactionOutput:
                preparedStatement.setBytes(5, solidityState.getMissingDependency());
                break;
            case Success:
                throw new RuntimeException("Should not happen");
            case Unfixable:
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
    public BlockEvaluation getTransactionOutputSpender(Sha256Hash txHash, long index) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_OUTPUT_SPENDER_SQL);
            preparedStatement.setBytes(1, txHash.getBytes());
            preparedStatement.setLong(2, index);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                    resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                    resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                    resultSet.getBoolean(10));
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
    public void updateTransactionOutputSpent(Sha256Hash prevTxHash, long index, boolean b,
            @Nullable Sha256Hash spenderBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(getUpdateOutputsSpentSQL());
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlockHash != null ? spenderBlockHash.getBytes() : null);
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
    public void updateTransactionOutputConfirmingBlock(Sha256Hash prevTxHash, int index, Sha256Hash blockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_OUTPUTS_CONFIRMING_BLOCK_SQL);
            preparedStatement.setBytes(1, blockHash != null ? blockHash.getBytes() : null);
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
    public List<Token> getTokensList(Set<String> tokenids) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_CONFIRMED_TOKENS_SQL + " AND tokentype=0 ";
            if (tokenids != null && !tokenids.isEmpty()) {
                sql += "  and tokenid in ( " + buildINList(tokenids) + " )";
            }
            sql += LIMIT_5000;
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {

                Token tokens = new Token();
                tokens.setBlockhash(resultSet.getString("blockhash"));
                tokens.setConfirmed(resultSet.getBoolean("confirmed"));
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                tokens.setAmount(resultSet.getLong("amount"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setUrl(resultSet.getString("url"));
                tokens.setSignnumber(resultSet.getInt("signnumber"));

                tokens.setTokentype(resultSet.getInt("tokentype"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                byte[] buf = resultSet.getBytes("tokenkeyvalues");
                if (buf != null) {
                    tokens.setTokenKeyValues(TokenKeyValues.parse(buf));
                }
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
                tokens.setBlockhash(resultSet.getString("blockhash"));
                tokens.setConfirmed(resultSet.getBoolean("confirmed"));
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                tokens.setAmount(resultSet.getLong("amount"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setUrl(resultSet.getString("url"));
                tokens.setSignnumber(resultSet.getInt("signnumber"));

                tokens.setTokentype(resultSet.getInt("tokentype"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                byte[] buf = resultSet.getBytes("tokenkeyvalues");
                if (buf != null) {
                    tokens.setTokenKeyValues(TokenKeyValues.parse(buf));
                }
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
    public List<Token> getTokensList(String name) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = SELECT_CONFIRMED_TOKENS_SQL;
            if (name != null && !"".equals(name.trim())) {
                sql += " AND (tokenname LIKE '%" + name + "%' OR description LIKE '%" + name + "%')";
            }
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Token tokens = new Token();
                tokens.setBlockhash(resultSet.getString("blockhash"));
                tokens.setConfirmed(resultSet.getBoolean("confirmed"));
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                tokens.setAmount(resultSet.getLong("amount"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setUrl(resultSet.getString("url"));
                tokens.setSignnumber(resultSet.getInt("signnumber"));

                tokens.setTokentype(resultSet.getInt("tokentype"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                byte[] buf = resultSet.getBytes("tokenkeyvalues");
                if (buf != null) {
                    tokens.setTokenKeyValues(TokenKeyValues.parse(buf));
                }
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
    public void insertToken(String blockhash, Token tokens) throws BlockStoreException {
        boolean confirmed = false;
        String tokenid = tokens.getTokenid();
        long tokenindex = tokens.getTokenindex();
        long amount = tokens.getAmount();
        String tokenname = tokens.getTokenname();
        String description = tokens.getDescription();
        String url = tokens.getUrl();
        int signnumber = tokens.getSignnumber();

        int tokentype = tokens.getTokentype();
        boolean tokenstop = tokens.isTokenstop();
        String prevblockhash = tokens.getPrevblockhash();
        byte[] tokenkeyvalues = null;
        if (tokens.getTokenKeyValues() != null) {
            tokenkeyvalues = tokens.getTokenKeyValues().toByteArray();
        }
        this.insertToken(blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, url, signnumber,
                tokentype, tokenstop, prevblockhash, tokenkeyvalues);
    }

    @Override
    public void insertToken(String blockhash, boolean confirmed, String tokenid, long tokenindex, long amount,
            String tokenname, String description, String url, int signnumber, int tokentype, boolean tokenstop,
            String prevblockhash, byte[] tokenkeyvalues) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(INSERT_TOKENS_SQL);
            preparedStatement.setString(1, blockhash);
            preparedStatement.setBoolean(2, confirmed);
            preparedStatement.setString(3, tokenid);
            preparedStatement.setLong(4, tokenindex);
            preparedStatement.setLong(5, amount);
            preparedStatement.setString(6, tokenname);
            preparedStatement.setString(7, description);
            preparedStatement.setString(8, url);
            preparedStatement.setInt(9, signnumber);

            preparedStatement.setInt(10, tokentype);
            preparedStatement.setBoolean(11, tokenstop);
            preparedStatement.setString(12, prevblockhash);
            preparedStatement.setBoolean(13, false);
            preparedStatement.setString(14, null);
            preparedStatement.setBytes(15, tokenkeyvalues);
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
    public String getTokenPrevblockhash(String blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_PREVBLOCKHASH_SQL);
            preparedStatement.setString(1, blockhash);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getString(1);
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
    public boolean getTokenSpent(String blockhash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL);
            preparedStatement.setString(1, blockhash);
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
    public boolean getTokenAnySpent(String tokenId, long tokenIndex) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_ANY_SPENT_BY_TOKEN_SQL);
            preparedStatement.setString(1, tokenId);
            preparedStatement.setLong(2, tokenIndex);
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
    public boolean getTokenConfirmed(String blockHash) throws BlockStoreException {
        PreparedStatement preparedStatement = null;
        maybeConnect();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_CONFIRMED_SQL);
            preparedStatement.setString(1, blockHash);
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
            return getBlockWrap(Sha256Hash.wrap(resultSet.getString(1)));
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
    public void updateTokenSpent(String blockhash, boolean b, Sha256Hash spenderBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(UPDATE_TOKEN_SPENT_SQL);
            preparedStatement.setBoolean(1, b);
            preparedStatement.setBytes(2, spenderBlockHash == null ? null : spenderBlockHash.getBytes());
            preparedStatement.setString(3, blockhash);
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
    public void updateTokenConfirmed(String blockHash, boolean confirmed) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(UPDATE_TOKEN_CONFIRMED_SQL);
            preparedStatement.setBoolean(1, confirmed);
            preparedStatement.setString(2, blockHash);
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
                BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(resultSet.getBytes(1)),
                        resultSet.getLong(2), resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                        resultSet.getBoolean(6), resultSet.getLong(7), resultSet.getLong(8), resultSet.getLong(9),
                        resultSet.getBoolean(10));
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
    public List<BlockEvaluationDisplay> getSearchBlockEvaluations(List<String> address, String lastestAmount)
            throws BlockStoreException {

        String sql = "";
        StringBuffer stringBuffer = new StringBuffer();
        if (!"0".equalsIgnoreCase(lastestAmount) && !"".equalsIgnoreCase(lastestAmount)) {
            sql += "SELECT hash, rating, depth, cumulativeweight, "
                    + " height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained, blocktype "
                    + "  FROM  blocks ";
            sql += " ORDER BY insertTime desc ";
            Integer a = Integer.valueOf(lastestAmount);
            if (a > 1000) {
                a = 2000;
            }
            sql += " LIMIT " + a;
        } else {
            sql += "SELECT blocks.hash, rating, depth, cumulativeweight, "
                    + " blocks.height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained, blocktype"
                    + " FROM outputs JOIN blocks " + "ON outputs.blockhash = blocks.hash  ";
            sql += "WHERE outputs.toaddress in ";
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
                        Sha256Hash.wrap(resultSet.getBytes(1)), resultSet.getLong(2), resultSet.getLong(3),
                        resultSet.getLong(4), resultSet.getLong(5), resultSet.getBoolean(6), resultSet.getLong(7),
                        resultSet.getLong(8), resultSet.getLong(9), resultSet.getBoolean(10), resultSet.getInt(11));
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
    public List<MultiSignAddress> getMultiSignAddressListByTokenidAndBlockHashHex(String tokenid, String prevblockhash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<MultiSignAddress> list = new ArrayList<MultiSignAddress>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MULTISIGNADDRESS_SQL);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, prevblockhash);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String tokenid0 = resultSet.getString("tokenid");
                String address = resultSet.getString("address");
                String pubKeyHex = resultSet.getString("pubKeyHex");
                MultiSignAddress multiSignAddress = new MultiSignAddress(tokenid0, address, pubKeyHex);
                int posIndex = resultSet.getInt("posIndex");
                multiSignAddress.setPosIndex(posIndex);
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
            preparedStatement.setString(5, multiSignAddress.getBlockhash());
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
                tokens.setBlockhash(resultSet.getString("blockhash"));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                return tokens;
            } else {
                tokens.setBlockhash("");
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
    public Token getToken(String blockhash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {

            preparedStatement = conn.get().prepareStatement(SELECT_TOKEN_SQL);
            preparedStatement.setString(1, blockhash);
            ResultSet resultSet = preparedStatement.executeQuery();
            Token tokens = null;
            if (resultSet.next()) {
                tokens = new Token();
                tokens.setBlockhash(resultSet.getString("blockhash"));
                tokens.setConfirmed(resultSet.getBoolean("confirmed"));
                tokens.setTokenid(resultSet.getString("tokenid"));
                tokens.setTokenindex(resultSet.getInt("tokenindex"));
                tokens.setAmount(resultSet.getLong("amount"));
                tokens.setTokenname(resultSet.getString("tokenname"));
                tokens.setDescription(resultSet.getString("description"));
                tokens.setUrl(resultSet.getString("url"));
                tokens.setSignnumber(resultSet.getInt("signnumber"));
                tokens.setTokentype(resultSet.getInt("tokentype"));
                tokens.setTokenstop(resultSet.getBoolean("tokenstop"));
                byte[] buf = resultSet.getBytes("tokenkeyvalues");
                if (buf != null) {
                    tokens.setTokenKeyValues(TokenKeyValues.parse(buf));
                }
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

    // TODO do nothing here?
    public List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String> addresses)
            throws BlockStoreException {
        List<TokenSerial> list = new ArrayList<TokenSerial>();
        return list;
        /*
         * maybeConnect(); PreparedStatement preparedStatement = null; String
         * sql = SELECT_SEARCH_TOKENSERIAL_ALL_SQL; if (addresses != null &&
         * !addresses.isEmpty()) { String addressString = " AND address IN(";
         * for (String address : addresses) { addressString += "'" + address +
         * "',"; } addressString = addressString.substring(0,
         * addressString.length() - 1) + ") "; sql += addressString; } if
         * (tokenid != null && !tokenid.trim().isEmpty()) { sql +=
         * " AND tokenid=?"; } sql += " ORDER BY tokenid,tokenindex";
         * 
         * try { preparedStatement = conn.get().prepareStatement(sql); if
         * (tokenid != null && !tokenid.trim().isEmpty()) {
         * preparedStatement.setString(1, tokenid); } ResultSet resultSet =
         * preparedStatement.executeQuery(); while (resultSet.next()) { String
         * tokenid0 = resultSet.getString("tokenid"); long tokenindex =
         * resultSet.getLong("tokenindex"); long amount =
         * resultSet.getLong("amount"); TokenSerial tokenSerial = new
         * TokenSerial(tokenid0, tokenindex, amount,
         * resultSet.getLong("signnumber"), resultSet.getLong("count"));
         * list.add(tokenSerial); } return list; } catch (SQLException ex) {
         * throw new BlockStoreException(ex); } finally { if (preparedStatement
         * != null) { try { preparedStatement.close(); } catch (SQLException e)
         * { throw new BlockStoreException("Failed to close PreparedStatement");
         * } } }
         */
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
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, Set<String> addresses, boolean isSign)
            throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();
        maybeConnect();
        PreparedStatement preparedStatement = null;
        String sql = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE 1 = 1 ";
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND address IN( " + buildINList(addresses) + " ) ";
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

    private String buildINList(Set<String> datalist) {
        String in = "";
        for (String d : datalist) {
            in += "'" + d + "',";
        }
        in = in.substring(0, in.length() - 1);
        return in;
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
    public void deleteMultiSignAddressByTokenidAndBlockhash(String tokenid, String blockhash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MULTISIGNADDRESS_SQL0);
            preparedStatement.setString(1, tokenid);
            preparedStatement.setString(2, blockhash);
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
    public Eligibility getRewardEligible(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_ELIGIBLE_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return Eligibility.values()[resultSet.getInt(1)];
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
    public long getRewardToHeight(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_TOHEIGHT_SQL);
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
    public void insertReward(Sha256Hash hash, long toHeight, Eligibility eligibility, Sha256Hash prevBlockHash,
            long nextTxReward) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_TX_REWARD_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            preparedStatement.setLong(2, toHeight);
            preparedStatement.setBoolean(3, false);
            preparedStatement.setBoolean(4, false);
            preparedStatement.setBytes(5, null);
            preparedStatement.setInt(6, eligibility.ordinal());
            preparedStatement.setBytes(7, prevBlockHash.getBytes());
            preparedStatement.setLong(8, nextTxReward);
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
    public void updateRewardNextTxReward(Sha256Hash hash, long nextTxReward) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_TX_REWARD_NEXT_TX_REWARD_SQL);
            preparedStatement.setLong(1, nextTxReward);
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
    public long getRewardNextTxReward(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_NEXT_TX_REWARD_SQL);
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
    public Sha256Hash getMaxConfirmedRewardBlockHash() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL);
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
            preparedStatement.setString(2, outputsMulti.getToAddress());
            preparedStatement.setLong(3, outputsMulti.getOutputIndex());
            preparedStatement.setLong(4, outputsMulti.getMinimumSignCount());
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
                long minimumSignCount = resultSet.getLong("minimumsign");
                OutputsMulti outputsMulti = new OutputsMulti(sha256Hash, address, outputindex, minimumSignCount);
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
            preparedStatement.setLong(5, payMultiSign.getAmount());
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
            payMultiSign.setAmount(resultSet.getLong("amount"));
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
                payMultiSign.setAmount(resultSet.getLong("amount"));
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
                + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
                + " spendpending FROM outputs WHERE hash = ? and outputindex = ?";

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
            Coin coinvalue = Coin.valueOf(results.getLong(1), results.getString(7));
            byte[] scriptBytes = results.getBytes(2);
            boolean coinbase = results.getBoolean(3);
            String address = results.getString(4);
            Sha256Hash blockhash = results.getBytes(6) != null ? Sha256Hash.wrap(results.getBytes(6)) : null;

            String fromaddress = results.getString(8);
            String memo = results.getString(9);
            boolean spent = results.getBoolean(10);
            boolean confirmed = results.getBoolean(11);
            boolean spendPending = results.getBoolean(12);
            String tokenid = results.getString("tokenid");

            // long outputindex = results.getLong("outputindex");

            UTXO utxo = new UTXO(Sha256Hash.wrap(hash), outputindex, coinvalue, coinbase, new Script(scriptBytes),
                    address, blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, 0);
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
            preparedStatement.setBytes(2, block.bitcoinSerialize());
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
                batchBlock.setBlock(resultSet.getBytes("block"));
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
                result.put(Sha256Hash.wrap(resultSet.getBytes(1)), new OrderRecord(
                        Sha256Hash.wrap(resultSet.getBytes(1)), Sha256Hash.wrap(resultSet.getBytes(2)),
                        resultSet.getLong(3), resultSet.getString(4), resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15)));
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
    public List<Sha256Hash> getLostOrders(long toHeight) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Sha256Hash> result = new ArrayList<>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_LOST_ORDERS_BEFORE_SQL);
            preparedStatement.setLong(1, toHeight - NetworkParameters.ORDER_MATCHING_OVERLAP_SIZE);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                result.add(Sha256Hash.wrap(resultSet.getBytes(1)));
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

            return new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)), Sha256Hash.wrap(resultSet.getBytes(2)),
                    resultSet.getLong(3), resultSet.getString(4), resultSet.getBoolean(5), resultSet.getBoolean(6),
                    resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)), resultSet.getLong(8),
                    resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11), resultSet.getInt(12),
                    resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
    public void insertOrder(OrderRecord record) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_ORDER_SQL);
            preparedStatement.setBytes(1, record.getInitialBlockHash().getBytes());
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
            preparedStatement.setInt(12, record.getOpIndex());
            preparedStatement.setLong(13, record.getValidFromTime());
            preparedStatement.setString(14, record.getSide() == null ? null : record.getSide().name());
            preparedStatement.setString(15, record.getBeneficiaryAddress());
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
    public List<OrderRecord> getAllOrdersSorted() throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_ORDERS_SORTED_SQL);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
    public List<UTXO> getAllUTXOsSorted() throws BlockStoreException {
        List<UTXO> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_UTXOS_SORTED_SQL);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                // Parse it.
                Coin coinvalue = Coin.valueOf(resultSet.getLong(1), resultSet.getString(7));
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
                UTXO txout = new UTXO(Sha256Hash.wrap(hash), index, coinvalue, coinbase, new Script(scriptBytes),
                        address, blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, 0);
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
    public List<OrderRecord> getAllAvailableOrdersSorted(boolean spent) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_AVAILABLE_ORDERS_SORTED_SQL);
            s.setBoolean(1, spent);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
    public List<OrderRecord> getAllAvailableOrdersSorted(boolean spent, String address, List<String> addresses)
            throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        String sql = SELECT_MY_AVAILABLE_ORDERS_SORTED_SQL;
        String orderby = " ORDER BY blockhash, collectinghash";
        if (address != null && !address.trim().isEmpty()) {
            sql += " AND beneficiaryaddress=? ";
        }
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND beneficiaryaddress in (";
            String temp = "";
            for (String addr : addresses) {
                temp += ",'" + addr + "'";
            }
            sql += temp.substring(1) + ")";
        }
        sql += orderby;
        PreparedStatement s = null;
        try {
            log.debug(sql);
            s = conn.get().prepareStatement(sql);
            s.setBoolean(1, spent);
            if (address != null && !address.trim().isEmpty()) {
                s.setString(2, address);
            }
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
    public List<OrderRecord> getMyClosedOrders(String address) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_MY_CLOSED_ORDERS_SQL + LIMIT_5000);
            s.setString(1, address);
            s.setString(2, address);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
            s = conn.get().prepareStatement(SELECT_MY_REMAINING_OPEN_ORDERS_SQL + LIMIT_5000);
            s.setString(1, address);
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
                OrderRecord order = new OrderRecord(Sha256Hash.wrap(resultSet.getBytes(1)),
                        Sha256Hash.wrap(resultSet.getBytes(2)), resultSet.getLong(3), resultSet.getString(4),
                        resultSet.getBoolean(5), resultSet.getBoolean(6),
                        resultSet.getBytes(7) == null ? null : Sha256Hash.wrap(resultSet.getBytes(7)),
                        resultSet.getLong(8), resultSet.getString(9), resultSet.getBytes(10), resultSet.getLong(11),
                        resultSet.getInt(12), resultSet.getLong(13), resultSet.getString(14), resultSet.getString(15));
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
                Coin coinvalue = Coin.valueOf(resultSet.getLong(1), resultSet.getString(7));
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
                UTXO txout = new UTXO(Sha256Hash.wrap(hash), index, coinvalue, coinbase, new Script(scriptBytes),
                        address, blockhash, fromaddress, memo, tokenid, spent, confirmed, spendPending, 0);
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
    public void insertDependents(Sha256Hash blockHash, Sha256Hash dependencyBlockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(INSERT_DEPENDENTS_SQL);
            s.setBytes(1, blockHash.getBytes());
            s.setBytes(2, dependencyBlockHash.getBytes());
            s.executeUpdate();
        } catch (SQLException e) {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
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
    public void removeDependents(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(REMOVE_DEPENDENCIES_SQL);
            s.setBytes(1, blockHash.getBytes());
            s.executeUpdate();
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
    public List<Sha256Hash> getDependents(Sha256Hash blockHash) throws BlockStoreException {
        List<Sha256Hash> result = new ArrayList<>();
        maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement(SELECT_DEPENDENTS_SQL);
            s.setBytes(1, blockHash.getBytes());
            ResultSet resultSet = s.executeQuery();
            while (resultSet.next()) {
                result.add(Sha256Hash.wrap(resultSet.getBytes(1)));
            }
            return result;
        } catch (SQLException e) {
            throw new BlockStoreException(e);
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
    public Eligibility getOrderMatchingEligible(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_ELIGIBLE_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return Eligibility.values()[resultSet.getInt(1)];
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
    public boolean getOrderMatchingSpent(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_SPENT_SQL);
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
    public Sha256Hash getOrderMatchingSpender(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_SPENDER_SQL);
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
    public Sha256Hash getOrderMatchingPrevBlockHash(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_PREVBLOCKHASH_SQL);
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
    public long getOrderMatchingToHeight(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_TOHEIGHT_SQL);
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
    public long getOrderMatchingFromHeight(Sha256Hash blockHash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_FROMHEIGHT_SQL);
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
    public boolean getOrderMatchingConfirmed(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_CONFIRMED_SQL);
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
    public void insertOrderMatching(Sha256Hash hash, long toHeight, Eligibility eligibility, Sha256Hash prevBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_ORDER_MATCHING_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            preparedStatement.setLong(2, toHeight);
            preparedStatement.setBoolean(3, false);
            preparedStatement.setBoolean(4, false);
            preparedStatement.setBytes(5, null);
            preparedStatement.setInt(6, eligibility.ordinal());
            preparedStatement.setBytes(7, prevBlockHash.getBytes());
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
    public void updateOrderMatchingConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_MATCHING_CONFIRMED_SQL);
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
    public void updateOrderMatchingSpent(Sha256Hash hash, boolean b, @Nullable Sha256Hash spenderBlockHash)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(UPDATE_ORDER_MATCHING_SPENT_SQL);
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
    public Sha256Hash getMaxConfirmedOrderMatchingBlockHash() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_MAX_CONFIRMED_SQL);
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
    public List<Sha256Hash> getOrderMatchingBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_ORDER_MATCHING_WHERE_PREV_HASH_SQL);
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
    public void insertMatchingEvent(Sha256Hash hash, String key, Match match, long currentTimeMillis)
            throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_MATCHING_EVENT_SQL);
            preparedStatement.setBytes(1, hash.getBytes());
            preparedStatement.setString(2, key);
            preparedStatement.setString(3, match.restingOrderId);
            preparedStatement.setString(4, match.restingOrderId);
            preparedStatement.setBoolean(5, match.incomingSide == Side.BUY);
            preparedStatement.setLong(6, match.price);
            preparedStatement.setLong(7, match.executedQuantity);
            preparedStatement.setLong(8, match.remainingQuantity);
            preparedStatement.setLong(9, currentTimeMillis);
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
    public List<Match> getLastMatchingEvents(String tokenId, int count) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_MATCHING_EVENT_BY_TIME_SQL);
            preparedStatement.setString(1, tokenId);
            preparedStatement.setInt(2, count);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<Match> list = new ArrayList<>();
            while (resultSet.next()) {
                list.add(new Match(resultSet.getString(3), resultSet.getString(4), 
                        resultSet.getBoolean(5) ? Side.BUY : Side.SELL, resultSet.getLong(6), resultSet.getLong(7), resultSet.getLong(8)));
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
    public void deleteMatchingEvents(Sha256Hash hash) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_MATCHING_EVENT_BY_HASH);
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
}
