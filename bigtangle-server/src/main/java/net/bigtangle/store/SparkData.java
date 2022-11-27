package net.bigtangle.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.sql.SparkSession;

import io.delta.tables.DeltaTable;

public class SparkData {

    public static DeltaTable blocks;
    public static DeltaTable settings;
    public static DeltaTable mcmc;

    public static DeltaTable outputs;
    public static DeltaTable outputsmulti;
    public static DeltaTable txreward;
    public static DeltaTable orders;
    public static DeltaTable ordercancel;
    public static DeltaTable matching;
    public static DeltaTable matchingdaily;
    public static DeltaTable matchinglast;
    public static DeltaTable matchinglastday;
    public static DeltaTable tokens;
    public static DeltaTable multisignaddress;
    public static DeltaTable multisign;
    public static DeltaTable paymultisign;
    public static DeltaTable paymultisignaddress;
    public static DeltaTable userdata;
    public static DeltaTable access_permission;
    public static DeltaTable chainblockqueue;
    public static DeltaTable lockobject;

    public static void loadDeltaTable(SparkSession sparkSession, String directory) {
        blocks = DeltaTable.forPath(sparkSession, directory + "/"+ "blocks");
        settings = DeltaTable.forPath(sparkSession, directory + "/"+ "settings");
        mcmc = DeltaTable.forPath(sparkSession, directory + "/"+ "mcmc");
        outputs = DeltaTable.forPath(sparkSession, directory + "/"+ "outputs");
        outputsmulti = DeltaTable.forPath(sparkSession, directory + "/"+ "outputsmulti");
        txreward = DeltaTable.forPath(sparkSession, directory + "/"+ "txreward");
        orders = DeltaTable.forPath(sparkSession, directory + "/"+ "orders");
        ordercancel = DeltaTable.forPath(sparkSession, directory + "/"+ "ordercancel");
        matching = DeltaTable.forPath(sparkSession, directory + "/"+ "matching");
        matchingdaily = DeltaTable.forPath(sparkSession, directory + "/"+ "matchingdaily");

        matchinglast = DeltaTable.forPath(sparkSession, directory + "/"+ "matchinglast");
        matchinglastday = DeltaTable.forPath(sparkSession, directory + "/"+ "matchinglastday");
        tokens = DeltaTable.forPath(sparkSession, directory + "/"+ "tokens");
        multisignaddress = DeltaTable.forPath(sparkSession, directory + "/"+ "multisignaddress");

        multisign = DeltaTable.forPath(sparkSession, directory + "/"+ "multisign");
        paymultisign = DeltaTable.forPath(sparkSession, directory + "/"+ "paymultisign");
        paymultisignaddress = DeltaTable.forPath(sparkSession, directory + "/"+ "paymultisignaddress");
        userdata = DeltaTable.forPath(sparkSession, directory + "/"+ "userdata");
      //  access_permission = DeltaTable.forPath(sparkSession, directory + "/"+ "access_permission");
        chainblockqueue = DeltaTable.forPath(sparkSession, directory + "/"+ "chainblockqueue");
        lockobject = DeltaTable.forPath(sparkSession, directory + "/"+ "lockobject");
    }
    public static Map<String, String> getCreateTablesSQL1() {
        Map<String, String> sqlStatements = new HashMap<String,String>();
        sqlStatements.put("blocks",CREATE_BLOCKS_TABLE);
        sqlStatements.put("outputs",CREATE_OUTPUT_TABLE);
        sqlStatements.put( "outputsmulti", CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.put("tokens",CREATE_TOKENS_TABLE);
        sqlStatements.put("matching",CREATE_MATCHING_TABLE);
        sqlStatements.put("multisignaddress",CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.put( "multisign",CREATE_MULTISIGN_TABLE);
        sqlStatements.put("txreward",CREATE_TX_REWARD_TABLE);
        sqlStatements.put("userdata",CREATE_USERDATA_TABLE);
        sqlStatements.put("paymultisign",CREATE_PAYMULTISIGN_TABLE);
        sqlStatements.put("paymultisignaddress",CREATE_PAYMULTISIGNADDRESS_TABLE);
        sqlStatements.put("ordercancel",CREATE_ORDER_CANCEL_TABLE);
      //  sqlStatements.put(CREATE_SUBTANGLE_PERMISSION_TABLE);
        sqlStatements.put("orders",CREATE_ORDERS_TABLE);
        sqlStatements.put("settings",CREATE_SETTINGS_TABLE);
        sqlStatements.put("mcmc",CREATE_MCMC_TABLE);
        sqlStatements.put("matchinglast",CREATE_MATCHING_LAST_TABLE);
        sqlStatements.put("matchinglastday",CREATE_MATCHING_LAST_DAY_TABLE);
        sqlStatements.put("matchingdaily",CREATE_MATCHINGDAILY_TABLE);
      //  sqlStatements.put(CREATE_ACCESS_PERMISSION_TABLE);
      //  sqlStatements.put(CREATE_ACCESS_GRANT_TABLE);
      //  sqlStatements.put(CREATE_CONTRACT_EVENT_TABLE);
      //  sqlStatements.put(CREATE_CONTRACT_ACCOUNT_TABLE);
      //  sqlStatements.put(CREATE_CONTRACT_EXECUTION_TABLE);
        sqlStatements.put("chainblockqueue",CREATE_CHAINBLOCKQUEUE_TABLE);
        sqlStatements.put("lockobject",CREATE_LOCKOBJECT_TABLE);
 
        return sqlStatements;
    }
    
    public static void createDeltaTable(SparkSession spark, String directory) {
        for(String key: getCreateTablesSQL1().keySet()) {
        String d = " USING DELTA " + "   LOCATION '" + directory+"/"+key+ "'";
        spark.sql(getCreateTablesSQL1().get(key) + d);
        }
    }

    // create table SQL
    public static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" + "    name string ,\n"
            + "    settingvalue string"
            // + ",\n" + " CONSTRAINT setting_pk PRIMARY KEY (name) \n"
            + ")\n";

    public static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n" + "    hash string ,\n"
            + "    height bigint ,\n" + "    block string ,\n" + "    prevblockhash  string  ,\n"
            + "    prevbranchblockhash  string ,\n" + "    mineraddress string  ,\n" + "    blocktype bigint ,\n"
            // reward block chain length is here milestone
            + "    milestone bigint ,\n" + "    milestonelastupdate bigint ,\n" + "    confirmed boolean ,\n"

            // solid is result of validation of the block,
            + "    solid bigint ,\n" + "    inserttime bigint \n"
            // + " CONSTRAINT blocks_pk PRIMARY KEY (hash) \n"
            + ")  ";

    public static final String CREATE_MCMC_TABLE = "CREATE TABLE mcmc (\n" + "    hash string ,\n"
            + "    rating bigint ,\n" + "    depth bigint ,\n" + "    cumulativeweight bigint "
            // + ",\n" + " CONSTRAINT mcmc_pk PRIMARY KEY (hash) \n"
            + ")  ";

    public static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" + "    blockhash string ,\n"
            + "    hash string ,\n" + "    outputindex bigint ,\n" + "    coinvalue string ,\n"
            + "    scriptbytes string ,\n" + "    toaddress string,\n" + "    addresstargetable bigint,\n"
            + "    coinbase boolean,\n" + "    tokenid string,\n" + "    fromaddress string,\n" + "    memo string,\n"
            + "    minimumsign bigint ,\n" + "    time bigint,\n"
            // begin the derived value of the output from block
            // this is for track the spent, spent = true means spenderblock is
            // confirmed
            + "    spent boolean ,\n" + "    spenderblockhash  string,\n"
            // confirmed = the block of this output is confirmed
            + "    confirmed boolean ,\n"
            // this is indicator for wallet to minimize conflict, is set for
            // create at spender block
            + "    spendpending boolean ,\n" + "    spendpendingtime bigint\n"
            // + " CONSTRAINT outputs_pk PRIMARY KEY (blockhash, hash,
            // outputindex) \n"
            + "   )  \n";

    // This is table for output with possible multi sign address
    public static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n" + "    hash string ,\n"
            + "    outputindex bigint ,\n" + "    toaddress string "
            // + ",\n" " CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex,
            // toaddress) \n"
            + ")  \n";

    public static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n" + "   blockhash string ,\n"
            + "   confirmed boolean ,\n" + "   spent boolean ,\n" + "   spenderblockhash string,\n"
            + "   prevblockhash string ,\n" + "   difficulty bigint ,\n" + "   chainlength bigint,  time bigint "
            // + ",\n" + " PRIMARY KEY (blockhash) "
            + ") ";

    public static final String CREATE_ORDERS_TABLE = "CREATE TABLE orders (\n"
            // initial issuing block hash
            + "    blockhash string ,\n"
            // ZEROHASH if confirmed by order blocks,
            // issuing ordermatch blockhash if issued by ordermatch block
            + "    issuingmatcherblockhash string ,\n" + "    offercoinvalue bigint ,\n" + "    offertokenid string,\n"
            + "   targetcoinvalue bigint,\n" + "    targettokenid string,\n"
            // buy or sell
            + "    side string,\n"
            // public address
            + "    beneficiaryaddress string,\n"
            // the pubkey that will receive the targettokens
            // on completion or returned tokens on cancels
            + "    beneficiarypubkey string,\n"
            // order is valid untill this time
            + "    validtotime bigint,\n"
            // a number used to track operations on the
            // order, e.g. increasing by one when refreshing
            // order is valid after this time
            + "    validfromtime bigint,\n"
            // order base token
            + "    orderbasetoken string,\n" + "    tokendecimals int ,\n" + "   price bigint,\n"
            // true iff a order block of this order is confirmed
            + "    confirmed boolean ,\n"
            // true if used by a confirmed ordermatch block (either
            // returned or used for another orderoutput/output)
            + "    spent boolean ,\n" + "    spenderblockhash  string \n"
            // + " CONSTRAINT orders_pk PRIMARY KEY (blockhash,
            // issuingmatcherblockhash) " + " USING HASH \n"
            + ")  \n";

    public static final String CREATE_ORDER_CANCEL_TABLE = "CREATE TABLE ordercancel (\n" + "   blockhash string ,\n"
            + "   orderblockhash string ,\n" + "   confirmed boolean ,\n" + "   spent boolean ,\n"
            + "   spenderblockhash string,\n" + "   time bigint "
            // + ",\n" + " PRIMARY KEY (blockhash) "
            + ") ";

    public static final String CREATE_MATCHING_TABLE = "CREATE TABLE matching (\n" + "    id bigint ,\n"
            + "    txhash string ,\n" + "    tokenid string ,\n" + "    basetokenid string ,\n" + "    price bigint ,\n"
            + "    executedQuantity bigint ,\n" + "    inserttime bigint "
            // + ",\n" + " PRIMARY KEY (id) \n"
            + ") \n";

    public static final String CREATE_MATCHINGDAILY_TABLE = "CREATE TABLE matchingdaily (\n"
            + "    id bigint ,\n" + "    matchday string ,\n" + "    tokenid string ,\n"
            + "    basetokenid string ,\n" + "    avgprice bigint ,\n" + "    totalQuantity bigint ,\n"
            + "    highprice bigint ,\n" + "    lowprice bigint ,\n" + "    open bigint ,\n" + "    close bigint ,\n"
            + "    matchinterval string ,\n" + "    inserttime bigint "
            // + ",\n" + " PRIMARY KEY (id) \n"
            + ") \n";

    public static final String CREATE_MATCHING_LAST_TABLE = "CREATE TABLE matchinglast (\n" + "    txhash string ,\n"
            + "    tokenid string ,\n" + "    basetokenid string ,\n" + "    price bigint ,\n"
            + "    executedQuantity bigint ,\n" + "    inserttime bigint  \n"
            // + " PRIMARY KEY ( tokenid,basetokenid) \n"
            + ") \n";
    public static final String CREATE_MATCHING_LAST_DAY_TABLE = "CREATE TABLE matchinglastday (\n"
            + "    txhash string ,\n" + "    tokenid string ,\n" + "    basetokenid string ,\n" + "    price bigint ,\n"
            + "    executedQuantity bigint ,\n" + "    inserttime bigint  \n"
            // + " PRIMARY KEY ( tokenid,basetokenid) \n"
            + ") \n";

    public static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n" + "    blockhash string ,\n"
            + "    confirmed boolean ,\n" + "    tokenid string   ,\n" + "    tokenindex bigint    ,\n"
            + "    amount string ,\n" + "    tokenname string ,\n" + "    description string ,\n"
            + "    domainname string ,\n" + "    signnumber bigint    ,\n" + "    tokentype int,\n"
            + "    tokenstop boolean,\n" + "    prevblockhash string,\n" + "    spent boolean ,\n"
            + "    spenderblockhash  string,\n" + "    tokenkeyvalues  string,\n" + "    revoked boolean   ,\n"
            + "    language string   ,\n" + "    classification string   ,\n"  
            + "    decimals int  \n"
            // + " PRIMARY KEY (blockhash)"
            + " \n) ";

    // Helpers
    public static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash string ,\n" + "    tokenid string   ,\n" + "    address string,\n"
            + "    pubkeyhex string,\n" + "    posindex int, \n" + "    tokenholder int   \n"
            // + " PRIMARY KEY (blockhash, tokenid, pubkeyhex) "
            + "\n) ";

    public static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n" + "    id string   ,\n"
            + "    tokenid string   ,\n" + "    tokenindex bigint    ,\n" + "    address string,\n"
            + "    blockhash  string ,\n" + "    sign int  \n"
            // + " PRIMARY KEY (id) "
            + "\n) ";

    public static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n" + "    orderid string   ,\n"
            + "    tokenid string   ,\n" + "    toaddress string ,\n" + "    blockhash string ,\n"
            + "    amount string ,\n" + "    minsignnumber bigint ,\n" + "    outputhashhex string ,\n"
            + "    outputindex bigint \n"
            // + " PRIMARY KEY (orderid) "
            + "\n) ";

    public static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid string   ,\n" + "    pubkey string,\n" + "    sign int ,\n" + "    signIndex int ,\n"
            + "    signInputData string \n"
            // + " PRIMARY KEY (orderid, pubKey)"
            + "\n) ";

    public static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n" + "    blockhash string ,\n"
            + "    dataclassname string ,\n" + "    data string ,\n" + "    pubkey string,\n"
            + "    blocktype bigint \n"
            // + " CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubkey)
            // USING BTREE"
            + " \n" + ") ";

    public static final String CREATE_SUBTANGLE_PERMISSION_TABLE = "CREATE TABLE subtangle_permission (\n"
            + "    pubkey string ,\n" + "    userdataPubkey string ,\n" + "    status string \n"
            // + " CONSTRAINT subtangle_permission_pk PRIMARY KEY (pubkey) USING
            // BTREE \n"
            + ") ";

    public static final String CREATE_ACCESS_PERMISSION_TABLE = "CREATE TABLE access_permission (\n"
            + "   accessToken string ,\n" + "   pubKey string,\n" + "   refreshTime bigint \n"
            // + " PRIMARY KEY (accessToken) "
            + ") ";

    public static final String CREATE_ACCESS_GRANT_TABLE = "CREATE TABLE access_grant (\n" + "   address string,\n"
            + "   createTime bigint n"
            // + " PRIMARY KEY (address) "
            + ") ";

    public static final String CREATE_CONTRACT_EVENT_TABLE = "CREATE TABLE contractevent (\n"
            // initial issuing block hash
            + "    blockhash string ,\n" + "    contracttokenid string,\n" + "   targetcoinvalue string,\n"
            + "    targettokenid string,\n"
            // public address
            + "    beneficiaryaddress string,\n"
            // the pubkey that will receive the targettokens
            // on completion or returned tokens on cancels
            + "    beneficiarypubkey string,\n"
            // valid until this time
            + "    validtotime bigint,\n" + "    validfromtime bigint,\n"
            // true iff a order block of this order is confirmed
            + "    confirmed boolean ,\n"
            // true if used by a confirmed block (either
            // returned or used for another )
            + "    spent boolean ,\n" + "    spenderblockhash  string \n"
            // + " CONSTRAINT contractevent_pk PRIMARY KEY (blockhash) " + "
            // USING HASH \n"
            + ")  \n";

    public static final String CREATE_CONTRACT_ACCOUNT_TABLE = "CREATE TABLE contractaccount (\n"
            + "    contracttokenid string  ,\n" + "    tokenid string  ,\n" + "    coinvalue string, \n"
            // block hash of the last execution block
            + "    blockhash string  \n"
            // + " CONSTRAINT contractaccount_pk PRIMARY KEY (contracttokenid,
            // tokenid) "
            + ")  \n";

    public static final String CREATE_CONTRACT_EXECUTION_TABLE = "CREATE TABLE contractexecution (\n"
            + "   blockhash string ,\n" + "   contracttokenid string  ,\n" + "   confirmed boolean ,\n"
            + "   spent boolean ,\n" + "   spenderblockhash string,\n" + "   prevblockhash string ,\n"
            + "   difficulty bigint ,\n" + "   chainlength bigint ,\n" + "   resultdata string  \n"
            // + " PRIMARY KEY (blockhash) "
            + ") ";

    public static final String CREATE_CHAINBLOCKQUEUE_TABLE = "CREATE TABLE chainblockqueue (\n" + "    hash string ,\n"
            + "    block string ,\n" + "    chainlength bigint,\n " + "    orphan boolean,\n "
            + "    inserttime bigint \n" 
          //  + "    CONSTRAINT chainblockqueue_pk PRIMARY KEY (hash)  \n" 
            + ")  \n";
    public static final String CREATE_LOCKOBJECT_TABLE = "CREATE TABLE lockobject (\n" + "    lockobjectid string ,\n"
            + "    locktime bigint  \n"
            // + " CONSTRAINT lockobject_pk PRIMARY KEY (lockobjectid) \n"
            + ")  \n";

  
 
   
  
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

}
