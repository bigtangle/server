/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public class SparkStoreParameter {

 
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
            + "    address varchar(255),\n" + "    pubkeyhex varchar(255),\n" + "    posindex int(11),\n"
            + "    tokenholder int(11)  DEFAULT 0 \n" 
            //+ "    PRIMARY KEY (blockhash, tokenid, pubkeyhex) "
                    + "\n) ";

    public static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255)   ,\n" + "    tokenid varchar(255)   ,\n"
            + "    tokenindex bigint    ,\n" + "    address varchar(255),\n"
            + "    blockhash  string ,\n" + "    sign int(11)  \n" 
            //+ "    PRIMARY KEY (id) "
                    + "\n) ";

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

 
}
