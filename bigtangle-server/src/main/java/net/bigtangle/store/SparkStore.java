/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.ContractExecution;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Token;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.ordermatch.AVGMatchResult;
import net.bigtangle.core.ordermatch.MatchLastdayResult;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.script.Script;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.BatchBlock;
import net.bigtangle.server.data.ChainBlockQueue;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.DepthAndWeight;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.Rating;
import net.bigtangle.server.model.BlockModel;
import net.bigtangle.server.model.MCMCModel;
import net.bigtangle.server.model.MultiSignAddressModel;
import net.bigtangle.server.model.MultiSignModel;
import net.bigtangle.server.model.OrderCancelModel;
import net.bigtangle.server.model.OrderRecordModel;
import net.bigtangle.server.model.OutputsMultiModel;
import net.bigtangle.server.model.PayMultiSignAddressModel;
import net.bigtangle.server.model.PayMultiSignModel;
import net.bigtangle.server.model.TXRewardModel;
import net.bigtangle.server.model.TokenModel;
import net.bigtangle.server.model.UTXOModel;
import net.bigtangle.server.model.UserDataModel;
import net.bigtangle.utils.Gzip;

/**
 * <p>
 * A generic full pruned block store for a spark. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
@SuppressWarnings(value = { "rawtypes", "unchecked", "serial" })
public class SparkStore implements FullBlockStore {

    private static final String OPENORDERHASH = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private static final String LIMIT_500 = " limit 500 ";

    private static final Logger log = LoggerFactory.getLogger(SparkStore.class);

    public static final String VERSION_SETTING = "version";

    // Drop table SQL.
    private static String DROP_SETTINGS_TABLE = "DROP TABLE IF EXISTS settings";
    private static String DROP_BLOCKS_TABLE = "DROP TABLE IF EXISTS blocks";
    private static String DROP_OPEN_OUTPUT_TABLE = "DROP TABLE IF EXISTS outputs";
    private static String DROP_OUTPUTSMULTI_TABLE = "DROP TABLE IF EXISTS outputsmulti";
    private static String DROP_TOKENS_TABLE = "DROP TABLE IF EXISTS tokens";
    private static String DROP_MATCHING_TABLE = "DROP TABLE IF EXISTS matching";
    private static String DROP_MULTISIGNADDRESS_TABLE = "DROP TABLE IF EXISTS multisignaddress";
    private static String DROP_MULTISIGNBY_TABLE = "DROP TABLE IF EXISTS multisignby";
    private static String DROP_MULTISIGN_TABLE = "DROP TABLE IF EXISTS multisign";
    private static String DROP_TX_REWARDS_TABLE = "DROP TABLE IF EXISTS txreward";
    private static String DROP_USERDATA_TABLE = "DROP TABLE IF EXISTS userdata";
    private static String DROP_PAYMULTISIGN_TABLE = "DROP TABLE IF EXISTS paymultisign";
    private static String DROP_PAYMULTISIGNADDRESS_TABLE = "DROP TABLE IF EXISTS paymultisignaddress";
    private static String DROP_CONTRACT_EXECUTION_TABLE = "DROP TABLE IF EXISTS contractexecution";
    private static String DROP_ORDERCANCEL_TABLE = "DROP TABLE IF EXISTS ordercancel";
    private static String DROP_BATCHBLOCK_TABLE = "DROP TABLE IF EXISTS batchblock";
    private static String DROP_SUBTANGLE_PERMISSION_TABLE = "DROP TABLE IF EXISTS subtangle_permission";
    private static String DROP_ORDERS_TABLE = "DROP TABLE IF EXISTS orders";

    private static String DROP_MYSERVERBLOCKS_TABLE = "DROP TABLE IF EXISTS myserverblocks";
    private static String DROP_EXCHANGE_TABLE = "DROP TABLE exchange";
    private static String DROP_EXCHANGEMULTI_TABLE = "DROP TABLE exchange_multisign";
    private static String DROP_ACCESS_PERMISSION_TABLE = "DROP TABLE access_permission";
    private static String DROP_ACCESS_GRANT_TABLE = "DROP TABLE access_grant";
    private static String DROP_CONTRACT_EVENT_TABLE = "DROP TABLE contractevent";
    private static String DROP_CONTRACT_ACCOUNT_TABLE = "DROP TABLE contractaccount";
    private static String DROP_CHAINBLOCKQUEUE_TABLE = "DROP TABLE chainblockqueue";
    private static String DROP_MCMC_TABLE = "DROP TABLE mcmc";
    private static String DROP_LOCKOBJECT_TABLE = "DROP TABLE lockobject";
    private static String DROP_MATCHING_LAST_TABLE = "DROP TABLE matchinglast";
    private static String DROP_MATCHINGDAILY_TABLE = "DROP TABLE matchingdaily";
    private static String DROP_MATCHINGLASTDAY_TABLE = "DROP TABLE matchinglastday";
    // Queries SQL.
    protected final String SELECT_SETTINGS_SQL = "SELECT settingvalue FROM settings WHERE name = %s";

    protected final String SELECT_BLOCKS_TEMPLATE = "  blocks.hash as hash, block, prevblockhash, prevbranchblockhash"
            + "  height, milestone, milestonelastupdate,  inserttime,   solid, confirmed";

    protected final String SELECT_BLOCKS_SQL = " select " + SELECT_BLOCKS_TEMPLATE + " FROM blocks WHERE hash =  ";

    protected final String SELECT_BLOCKS_MILESTONE_SQL = " select " + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE height "
            + " >= (select min(height) from blocks where  milestone >= %s and  milestone <=%s)"
            + " and height <= (select max(height) from blocks where  milestone >= %s and  milestone <=%s) "
            + " order by height asc ";

    protected final String SELECT_MCMC_TEMPLATE = "  hash, rating, depth, cumulativeweight ";

    protected final String SELECT_NOT_INVALID_APPROVER_BLOCKS_SQL = "SELECT " + SELECT_BLOCKS_TEMPLATE
            + "  , rating, depth, cumulativeweight "
            + "  FROM blocks, mcmc WHERE blocks.hash= mcmc.hash and (prevblockhash = %s or prevbranchblockhash = %s) AND solid >= 0 ";

    protected final String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " ,  rating, depth, cumulativeweight "
            + " FROM blocks, mcmc WHERE blocks.hash= mcmc.hash and (prevblockhash = %s or prevbranchblockhash = %s) AND solid = 2 ";

    protected final String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM blocks "
            + "WHERE blocks.prevblockhash = %s or blocks.prevbranchblockhash = %s";

    protected final String SELECT_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
            + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending , spendpendingtime, minimumsign, time, spenderblockhash FROM outputs WHERE hash = %s AND outputindex = %s AND blockhash = %s ";

    protected final String SELECT_TRANSACTION_OUTPUTS_SQL_BASE = "SELECT " + "outputs.hash, coinvalue, scriptbytes, "
            + " outputs.outputindex, coinbase, " + "  outputs.toaddress  as  toaddress,"
            + " outputsmulti.toaddress  as multitoaddress, " + "  addresstargetable, blockhash, tokenid, "
            + " fromaddress, memo, spent, confirmed, "
            + "spendpending,spendpendingtime,  minimumsign, time , spenderblockhash "
            + " FROM outputs LEFT JOIN outputsmulti " + " ON outputs.hash = outputsmulti.hash"
            + " AND outputs.outputindex = outputsmulti.outputindex ";

    protected final String SELECT_OPEN_TRANSACTION_OUTPUTS_SQL = SELECT_TRANSACTION_OUTPUTS_SQL_BASE
            + " WHERE  confirmed=true and spent= false and outputs.toaddress = %s " + " OR outputsmulti.toaddress = %s";

    protected final String SELECT_OPEN_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress as toaddress , addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime, minimumsign, time , spenderblockhash"
            + " , outputsmulti.toaddress  as multitoaddress" + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE   (outputs.toaddress = %s " + " OR outputsmulti.toaddress = %s) " + " AND tokenid = %s";
    protected final String SELECT_ALL_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress, addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime , minimumsign, time , spenderblockhash"
            + " FROM outputs  WHERE  confirmed=true and spent= false and tokenid = %s";

    // Tables exist SQL.
    protected final String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

    protected final String SELECT_BLOCKS_TO_CONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks, mcmc  WHERE blocks.hash=mcmc.hash and solid=2 AND milestone = -1 AND confirmed = false AND height > %s"
            + " AND height <= %s AND mcmc.rating >= " + NetworkParameters.CONFIRMATION_UPPER_THRESHOLD;

    protected final String SELECT_BLOCKS_TO_UNCONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks , mcmc WHERE blocks.hash=mcmc.hash and solid=2 AND milestone = -1 AND confirmed = true AND mcmc.rating < "
            + NetworkParameters.CONFIRMATION_LOWER_THRESHOLD;

    protected final String SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE milestone >= %s AND milestone <= %s";

    protected final String SELECT_SOLID_BLOCKS_IN_INTERVAL_SQL = "SELECT   " + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks WHERE   height > %s AND height <= %s AND solid = 2 ";

    protected final String SELECT_BLOCKS_CONFIRMED_AND_NOT_MILESTONE_SQL = "SELECT hash "
            + "FROM blocks WHERE milestone = -1 AND confirmed = 1 ";

    protected final String SELECT_BLOCKS_NON_CHAIN_HEIGTH_SQL = "SELECT block "
            + "FROM blocks WHERE milestone = -1 AND height >= %s ";

    protected final String UPDATE_ORDER_SPENT_SQL = getUpdate() + " orders SET spent = %s, spenderblockhash = %s "
            + " WHERE blockhash = %s AND issuingmatcherblockhash = %s";
    protected final String UPDATE_ORDER_CONFIRMED_SQL = getUpdate() + " orders SET confirmed = %s "
            + " WHERE blockhash = %s AND issuingmatcherblockhash = %s";

    protected final String ORDER_TEMPLATE = "  blockhash, issuingmatcherblockhash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, "
            + "beneficiarypubkey, validToTime, validFromTime, side , beneficiaryaddress, orderbasetoken, price, tokendecimals ";
    protected final String SELECT_ORDERS_BY_ISSUER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE issuingmatcherblockhash = %s";

    protected final String SELECT_ORDER_SPENT_SQL = "SELECT spent FROM orders WHERE blockhash = %s AND issuingmatcherblockhash = %s";
    protected final String SELECT_ORDER_CONFIRMED_SQL = "SELECT confirmed FROM orders WHERE blockhash = %s AND issuingmatcherblockhash = %s";
    protected final String SELECT_ORDER_SPENDER_SQL = "SELECT spenderblockhash FROM orders WHERE blockhash = %s AND issuingmatcherblockhash = %s";
    protected final String SELECT_ORDER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE blockhash = %s AND issuingmatcherblockhash = %s";

    protected String SELECT_TOKENS_SQL_TEMPLATE = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, domainname, signnumber,tokentype, tokenstop ,"
            + "tokenkeyvalues, revoked,language,classification,decimals, domainpredblockhash ";

    protected final String SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL = "SELECT spent FROM tokens WHERE blockhash = %s";

    protected final String SELECT_TOKEN_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE blockhash = %s";

    protected final String SELECT_TOKEN_ANY_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE tokenid = %s AND tokenindex = %s AND confirmed = true";

    protected final String SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenid = %s AND tokenindex = %s AND confirmed = true";

    protected final String SELECT_DOMAIN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenname = %s AND domainpredblockhash = %s AND tokenindex = %s AND confirmed = true";

    protected final String SELECT_DOMAIN_DESCENDANT_CONFIRMED_BLOCKS_SQL = "SELECT blockhash FROM tokens WHERE domainpredblockhash = %s AND confirmed = true";

    protected final String SELECT_TOKEN_SPENDER_SQL = "SELECT spenderblockhash FROM tokens WHERE blockhash = %s";

    protected final String SELECT_TOKEN_PREVBLOCKHASH_SQL = "SELECT prevblockhash FROM tokens WHERE blockhash = %s";

    protected final String SELECT_TOKEN_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE blockhash = %s";

    protected final String SELECT_TOKENID_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE tokenid = %s";

    protected final String UPDATE_TOKEN_SPENT_SQL = getUpdate() + " tokens SET spent = %s, spenderblockhash = %s "
            + " WHERE blockhash = %s";

    protected final String UPDATE_TOKEN_CONFIRMED_SQL = getUpdate() + " tokens SET confirmed = %s "
            + " WHERE blockhash = %s";

    protected final String SELECT_CONFIRMED_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE confirmed = true";

    protected final String SELECT_MARKET_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE tokentype = 1 and confirmed = true";

    protected final String SELECT_TOKENS_ACOUNT_MAP_SQL = "SELECT tokenid, amount  as amount "
            + "FROM tokens WHERE confirmed = true ";

    protected final String COUNT_TOKENSINDEX_SQL = "SELECT blockhash, tokenindex FROM tokens"
            + " WHERE tokenid = %s AND confirmed = true ORDER BY tokenindex DESC limit 1";

    protected final String SELECT_TOKENS_BY_DOMAINNAME_SQL = "SELECT blockhash, tokenid FROM tokens WHERE blockhash = %s limit 1";

    protected final String SELECT_TOKENS_BY_DOMAINNAME_SQL0 = "SELECT blockhash, tokenid "
            + "FROM tokens WHERE tokenname = %s  AND confirmed = true limit 1";

    protected final String UPDATE_SETTINGS_SQL = getUpdate() + " settings SET settingvalue = %s WHERE name = %s";

    protected final String UPDATE_OUTPUTS_SPENT_SQL = getUpdate()
            + " outputs SET spent = %s, spenderblockhash = %s WHERE hash = %s AND outputindex= %s AND blockhash = %s";

    protected final String UPDATE_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = %s WHERE hash = %s AND outputindex= %s AND blockhash = %s";

    protected final String UPDATE_ALL_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = %s WHERE blockhash = %s";

    protected final String UPDATE_OUTPUTS_SPENDPENDING_SQL = getUpdate()
            + " outputs SET spendpending = %s, spendpendingtime=%s WHERE hash = %s AND outputindex= %s AND blockhash = %s";

    protected final String UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL = getUpdate()
            + " mcmc SET cumulativeweight = %s, depth = %s WHERE hash = %s";

    protected final String SELECT_MCMC_CHAINLENGHT_SQL = "  select mcmc.hash "
            + " from blocks, mcmc where mcmc.hash=blocks.hash and milestone < %s  and milestone > 0  ";

    protected final String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = %s, milestonelastupdate= %s  WHERE hash = %s";

    protected final String UPDATE_BLOCKEVALUATION_CONFIRMED_SQL = getUpdate()
            + " blocks SET confirmed = %s WHERE hash = %s";

    protected final String UPDATE_BLOCKEVALUATION_RATING_SQL = getUpdate() + " mcmc SET rating = %s WHERE hash = %s";

    protected final String UPDATE_BLOCKEVALUATION_SOLID_SQL = getUpdate() + " blocks SET solid = %s WHERE hash = %s";

    protected final String SELECT_MULTISIGNADDRESS_SQL = "SELECT blockhash, tokenid, address, pubkeyhex, posindex, tokenholder FROM multisignaddress WHERE tokenid = %s AND blockhash = %s";
    protected final String DELETE_MULTISIGNADDRESS_SQL = "DELETE FROM multisignaddress WHERE tokenid = %s AND address = %s";
    protected final String COUNT_MULTISIGNADDRESS_SQL = "SELECT COUNT(*) as count FROM multisignaddress WHERE tokenid = %s";

    protected final String INSERT_MULTISIGNBY_SQL = "INSERT INTO multisignby (tokenid, tokenindex, address) VALUES (%s, %s, %s)";
    protected final String SELECT_MULTISIGNBY_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = %s AND tokenindex = %s AND address = %s";
    protected final String SELECT_MULTISIGNBY0_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = %s AND tokenindex = %s";

    protected final String SELECT_MULTISIGN_ALL_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign,(select count(ms1.sign) from multisign ms1 where ms1.tokenid=tokenid and tokenindex=ms1.tokenindex and ms1.sign!=0 ) as count FROM multisign  WHERE 1=1 ";
    protected final String SELECT_MULTISIGN_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE address = %s ORDER BY tokenindex ASC";
    protected final String SELECT_MULTISIGN_TOKENID_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE tokenid = %s and address = %s ORDER BY tokenindex ASC";

    protected final String INSERT_MULTISIGN_SQL = "INSERT INTO multisign (tokenid, tokenindex, address, blockhash, sign, id) VALUES (%s, %s, %s, %s, %s, %s)";
    protected final String UPDATE_MULTISIGN_SQL = "UPDATE multisign SET blockhash = %s, sign = %s WHERE tokenid = %s AND tokenindex = %s AND address = %s";
    protected final String UPDATE_MULTISIGN1_SQL = "UPDATE multisign SET blockhash = %s WHERE tokenid = %s AND tokenindex = %s";
    protected final String SELECT_COUNT_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = %s AND tokenindex = %s AND address = %s ";
    protected final String SELECT_COUNT_ALL_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = %s AND tokenindex = %s  AND sign=%s";

    protected final String DELETE_MULTISIGN_SQL = "DELETE FROM multisign WHERE tokenid = %s";

    protected final String SELECT_COUNT_MULTISIGN_SIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = %s AND tokenindex = %s AND sign = %s";

    /* REWARD */
    protected final String SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=(SELECT MAX(chainlength) FROM txreward WHERE confirmed=1)";
    protected final String SELECT_TX_REWARD_CONFIRMED_AT_HEIGHT_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=%s";
    protected final String SELECT_TX_REWARD_ALL_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, "
            + "spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward "
            + "WHERE confirmed = 1 order by chainlength ";
    protected final String SELECT_TX_REWARD_CONFIRMED_SQL = "SELECT confirmed " + "FROM txreward WHERE blockhash = ?";
    protected final String SELECT_TX_REWARD_CHAINLENGTH_SQL = "SELECT chainlength "
            + "FROM txreward WHERE blockhash = %s";
    protected final String SELECT_TX_REWARD_DIFFICULTY_SQL = "SELECT difficulty "
            + "FROM txreward WHERE blockhash = %s";
    protected final String SELECT_TX_REWARD_SPENT_SQL = "SELECT spent " + "FROM txreward WHERE blockhash = %s";
    protected final String SELECT_TX_REWARD_SPENDER_SQL = "SELECT spenderblockhash "
            + "FROM txreward WHERE blockhash = %s";
    protected final String SELECT_TX_REWARD_PREVBLOCKHASH_SQL = "SELECT prevblockhash "
            + "FROM txreward WHERE blockhash = %s";
    protected final String SELECT_REWARD_WHERE_PREV_HASH_SQL = "SELECT blockhash "
            + "FROM txreward WHERE prevblockhash = %s";
    protected final String UPDATE_TX_REWARD_CONFIRMED_SQL = "UPDATE txreward SET confirmed = %s WHERE blockhash = %s";
    protected final String UPDATE_TX_REWARD_SPENT_SQL = "UPDATE txreward SET spent = %s, spenderblockhash = %s WHERE blockhash = %s";

    /* MATCHING EVENTS */
    protected final String SELECT_MATCHING_EVENT = "SELECT txhash, tokenid,basetokenid,  price, executedQuantity, inserttime "
            + "FROM matching ";
    protected final String DELETE_MATCHING_EVENT_BY_HASH = "DELETE FROM matching WHERE txhash = %s";
    // lastest MATCHING EVENTS
    protected final String SELECT_MATCHING_EVENT_LAST = "SELECT txhash, tokenid,basetokenid,  price, executedQuantity, inserttime "
            + "FROM matchinglast ";
    protected final String DELETE_MATCHING_EVENT_LAST_BY_KEY = "DELETE FROM matchinglast WHERE tokenid = %s and basetokenid=%s";

    /* OTHER */
    protected final String INSERT_OUTPUTSMULTI_SQL = "insert into outputsmulti (hash, toaddress, outputindex) values (%s, %s, %s)";
    protected final String SELECT_OUTPUTSMULTI_SQL = "select hash, toaddress, outputindex from outputsmulti where hash=%s and outputindex=%s";

    protected final String SELECT_USERDATA_SQL = "SELECT blockhash, dataclassname, data, pubKey, blocktype FROM userdata WHERE dataclassname = %s and pubKey = %s";
    protected final String INSERT_USERDATA_SQL = "INSERT INTO userdata (blockhash, dataclassname, data, pubKey, blocktype) VALUES (%s, %s, %s, %s, %s)";
    protected final String UPDATE_USERDATA_SQL = "UPDATE userdata SET blockhash = %s, data = %s WHERE dataclassname = %s and pubKey = %s";

    protected final String INSERT_BATCHBLOCK_SQL = "INSERT INTO batchblock (hash, block, inserttime) VALUE (%s, %s, %s)";
    protected final String DELETE_BATCHBLOCK_SQL = "DELETE FROM batchblock WHERE hash = %s";
    protected final String SELECT_BATCHBLOCK_SQL = "SELECT hash, block, inserttime FROM batchblock order by inserttime ASC";
    protected final String INSERT_SUBTANGLE_PERMISSION_SQL = "INSERT INTO  subtangle_permission (pubkey, userdataPubkey , status) VALUE (%s, %s, %s)";

    protected final String DELETE_SUBTANGLE_PERMISSION_SQL = "DELETE FROM  subtangle_permission WHERE pubkey=%s";
    protected final String UPATE_ALL_SUBTANGLE_PERMISSION_SQL = "UPDATE   subtangle_permission set status=%s ,userdataPubkey=%s WHERE  pubkey=%s ";

    protected final String SELECT_ALL_SUBTANGLE_PERMISSION_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission ";

    protected final String SELECT_SUBTANGLE_PERMISSION_BY_PUBKEYS_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission WHERE 1=1 ";

    protected final String SELECT_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders ORDER BY blockhash, issuingmatcherblockhash";

    protected final String SELECT_OPEN_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE confirmed=1 AND spent=0 ";

    protected final String SELECT_MY_REMAINING_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=%s ";
    protected final String SELECT_MY_INITIAL_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress=%s AND issuingmatcherblockhash=" + OPENORDERHASH
            + " AND blockhash IN ( SELECT blockhash FROM orders "
            + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=%s )";
    // TODO remove test
    protected final String SELECT_AVAILABLE_UTXOS_SORTED_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress, "
            + "addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,spendpendingtime, minimumsign, time, hash, outputindex, spenderblockhash "
            + " FROM outputs WHERE confirmed=1 AND spent=0 ORDER BY hash, outputindex";

    protected final String SELECT_ORDERCANCEL_SQL = "SELECT blockhash, orderblockhash, confirmed, spent, spenderblockhash,time FROM ordercancel WHERE 1 = 1";

    protected final String SELECT_CONTRACT_EXECUTION_SQL = "SELECT blockhash, contracttokenid confirmed, spent, "
            + "spenderblockhash, prevblockhash, difficulty, chainlength ";

    protected final String CONTRACT_EXECUTION_SELECT_MAX_CONFIRMED_SQL = SELECT_CONTRACT_EXECUTION_SQL
            + " FROM contractexecution" + " WHERE confirmed = 1 AND  contracttokenid = %s "
            + " AND chainlength=(SELECT MAX(chainlength) FROM contractexecution WHERE confirmed=1 and contracttokenid=%s)";

    protected final String BlockPrototype_SELECT_SQL = "   select prevblockhash, prevbranchblockhash, "
            + " inserttime from blockprototype   ";
    protected final String BlockPrototype_DELETE_SQL = "   delete from blockprototype  where  prevblockhash =%s and prevbranchblockhash=%s  ";

    protected final String ChainBlockQueueColumn = " hash, block, chainlength, orphan, inserttime";
    protected final String SELECT_CHAINBLOCKQUEUE = " select " + ChainBlockQueueColumn + " from chainblockqueue  ";

    protected NetworkParameters params;
    protected SparkSession sparkSession;
    protected String location;

    public SparkStore(NetworkParameters params, SparkSession sparkSession, String location) {
        this.params = params;
        this.sparkSession = sparkSession;
        this.location = location;
    }

    /*
     * delta llake table name
     */
    public String tablename(String name) throws BlockStoreException {
        return "delta.`" + location + "/" + name + "`" + " as " + name;
    }

    public void create() throws BlockStoreException {

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

    protected String getUpdate() {
        return "update ";
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
     * Get the SQL to drop all the tables (DDL).
     * 
     * @return The SQL drop statements.
     */
    protected List<String> getDropTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(DROP_SETTINGS_TABLE);
        sqlStatements.add(DROP_BLOCKS_TABLE);
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
        sqlStatements.add(DROP_CONTRACT_EXECUTION_TABLE);
        sqlStatements.add(DROP_ORDERCANCEL_TABLE);
        sqlStatements.add(DROP_BATCHBLOCK_TABLE);
        sqlStatements.add(DROP_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(DROP_ORDERS_TABLE);
        sqlStatements.add(DROP_MYSERVERBLOCKS_TABLE);
        sqlStatements.add(DROP_EXCHANGE_TABLE);
        sqlStatements.add(DROP_EXCHANGEMULTI_TABLE);
        sqlStatements.add(DROP_ACCESS_PERMISSION_TABLE);
        sqlStatements.add(DROP_ACCESS_GRANT_TABLE);
        sqlStatements.add(DROP_CONTRACT_EVENT_TABLE);
        sqlStatements.add(DROP_CONTRACT_ACCOUNT_TABLE);
        sqlStatements.add(DROP_CHAINBLOCKQUEUE_TABLE);
        sqlStatements.add(DROP_MCMC_TABLE);
        sqlStatements.add(DROP_LOCKOBJECT_TABLE);
        sqlStatements.add(DROP_MATCHING_LAST_TABLE);
        sqlStatements.add(DROP_MATCHINGDAILY_TABLE);
        sqlStatements.add(DROP_MATCHINGLASTDAY_TABLE);
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
        return sparkSession.sql(getTablesExistSQL()).count() > 0;

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
            updateTables(getCreateTablesSQL());
            // insert the initial settings for this store
            dbversion("05");
            createNewStore(params);

        } catch (Exception e) {
            log.error("", e);
            // this.abortDatabaseBatchWrite();
        }
    }

    /*
     * initial ps.setBytes(2, "03".getBytes());
     */
    private void dbversion(String version) throws SQLException {

    }

    protected void dbupdateversion(String version) throws SQLException {
        sparkSession.sql(UPDATE_SETTINGS_SQL);

    }

    /*
     * check version and update the tables
     */
    protected synchronized void updateTables(List<String> sqls) throws SQLException, BlockStoreException {
        for (String sql : sqls) {
            sparkSession.sql(sql + " USING DELTA " + "   LOCATION '" + location + "'");
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

            // insert MCMC table
            ArrayList<DepthAndWeight> depthAndWeight = new ArrayList<DepthAndWeight>();
            depthAndWeight.add(new DepthAndWeight(params.getGenesisBlock().getHash(), 1, 0));
            updateBlockEvaluationWeightAndDepth(depthAndWeight);

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
                    true, false, minsignnumber, 0, block.getTimeSeconds(), null);
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

        List<BlockModel> models = new ArrayList<>();

        models.add(BlockModel.from(block, blockEvaluation));

        Dataset source = sparkSession.createDataset(models, Encoders.bean(BlockModel.class));

        SparkData.blocks.as("target").merge(source.as("source"), "target.hash = source.hash ").whenNotMatched()
                .insertAll().execute();

    }

    @Override
    public void put(Block block) throws BlockStoreException {

        try {

            BlockEvaluation blockEval = BlockEvaluation.buildInitial(block);

            putUpdateStoredBlock(block, blockEval);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }

    }

    public Block get(Sha256Hash hash) throws BlockStoreException {
        try {
            return params.getDefaultSerializer().makeZippedBlock(Utils.HEX.decode(sparkSession
                    .sql(SELECT_BLOCKS_SQL + hash.toString()).as(Encoders.bean(BlockModel.class)).first().getBlock()));
        } catch (Exception ex) {
            throw new BlockStoreException(ex);
        }
    }

    public List<byte[]> blocksFromChainLength(Long start, Long end) throws BlockStoreException {
        // Optimize for chain head
        List<byte[]> re = new ArrayList<byte[]>();

        Dataset<BlockModel> s = sparkSession.sql(String.format(SELECT_BLOCKS_MILESTONE_SQL, start, end, start, end))
                .as(Encoders.bean(BlockModel.class));

        for (BlockModel b : s.collectAsList()) {
            try {
                re.add(Gzip.decompressOut(b.getBlock().getBytes()));
            } catch (IOException e) {
                throw new BlockStoreException(e);
            }
        }

        return re;
    }

    public List<byte[]> blocksFromNonChainHeigth(long heigth) throws BlockStoreException {
        // Optimize for chain head
        List<byte[]> re = new ArrayList<byte[]>();

        Dataset<BlockModel> s = sparkSession.sql(String.format(SELECT_BLOCKS_NON_CHAIN_HEIGTH_SQL, heigth))
                .as(Encoders.bean(BlockModel.class));

        for (BlockModel b : s.collectAsList()) {
            try {
                re.add(Gzip.decompressOut(b.getBlock().getBytes()));
            } catch (IOException e) {
                throw new BlockStoreException(e);
            }
        }

        return re;

    }

    private boolean verifyHeader(Block block) {
        try {
            block.verifyHeader();
            return true;
        } catch (VerificationException e) {
            return false;
        }
    }

    public BlockMCMC getMCMC(Sha256Hash hash) throws BlockStoreException {

        return sparkSession.sql("SELECT " + SELECT_MCMC_TEMPLATE + " from mcmc where hash =  " + quotedString(hash))
                .as(Encoders.bean(MCMCModel.class)).first().toBlockMCMC();

    }

    public List<BlockWrap> getNotInvalidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        try {
            List<BlockWrap> storedBlocks = new ArrayList<BlockWrap>();
            Dataset<MCMCModel> s = sparkSession
                    .sql(String.format(SELECT_NOT_INVALID_APPROVER_BLOCKS_SQL, hash.toString(), hash.toString()))
                    .as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                BlockMCMC mcmc = b.toBlockMCMC();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    storedBlocks.add(new BlockWrap(block, blockEvaluation, mcmc, params));
                }
            }
            return storedBlocks;
        } catch (Exception e) {
            throw new BlockStoreException(e);
        }

    }

    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException {
        try {
            List<BlockWrap> storedBlocks = new ArrayList<BlockWrap>();
            Dataset<MCMCModel> s = sparkSession
                    .sql(String.format(SELECT_SOLID_APPROVER_BLOCKS_SQL, hash.toString(), hash.toString()))
                    .as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                BlockMCMC mcmc = b.toBlockMCMC();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    storedBlocks.add(new BlockWrap(block, blockEvaluation, mcmc, params));
                }
            }
            return storedBlocks;
        } catch (Exception e) {
            throw new BlockStoreException(e);
        }

    }

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException {
        List<Sha256Hash> storedBlockHash = new ArrayList<Sha256Hash>();
        Dataset<Row> s = sparkSession
                .sql(String.format(SELECT_SOLID_APPROVER_HASHES_SQL, hash.toString(), hash.toString()));
        for (Row b : s.collectAsList()) {
            storedBlockHash.add(Sha256Hash.wrap(b.getString(1)));
        }
        return storedBlockHash;

    }

    @Override
    public boolean getOutputConfirmation(Sha256Hash blockHash, Sha256Hash hash, long index) throws BlockStoreException {

        return getTransactionOutput(blockHash, hash, index).isConfirmed();
    }

    public UTXO getTransactionOutput(Sha256Hash blockHash, Sha256Hash hash, Long index) {

        Dataset<UTXOModel> s = sparkSession.sql(SELECT_OUTPUTS_SQL + " where blockhash ='" + blockHash.getBytes()
                + "' and hash='" + hash.getBytes() + "'and index=" + index).as(Encoders.bean(UTXOModel.class));
        return s.first().toUTXO();

    }

    @Override
    public void addUnspentTransactionOutput(List<UTXO> utxos) throws BlockStoreException {
        List<UTXOModel> utxomodels = new ArrayList<>();

        for (UTXO out : utxos) {
            utxomodels.add(UTXOModel.fromUTXO(out));
        }
        Dataset source = sparkSession.createDataset(utxomodels, Encoders.bean(UTXOModel.class));

        SparkData.outputs.as("target").merge(source.as("source"),
                "target.blockhash = source.blockhash and target.hash = source.hash and target.index = source.index")
                .whenNotMatched().insertAll().whenMatched().updateAll().execute();
//blockhash, hash, outputindex
    }

    @Override
    public void addUnspentTransactionOutput(UTXO out) throws BlockStoreException {
        List<UTXO> a = new ArrayList<UTXO>();
        a.add(out);
        addUnspentTransactionOutput(a);
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

    public void resetStore() throws BlockStoreException {

        defaultDatabaseBatchWrite();
        try {
            deleteStore();
            createTables();
        } catch (SQLException ex) {
            log.warn("Warning: deleteStore", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public List<UTXO> getOpenAllOutputs(String tokenid) throws UTXOProviderException {

        List<UTXO> outputs = new ArrayList<UTXO>();

        // Must be sorted for hash checkpoint
        Dataset<UTXOModel> s = sparkSession.sql(
                SELECT_ALL_OUTPUTS_TOKEN_SQL + " order by hash, outputindex " + " where tokenid = '" + tokenid + "'")
                .as(Encoders.bean(UTXOModel.class));

        for (UTXOModel u : s.collectAsList()) {
            outputs.add(u.toUTXO());
        }
        return outputs;

    }

    public String quotedString(String s) {
        return "'" + s + "'";
    }

    public String quotedString(byte[] s) {
        return "'" + Utils.HEX.encode(s) + "'";
    }

    public String quotedString(Sha256Hash s) {
        if (s == null)
            return "''";
        return "'" + s.toString() + "'";
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(String address) throws UTXOProviderException {

        List<UTXO> outputs = new ArrayList<UTXO>();

        Dataset<UTXOModel> s = sparkSession
                .sql(String.format(SELECT_OPEN_TRANSACTION_OUTPUTS_SQL, quotedString(address), quotedString(address)))
                .as(Encoders.bean(UTXOModel.class));
        for (UTXOModel u : s.collectAsList()) {
            outputs.add(u.toUTXO());
        }
        return outputs;

    }

    @Override
    public BlockWrap getBlockWrap(Sha256Hash hash) throws BlockStoreException {
        try {
            BlockModel b = sparkSession.sql(String.format(SELECT_BLOCKS_SQL, quotedString(hash)))
                    .as(Encoders.bean(BlockModel.class)).first();

            BlockEvaluation blockEvaluation = b.toBlockEvaluation();

            Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
            return new BlockWrap(block, blockEvaluation, getMCMC(hash), params);
        } catch (Exception e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public List<UTXO> getOutputsHistory(String fromaddress, String toaddress, Long starttime, Long endtime)
            throws BlockStoreException {
        List<UTXO> outputs = new ArrayList<UTXO>();

        String sql = SELECT_TRANSACTION_OUTPUTS_SQL_BASE + "WHERE  confirmed=true ";

        if (fromaddress != null && !"".equals(fromaddress.trim())) {
            sql += " AND outputs.fromaddress=" + quotedString(fromaddress);
        }
        if (toaddress != null && !"".equals(toaddress.trim())) {
            sql += " AND outputs.toaddress=" + quotedString(fromaddress);
        }
        if (starttime != null) {
            sql += " AND time>=" + starttime;
        }
        if (endtime != null) {
            sql += " AND time<=" + endtime;
        }
        Dataset<UTXOModel> s = sparkSession.sql(sql).as(Encoders.bean(UTXOModel.class));

        for (UTXOModel u : s.collectAsList()) {
            outputs.add(u.toUTXO());

        }
        return outputs;

    }

    @Override
    public TreeSet<BlockWrap> getBlocksToConfirm(long cutoffHeight, long maxHeight) throws BlockStoreException {
        Comparator<BlockWrap> comparator = Comparator.comparingLong((BlockWrap b) -> b.getBlock().getHeight())
                .thenComparing((BlockWrap b) -> b.getBlock().getHash());
        try {
            TreeSet<BlockWrap> storedBlockHashes = new TreeSet<>(comparator);
            Dataset<MCMCModel> s = sparkSession
                    .sql(String.format(SELECT_BLOCKS_TO_CONFIRM_SQL, cutoffHeight, maxHeight))
                    .as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                BlockMCMC mcmc = b.toBlockMCMC();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    storedBlockHashes.add(new BlockWrap(block, blockEvaluation, mcmc, params));
                }
            }
            return storedBlockHashes;
        } catch (Exception ex) {
            throw new BlockStoreException(ex);
        }
    }

    @Override
    public HashSet<BlockEvaluation> getBlocksToUnconfirm() throws BlockStoreException {
        HashSet<BlockEvaluation> storedBlockHashes = new HashSet<BlockEvaluation>();
        try {

            Dataset<MCMCModel> s = sparkSession.sql(SELECT_BLOCKS_TO_UNCONFIRM_SQL).as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    storedBlockHashes.add(blockEvaluation);
                }
            }
            return storedBlockHashes;
        } catch (Exception ex) {
            throw new BlockStoreException(ex);
        }

    }

    @Override
    public PriorityQueue<BlockWrap> getSolidBlocksInIntervalDescending(long cutoffHeight, long maxHeight)
            throws BlockStoreException {
        PriorityQueue<BlockWrap> blocksByDescendingHeight = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        try {

            Dataset<MCMCModel> s = sparkSession
                    .sql(String.format(SELECT_SOLID_BLOCKS_IN_INTERVAL_SQL, cutoffHeight, maxHeight))
                    .as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                BlockMCMC mcmc = b.toBlockMCMC();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    blocksByDescendingHeight.add(new BlockWrap(block, blockEvaluation, mcmc, params));
                }
            }
            return blocksByDescendingHeight;
        } catch (Exception ex) {
            throw new BlockStoreException(ex);
        }
    }

    @Override
    public List<BlockWrap> getBlocksInMilestoneInterval(long minMilestone, long maxMilestone)
            throws BlockStoreException {
        List<BlockWrap> storedBlockHashes = new ArrayList<>();

        try {

            Dataset<MCMCModel> s = sparkSession
                    .sql(String.format(SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL, minMilestone, maxMilestone))
                    .as(Encoders.bean(MCMCModel.class));
            for (MCMCModel b : s.collectAsList()) {
                BlockEvaluation blockEvaluation = b.toBlockEvaluation();
                BlockMCMC mcmc = b.toBlockMCMC();
                Block block = params.getDefaultSerializer().makeZippedBlock(b.getBlockBytes());
                if (verifyHeader(block)) {
                    storedBlockHashes.add(new BlockWrap(block, blockEvaluation, mcmc, params));
                }
            }
            return storedBlockHashes;
        } catch (Exception ex) {
            throw new BlockStoreException(ex);
        }

    }

    public List<BlockWrap> getEntryPoints(Long currChainLength) throws BlockStoreException {
        // long currChainLength = getMaxConfirmedReward().getChainLength();
        long minChainLength = Math.max(0, currChainLength - NetworkParameters.MILESTONE_CUTOFF);
        return getBlocksInMilestoneInterval(minChainLength, currChainLength);

    }

    @Override
    public void deleteMCMC(long chainlength) throws BlockStoreException {
        sparkSession.sql(String.format(" delete from mcmc where hash  in " + SELECT_MCMC_CHAINLENGHT_SQL, chainlength));

    }

    @Override
    public void updateBlockEvaluationWeightAndDepth(List<DepthAndWeight> depthAndWeight) throws BlockStoreException {

        Dataset source = sparkSession.createDataset(depthAndWeight, Encoders.bean(DepthAndWeight.class));

        SparkData.mcmc.as("target").merge(source, "target.blockhash = souce.blockHash").whenMatched()
                .update(new HashMap<String, Column>() {
                    {
                        put("depth", functions.col("source.depth"));
                        put("weight", functions.col("source.weight"));
                    }
                }).whenNotMatched().insert(new HashMap<String, Column>() {
                    {
                        put("blockhash", functions.col("souce.blockHash"));
                        put("depth", functions.col("source.depth"));
                        put("weight", functions.col("source.weight"));
                        // put("rating", functions.expr("0"));
                    }
                }).execute();

    }

    @Override
    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, long b) throws BlockStoreException {

        sparkSession.sql(String.format(getUpdateBlockEvaluationMilestoneSQL(), b, System.currentTimeMillis(),
                blockhash.toString()));

    }

    @Override
    public void updateBlockEvaluationConfirmed(Sha256Hash blockhash, boolean b) throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_BLOCKEVALUATION_CONFIRMED_SQL, b, blockhash.toString()));

    }

    @Override
    public void updateBlockEvaluationRating(List<Rating> ratings) throws BlockStoreException {
        List<MCMCModel> m = new ArrayList<>();
        for (Rating r : ratings) {
            MCMCModel a = new MCMCModel();
            a.setBlock(r.toString());
            a.setRating(r.getRating());
            m.add(a);
        }
        Dataset source = sparkSession.createDataset(m, Encoders.bean(MCMCModel.class));

        SparkData.mcmc.as("target").merge(source, "target.blockhash = souce.blockhash").whenMatched()
                .update(new HashMap<String, Column>() {
                    {
                        put("rating", functions.col("source.rating"));

                    }
                }).whenNotMatched().insert(new HashMap<String, Column>() {
                    {
                        put("blockhash", functions.col("souce.blockhash"));
                        put("rating", functions.col("source.rating"));

                    }
                }).execute();

    }

    @Override
    public void updateBlockEvaluationSolid(Sha256Hash blockhash, long solid) throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_BLOCKEVALUATION_SOLID_SQL, solid, blockhash.toString()));

    }

    @Override
    public BlockEvaluation getTransactionOutputSpender(Sha256Hash blockHash, Sha256Hash hash, long index)
            throws BlockStoreException {
        UTXO u = getTransactionOutput(blockHash, hash, index);
        if (u == null || u.getSpenderBlockHash() == null)
            return null;
        Dataset<BlockModel> b = sparkSession.sql(String.format(SELECT_BLOCKS_SQL, u.getSpenderBlockHash().toString()))
                .as(Encoders.bean(BlockModel.class));
        if (b.isEmpty()) {
            return null;
        } else {
            return b.first().toBlockEvaluation();
        }

    }

    @Override
    public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, Sha256Hash prevTxHash, long index, boolean b,
            @Nullable Sha256Hash spenderBlockHash) throws BlockStoreException {
        sparkSession.sql(String.format(getUpdateOutputsSpentSQL(), b,
                spenderBlockHash != null ? spenderBlockHash.toString() : null, prevTxHash.toString(), index,
                prevBlockHash.toString()));

    }

    @Override
    public void updateTransactionOutputConfirmed(Sha256Hash prevBlockHash, Sha256Hash prevTxHash, long index, boolean b)
            throws BlockStoreException {
        sparkSession.sql(String.format(getUpdateOutputsConfirmedSQL(), b, prevTxHash.toString(), index,
                prevBlockHash.toString()));
    }

    @Override
    public void updateAllTransactionOutputsConfirmed(Sha256Hash prevBlockHash, boolean b) throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_ALL_OUTPUTS_CONFIRMED_SQL, b, quotedString(prevBlockHash)));

    }

    @Override
    public void updateTransactionOutputSpendPending(List<UTXO> utxos) throws BlockStoreException {
        List<UTXOModel> list = new ArrayList<UTXOModel>();
        for (UTXO u : utxos) {
            list.add(UTXOModel.fromUTXO(u));
        }
        Dataset source = sparkSession.createDataset(list, Encoders.bean(UTXOModel.class));
        SparkData.outputs.as("target")
                .merge(source.as("source"),
                        "target.hash = source.hash " + "and target.blockhash = source.blockhash "
                                + "and target.outputindex = source.outputindex ")
                .whenMatched().update(new HashMap<String, Column>() {
                    {
                        put("spendpending", functions.col("source.spendpending"));
                        put("spendpendingtime", functions.col("source.spendpendingtime"));

                    }
                }).execute();
    }

    @Override
    public List<Token> getTokensList(Set<String> tokenids) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        if (tokenids.isEmpty())
            return list;

        String sql = SELECT_CONFIRMED_TOKENS_SQL;
        if (tokenids != null && !tokenids.isEmpty()) {
            sql += "  and tokenid in ( " + buildINList(tokenids) + " )";
        }
        // sql += LIMIT_500;
        for (TokenModel t : sparkSession.sql(sql).as(Encoders.bean(TokenModel.class)).collectAsList()) {
            list.add(t.toToken());
        }
        return list;

    }

    @Override
    public List<Token> getTokensList(String name) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();

        String sql = SELECT_CONFIRMED_TOKENS_SQL;
        if (name != null && !"".equals(name.trim())) {
            sql += " AND (tokenname LIKE '%" + name + "%' OR description LIKE '%" + name + "%' OR domainname LIKE '%"
                    + name + "%')";
        }
        sql += LIMIT_500;
        for (TokenModel t : sparkSession.sql(sql).as(Encoders.bean(TokenModel.class)).collectAsList()) {
            list.add(t.toToken());
        }
        return list;
    }

    @Override
    public void insertToken(Sha256Hash blockhash, Token token) throws BlockStoreException {
        TokenModel tokenModel = TokenModel.fromToken(token);
        tokenModel.setConfirmed(true);
        List<TokenModel> list = new ArrayList<>();
        list.add(tokenModel);

        Dataset source = sparkSession.createDataset(list, Encoders.bean(TokenModel.class));
        SparkData.tokens.as("target")
                .merge(source.as("source"),
                        "target.tokenid = source.tokenid " + "and target.tokenindex = source.tokenindex ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();
    }

    @Override
    public Sha256Hash getTokenPrevblockhash(Sha256Hash blockhash) throws BlockStoreException {

        return Sha256Hash.wrap(sparkSession.sql(String.format(SELECT_TOKEN_PREVBLOCKHASH_SQL, blockhash.toString()))
                .first().getString(0));

    }

    @Override
    public Sha256Hash getTokenSpender(String blockhash) throws BlockStoreException {
        return Sha256Hash.wrap(
                sparkSession.sql(String.format(SELECT_TOKEN_SPENDER_SQL, blockhash.toString())).first().getString(0));

    }

    @Override
    public boolean getTokenSpent(Sha256Hash blockhash) throws BlockStoreException {
        return sparkSession.sql(String.format(SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL, blockhash.toString())).first()
                .getBoolean(0);

    }

    @Override
    public boolean getTokenConfirmed(Sha256Hash blockHash) throws BlockStoreException {
        return sparkSession.sql(String.format(SELECT_TOKEN_CONFIRMED_SQL, blockHash.toString())).first().getBoolean(0);

    }

    @Override
    public boolean getTokenAnyConfirmed(String tokenid, long tokenIndex) throws BlockStoreException {
        return sparkSession.sql(String.format(SELECT_TOKEN_ANY_CONFIRMED_SQL, quotedString(tokenid), tokenIndex))
                .first().getBoolean(0);

    }

    @Override
    public boolean getTokennameAndDomain(String tokenname, String domainpre) throws BlockStoreException {

        String sql = "SELECT confirmed FROM tokens WHERE tokenname = %s AND domainpredblockhash = %s  ";

        return sparkSession.sql(String.format(sql, quotedString(tokenname), quotedString(domainpre))).first()
                .getBoolean(0);

    }

    @Override
    public BlockWrap getTokenIssuingConfirmedBlock(String tokenid, long tokenIndex) throws BlockStoreException {

        Dataset<Row> s = sparkSession
                .sql(String.format(SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL, quotedString(tokenid), tokenIndex));

        if (s.isEmpty()) {
            return null;
        }
        return getBlockWrap(Sha256Hash.wrap(s.first().getString(0)));

    }

    @Override
    public BlockWrap getDomainIssuingConfirmedBlock(String tokenName, String domainPred, long index)
            throws BlockStoreException {
        Dataset<Row> s = sparkSession.sql(String.format(SELECT_DOMAIN_ISSUING_CONFIRMED_BLOCK_SQL,
                quotedString(tokenName), quotedString(domainPred), index));

        if (s.isEmpty()) {
            return null;
        }
        return getBlockWrap(Sha256Hash.wrap(s.first().getString(0)));

    }

    @Override
    public List<String> getDomainDescendantConfirmedBlocks(String domainPred) throws BlockStoreException {
        List<String> storedBlocks = new ArrayList<String>();

        Dataset<Row> s = sparkSession
                .sql(String.format(SELECT_DOMAIN_DESCENDANT_CONFIRMED_BLOCKS_SQL, quotedString(domainPred)));

        for (Row r : s.collectAsList()) {
            storedBlocks.add(r.getString(0));
        }
        return storedBlocks;

    }

    @Override
    public void updateTokenSpent(Sha256Hash blockhash, boolean b, Sha256Hash spenderBlockHash)
            throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_TOKEN_SPENT_SQL, b, quotedString(spenderBlockHash)));

    }

    @Override
    public void updateTokenConfirmed(Sha256Hash blockHash, boolean confirmed) throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_TOKEN_CONFIRMED_SQL, confirmed, quotedString(blockHash)));

    }

    @Override
    public List<BlockEvaluationDisplay> getSearchBlockEvaluations(List<String> address, String lastestAmount,
            long height, long maxblocks) throws BlockStoreException {

        String sql = "";
        sql += SELECT_BLOCKS_TEMPLATE;
        sql += " where height >= " + height;
        sql += " ORDER BY insertTime desc ";
        Long a = Long.valueOf(lastestAmount);
        if (a > maxblocks) {
            a = maxblocks;
        }
        sql += " LIMIT " + a;

        List<BlockEvaluationDisplay> result = new ArrayList<BlockEvaluationDisplay>();
        TXReward maxConfirmedReward = getMaxConfirmedReward();

        Dataset<BlockModel> s = sparkSession.sql(sql).as(Encoders.bean(BlockModel.class));

        for (BlockModel b : s.collectAsList()) {
            BlockEvaluationDisplay blockEvaluation = b.toBlockEvaluationDisplay(maxConfirmedReward.getChainLength());
            blockEvaluation.setMcmcWithDefault(getMCMC(blockEvaluation.getBlockHash()));
            result.add(blockEvaluation);
        }
        return result;

    }

    @Override
    public List<BlockEvaluationDisplay> getSearchBlockEvaluationsByhashs(List<String> blockhashs)
            throws BlockStoreException {

        List<BlockEvaluationDisplay> result = new ArrayList<BlockEvaluationDisplay>();
        if (blockhashs == null || blockhashs.isEmpty()) {
            return result;
        }
        String sql = "";

        sql += SELECT_BLOCKS_TEMPLATE + "  FROM  blocks WHERE hash =  ";

        TXReward maxConfirmedReward = getMaxConfirmedReward();

        for (String hash : blockhashs) {

            Dataset<BlockModel> s = sparkSession.sql(sql + quotedString(hash)).as(Encoders.bean(BlockModel.class));

            for (BlockModel b : s.collectAsList()) {
                BlockEvaluationDisplay blockEvaluation = b
                        .toBlockEvaluationDisplay(maxConfirmedReward.getChainLength());
                blockEvaluation.setMcmcWithDefault(getMCMC(blockEvaluation.getBlockHash()));
                result.add(blockEvaluation);
            }

        }
        return result;

    }

    @Override
    public List<MultiSignAddress> getMultiSignAddressListByTokenidAndBlockHashHex(String tokenid,
            Sha256Hash prevblockhash) throws BlockStoreException {

        List<MultiSignAddress> list = new ArrayList<MultiSignAddress>();

        Dataset<MultiSignAddressModel> s = sparkSession
                .sql(String.format(SELECT_MULTISIGNADDRESS_SQL, quotedString(tokenid), prevblockhash.toString()))
                .as(Encoders.bean(MultiSignAddressModel.class));

        for (MultiSignAddressModel m : s.collectAsList()) {
            list.add(m.toMultiSignAddress());
        }
        return list;

    }

    @Override
    public void insertMultiSignAddress(MultiSignAddress multiSignAddress) throws BlockStoreException {

        List<MultiSignAddressModel> list = new ArrayList<MultiSignAddressModel>();

        list.add(MultiSignAddressModel.from(multiSignAddress));

        Dataset source = sparkSession.createDataset(list, Encoders.bean(MultiSignAddressModel.class));
        SparkData.multisignaddress.as("target")
                .merge(source.as("source"),
                        "target.tokenid = source.tokenid " + "and target.blockhash = source.blockhash "
                                + "and target.pubkeyhex = source.pubkeyhex ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();
    }

    @Override
    public void deleteMultiSignAddress(String tokenid, String address) throws BlockStoreException {

        sparkSession.sql(String.format(DELETE_MULTISIGNADDRESS_SQL, quotedString(tokenid), quotedString(address)));

    }

    @Override
    public Token getCalMaxTokenIndex(String tokenid) throws BlockStoreException {

        Dataset<Row> s = sparkSession.sql(String.format(COUNT_TOKENSINDEX_SQL, quotedString(tokenid)));

        Token tokens = new Token();
        if (!s.isEmpty()) {
            tokens.setBlockHash(Sha256Hash.wrap(s.first().getString(0)));
            tokens.setTokenindex(s.first().getInt(1));
            return tokens;
        } else {
            // tokens.setBlockhash("");
            tokens.setTokenindex(-1);
        }
        return tokens;

    }

    @Override
    public Token getTokenByBlockHash(Sha256Hash blockhash) throws BlockStoreException {

        Dataset<TokenModel> s = sparkSession.sql(String.format(SELECT_TOKEN_SQL, quotedString(blockhash)))
                .as(Encoders.bean(TokenModel.class));
        Token token = null;
        if (!s.isEmpty()) {
            token = s.first().toToken();
        }
        return token;

    }

    @Override
    public List<Token> getTokenID(String tokenid) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();

        Dataset<TokenModel> s = sparkSession.sql(String.format(SELECT_TOKENID_SQL, quotedString(tokenid)))
                .as(Encoders.bean(TokenModel.class));
        for (TokenModel t : s.collectAsList()) {
            list.add(t.toToken());
        }
        return list;
    }

    @Override
    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();

        Dataset<MultiSignModel> s = sparkSession.sql(String.format(SELECT_MULTISIGN_SQL, quotedString(address)))
                .as(Encoders.bean(MultiSignModel.class));

        for (MultiSignModel m : s.collectAsList()) {
            list.add(m.toMultiSign());
        }
        return list;

    }

    public List<MultiSign> getMultiSignListByTokenidAndAddress(final String tokenid, String address)
            throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();

        Dataset<MultiSignModel> s = sparkSession
                .sql(String.format(SELECT_MULTISIGN_TOKENID_ADDRESS_SQL, quotedString(tokenid), quotedString(address)))
                .as(Encoders.bean(MultiSignModel.class));

        for (MultiSignModel m : s.collectAsList()) {
            list.add(m.toMultiSign());
        }
        return list;

    }

    @Override
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, int tokenindex, Set<String> addresses,
            boolean isSign) throws BlockStoreException {
        List<MultiSign> list = new ArrayList<MultiSign>();

        String sql = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE 1 = 1 ";
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND address IN( " + buildINList(addresses) + " ) ";
        }
        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND tokenid=  " + quotedString(tokenid);
            if (tokenindex != -1) {
                sql += "  AND tokenindex =   " + tokenindex;
            }
        }

        if (!isSign) {
            sql += " AND sign = 0";
        }
        sql += " ORDER BY tokenid,tokenindex DESC";

        Dataset<MultiSignModel> s = sparkSession.sql(sql).as(Encoders.bean(MultiSignModel.class));

        for (MultiSignModel m : s.collectAsList()) {
            list.add(m.toMultiSign());
        }
        return list;

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

        Dataset<Row> s = sparkSession.sql(
                String.format(SELECT_COUNT_MULTISIGN_SQL, quotedString(tokenid), tokenindex, quotedString(address)));

        if (!s.isEmpty()) {
            return s.first().getInt(0);
        }
        return 0;
    }

    public int countMultiSign(String tokenid, long tokenindex, int sign) throws BlockStoreException {

        Dataset<Row> s = sparkSession
                .sql(String.format(SELECT_COUNT_MULTISIGN_SQL, quotedString(tokenid), tokenindex, sign));

        if (!s.isEmpty()) {
            return s.first().getInt(0);
        }
        return 0;

    }

    @Override
    public void saveMultiSign(MultiSign multiSign) throws BlockStoreException {

        if (multiSign.getTokenid() == null || "".equals(multiSign.getTokenid())) {
            return;
        }

        List<MultiSignModel> list = new ArrayList<MultiSignModel>();

        list.add(MultiSignModel.from(multiSign));

        Dataset source = sparkSession.createDataset(list, Encoders.bean(MultiSignModel.class));
        SparkData.multisign.as("target").merge(source.as("source"), "target.id = source.id ").whenMatched().updateAll()
                .whenNotMatched().insertAll().execute();

    }

    @Override
    public void updateMultiSign(String tokenid, long tokenIndex, String address, byte[] blockhash, int sign)
            throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_MULTISIGN_SQL, Utils.HEX.encode(blockhash), sign, quotedString(tokenid),
                tokenIndex, quotedString(address)));

    }

    @Override
    public void deleteMultiSign(String tokenid) throws BlockStoreException {

        sparkSession.sql(String.format(DELETE_MULTISIGN_SQL, quotedString(tokenid)));
    }

    @Override
    public boolean getRewardSpent(Sha256Hash hash) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_SPENT_SQL, quotedString(hash.toString()))).first()
                .getBoolean(0);

    }

    @Override
    public Sha256Hash getRewardSpender(Sha256Hash hash) throws BlockStoreException {

        Dataset<Row> s = sparkSession.sql(String.format(SELECT_TX_REWARD_SPENDER_SQL, quotedString(hash.toString())));

        if (s.isEmpty()) {
            return null;
        }
        return s.first().getString(0) == null ? null : Sha256Hash.wrap(s.first().getString(0));

    }

    @Override
    public Sha256Hash getRewardPrevBlockHash(Sha256Hash blockHash) throws BlockStoreException {

        return Sha256Hash.wrap(
                sparkSession.sql(String.format(SELECT_TX_REWARD_PREVBLOCKHASH_SQL, quotedString(blockHash.toString())))
                        .first().getString(0));
    }

    @Override
    public long getRewardDifficulty(Sha256Hash blockHash) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_DIFFICULTY_SQL, quotedString(blockHash.toString())))
                .first().getLong(0);

    }

    @Override
    public long getRewardChainLength(Sha256Hash blockHash) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_CHAINLENGTH_SQL, quotedString(blockHash.toString())))
                .first().getLong(0);

    }

    @Override
    public boolean getRewardConfirmed(Sha256Hash hash) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_CONFIRMED_SQL, quotedString(hash.toString()))).first()
                .getBoolean(0);

    }

    @Override
    public void insertReward(Sha256Hash hash, Sha256Hash prevBlockHash, long difficulty, long chainLength)
            throws BlockStoreException {

        List<TXRewardModel> list = new ArrayList<TXRewardModel>();
        TXRewardModel t = new TXRewardModel();
        t.setBlockhash(hash.toString());
        t.setChainLength(chainLength);
        t.setPrevblockhash(prevBlockHash.toString());
        t.setConfirmed(false);
        t.setSpent(false);
        t.setDifficulty(difficulty);
        list.add(t);

        Dataset source = sparkSession.createDataset(list, Encoders.bean(TXRewardModel.class));
        SparkData.txreward.as("target").merge(source.as("source"), "target.blockhash = source.blockhash ").whenMatched()
                .updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void updateRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_TX_REWARD_CONFIRMED_SQL, b, quotedString(hash.toString())));
    }

    @Override
    public void updateRewardSpent(Sha256Hash hash, boolean b, @Nullable Sha256Hash spenderBlockHash)
            throws BlockStoreException {

        sparkSession
                .sql(String.format(UPDATE_TX_REWARD_SPENT_SQL, b, quotedString(spenderBlockHash), quotedString(hash)));
    }

    @Override
    public TXReward getRewardConfirmedAtHeight(long chainlength) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_CONFIRMED_AT_HEIGHT_REWARD_SQL, chainlength))
                .as(Encoders.bean(TXRewardModel.class)).first().toTXReward();

    }

    @Override
    public TXReward getMaxConfirmedReward() throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL))
                .as(Encoders.bean(TXRewardModel.class)).first().toTXReward();

    }

    @Override
    public List<TXReward> getAllConfirmedReward() throws BlockStoreException {

        Dataset<TXRewardModel> s = sparkSession.sql(String.format(SELECT_TX_REWARD_ALL_CONFIRMED_REWARD_SQL))
                .as(Encoders.bean(TXRewardModel.class));

        List<TXReward> list = new ArrayList<TXReward>();
        for (TXRewardModel t : s.collectAsList()) {
            list.add(t.toTXReward());
        }

        return list;

    }

    @Override
    public void updateMultiSignBlockBitcoinSerialize(String tokenid, long tokenindex, byte[] bytes)
            throws BlockStoreException {

        sparkSession
                .sql(String.format(UPDATE_MULTISIGN1_SQL, Utils.HEX.encode(bytes), quotedString(tokenid), tokenindex));

    }

    @Override
    public void insertOutputsMulti(OutputsMulti outputsMulti) throws BlockStoreException {

        List<OutputsMultiModel> list = new ArrayList<OutputsMultiModel>();
        OutputsMultiModel t = new OutputsMultiModel(outputsMulti.getHash(), outputsMulti.getToAddress(),
                outputsMulti.getOutputIndex());

        list.add(t);

        Dataset source = sparkSession.createDataset(list, Encoders.bean(OutputsMultiModel.class));
        SparkData.outputs.as("target")
                .merge(source.as("source"), "target.hash = source.hash and  target.outputindex = source.outputindex ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    public List<OutputsMulti> queryOutputsMultiByHashAndIndex(byte[] hash, long index) throws BlockStoreException {

        List<OutputsMulti> list = new ArrayList<OutputsMulti>();

        Dataset<OutputsMultiModel> s = sparkSession
                .sql(String.format(SELECT_OUTPUTSMULTI_SQL, Utils.HEX.encode(hash), index))
                .as(Encoders.bean(OutputsMultiModel.class));

        for (OutputsMultiModel m : s.collectAsList()) {
            Sha256Hash sha256Hash = Sha256Hash.wrap(m.getHash());

            OutputsMulti outputsMulti = new OutputsMulti(sha256Hash, m.getToaddress(), m.getOutputindex());
            list.add(outputsMulti);
        }
        return list;

    }

    @Override
    public UserData queryUserDataWithPubKeyAndDataclassname(String dataclassname, String pubKey)
            throws BlockStoreException {

        Dataset<UserDataModel> s = sparkSession
                .sql(String.format(SELECT_USERDATA_SQL, quotedString(dataclassname), quotedString(pubKey)))
                .as(Encoders.bean(UserDataModel.class));
        if (!s.isEmpty()) {
            return null;
        }
        return s.first().toUserData();

    }

    @Override
    public void insertUserData(UserData userData) throws BlockStoreException {
        List<UserDataModel> list = new ArrayList<>();
        list.add(UserDataModel.from(userData));
        Dataset source = sparkSession.createDataset(list, Encoders.bean(UserDataModel.class));
        SparkData.userdata.as("target")
                .merge(source.as("source"),
                        "target.dataclassname = source.dataclassname and  target.pubkey = source.pubkey ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public List<UserData> getUserDataListWithBlocktypePubKeyList(int blocktype, List<String> pubKeyList)
            throws BlockStoreException {
        if (pubKeyList.isEmpty()) {
            return new ArrayList<UserData>();
        }
        String sql = "select blockhash, dataclassname, data, pubkey, blocktype from userdata where blocktype = %s and pubkey in ";
        StringBuffer stringBuffer = new StringBuffer();
        for (String str : pubKeyList)
            stringBuffer.append(",'").append(str).append("'");
        sql += "(" + stringBuffer.substring(1) + ")";

        Dataset<UserDataModel> s = sparkSession.sql(String.format(sql, blocktype))
                .as(Encoders.bean(UserDataModel.class));

        List<UserData> list = new ArrayList<UserData>();
        for (UserDataModel m : s.collectAsList()) {
            list.add(m.toUserData());
        }
        return list;

    }

    @Override
    public void updateUserData(UserData userData) throws BlockStoreException {

        List<UserDataModel> list = new ArrayList<>();
        list.add(UserDataModel.from(userData));
        Dataset source = sparkSession.createDataset(list, Encoders.bean(UserDataModel.class));
        SparkData.userdata.as("target")
                .merge(source.as("source"),
                        "target.dataclassname = source.dataclassname and  target.pubkey = source.pubkey ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void insertPayPayMultiSign(PayMultiSign payMultiSign) throws BlockStoreException {
        List<PayMultiSignModel> list = new ArrayList<>();
        list.add(PayMultiSignModel.from(payMultiSign));
        Dataset source = sparkSession.createDataset(list, Encoders.bean(PayMultiSignModel.class));
        SparkData.paymultisign.as("target").merge(source.as("source"), "target.orderid = source.orderid  ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void insertPayMultiSignAddress(PayMultiSignAddress payMultiSignAddress) throws BlockStoreException {
        List<PayMultiSignAddressModel> list = new ArrayList<>();
        list.add(PayMultiSignAddressModel.from(payMultiSignAddress));
        Dataset source = sparkSession.createDataset(list, Encoders.bean(PayMultiSignAddressModel.class));
        SparkData.paymultisignaddress.as("target").merge(source.as("source"), "target.orderid = source.orderid  ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();
    }

    @Override
    public void updatePayMultiSignAddressSign(String orderid, String pubKey, int sign, byte[] signInputData)
            throws BlockStoreException {
        String sql = "update paymultisignaddress set sign = %s, signInputData = %s where orderid = %s and pubKey = %s";

        sparkSession.sql(
                String.format(sql, sign, Utils.HEX.encode(signInputData), quotedString(orderid), quotedString(pubKey)));

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

        Dataset<PayMultiSignModel> s = sparkSession.sql(sql).as(Encoders.bean(PayMultiSignModel.class));

        List<PayMultiSign> list = new ArrayList<PayMultiSign>();
        for (PayMultiSignModel p : s.collectAsList()) {
            list.add(p.toPayMultiSign());
        }
        return list;

    }

    @Override
    public int getCountPayMultiSignAddressStatus(String orderid) throws BlockStoreException {

        String sql = "select count(*) as count from paymultisignaddress where orderid = %s and sign = 1";
        Dataset<Row> s = sparkSession.sql(String.format(sql, quotedString(orderid)));

        if (!s.isEmpty()) {
            return s.first().getInt(0);
        }
        return 0;

    }

    @Override
    public UTXO getOutputsWithHexStr(byte[] hash, long outputindex) throws BlockStoreException {
        String sql = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
                + " addresstargetable, blockhash, tokenid, fromaddress, memo, minimumsign, time, spent, confirmed, "
                + " spendpending, spendpendingtime FROM outputs WHERE hash = %s and outputindex = %s";

        Dataset<UTXOModel> s = sparkSession.sql(String.format(sql, quotedString(hash), outputindex))
                .as(Encoders.bean(UTXOModel.class));

        if (!s.isEmpty()) {
            return null;
        }
        return s.first().toUTXO();

    }

    @Override
    public String getSettingValue(String name) throws BlockStoreException {

        Dataset<Row> s = sparkSession.sql(String.format(getSelectSettingsSQL(), quotedString(name)));

        if (!s.isEmpty()) {
            return null;
        }
        return s.first().getString(0);

    }

    @Override
    public boolean getOrderConfirmed(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {

        return sparkSession.sql(
                String.format(SELECT_ORDER_CONFIRMED_SQL, quotedString(txHash), quotedString(issuingMatcherBlockHash)))
                .first().getBoolean(0);

    }

    @Override
    public Sha256Hash getOrderSpender(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException {

        return Sha256Hash.wrap(sparkSession.sql(
                String.format(SELECT_ORDER_SPENDER_SQL, quotedString(txHash), quotedString(issuingMatcherBlockHash)))
                .first().getString(0));

    }

    @Override
    public boolean getOrderSpent(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {

        return sparkSession
                .sql(String.format(SELECT_ORDER_SPENT_SQL, quotedString(txHash), quotedString(issuingMatcherBlockHash)))
                .first().getBoolean(0);

    }

    @Override
    public HashMap<Sha256Hash, OrderRecord> getOrderMatchingIssuedOrders(Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException {

        Dataset<OrderRecordModel> s = sparkSession
                .sql(String.format(SELECT_ORDERS_BY_ISSUER_SQL, quotedString(issuingMatcherBlockHash)))
                .as(Encoders.bean(OrderRecordModel.class));
        ;
        HashMap<Sha256Hash, OrderRecord> result = new HashMap<>();

        for (OrderRecordModel p : s.collectAsList()) {
            result.put(Sha256Hash.wrap(p.getBlockhash()), p.toOrderRecord());
        }
        return result;

    }

    @Override
    public OrderRecord getOrder(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException {

        Dataset<OrderRecordModel> s = sparkSession
                .sql(String.format(SELECT_ORDER_SQL, quotedString(txHash), quotedString(issuingMatcherBlockHash)))
                .as(Encoders.bean(OrderRecordModel.class));

        if (!s.isEmpty())
            return null;

        return s.first().toOrderRecord();

    }

    @Override
    public void insertCancelOrder(OrderCancel orderCancel) throws BlockStoreException {

        List<OrderCancelModel> list = new ArrayList<>();
        list.add(OrderCancelModel.from(orderCancel));
        Dataset source = sparkSession.createDataset(list, Encoders.bean(OrderCancelModel.class));
        SparkData.ordercancel.as("target").merge(source.as("source"), "target.orderblockhash = source.orderblockhash  ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void insertOrder(Collection<OrderRecord> records) throws BlockStoreException {
        if (records == null)
            return;

        List<OrderRecordModel> list = new ArrayList<>();
        for (OrderRecord record : records) {
            list.add(OrderRecordModel.from(record));
        }

        Dataset source = sparkSession.createDataset(list, Encoders.bean(OrderRecordModel.class));
        SparkData.orders.as("target")
                .merge(source.as("source"),
                        "target.orderblockhash = source.orderblockhash and"
                                + "target.issuingmatcherblockhash = source.issuingmatcherblockhash  ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void updateOrderConfirmed(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, boolean confirmed)
            throws BlockStoreException {

        sparkSession.sql(String.format(UPDATE_ORDER_CONFIRMED_SQL, confirmed, quotedString(initialBlockHash),
                quotedString(issuingMatcherBlockHash)));

    }

    @Override
    public void updateOrderConfirmed(Collection<OrderRecord> orderRecords, boolean confirm) throws BlockStoreException {

        if (orderRecords == null || orderRecords.isEmpty())
            return;

        List<OrderRecordModel> list = new ArrayList<>();
        for (OrderRecord record : orderRecords) {
            list.add(OrderRecordModel.from(record));
        }

        Dataset source = sparkSession.createDataset(list, Encoders.bean(OrderRecordModel.class));
        SparkData.orders.as("target")
                .merge(source.as("source"),
                        "target.orderblockhash = source.orderblockhash and"
                                + "target.issuingmatcherblockhash = source.issuingmatcherblockhash  ")
                .whenMatched().update(new HashMap<String, Column>() {
                    {
                        put("confirmed", functions.col("source.confirmed"));

                    }
                }).execute();

    }

    @Override
    public void updateOrderSpent(Set<OrderRecord> orderRecords) throws BlockStoreException {

        if (orderRecords == null || orderRecords.isEmpty())
            return;

        List<OrderRecordModel> list = new ArrayList<>();
        for (OrderRecord record : orderRecords) {
            list.add(OrderRecordModel.from(record));
        }

        Dataset source = sparkSession.createDataset(list, Encoders.bean(OrderRecordModel.class));
        SparkData.orders.as("target")
                .merge(source.as("source"),
                        "target.orderblockhash = source.orderblockhash and"
                                + "target.issuingmatcherblockhash = source.issuingmatcherblockhash  ")
                .whenMatched().update(new HashMap<String, Column>() {
                    {
                        put("spent", functions.col("source.spent"));

                    }
                }).execute();

    }

    /*
     * all spent order and older than a month will be deleted from order table.
     */
    @Override
    public void prunedClosedOrders(Long beforetime) throws BlockStoreException {

        sparkSession.sql(String.format(" delete FROM orders WHERE  spent=1 AND validtotime < %s limit 1000 ",
                beforetime - 100 * NetworkParameters.ORDER_TIMEOUT_MAX));

    }

    /*
     * remove the blocks, only if : 1) there is no unspent transaction related
     * to the block 2) this block is outside the cutoff height, reorg is
     * possible 3) the spenderblock is outside the cutoff height, reorg is
     * possible
     */
    @Override
    public void prunedBlocks(Long height, Long chain) throws BlockStoreException {

        sparkSession
                .sql(" delete FROM blocks WHERE   hash    in (  select distinct( blocks.hash) from  blocks  , outputs "
                        + " where spenderblockhash = blocks.hash    " + "  and blocks.milestone < " + chain
                        + "and blocks.milestone !=0  " + " and ( blocks.blocktype = "
                        + Block.Type.BLOCKTYPE_TRANSFER.ordinal() + " or blocks.blocktype = "
                        + Block.Type.BLOCKTYPE_ORDER_OPEN.ordinal() + " or blocks.blocktype = "
                        + Block.Type.BLOCKTYPE_REWARD.ordinal() + "  ) limit 1000 ) ");

    }

    /*
     * all spent UTXO History and older than the maxRewardblock
     * can be pruned.
     */
    @Override
    public void prunedHistoryUTXO(Long maxRewardblock) throws BlockStoreException {

        sparkSession.sql(" delete FROM outputs WHERE  spent=1 AND "
                + "spenderblockhash in (select hash from blocks where milestone < " + maxRewardblock
                + " ) limit 1000 ");
    }

    /*
     * all spent UTXO History and older than the before time, minimum 60 days
     */
    @Override
    public void prunedPriceTicker(Long beforetime) throws BlockStoreException {

        long minTime = Math.min(beforetime, System.currentTimeMillis() / 1000 - 60 * 24 * 60 * 60);

        sparkSession.sql(" delete FROM matching WHERE inserttime < " + minTime + "  limit 1000 ");
    }

    @Override
    public List<OrderRecord> getAllOpenOrdersSorted(List<String> addresses, String tokenid) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();

        String sql = SELECT_OPEN_ORDERS_SORTED_SQL;
        String orderby = " ORDER BY blockhash, issuingmatcherblockhash";

        if (tokenid != null && !tokenid.trim().isEmpty()) {
            sql += " AND (offertokenid=" + quotedString(tokenid) + " or targettokenid= " + quotedString(tokenid) + ")";
        }
        if (addresses != null && !addresses.isEmpty()) {
            sql += " AND beneficiaryaddress in (";

            sql += buildINList(addresses) + ")";
        }
        sql += orderby;
        Dataset<OrderRecordModel> s = sparkSession.sql(sql).as(Encoders.bean(OrderRecordModel.class));
        for (OrderRecordModel m : s.collectAsList()) {
            result.add(m.toOrderRecord());
        }
        return result;

    }

    @Override
    public List<OrderRecord> getMyClosedOrders(List<String> addresses) throws BlockStoreException {
        List<OrderRecord> result = new ArrayList<>();
        if (addresses == null || addresses.isEmpty())
            return new ArrayList<OrderRecord>();

        String myaddress = " in (" + buildINList(addresses) + ")";

        String sql = "SELECT " + ORDER_TEMPLATE + " FROM orders "
                + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress" + myaddress + " AND issuingmatcherblockhash="
                + OPENORDERHASH + " AND blockhash NOT IN ( SELECT blockhash FROM orders "
                + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress" + myaddress + ")";

        Dataset<OrderRecordModel> s = sparkSession.sql(sql).as(Encoders.bean(OrderRecordModel.class));
        for (OrderRecordModel m : s.collectAsList()) {
            result.add(m.toOrderRecord());
        }
        return result;

    }

    @Override
    public List<UTXO> getAllAvailableUTXOsSorted() throws BlockStoreException {
        List<UTXO> result = new ArrayList<>();

        Dataset<UTXOModel> s = sparkSession.sql(SELECT_AVAILABLE_UTXOS_SORTED_SQL).as(Encoders.bean(UTXOModel.class));
        for (UTXOModel m : s.collectAsList()) {
            result.add(m.toUTXO());
        }
        return result;

    }

    @Override
    public boolean existBlock(Sha256Hash hash) throws BlockStoreException {

        return sparkSession.sql(" select hash from blocks where hash =  " + quotedString(hash)).isEmpty();

    }

    @Override
    public void insertMatchingEvent(List<MatchResult> matchs) throws BlockStoreException {

        Dataset source = sparkSession.createDataset(matchs, Encoders.bean(MatchResult.class));
        SparkData.matching.as("target")
                .merge(source.as("source"),
                        "target.txhash = source.txhash and" + "target.tokenid = source.tokenid  "
                                + " and target.basetokenid = source.basetokenid  ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();
    }

    @Override
    public List<MatchLastdayResult> getLastMatchingEvents(Set<String> tokenIds, String basetoken)
            throws BlockStoreException {

        String sql = "SELECT  ml.txhash txhash,ml.tokenid tokenid ,ml.basetokenid basetokenid,  ml.price price, ml.executedQuantity executedQuantity,ml.inserttime inserttime, "
                + "mld.price lastdayprice,mld.executedQuantity lastdayQuantity "
                + "FROM matchinglast ml LEFT JOIN matchinglastday mld ON ml.tokenid=mld.tokenid AND  ml.basetokenid=mld.basetokenid";
        sql += " where ml.basetokenid= " + quotedString(basetoken);
        if (tokenIds != null && !tokenIds.isEmpty()) {
            sql += "  and ml.tokenid IN ( " + buildINList(tokenIds) + " )";
        }

        return sparkSession.sql(sql).as(Encoders.bean(MatchLastdayResult.class)).collectAsList();

    }

    @Override
    public void deleteMatchingEvents(String hash) throws BlockStoreException {
        sparkSession.sql(String.format(DELETE_MATCHING_EVENT_BY_HASH, quotedString(hash)));
    }

    @Override
    public Token queryDomainnameToken(Sha256Hash domainNameBlockHash) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TOKENS_BY_DOMAINNAME_SQL, quotedString(domainNameBlockHash)))
                .as(Encoders.bean(TokenModel.class)).first().toToken();
    }

    @Override
    public Token getTokensByDomainname(String domainname) throws BlockStoreException {

        return sparkSession.sql(String.format(SELECT_TOKENS_BY_DOMAINNAME_SQL0, quotedString(domainname)))
                .as(Encoders.bean(TokenModel.class)).first().toToken();

    }

    @Override
    public List<Sha256Hash> getWhereConfirmedNotMilestone() throws BlockStoreException {
        List<Sha256Hash> storedBlockHashes = new ArrayList<Sha256Hash>();

        Dataset<Row> s = sparkSession.sql(SELECT_BLOCKS_CONFIRMED_AND_NOT_MILESTONE_SQL);

        for (Row r : s.collectAsList()) {
            storedBlockHashes.add(Sha256Hash.wrap(r.getString(0)));
        }
        return storedBlockHashes;

    }

    @Override
    public List<OrderCancel> getOrderCancelByOrderBlockHash(HashSet<String> orderBlockHashs)
            throws BlockStoreException {
        if (orderBlockHashs.isEmpty()) {
            return new ArrayList<OrderCancel>();
        }
        List<OrderCancel> orderCancels = new ArrayList<OrderCancel>();

        StringBuffer sql = new StringBuffer();
        for (String s : orderBlockHashs) {
            sql.append(",'").append(quotedString(s)).append("'");
        }
        Dataset<OrderCancelModel> s = sparkSession
                .sql(SELECT_ORDERCANCEL_SQL + " AND orderblockhash IN (" + sql.substring(1) + ")")
                .as(Encoders.bean(OrderCancelModel.class));

        for (OrderCancelModel m : s.collectAsList()) {

            orderCancels.add(m.toOrderCancel());
        }
        return orderCancels;
    }

    @Override
    public List<MatchLastdayResult> getTimeBetweenMatchingEvents(String tokenid, String basetoken, Long startDate,
            Long endDate, int count) throws BlockStoreException {

        String sql = SELECT_MATCHING_EVENT + " where  basetokenid = " + quotedString(basetoken) + " and  tokenid = "
                + quotedString(tokenid);

        if (startDate != null)
            sql += " AND inserttime >= " + startDate;
        sql += "  ORDER BY inserttime DESC " + "LIMIT   " + count;

        return sparkSession.sql(sql).as(Encoders.bean(MatchLastdayResult.class)).collectAsList();

    }

    @Override
    public List<MatchLastdayResult> getTimeAVGBetweenMatchingEvents(String tokenid, String basetoken, Long startDate,
            Long endDate, int count) throws BlockStoreException {

        String SELECT_AVG = "select tokenid,basetokenid,  avgprice, totalQuantity,matchday "
                + "from matchingdaily where datediff(curdate(),str_to_date(matchday,'%Y-%m-%d'))<=30";
        String sql = SELECT_AVG + " AND  basetokenid = " + quotedString(basetoken) + " AND  tokenid = "
                + quotedString(tokenid);

        sql += "  ORDER BY inserttime DESC " + "LIMIT   " + count;
        // log.debug(sql + " tokenid = " +tokenid + " basetoken =" +
        // basetoken );
        return sparkSession.sql(sql).as(Encoders.bean(MatchLastdayResult.class)).collectAsList();

    }

    @Override
    public void insertAccessPermission(String pubKey, String accessToken) throws BlockStoreException {
    }

    @Override
    public long getCountAccessPermissionByPubKey(String pubKey, String accessToken) throws BlockStoreException {

        return sparkSession.sql("select count(1) as count from access_permission where pubKey = " + quotedString(pubKey)
                + " and accessToken = " + quotedString(accessToken)).count();

    }

    @Override
    public void insertAccessGrant(String address) throws BlockStoreException {
    }

    @Override
    public void deleteAccessGrant(String address) throws BlockStoreException {
    }

    @Override
    public long getCountAccessGrantByAddress(String address) throws BlockStoreException {

        return sparkSession.sql("select count(1) as count from access_grant where address =" + quotedString(address))
                .count();

    }

    @Override
    public void insertChainBlockQueue(ChainBlockQueue chainBlockQueue) throws BlockStoreException {
        List<ChainBlockQueue> list = new ArrayList<>();
        list.add(chainBlockQueue);
        Dataset source = sparkSession.createDataset(list, Encoders.bean(ChainBlockQueue.class));
        SparkData.chainblockqueue.as("target").merge(source.as("source"), "target.hash = source.hash ").whenMatched()
                .updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void deleteAllChainBlockQueue() throws BlockStoreException {
        sparkSession.sql(" delete from chainblockqueue ");

    }

    @Override
    public void deleteChainBlockQueue(List<ChainBlockQueue> chainBlockQueues) throws BlockStoreException {

        for (ChainBlockQueue chainBlockQueue : chainBlockQueues) {

            sparkSession.sql(" delete from chainblockqueue  where hash = " + chainBlockQueue.getHash());

        }
    }

    @Override
    public List<ChainBlockQueue> selectChainblockqueue(boolean orphan, int limit) throws BlockStoreException {

        return sparkSession.sql(
                SELECT_CHAINBLOCKQUEUE + " where orphan =  " + orphan + " order by chainlength asc" + " limit " + limit)
                .as(Encoders.bean(ChainBlockQueue.class)).collectAsList();

    }

    @Override
    public void insertLockobject(LockObject lockObject) throws BlockStoreException {
        List<LockObject> list = new ArrayList<>();
        list.add(lockObject);
        Dataset source = sparkSession.createDataset(list, Encoders.bean(LockObject.class));
        SparkData.lockobject.as("target").merge(source.as("source"), "target.lockobjectid = source.lockobjectid ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    @Override
    public void deleteLockobject(String lockobjectid) throws BlockStoreException {
        sparkSession.sql(" delete from lockobject  where lockobjectid =" + quotedString(lockobjectid));

    }

    @Override
    public void deleteAllLockobject() throws BlockStoreException {
        sparkSession.sql(" delete from lockobject ");

    }

    @Override
    public void saveAvgPrice(AVGMatchResult matchResult) throws BlockStoreException {
    }

    @Override
    public void batchAddAvgPrice() throws Exception {
        List<Long> times = selectTimesUntilNow();
        for (Long time : times) {
            Date date = new Date(time * 1000);
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String day = dateFormat.format(date);
            if (getCountMatching(day) == 0) {
                DateFormat dateFormat0 = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS");
                Date startDate = dateFormat0.parse(day + "-00:00:00:000");
                Date endDate = dateFormat0.parse(day + "-23:59:59:999");
                List<AVGMatchResult> list = queryTickerByTime(startDate.getTime(), endDate.getTime());
                if (list != null && !list.isEmpty()) {
                    List<String> tokenids = new ArrayList<String>();
                    for (AVGMatchResult matchResult : list) {
                        tokenids.add(matchResult.getTokenid() + "-" + matchResult.getBasetokenid());
                        saveAvgPrice(matchResult);

                    }
                    addLastdayPrice(tokenids);
                }
            }
        }

    }

    public void addLastdayPrice(List<String> tokenids) throws Exception {
        if (tokenids != null && !tokenids.isEmpty()) {
            Date yesterdayDate = new Date(System.currentTimeMillis() - 86400000L);
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String yesterday = dateFormat.format(yesterdayDate);
            DateFormat dateFormat0 = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS");
            long starttime = dateFormat0.parse(yesterday + "-00:00:00:000").getTime();
            long endtime = dateFormat0.parse(yesterday + "-23:59:59:999").getTime();
            for (String tokenid : tokenids) {
                MatchResult tempMatchResult = queryTickerLast(starttime, endtime, tokenid.split("-")[0],
                        tokenid.split("-")[1]);
                if (tempMatchResult != null) {
                    deleteLastdayPrice(tempMatchResult);
                    saveLastdayPrice(tempMatchResult);
                }

            }
        }

    }

    public void saveLastdayPrice(MatchResult matchResult) throws BlockStoreException {
        List<MatchResult> list = new ArrayList<>();
        list.add(matchResult);
        Dataset source = sparkSession.createDataset(list, Encoders.bean(MatchResult.class));
        SparkData.matchinglastday.as("target")
                .merge(source.as("source"),
                        "target.tokenid = source.tokenid " + "target.basetokenid = source.basetokenid ")
                .whenMatched().updateAll().whenNotMatched().insertAll().execute();

    }

    public void deleteLastdayPrice(MatchResult matchResult) throws BlockStoreException {
        sparkSession.sql("delete from  matchinglastday where tokenid=" + quotedString(matchResult.getTokenid())
                + " and basetokenid=" + quotedString(matchResult.getBasetokenid()));
    }

    public List<Long> selectTimesUntilNow() throws ParseException {

        Date yesterdayDate = new Date(System.currentTimeMillis() - 86400000L);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String yesterday = dateFormat.format(yesterdayDate);
        DateFormat dateFormat0 = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS");

        long time = dateFormat0.parse(yesterday + "-23:59:59:999").getTime();

        Dataset<Row> s = sparkSession
                .sql(" select inserttime from matching where inserttime<=" + time / 1000 + " order by  inserttime asc");

        List<Long> times = new ArrayList<Long>();
        for (Row r : s.collectAsList()) {
            times.add(r.getLong(0));

        }
        return times;

    }

    public long getCountMatching(String matchday) throws BlockStoreException {

        return sparkSession.sql(" select count(1) from matchingdaily where matchday=" + quotedString(matchday)).count();

    }

    @Override
    public List<AVGMatchResult> queryTickerByTime(long starttime, long endtime) throws BlockStoreException {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        return sparkSession
                .sql(" select tokenid,basetokenid,sum(price),count(price),"
                        + "max(price),min(price),sum(executedQuantity)" + " from matching where inserttime>="
                        + starttime / 1000 + " and inserttime<=" + endtime / 1000 + " group by tokenid,basetokenid  ")
                .as(Encoders.bean(AVGMatchResult.class)).collectAsList();

    }

    public MatchResult queryTickerLast(long starttime, long endtime, String tokenid, String basetokenid)
            throws BlockStoreException {
        return sparkSession.sql(" select tokenid,basetokenid,  price,  executedQuantity "
                + " from matching where inserttime>=" + starttime / 1000 + " and inserttime<=" + endtime / 1000
                + "   and  tokenid=" + "%s" + " and basetokenid=%s  ").as(Encoders.bean(MatchResult.class)).first();
    }

    @Override
    public LockObject selectLockobject(String lockobjectid) throws BlockStoreException {
        return sparkSession.sql(
                " select lockobjectid, locktime from lockobject  where lockobjectid = " + quotedString(lockobjectid))
                .as(Encoders.bean(LockObject.class)).first();

    }

    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.addAll(getCreateTablesSQL1());
        sqlStatements.addAll(getCreateTablesSQL2());
        return sqlStatements;
    }

    protected List<String> getCreateTablesSQL1() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(SparkStoreParameter.CREATE_BLOCKS_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_OUTPUT_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_TOKENS_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MATCHING_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MULTISIGN_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_TX_REWARD_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_USERDATA_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_PAYMULTISIGN_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_PAYMULTISIGNADDRESS_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_ORDER_CANCEL_TABLE);

        sqlStatements.add(SparkStoreParameter.CREATE_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_ORDERS_TABLE);

        sqlStatements.add(SparkStoreParameter.CREATE_SETTINGS_TABLE);

        sqlStatements.add(SparkStoreParameter.CREATE_MCMC_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MATCHING_LAST_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MATCHING_LAST_DAY_TABLE);
        return sqlStatements;
    }

    protected List<String> getCreateTablesSQL2() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(SparkStoreParameter.CREATE_ACCESS_PERMISSION_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_ACCESS_GRANT_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_CONTRACT_EVENT_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_CONTRACT_ACCOUNT_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_CONTRACT_EXECUTION_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_CHAINBLOCKQUEUE_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_LOCKOBJECT_TABLE);
        sqlStatements.add(SparkStoreParameter.CREATE_MATCHINGDAILY_TABLE);
        return sqlStatements;
    }

    public void updateDatabse() throws BlockStoreException, SQLException {

        String settingValue = getSettingValue("version");
        String ver = "";
        if (settingValue != null)
            ver = new String(settingValue);

        if ("03".equals(ver)) {
            updateTables(getCreateTablesSQL2());

            dbupdateversion("05");
        }

    }

    protected List<String> getCreateSchemeSQL() {
        // do nothing
        return Collections.emptyList();
    }

    protected String getUpdateSettingsSLQ() {
        // return UPDATE_SETTINGS_SQL;
        return getUpdate() + " settings SET settingvalue = %s WHERE name = %s";
    }

    public String getUpdateBlockEvaluationMilestoneSQL() {
        return UPDATE_BLOCKEVALUATION_MILESTONE_SQL;
    }

    protected String getUpdateBlockEvaluationRatingSQL() {
        return UPDATE_BLOCKEVALUATION_RATING_SQL;
    }

    protected String getUpdateOutputsSpentSQL() {
        return UPDATE_OUTPUTS_SPENT_SQL;
    }

    protected String getUpdateOutputsConfirmedSQL() {
        return UPDATE_OUTPUTS_CONFIRMED_SQL;
    }

    protected String getUpdateOutputsSpendPendingSQL() {
        return UPDATE_OUTPUTS_SPENDPENDING_SQL;
    }

    @Override
    public void close() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses, byte[] tokenid) throws UTXOProviderException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UTXO getTransactionOutput(Sha256Hash blockHash, Sha256Hash txHash, long index) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void abortDatabaseBatchWrite() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void defaultDatabaseBatchWrite() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteStore() throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateOrderSpent(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash, boolean spent,
            Sha256Hash spenderBlockHash) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Sha256Hash> getRewardBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Token> getMarketTokenList() throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, BigInteger> getTokenAmountMap() throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Block> findRetryBlocks(long minheight) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<byte[]> blocksFromChainLength(long start, long end) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getCountMultiSignAddress(String tokenid) throws BlockStoreException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getCountMultiSignByTokenIndexAndAddress(String tokenid, long tokenindex, String address)
            throws BlockStoreException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getMaxPayMultiSignAddressSignIndex(String orderid) throws BlockStoreException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public PayMultiSign getPayMultiSignWithOrderid(String orderid) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<PayMultiSignAddress> getPayMultiSignAddressWithOrderid(String orderid) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updatePayMultiSignBlockhash(String orderid, byte[] blockhash) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void insertBatchBlock(Block block) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteBatchBlock(Sha256Hash hash) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<BatchBlock> getBatchBlockList() throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void insertSubtanglePermission(String pubkey, String userdatapubkey, String status)
            throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteSubtanglePermission(String pubkey) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateSubtanglePermission(String pubkey, String userdataPubkey, String status)
            throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Map<String, String>> getAllSubtanglePermissionList() throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Map<String, String>> getSubtanglePermissionListByPubkey(String pubkey) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Map<String, String>> getSubtanglePermissionListByPubkeys(List<String> pubkeys)
            throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean existMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getHeightTransactions(List<Sha256Hash> txHashs) throws BlockStoreException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public List<BlockWrap> getEntryPoints(long currChainLength) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void insertContractEvent(Collection<ContractEventRecord> records) throws BlockStoreException {
        // TODO Auto-generated method stub

    }

    @Override
    public ContractExecution getMaxConfirmedContractExecution() throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<UTXO> getOpenOutputsByBlockhash(String blockhash) throws UTXOProviderException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Token> getTokenID(Set<String> tokenids) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Token> getTokensListFromDomain(String domainname) throws BlockStoreException {
        // TODO Auto-generated method stub
        return null;
    }

}
