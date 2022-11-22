/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public class SparkStoreParameter {

    public static final String OPENORDERHASH = "0x0000000000000000000000000000000000000000000000000000000000000000";

    public static final String LIMIT_500 = " limit 500 ";

    public static final Logger log = LoggerFactory.getLogger(SparkStoreParameter.class);

    public static final String VERSION_SETTING = "version";

    // Drop table SQL.
    public static String DROP_SETTINGS_TABLE = "DROP TABLE IF EXISTS settings";
    public static String DROP_BLOCKS_TABLE = "DROP TABLE IF EXISTS blocks";
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
    public static String DROP_CONTRACT_EXECUTION_TABLE = "DROP TABLE IF EXISTS contractexecution";
    public static String DROP_ORDERCANCEL_TABLE = "DROP TABLE IF EXISTS ordercancel";
    public static String DROP_BATCHBLOCK_TABLE = "DROP TABLE IF EXISTS batchblock";
    public static String DROP_SUBTANGLE_PERMISSION_TABLE = "DROP TABLE IF EXISTS subtangle_permission";
    public static String DROP_ORDERS_TABLE = "DROP TABLE IF EXISTS orders";

    public static String DROP_MYSERVERBLOCKS_TABLE = "DROP TABLE IF EXISTS myserverblocks";
    public static String DROP_EXCHANGE_TABLE = "DROP TABLE exchange";
    public static String DROP_EXCHANGEMULTI_TABLE = "DROP TABLE exchange_multisign";
    public static String DROP_ACCESS_PERMISSION_TABLE = "DROP TABLE access_permission";
    public static String DROP_ACCESS_GRANT_TABLE = "DROP TABLE access_grant";
    public static String DROP_CONTRACT_EVENT_TABLE = "DROP TABLE contractevent";
    public static String DROP_CONTRACT_ACCOUNT_TABLE = "DROP TABLE contractaccount";
    public static String DROP_CHAINBLOCKQUEUE_TABLE = "DROP TABLE chainblockqueue";
    public static String DROP_MCMC_TABLE = "DROP TABLE mcmc";
    public static String DROP_LOCKOBJECT_TABLE = "DROP TABLE lockobject";
    public static String DROP_MATCHING_LAST_TABLE = "DROP TABLE matchinglast";
    public static String DROP_MATCHINGDAILY_TABLE = "DROP TABLE matchingdaily";
    public static String DROP_MATCHINGLASTDAY_TABLE = "DROP TABLE matchinglastday";
    // Queries SQL.
    public static final String SELECT_SETTINGS_SQL = "SELECT settingvalue FROM settings WHERE name = ?";
    public static final String INSERT_SETTINGS_SQL = getInsert() + "  INTO settings(name, settingvalue) VALUES(?, ?)";

    public static final String SELECT_BLOCKS_TEMPLATE = "  blocks.hash, block,  "
            + "  height, milestone, milestonelastupdate,  inserttime,   solid, confirmed";

    public static final String SELECT_BLOCKS_SQL = " select " + SELECT_BLOCKS_TEMPLATE + " FROM blocks WHERE hash = ?"
            + afterSelect();

    public static final String SELECT_BLOCKS_MILESTONE_SQL = "SELECT block, height FROM blocks WHERE height "
            + " >= (select min(height) from blocks where  milestone >= ? and  milestone <=?)"
            + " and height <= (select max(height) from blocks where  milestone >= ? and  milestone <=?) "
            + afterSelect() + " order by height asc ";

    public static final String SELECT_MCMC_TEMPLATE = "  hash, rating, depth, cumulativeweight ";

    public static final String SELECT_NOT_INVALID_APPROVER_BLOCKS_SQL = "SELECT " + SELECT_BLOCKS_TEMPLATE
            + "  , rating, depth, cumulativeweight "
            + "  FROM blocks, mcmc WHERE blocks.hash= mcmc.hash and (prevblockhash = ? or prevbranchblockhash = ?) AND solid >= 0 "
            + afterSelect();

    public static final String SELECT_SOLID_APPROVER_BLOCKS_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " ,  rating, depth, cumulativeweight "
            + " FROM blocks, mcmc WHERE blocks.hash= mcmc.hash and (prevblockhash = ? or prevbranchblockhash = ?) AND solid = 2 "
            + afterSelect();

    public static final String SELECT_SOLID_APPROVER_HASHES_SQL = "SELECT hash FROM blocks "
            + "WHERE blocks.prevblockhash = ? or blocks.prevbranchblockhash = ?" + afterSelect();

    public static final String INSERT_BLOCKS_SQL = getInsert() + "  INTO blocks(hash,  height, block,  prevblockhash,"
            + "prevbranchblockhash,mineraddress,blocktype,  "
            + "milestone, milestonelastupdate,  inserttime,  solid, confirmed  )"
            + " VALUES(?, ?, ?, ?, ?,?, ?, ?, ?, ? ,  ?, ? )";

    public static final String INSERT_OUTPUTS_SQL = getInsert()
            + " INTO outputs (hash, outputindex, coinvalue, scriptbytes, toaddress, addresstargetable,"
            + " coinbase, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,time, spendpendingtime, minimumsign)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)";

    public static final String SELECT_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
            + " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
            + "spendpending , spendpendingtime, minimumsign, time, spenderblockhash FROM outputs WHERE hash = ? AND outputindex = ? AND blockhash = ? ";

    public static final String SELECT_TRANSACTION_OUTPUTS_SQL_BASE = "SELECT " + "outputs.hash, coinvalue, scriptbytes, "
            + " outputs.outputindex, coinbase, " + "  outputs.toaddress  as  toaddress,"
            + " outputsmulti.toaddress  as multitoaddress, " + "  addresstargetable, blockhash, tokenid, "
            + " fromaddress, memo, spent, confirmed, "
            + "spendpending,spendpendingtime,  minimumsign, time , spenderblockhash "
            + " FROM outputs LEFT JOIN outputsmulti " + " ON outputs.hash = outputsmulti.hash"
            + " AND outputs.outputindex = outputsmulti.outputindex ";

    public static final String SELECT_OPEN_TRANSACTION_OUTPUTS_SQL = SELECT_TRANSACTION_OUTPUTS_SQL_BASE
            + " WHERE  confirmed=true and spent= false and outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?";

    public static final String SELECT_OPEN_TRANSACTION_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress as toaddress , addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime, minimumsign, time , spenderblockhash"
            + " , outputsmulti.toaddress  as multitoaddress" + " FROM outputs LEFT JOIN outputsmulti "
            + " ON outputs.hash = outputsmulti.hash AND outputs.outputindex = outputsmulti.outputindex "
            + " WHERE   (outputs.toaddress = ? " + " OR outputsmulti.toaddress = ?) " + " AND tokenid = ?";
    public static final String SELECT_ALL_OUTPUTS_TOKEN_SQL = "SELECT " + " outputs.hash, coinvalue, "
            + " scriptbytes, outputs.outputindex, coinbase, outputs.toaddress, addresstargetable,"
            + " blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending, spendpendingtime , minimumsign, time , spenderblockhash"
            + " FROM outputs  WHERE  confirmed=true and spent= false and tokenid = ?";

    // Tables exist SQL.
    public static final String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM settings WHERE 1 = 2";

    public static final String SELECT_BLOCKS_TO_CONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks, mcmc  WHERE blocks.hash=mcmc.hash and solid=2 AND milestone = -1 AND confirmed = false AND height > ?"
            + " AND height <= ? AND mcmc.rating >= " + NetworkParameters.CONFIRMATION_UPPER_THRESHOLD + afterSelect();

    public static final String SELECT_BLOCKS_TO_UNCONFIRM_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks , mcmc WHERE blocks.hash=mcmc.hash and solid=2 AND milestone = -1 AND confirmed = true AND mcmc.rating < "
            + NetworkParameters.CONFIRMATION_LOWER_THRESHOLD + afterSelect();

    public static final String SELECT_BLOCKS_IN_MILESTONE_INTERVAL_SQL = "SELECT" + SELECT_BLOCKS_TEMPLATE
            + "  FROM blocks WHERE milestone >= ? AND milestone <= ?" + afterSelect();

    public static final String SELECT_SOLID_BLOCKS_IN_INTERVAL_SQL = "SELECT   " + SELECT_BLOCKS_TEMPLATE
            + " FROM blocks WHERE   height > ? AND height <= ? AND solid = 2 " + afterSelect();

    public static final String SELECT_BLOCKS_CONFIRMED_AND_NOT_MILESTONE_SQL = "SELECT hash "
            + "FROM blocks WHERE milestone = -1 AND confirmed = 1 " + afterSelect();

    public static final String SELECT_BLOCKS_NON_CHAIN_HEIGTH_SQL = "SELECT block "
            + "FROM blocks WHERE milestone = -1 AND height >= ? " + afterSelect();

    public static final String UPDATE_ORDER_SPENT_SQL = getUpdate() + " orders SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";
    public static final String UPDATE_ORDER_CONFIRMED_SQL = getUpdate() + " orders SET confirmed = ? "
            + " WHERE blockhash = ? AND collectinghash = ?";

    public static final String ORDER_TEMPLATE = "  blockhash, collectinghash, offercoinvalue, offertokenid, "
            + "confirmed, spent, spenderblockhash, targetcoinvalue, targettokenid, "
            + "beneficiarypubkey, validToTime, validFromTime, side , beneficiaryaddress, orderbasetoken, price, tokendecimals ";
    public static final String SELECT_ORDERS_BY_ISSUER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE collectinghash = ?";

    public static final String SELECT_ORDER_SPENT_SQL = "SELECT spent FROM orders WHERE blockhash = ? AND collectinghash = ?";
    public static final String SELECT_ORDER_CONFIRMED_SQL = "SELECT confirmed FROM orders WHERE blockhash = ? AND collectinghash = ?";
    public static final String SELECT_ORDER_SPENDER_SQL = "SELECT spenderblockhash FROM orders WHERE blockhash = ? AND collectinghash = ?";
    public static final String SELECT_ORDER_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE blockhash = ? AND collectinghash = ?";
    public static final String INSERT_ORDER_SQL = getInsert()
            + "  INTO orders (blockhash, collectinghash, offercoinvalue, offertokenid, confirmed, spent, spenderblockhash, "
            + "targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, validFromTime, side, beneficiaryaddress, orderbasetoken, price, tokendecimals) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,  ?,?,?,?,?,?,?)";
    public static final String INSERT_CONTRACT_EVENT_SQL = getInsert()
            + "  INTO contractevent (blockhash,   contracttokenid, confirmed, spent, spenderblockhash, "
            + "targetcoinvalue, targettokenid, beneficiarypubkey, validToTime, validFromTime,  beneficiaryaddress) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,  ?)";
    public static final String INSERT_OrderCancel_SQL = getInsert()
            + " INTO ordercancel (blockhash, orderblockhash, confirmed, spent, spenderblockhash,time) "
            + " VALUES (?, ?, ?, ?, ?,?)";

    public static final String INSERT_TOKENS_SQL = getInsert()
            + " INTO tokens (blockhash, confirmed, tokenid, tokenindex, amount, "
            + "tokenname, description, domainname, signnumber,tokentype, tokenstop,"
            + " prevblockhash, spent, spenderblockhash, tokenkeyvalues, revoked,language,classification, decimals, domainpredblockhash) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?,?)";

    public static String SELECT_TOKENS_SQL_TEMPLATE = "SELECT blockhash, confirmed, tokenid, tokenindex, amount, tokenname, description, domainname, signnumber,tokentype, tokenstop ,"
            + "tokenkeyvalues, revoked,language,classification,decimals, domainpredblockhash ";

    public static final String SELECT_TOKEN_SPENT_BY_BLOCKHASH_SQL = "SELECT spent FROM tokens WHERE blockhash = ?";

    public static final String SELECT_TOKEN_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE blockhash = ?";

    public static final String SELECT_TOKEN_ANY_CONFIRMED_SQL = "SELECT confirmed FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    public static final String SELECT_TOKEN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenid = ? AND tokenindex = ? AND confirmed = true";

    public static final String SELECT_DOMAIN_ISSUING_CONFIRMED_BLOCK_SQL = "SELECT blockhash FROM tokens WHERE tokenname = ? AND domainpredblockhash = ? AND tokenindex = ? AND confirmed = true";

    public static final String SELECT_DOMAIN_DESCENDANT_CONFIRMED_BLOCKS_SQL = "SELECT blockhash FROM tokens WHERE domainpredblockhash = ? AND confirmed = true";

    public static final String SELECT_TOKEN_SPENDER_SQL = "SELECT spenderblockhash FROM tokens WHERE blockhash = ?";

    public static final String SELECT_TOKEN_PREVBLOCKHASH_SQL = "SELECT prevblockhash FROM tokens WHERE blockhash = ?";

    public static final String SELECT_TOKEN_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE blockhash = ?";

    public static final String SELECT_TOKENID_SQL = SELECT_TOKENS_SQL_TEMPLATE + " FROM tokens WHERE tokenid = ?";

    public static final String UPDATE_TOKEN_SPENT_SQL = getUpdate() + " tokens SET spent = ?, spenderblockhash = ? "
            + " WHERE blockhash = ?";

    public static final String UPDATE_TOKEN_CONFIRMED_SQL = getUpdate() + " tokens SET confirmed = ? "
            + " WHERE blockhash = ?";

    public static final String SELECT_CONFIRMED_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE confirmed = true";

    public static final String SELECT_MARKET_TOKENS_SQL = SELECT_TOKENS_SQL_TEMPLATE
            + " FROM tokens WHERE tokentype = 1 and confirmed = true";

    public static final String SELECT_TOKENS_ACOUNT_MAP_SQL = "SELECT tokenid, amount  as amount "
            + "FROM tokens WHERE confirmed = true ";

    public static final String COUNT_TOKENSINDEX_SQL = "SELECT blockhash, tokenindex FROM tokens"
            + " WHERE tokenid = ? AND confirmed = true ORDER BY tokenindex DESC limit 1";

    public static final String SELECT_TOKENS_BY_DOMAINNAME_SQL = "SELECT blockhash, tokenid FROM tokens WHERE blockhash = ? limit 1";

    public static final String SELECT_TOKENS_BY_DOMAINNAME_SQL0 = "SELECT blockhash, tokenid "
            + "FROM tokens WHERE tokenname = ?  AND confirmed = true limit 1";

    public static final String UPDATE_SETTINGS_SQL = getUpdate() + " settings SET settingvalue = ? WHERE name = ?";

    public static final String UPDATE_OUTPUTS_SPENT_SQL = getUpdate()
            + " outputs SET spent = ?, spenderblockhash = ? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    public static final String UPDATE_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    public static final String UPDATE_ALL_OUTPUTS_CONFIRMED_SQL = getUpdate()
            + " outputs SET confirmed = ? WHERE blockhash = ?";

    public static final String UPDATE_OUTPUTS_SPENDPENDING_SQL = getUpdate()
            + " outputs SET spendpending = ?, spendpendingtime=? WHERE hash = ? AND outputindex= ? AND blockhash = ?";

    public static final String UPDATE_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL = getUpdate()
            + " mcmc SET cumulativeweight = ?, depth = ? WHERE hash = ?";
    public static final String INSERT_BLOCKEVALUATION_WEIGHT_AND_DEPTH_SQL = getInsert()
            + " into mcmc ( cumulativeweight  , depth   , hash, rating  ) VALUES (?,?,?, ?)  ";

    public static final String SELECT_MCMC_CHAINLENGHT_SQL = "  select mcmc.hash "
            + " from blocks, mcmc where mcmc.hash=blocks.hash and milestone < ?  and milestone > 0  ";

    public static final String UPDATE_BLOCKEVALUATION_MILESTONE_SQL = getUpdate()
            + " blocks SET milestone = ?, milestonelastupdate= ?  WHERE hash = ?";

    public static final String UPDATE_BLOCKEVALUATION_CONFIRMED_SQL = getUpdate()
            + " blocks SET confirmed = ? WHERE hash = ?";

    public static final String UPDATE_BLOCKEVALUATION_RATING_SQL = getUpdate() + " mcmc SET rating = ? WHERE hash = ?";

    public static final String UPDATE_BLOCKEVALUATION_SOLID_SQL = getUpdate() + " blocks SET solid = ? WHERE hash = ?";

    public static final String SELECT_MULTISIGNADDRESS_SQL = "SELECT blockhash, tokenid, address, pubKeyHex, posIndex, tokenHolder FROM multisignaddress WHERE tokenid = ? AND blockhash = ?";
    public static final String INSERT_MULTISIGNADDRESS_SQL = "INSERT INTO multisignaddress (tokenid, address, pubKeyHex, posIndex,blockhash,tokenHolder) VALUES (?, ?, ?, ?,?,?)";
    public static final String DELETE_MULTISIGNADDRESS_SQL = "DELETE FROM multisignaddress WHERE tokenid = ? AND address = ?";
    public static final String COUNT_MULTISIGNADDRESS_SQL = "SELECT COUNT(*) as count FROM multisignaddress WHERE tokenid = ?";

    public static final String INSERT_MULTISIGNBY_SQL = "INSERT INTO multisignby (tokenid, tokenindex, address) VALUES (?, ?, ?)";
    public static final String SELECT_MULTISIGNBY_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    public static final String SELECT_MULTISIGNBY0_SQL = "SELECT COUNT(*) as count FROM multisignby WHERE tokenid = ? AND tokenindex = ?";

    public static final String SELECT_MULTISIGN_ADDRESS_ALL_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign,(select count(ms1.sign) from multisign ms1 where ms1.tokenid=tokenid and tokenindex=ms1.tokenindex and ms1.sign!=0 ) as count FROM multisign  WHERE 1=1 ";
    public static final String SELECT_MULTISIGN_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE address = ? ORDER BY tokenindex ASC";
    public static final String SELECT_MULTISIGN_TOKENID_ADDRESS_SQL = "SELECT id, tokenid, tokenindex, address, blockhash, sign FROM multisign WHERE tokenid = ? and address = ? ORDER BY tokenindex ASC";

    public static final String INSERT_MULTISIGN_SQL = "INSERT INTO multisign (tokenid, tokenindex, address, blockhash, sign, id) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String UPDATE_MULTISIGN_SQL = "UPDATE multisign SET blockhash = ?, sign = ? WHERE tokenid = ? AND tokenindex = ? AND address = ?";
    public static final String UPDATE_MULTISIGN1_SQL = "UPDATE multisign SET blockhash = ? WHERE tokenid = ? AND tokenindex = ?";
    public static final String SELECT_COUNT_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND address = ? ";
    public static final String SELECT_COUNT_ALL_MULTISIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ?  AND sign=?";

    public static final String DELETE_MULTISIGN_SQL = "DELETE FROM multisign WHERE tokenid = ?";

    public static final String SELECT_COUNT_MULTISIGN_SIGN_SQL = "SELECT COUNT(*) as count FROM multisign WHERE tokenid = ? AND tokenindex = ? AND sign = ?";

    /* REWARD */
    public static final String INSERT_TX_REWARD_SQL = getInsert()
            + "  INTO txreward (blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength) VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String SELECT_TX_REWARD_MAX_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=(SELECT MAX(chainlength) FROM txreward WHERE confirmed=1)";
    public static final String SELECT_TX_REWARD_CONFIRMED_AT_HEIGHT_REWARD_SQL = "SELECT blockhash, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward"
            + " WHERE confirmed = 1 AND chainlength=?";
    public static final String SELECT_TX_REWARD_ALL_CONFIRMED_REWARD_SQL = "SELECT blockhash, confirmed, "
            + "spent, spenderblockhash, prevblockhash, difficulty, chainlength FROM txreward "
            + "WHERE confirmed = 1 order by chainlength ";

    public static final String SELECT_TX_REWARD_CONFIRMED_SQL = "SELECT confirmed " + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_TX_REWARD_CHAINLENGTH_SQL = "SELECT chainlength "
            + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_TX_REWARD_DIFFICULTY_SQL = "SELECT difficulty " + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_TX_REWARD_SPENT_SQL = "SELECT spent " + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_TX_REWARD_SPENDER_SQL = "SELECT spenderblockhash "
            + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_TX_REWARD_PREVBLOCKHASH_SQL = "SELECT prevblockhash "
            + "FROM txreward WHERE blockhash = ?";
    public static final String SELECT_REWARD_WHERE_PREV_HASH_SQL = "SELECT blockhash "
            + "FROM txreward WHERE prevblockhash = ?";
    public static final String UPDATE_TX_REWARD_CONFIRMED_SQL = "UPDATE txreward SET confirmed = ? WHERE blockhash = ?";
    public static final String UPDATE_TX_REWARD_SPENT_SQL = "UPDATE txreward SET spent = ?, spenderblockhash = ? WHERE blockhash = ?";

    /* MATCHING EVENTS */
    public static final String INSERT_MATCHING_EVENT_SQL = getInsert()
            + " INTO matching (txhash, tokenid, basetokenid, price, executedQuantity, inserttime) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String SELECT_MATCHING_EVENT = "SELECT txhash, tokenid,basetokenid,  price, executedQuantity, inserttime "
            + "FROM matching ";
    public static final String DELETE_MATCHING_EVENT_BY_HASH = "DELETE FROM matching WHERE txhash = ?";
    // lastest MATCHING EVENTS
    public static final String INSERT_MATCHING_EVENT_LAST_SQL = getInsert()
            + " INTO matchinglast (txhash, tokenid, basetokenid, price, executedQuantity, inserttime) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String SELECT_MATCHING_EVENT_LAST = "SELECT txhash, tokenid,basetokenid,  price, executedQuantity, inserttime "
            + "FROM matchinglast ";
    public static final String DELETE_MATCHING_EVENT_LAST_BY_KEY = "DELETE FROM matchinglast WHERE tokenid = ? and basetokenid=?";

    /* OTHER */
    public static final String INSERT_OUTPUTSMULTI_SQL = "insert into outputsmulti (hash, toaddress, outputindex) values (?, ?, ?)";
    public static final String SELECT_OUTPUTSMULTI_SQL = "select hash, toaddress, outputindex from outputsmulti where hash=? and outputindex=?";

    public static final String SELECT_USERDATA_SQL = "SELECT blockhash, dataclassname, data, pubKey, blocktype FROM userdata WHERE dataclassname = ? and pubKey = ?";
    public static final String INSERT_USERDATA_SQL = "INSERT INTO userdata (blockhash, dataclassname, data, pubKey, blocktype) VALUES (?, ?, ?, ?, ?)";
    public static final String UPDATE_USERDATA_SQL = "UPDATE userdata SET blockhash = ?, data = ? WHERE dataclassname = ? and pubKey = ?";

    public static final String INSERT_BATCHBLOCK_SQL = "INSERT INTO batchblock (hash, block, inserttime) VALUE (?, ?, ?)";
    public static final String DELETE_BATCHBLOCK_SQL = "DELETE FROM batchblock WHERE hash = ?";
    public static final String SELECT_BATCHBLOCK_SQL = "SELECT hash, block, inserttime FROM batchblock order by inserttime ASC";
    public static final String INSERT_SUBTANGLE_PERMISSION_SQL = "INSERT INTO  subtangle_permission (pubkey, userdataPubkey , status) VALUE (?, ?, ?)";

    public static final String DELETE_SUBTANGLE_PERMISSION_SQL = "DELETE FROM  subtangle_permission WHERE pubkey=?";
    public static final String UPATE_ALL_SUBTANGLE_PERMISSION_SQL = "UPDATE   subtangle_permission set status=? ,userdataPubkey=? WHERE  pubkey=? ";

    public static final String SELECT_ALL_SUBTANGLE_PERMISSION_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission ";

    public static final String SELECT_SUBTANGLE_PERMISSION_BY_PUBKEYS_SQL = "SELECT   pubkey, userdataPubkey , status FROM subtangle_permission WHERE 1=1 ";

    public static final String SELECT_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders ORDER BY blockhash, collectinghash";

    public static final String SELECT_OPEN_ORDERS_SORTED_SQL = "SELECT " + ORDER_TEMPLATE
            + " FROM orders WHERE confirmed=1 AND spent=0 ";

    public static final String SELECT_MY_REMAINING_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? ";
    public static final String SELECT_MY_INITIAL_OPEN_ORDERS_SQL = "SELECT " + ORDER_TEMPLATE + " FROM orders "
            + " WHERE confirmed=1 AND spent=1 AND beneficiaryaddress=? AND collectinghash=" + OPENORDERHASH
            + " AND blockhash IN ( SELECT blockhash FROM orders "
            + "     WHERE confirmed=1 AND spent=0 AND beneficiaryaddress=? )";
    // TODO remove test
    public static final String SELECT_AVAILABLE_UTXOS_SORTED_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress, "
            + "addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, spendpending,spendpendingtime, minimumsign, time, hash, outputindex, spenderblockhash "
            + " FROM outputs WHERE confirmed=1 AND spent=0 ORDER BY hash, outputindex";

    public static String INSERT_EXCHANGE_SQL = getInsert()
            + "  INTO exchange (orderid, fromAddress, fromTokenHex, fromAmount,"
            + " toAddress, toTokenHex, toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market,memo) VALUES (?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static String DELETE_EXCHANGE_SQL = "DELETE FROM exchange WHERE orderid=?";
    public static String SELECT_EXCHANGE_ORDERID_SQL = "SELECT orderid,"
            + " fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex,"
            + " toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market,signInputData FROM exchange WHERE orderid = ?";

    public static String INSERT_EXCHANGEMULTI_SQL = getInsert()
            + "  INTO exchange_multisign (orderid, pubkey,sign) VALUES (?, ?,?)";

    public static final String SELECT_ORDERCANCEL_SQL = "SELECT blockhash, orderblockhash, confirmed, spent, spenderblockhash,time FROM ordercancel WHERE 1 = 1";
    public static String SELECT_EXCHANGE_SQL_A = "SELECT DISTINCT orderid, fromAddress, "
            + "fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, "
            + "data, toSign, fromSign, toOrderId, fromOrderId, market,memo "
            + "FROM exchange e WHERE (toSign = false OR fromSign = false) AND " + "(fromAddress = ? OR toAddress = ?) "
            + afterSelect();

    public static final String SELECT_CONTRACT_EXECUTION_SQL = "SELECT blockhash, contracttokenid confirmed, spent, "
            + "spenderblockhash, prevblockhash, difficulty, chainlength ";

    public static final String CONTRACT_EXECUTION_SELECT_MAX_CONFIRMED_SQL = SELECT_CONTRACT_EXECUTION_SQL
            + " FROM contractexecution" + " WHERE confirmed = 1 AND  contracttokenid = ? "
            + " AND chainlength=(SELECT MAX(chainlength) FROM contractexecution WHERE confirmed=1 and contracttokenid=?)";
    public static final String CONTRACT_EXECUTION_INSERT_SQL = getInsert()
            + "  INTO contractexecution (blockhash, contracttokenid, confirmed, spent, spenderblockhash, prevblockhash, difficulty, chainlength) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?,?)";

    public static final String BlockPrototype_SELECT_SQL = "   select prevblockhash, prevbranchblockhash, "
            + " inserttime from blockprototype   ";
    public static final String BlockPrototype_INSERT_SQL = getInsert()
            + "  INTO blockprototype (prevblockhash, prevbranchblockhash, inserttime) " + "VALUES (?, ?, ?)";
    public static final String BlockPrototype_DELETE_SQL = "   delete from blockprototype  where  prevblockhash =? and prevbranchblockhash=?  ";

    public static final String ChainBlockQueueColumn = " hash, block, chainlength, orphan, inserttime";
    public static final String INSERT_CHAINBLOCKQUEUE = getInsert() + "  INTO chainblockqueue (" + ChainBlockQueueColumn
            + ") " + " VALUES (?, ?, ?,?,?)";
    public static final String SELECT_CHAINBLOCKQUEUE = " select " + ChainBlockQueueColumn + " from chainblockqueue  ";

    public static NetworkParameters params;
    public static SparkSession sparkSession;
    public static String location;

    public SparkStoreParameter(NetworkParameters params, SparkSession sparkSession, String location) {
        this.params = params;
        this.sparkSession = sparkSession;
        this.location = location;
    }

    public static String afterSelect() {
        return "";
    }

    public static String getInsert() {
        return "insert ";
    }

    public static String getUpdate() {
        return "update ";
    }

    /**
     * Get the SQL statement that checks if tables exist.
     * 
     * @return The SQL prepared statement.
     */
    public static String getTablesExistSQL() {
        return SELECT_CHECK_TABLES_EXIST_SQL;
    }

    /**
     * Get the SQL to drop all the tables (DDL).
     * 
     * @return The SQL drop statements.
     */
    public static List<String> getDropTablesSQL() {
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
    public static String getSelectSettingsSQL() {
        return SELECT_SETTINGS_SQL;
    }

    /**
     * Get the SQL to insert a settings record.
     * 
     * @return The SQL insert statement.
     */
    public static String getInsertSettingsSQL() {
        return INSERT_SETTINGS_SQL;
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
    public boolean tablesExists() throws SQLException {
        return sparkSession.sql(getTablesExistSQL()).count() > 0;

    }

    /*
     * initial ps.setBytes(2, "03".getBytes());
     */
    public void dbversion(String version) throws SQLException {
        sparkSession.sql(getInsertSettingsSQL());

    }

    public static void dbupdateversion(String version) throws SQLException {
        sparkSession.sql(UPDATE_SETTINGS_SQL);

    }

    /*
     * check version and update the tables
     */
    public static synchronized void updateTables(List<String> sqls) throws SQLException, BlockStoreException {
        for (String sql : sqls) {

            sparkSession.sql(sql + " USING DELTA " + "   LOCATION '" + location + "'");
        }

    }

    public static String getDuplicateKeyErrorCode() {
        return "23000";
    }

    public static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    public static final String DATABASE_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    public static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:mysql://"; // "jdbc:log4jdbc:mysql://";

    // create table SQL
    public static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" + "    name varchar(32) ,\n"
            + "    settingvalue string,\n" + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" + ")\n";

    public static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n" + "    hash string ,\n"
            + "    height bigint ,\n" + "    block string ,\n"
            + "    prevblockhash  string  ,\n" + "    prevbranchblockhash  string ,\n"
            + "    mineraddress string  ,\n" + "    blocktype bigint ,\n"
            // reward block chain length is here milestone
            + "    milestone bigint ,\n" + "    milestonelastupdate bigint ,\n"
            + "    confirmed boolean ,\n"

            // solid is result of validation of the block,
            + "    solid bigint ,\n" + "    inserttime bigint \n"
         //   + "    CONSTRAINT blocks_pk PRIMARY KEY (hash) \n" 
            + ")  ";

    public static final String CREATE_MCMC_TABLE = "CREATE TABLE mcmc (\n" + "    hash string ,\n"
    // dynamic data
    // MCMC rating,depth,cumulativeweight
            + "    rating bigint ,\n" + "    depth bigint ,\n"
            + "    cumulativeweight bigint ,\n" + "    CONSTRAINT mcmc_pk PRIMARY KEY (hash) \n" + ")  ";

    public static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n"
            + "    blockhash string ,\n" + "    hash string ,\n"
            + "    outputindex bigint ,\n" + "    coinvalue string ,\n"
            + "    scriptbytes string ,\n" + "    toaddress varchar(255),\n"
            + "    addresstargetable bigint,\n" + "    coinbase boolean,\n" + "    tokenid varchar(255),\n"
            + "    fromaddress varchar(255),\n" + "    memo MEDIUMTEXT,\n" + "    minimumsign bigint ,\n"
            + "    time bigint,\n"
            // begin the derived value of the output from block
            // this is for track the spent, spent = true means spenderblock is
            // confirmed
            + "    spent boolean ,\n" + "    spenderblockhash  string,\n"
            // confirmed = the block of this output is confirmed
            + "    confirmed boolean ,\n"
            // this is indicator for wallet to minimize conflict, is set for
            // create at spender block
            + "    spendpending boolean ,\n" + "    spendpendingtime bigint\n"
          //  + "    CONSTRAINT outputs_pk PRIMARY KEY (blockhash, hash, outputindex) \n" 
            + "   )  \n";

    // This is table for output with possible multi sign address
    public static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n"
            + "    hash string ,\n" + "    outputindex bigint ,\n"
            + "    toaddress varchar(255) ,\n"
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress) \n" + ")  \n";

    public static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash string ,\n" + "   confirmed boolean ,\n"
            + "   spent boolean ,\n" + "   spenderblockhash string,\n"
            + "   prevblockhash string ,\n" + "   difficulty bigint ,\n"
            + "   chainlength bigint ,\n" + "   PRIMARY KEY (blockhash) ) ";

    public static final String CREATE_ORDERS_TABLE = "CREATE TABLE orders (\n"
            // initial issuing block hash
            + "    blockhash string ,\n"
            // ZEROHASH if confirmed by order blocks,
            // issuing ordermatch blockhash if issued by ordermatch block
            + "    collectinghash string ,\n" + "    offercoinvalue bigint ,\n"
            + "    offertokenid varchar(255),\n" + "   targetcoinvalue bigint,\n" + "    targettokenid varchar(255),\n"
            // buy or sell
            + "    side varchar(255),\n"
            // public address
            + "    beneficiaryaddress varchar(255),\n"
            // the pubkey that will receive the targettokens
            // on completion or returned tokens on cancels
            + "    beneficiarypubkey string(33),\n"
            // order is valid untill this time
            + "    validToTime bigint,\n"
            // a number used to track operations on the
            // order, e.g. increasing by one when refreshing
            // order is valid after this time
            + "    validFromTime bigint,\n"
            // order base token
            + "    orderbasetoken varchar(255),\n" + "    tokendecimals int ,\n" + "   price bigint,\n"
            // true iff a order block of this order is confirmed
            + "    confirmed boolean ,\n"
            // true if used by a confirmed ordermatch block (either
            // returned or used for another orderoutput/output)
            + "    spent boolean ,\n" + "    spenderblockhash  string,\n"
            + "    CONSTRAINT orders_pk PRIMARY KEY (blockhash, collectinghash) " + " USING HASH \n" + ")  \n";

    public static final String CREATE_ORDER_CANCEL_TABLE = "CREATE TABLE ordercancel (\n"
            + "   blockhash string ,\n" + "   orderblockhash string ,\n"
            + "   confirmed boolean ,\n" + "   spent boolean ,\n" + "   spenderblockhash string,\n"
            + "   time bigint ,\n" + "   PRIMARY KEY (blockhash) ) ";

    public static final String CREATE_MATCHING_TABLE = "CREATE TABLE matching (\n"
            + "    id bigint  AUTO_INCREMENT,\n" + "    txhash varchar(255) ,\n"
            + "    tokenid varchar(255) ,\n" + "    basetokenid varchar(255) ,\n"
            + "    price bigint ,\n" + "    executedQuantity bigint ,\n"
            + "    inserttime bigint ,\n" + "    PRIMARY KEY (id) \n" + ") \n";

    public static final String CREATE_MATCHINGDAILY_TABLE = "CREATE TABLE matchingdaily (\n"
            + "    id bigint  AUTO_INCREMENT,\n" + "    matchday varchar(255) ,\n"
            + "    tokenid varchar(255) ,\n" + "    basetokenid varchar(255) ,\n"
            + "    avgprice bigint ,\n" + "    totalQuantity bigint ,\n"
            + "    highprice bigint ,\n" + "    lowprice bigint ,\n" + "    open bigint ,\n"
            + "    close bigint ,\n" + "    matchinterval varchar(255) ,\n"
            + "    inserttime bigint ,\n" + "    PRIMARY KEY (id) \n" + ") \n";

    public static final String CREATE_MATCHING_LAST_TABLE = "CREATE TABLE matchinglast (\n"
            + "    txhash varchar(255) ,\n" + "    tokenid varchar(255) ,\n"
            + "    basetokenid varchar(255) ,\n" + "    price bigint ,\n"
            + "    executedQuantity bigint ,\n" + "    inserttime bigint ,\n"
            + "    PRIMARY KEY ( tokenid,basetokenid) \n" + ") \n";
    public static final String CREATE_MATCHING_LAST_DAY_TABLE = "CREATE TABLE matchinglastday (\n"
            + "    txhash varchar(255) ,\n" + "    tokenid varchar(255) ,\n"
            + "    basetokenid varchar(255) ,\n" + "    price bigint ,\n"
            + "    executedQuantity bigint ,\n" + "    inserttime bigint ,\n"
            + "    PRIMARY KEY ( tokenid,basetokenid) \n" + ") \n";

    public static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n" + "    blockhash string ,\n"
            + "    confirmed boolean ,\n" + "    tokenid varchar(255)   ,\n"
            + "    tokenindex bigint    ,\n" + "    amount string ,\n" + "    tokenname varchar(100) ,\n"
            + "    description varchar(5000) ,\n" + "    domainname varchar(100) ,\n"
            + "    signnumber bigint    ,\n" + "    tokentype int(11),\n" + "    tokenstop boolean,\n"
            + "    prevblockhash string,\n" + "    spent boolean ,\n"
            + "    spenderblockhash  string,\n" + "    tokenkeyvalues  string,\n" + "    revoked boolean   ,\n"
            + "    language char(2)   ,\n" + "    classification varchar(255)   ,\n"
            + "    domainpredblockhash varchar(255) ,\n" + "    decimals int  \n"
           // + "    PRIMARY KEY (blockhash)"
            + " \n) ";

    // Helpers
    public static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash string ,\n" + "    tokenid varchar(255)   ,\n"
            + "    address varchar(255),\n" + "    pubKeyHex varchar(255),\n" + "    posIndex int(11),\n"
            + "    tokenHolder int(11)  DEFAULT 0,\n" + "    PRIMARY KEY (blockhash, tokenid, pubKeyHex) \n) ";

    public static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255)   ,\n" + "    tokenid varchar(255)   ,\n"
            + "    tokenindex bigint    ,\n" + "    address varchar(255),\n"
            + "    blockhash  string ,\n" + "    sign int(11) ,\n" + "    PRIMARY KEY (id) \n) ";

    public static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n"
            + "    orderid varchar(255)   ,\n" + "    tokenid varchar(255)   ,\n"
            + "    toaddress varchar(255) ,\n" + "    blockhash string ,\n"
            + "    amount string ,\n" + "    minsignnumber bigint(20) ,\n" + "    outputHashHex varchar(255) ,\n"
            + "    outputindex bigint ,\n" + "    PRIMARY KEY (orderid) \n) ";

    public static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255)   ,\n" + "    pubKey varchar(255),\n" + "    sign int(11) ,\n"
            + "    signIndex int(11) ,\n" + "    signInputData string,\n"
            + "    PRIMARY KEY (orderid, pubKey) \n) ";

    public static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n"
            + "    blockhash string ,\n" + "    dataclassname varchar(255) ,\n"
            + "    data string ,\n" + "    pubKey varchar(255),\n" + "    blocktype bigint,\n"
            + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey) USING BTREE \n" + ") ";

    public static final String CREATE_BATCHBLOCK_TABLE = "CREATE TABLE batchblock (\n"
            + "    hash string ,\n" + "    block string ,\n"
            + "    inserttime datetime ,\n" + "   CONSTRAINT batchblock_pk PRIMARY KEY (hash)  \n" + ") ";

    public static final String CREATE_SUBTANGLE_PERMISSION_TABLE = "CREATE TABLE subtangle_permission (\n"
            + "    pubkey varchar(255) ,\n" + "    userdataPubkey varchar(255) ,\n"
            + "    status varchar(255) ,\n"
            + "   CONSTRAINT subtangle_permission_pk PRIMARY KEY (pubkey) USING BTREE \n" + ") ";

    /*
     * indicate of a server created block
     */
    public static final String CREATE_MYSERVERBLOCKS_TABLE = "CREATE TABLE myserverblocks (\n"
            + "    prevhash string ,\n" + "    hash string ,\n" + "    inserttime bigint,\n"
            + "    CONSTRAINT myserverblocks_pk PRIMARY KEY (prevhash, hash) USING BTREE \n" + ") ";

 
 
    public static final String CREATE_ACCESS_PERMISSION_TABLE = "CREATE TABLE access_permission (\n"
            + "   accessToken varchar(255) ,\n" + "   pubKey varchar(255),\n" + "   refreshTime bigint,\n"
            + "   PRIMARY KEY (accessToken) ) ";

    public static final String CREATE_ACCESS_GRANT_TABLE = "CREATE TABLE access_grant (\n"
            + "   address varchar(255),\n" + "   createTime bigint,\n" + "   PRIMARY KEY (address) ) ";

    public static final String CREATE_CONTRACT_EVENT_TABLE = "CREATE TABLE contractevent (\n"
            // initial issuing block hash
            + "    blockhash string ,\n" + "    contracttokenid varchar(255),\n"
            + "   targetcoinvalue string,\n" + "    targettokenid varchar(255),\n"
            // public address
            + "    beneficiaryaddress varchar(255),\n"
            // the pubkey that will receive the targettokens
            // on completion or returned tokens on cancels
            + "    beneficiarypubkey string,\n"
            // valid until this time
            + "    validToTime bigint,\n" + "    validFromTime bigint,\n"
            // true iff a order block of this order is confirmed
            + "    confirmed boolean ,\n"
            // true if used by a confirmed block (either
            // returned or used for another )
            + "    spent boolean ,\n" + "    spenderblockhash  string,\n"
            + "    CONSTRAINT contractevent_pk PRIMARY KEY (blockhash) " + " USING HASH \n" + ")  \n";

    public static final String CREATE_CONTRACT_ACCOUNT_TABLE = "CREATE TABLE contractaccount (\n"
            + "    contracttokenid varchar(255)  ,\n" + "    tokenid varchar(255)  ,\n"
            + "    coinvalue string, \n"
            // block hash of the last execution block
            + "    blockhash string ,\n"
            + "    CONSTRAINT contractaccount_pk PRIMARY KEY (contracttokenid, tokenid) " + ")  \n";

    public static final String CREATE_CONTRACT_EXECUTION_TABLE = "CREATE TABLE contractexecution (\n"
            + "   blockhash string ,\n" + "   contracttokenid varchar(255)  ,\n"
            + "   confirmed boolean ,\n" + "   spent boolean ,\n" + "   spenderblockhash string,\n"
            + "   prevblockhash string ,\n" + "   difficulty bigint ,\n"
            + "   chainlength bigint ,\n" + "   resultdata string ,\n" + "   PRIMARY KEY (blockhash) ) ";

    public static final String CREATE_CHAINBLOCKQUEUE_TABLE = "CREATE TABLE chainblockqueue (\n"
            + "    hash string ,\n" + "    block string ,\n" + "    chainlength bigint,\n "
            + "    orphan boolean,\n " + "    inserttime bigint ,\n"
            + "    CONSTRAINT chainblockqueue_pk PRIMARY KEY (hash)  \n" + ")  \n";
    public static final String CREATE_LOCKOBJECT_TABLE = "CREATE TABLE lockobject (\n"
            + "    lockobjectid varchar(255) ,\n" + "    locktime bigint ,\n"
            + "    CONSTRAINT lockobject_pk PRIMARY KEY (lockobjectid)  \n" + ")  \n";

    // Some indexes to speed up stuff
    public static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_toaddress_idx ON outputs (hash, outputindex, toaddress) USING HASH";
    public static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING HASH";
    public static final String CREATE_OUTPUTS_FROMADDRESS_INDEX = "CREATE INDEX outputs_fromaddress_idx ON outputs (fromaddress) USING HASH";

    public static final String CREATE_PREVBRANCH_HASH_INDEX = "CREATE INDEX blocks_prevbranchblockhash_idx ON blocks (prevbranchblockhash) USING HASH";
    public static final String CREATE_PREVTRUNK_HASH_INDEX = "CREATE INDEX blocks_prevblockhash_idx ON blocks (prevblockhash) USING HASH";

    public static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    public static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";

    public static final String CREATE_ORDERS_COLLECTINGHASH_TABLE_INDEX = "CREATE INDEX orders_collectinghash_idx ON orders (collectinghash) USING btree";
    public static final String CREATE_BLOCKS_MILESTONE_INDEX = "CREATE INDEX blocks_milestone_idx ON blocks (milestone)  USING btree ";
    public static final String CREATE_BLOCKS_HEIGHT_INDEX = "CREATE INDEX blocks_height_idx ON blocks (height)  USING btree ";
    public static final String CREATE_TXREARD_CHAINLENGTH_INDEX = "CREATE INDEX txreard_chainlength_idx ON txreward (chainlength)  USING btree ";
    public static final String CREATE_CONTRACT_EVENT_CONTRACTTOKENID_TABLE_INDEX = "CREATE INDEX contractevent_contracttokenid_idx ON contractevent (contracttokenid) USING btree";
    public static final String CREATE_CONTRACT_EXECUTION_CONTRACTTOKENID_TABLE_INDEX = "CREATE INDEX contractexecution_contracttokenid_idx ON contractexecution (contracttokenid) USING btree";
    public static final String CREATE_ORDERS_SPENT_TABLE_INDEX = "CREATE INDEX orders_spent_idx ON orders (confirmed, spent) ";
    public static final String CREATE_MATCHING_TOKEN_TABLE_INDEX = "CREATE INDEX matching_inserttime_idx ON matching (inserttime) ";

    public static final String CREATE_TOKEN_TOKENID_TABLE_INDEX = "CREATE INDEX tokens_tokenid_idx ON tokens (tokenid) ";

    public static List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.addAll(getCreateTablesSQL1());
        sqlStatements.addAll(getCreateTablesSQL2());
        return sqlStatements;
    }

    public static List<String> getCreateTablesSQL1() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_BLOCKS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_MATCHING_TABLE);
        sqlStatements.add(CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_MULTISIGN_TABLE);
        sqlStatements.add(CREATE_TX_REWARD_TABLE);
        sqlStatements.add(CREATE_USERDATA_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGN_TABLE);
        sqlStatements.add(CREATE_PAYMULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_ORDER_CANCEL_TABLE);
        sqlStatements.add(CREATE_BATCHBLOCK_TABLE);
        sqlStatements.add(CREATE_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.add(CREATE_ORDERS_TABLE);
        sqlStatements.add(CREATE_MYSERVERBLOCKS_TABLE);
        sqlStatements.add(CREATE_SETTINGS_TABLE); 
        sqlStatements.add(CREATE_MCMC_TABLE);
        sqlStatements.add(CREATE_MATCHING_LAST_TABLE);
        sqlStatements.add(CREATE_MATCHING_LAST_DAY_TABLE);
        return sqlStatements;
    }

    public static List<String> getCreateTablesSQL2() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_ACCESS_PERMISSION_TABLE);
        sqlStatements.add(CREATE_ACCESS_GRANT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_EVENT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_ACCOUNT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_EXECUTION_TABLE);
        sqlStatements.add(CREATE_CHAINBLOCKQUEUE_TABLE);
        sqlStatements.add(CREATE_LOCKOBJECT_TABLE);
        sqlStatements.add(CREATE_MATCHINGDAILY_TABLE);
        return sqlStatements;
    }

    public static List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.addAll(getCreateIndexesSQL1());
        sqlStatements.addAll(getCreateIndexesSQL2());
        return sqlStatements;
    }

    public static List<String> getCreateIndexesSQL1() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_OUTPUTS_ADDRESS_MULTI_INDEX);
        sqlStatements.add(CREATE_BLOCKS_HEIGHT_INDEX);
        sqlStatements.add(CREATE_OUTPUTS_TOADDRESS_INDEX);
        sqlStatements.add(CREATE_PREVBRANCH_HASH_INDEX);
        sqlStatements.add(CREATE_PREVTRUNK_HASH_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERS_COLLECTINGHASH_TABLE_INDEX);
        sqlStatements.add(CREATE_BLOCKS_MILESTONE_INDEX);
        sqlStatements.add(CREATE_TXREARD_CHAINLENGTH_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
        return sqlStatements;
    }

    public static List<String> getCreateIndexesSQL2() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_OUTPUTS_FROMADDRESS_INDEX);
        sqlStatements.add(CREATE_CONTRACT_EVENT_CONTRACTTOKENID_TABLE_INDEX);
        sqlStatements.add(CREATE_CONTRACT_EXECUTION_CONTRACTTOKENID_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERS_SPENT_TABLE_INDEX);
        sqlStatements.add(CREATE_MATCHING_TOKEN_TABLE_INDEX);
        sqlStatements.add(CREATE_TOKEN_TOKENID_TABLE_INDEX);
        return sqlStatements;
    }

    public static List<String> getCreateSchemeSQL() {
        // do nothing
        return Collections.emptyList();
    }

    public static String getDatabaseDriverClass() {
        return DATABASE_DRIVER_CLASS;
    }

    public static String getUpdateSettingsSLQ() {
        // return UPDATE_SETTINGS_SQL;
        return getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    }

    public String getUpdateBlockEvaluationMilestoneSQL() {
        return UPDATE_BLOCKEVALUATION_MILESTONE_SQL;
    }

    public static String getUpdateBlockEvaluationRatingSQL() {
        return UPDATE_BLOCKEVALUATION_RATING_SQL;
    }

    public static String getUpdateOutputsSpentSQL() {
        return UPDATE_OUTPUTS_SPENT_SQL;
    }

    public static String getUpdateOutputsConfirmedSQL() {
        return UPDATE_OUTPUTS_CONFIRMED_SQL;
    }

    public static String getUpdateOutputsSpendPendingSQL() {
        return UPDATE_OUTPUTS_SPENDPENDING_SQL;
    }

}
