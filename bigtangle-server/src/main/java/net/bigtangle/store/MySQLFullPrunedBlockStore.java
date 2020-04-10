/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
 

/**
 * <p>
 * A full pruned block store using the MySQL database engine.
 * </p>
 */

public class MySQLFullPrunedBlockStore extends DatabaseFullPrunedBlockStore {

    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:mysql://"; // "jdbc:log4jdbc:mysql://";

    // create table SQL
    private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings (\n" 
            + "    name varchar(32) NOT NULL,\n"
            + "    settingvalue blob,\n" 
            + "    CONSTRAINT setting_pk PRIMARY KEY (name)  \n" 
            + ")\n";


    private static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks (\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    height bigint NOT NULL,\n" 
            + "    block mediumblob NOT NULL,\n"
            + "    prevblockhash  binary(32) NOT NULL,\n"
            + "    prevbranchblockhash  binary(32) NOT NULL,\n" 
            + "    mineraddress binary(20) NOT NULL,\n"
            + "    blocktype bigint NOT NULL,\n" 

            //dynamic data
            //MCMC rating,depth,cumulativeweight
            + "    rating bigint NOT NULL,\n"
            + "    depth bigint NOT NULL,\n" 
            + "    cumulativeweight bigint NOT NULL,\n"
            //reward block chain length is here milestone
            + "    milestone bigint NOT NULL,\n"
            + "    milestonelastupdate bigint NOT NULL,\n"  
            + "    confirmed boolean NOT NULL,\n"
       
            //solid is result of validation of the block, 
            + "    solid bigint NOT NULL,\n"
            + "    inserttime bigint NOT NULL,\n"
            + "    CONSTRAINT blocks_pk PRIMARY KEY (hash) \n" 
            + ") ENGINE=InnoDB ";

    
    private static final String CREATE_UNSOLIDBLOCKS_TABLE = "CREATE TABLE unsolidblocks (\n"
            + "    hash binary(32) NOT NULL,\n" 
            + "    block mediumblob NOT NULL,\n" 
            + "    inserttime bigint NOT NULL,\n"
            + "    reason bigint NOT NULL,\n" 
            + "    missingdependency mediumblob NOT NULL,\n" 
            + "    height bigint ,\n"
            + "    directlymissing boolean NOT NULL,\n" 
            + "    CONSTRAINT unsolidblocks_pk PRIMARY KEY (hash) \n" + ") ENGINE=InnoDB";

    private static final String CREATE_OUTPUT_TABLE = "CREATE TABLE outputs (\n" 
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    hash binary(32) NOT NULL,\n"
            + "    outputindex bigint NOT NULL,\n" 
            + "    coinvalue mediumblob NOT NULL,\n"
            + "    scriptbytes mediumblob NOT NULL,\n" 
            + "    toaddress varchar(255),\n"
            + "    addresstargetable bigint,\n" 
            + "    coinbase boolean,\n" 
            + "    tokenid varchar(255),\n" 
            + "    fromaddress varchar(255),\n" 
            + "    memo MEDIUMTEXT,\n"
            + "    minimumsign bigint NOT NULL,\n"
            + "    time bigint,\n" 
            //begin the derived value of the output from block
            //this is for track the spent, spent = true means spenderblock is confirmed
            + "    spent boolean NOT NULL,\n" 
            + "    spenderblockhash  binary(32),\n"
            //confirmed = the block of this output is confirmed
            + "    confirmed boolean NOT NULL,\n"
            //this is indicator for wallet to minimize conflict, is set for create at spender block
            + "    spendpending boolean NOT NULL,\n" 
            + "    spendpendingtime bigint,\n" 
            + "    CONSTRAINT outputs_pk PRIMARY KEY (blockhash, hash, outputindex) \n" 
            + "   ) ENGINE=InnoDB \n";


    //This is table for output with possible multi sign address
    private static final String CREATE_OUTPUT_MULTI_TABLE = "CREATE TABLE outputsmulti (\n"
            + "    hash binary(32) NOT NULL,\n" 
            + "    outputindex bigint NOT NULL,\n"
            + "    toaddress varchar(255) NOT NULL,\n" 
            + "    CONSTRAINT outputs_pk PRIMARY KEY (hash, outputindex, toaddress) \n" 
            + ") ENGINE=InnoDB \n";

    
    private static final String CREATE_TX_REWARD_TABLE = "CREATE TABLE txreward (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n" 
            + "   prevblockhash binary(32) NOT NULL,\n" 
            + "   difficulty bigint NOT NULL,\n" 
            + "   chainlength bigint NOT NULL,\n" 
            + "   PRIMARY KEY (blockhash) ) ENGINE=InnoDB";

    private static final String CREATE_ORDERS_TABLE = "CREATE TABLE orders (\n"
                // initial issuing block  hash
            + "    blockhash binary(32) NOT NULL,\n" 
                // ZEROHASH if confirmed by order blocks,
                // issuing ordermatch blockhash if issued by ordermatch block
            + "    collectinghash binary(32) NOT NULL,\n" 
            + "    offercoinvalue bigint NOT NULL,\n" 
            + "    offertokenid varchar(255),\n" 
             + "   targetcoinvalue bigint,\n" 
            + "    targettokenid varchar(255),\n" 
                // buy or sell
            + "    side varchar(255),\n" 
                // public address
            + "    beneficiaryaddress varchar(255),\n" 
                // the pubkey that will receive the targettokens
                // on completion or returned   tokens on cancels 
            + "    beneficiarypubkey binary(33),\n"
               // order is valid untill this time
            + "    validToTime bigint,\n" 
                // a number used to track operations on the
                // order, e.g. increasing by one when refreshing
                // order is valid after this time
            + "    validFromTime bigint,\n" 
            // true iff a order block of this order is confirmed
            + "    confirmed boolean NOT NULL,\n" 
            // true if used by a confirmed  ordermatch block (either
            // returned or used for another orderoutput/output)
            + "    spent boolean NOT NULL,\n" 
            + "    spenderblockhash  binary(32),\n" 
            + "    CONSTRAINT orders_pk PRIMARY KEY (blockhash, collectinghash) "
            + " USING HASH \n" + ") ENGINE=InnoDB \n";

    private static final String CREATE_ORDER_CANCEL_TABLE = "CREATE TABLE ordercancel (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   orderblockhash binary(32) NOT NULL,\n"
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n" 
            + "   time bigint NOT NULL,\n"
            + "   PRIMARY KEY (blockhash) ) ENGINE=InnoDB";
    
    private static final String CREATE_MATCHING_TABLE = "CREATE TABLE matching (\n"
            + "    id bigint NOT NULL AUTO_INCREMENT,\n" 
            + "    txhash varchar(255) NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL,\n" 
            + "    price bigint NOT NULL,\n"
            + "    executedQuantity bigint NOT NULL,\n" 
            + "    inserttime bigint NOT NULL,\n"
            + "    PRIMARY KEY (id) \n" 
            + ") ENGINE=InnoDB\n";

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    blockhash binary(32) NOT NULL,\n" 
    		+ "    confirmed boolean NOT NULL,\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
    		+ "    tokenindex bigint NOT NULL   ,\n"
            + "    amount mediumblob ,\n" 
            + "    tokenname varchar(100) ,\n" 
            + "    description varchar(5000) ,\n"
            + "    domainname varchar(100) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n" 
            + "    tokentype int(11),\n"
            + "    tokenstop boolean,\n" 
            + "    prevblockhash binary(32),\n"
            + "    spent boolean NOT NULL,\n" 
            + "    spenderblockhash  binary(32),\n"
            + "    tokenkeyvalues  mediumblob,\n" 
            + "    revoked boolean   ,\n" 
            + "    language char(2)   ,\n"
            + "    classification varchar(255)   ,\n"
            + "    domainpredblockhash varchar(255) NOT NULL,\n"
            + "    decimals int ,\n" 
            + "    PRIMARY KEY (blockhash) \n) ENGINE=InnoDB";

    // Helpers
    private static final String CREATE_MULTISIGNADDRESS_TABLE = "CREATE TABLE multisignaddress (\n"
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    address varchar(255),\n" 
            + "    pubKeyHex varchar(255),\n" 
            + "    posIndex int(11),\n"
            + "    tokenHolder int(11) NOT NULL DEFAULT 0,\n"
            + "    PRIMARY KEY (blockhash, tokenid, pubKeyHex) \n) ENGINE=InnoDB";

    private static final String CREATE_MULTISIGNBY_TABLE = "CREATE TABLE multisignby (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenindex bigint NOT NULL   ,\n"
            + "    address varchar(255),\n" 
            + "    PRIMARY KEY (tokenid,tokenindex, address) \n) ENGINE=InnoDB";

    private static final String CREATE_MULTISIGN_TABLE = "CREATE TABLE multisign (\n"
            + "    id varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    tokenindex bigint NOT NULL   ,\n" 
            + "    address varchar(255),\n"
            + "    blockhash  mediumblob NOT NULL,\n" 
            + "    sign int(11) NOT NULL,\n" 
            + "    PRIMARY KEY (id) \n) ENGINE=InnoDB";

    private static final String CREATE_PAYMULTISIGN_TABLE = "CREATE TABLE paymultisign (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    tokenid varchar(255) NOT NULL  ,\n"
            + "    toaddress varchar(255) NOT NULL,\n" 
            + "    blockhash mediumblob NOT NULL,\n"
            + "    amount mediumblob ,\n" 
            + "    minsignnumber bigint(20) ,\n" 
            + "    outputHashHex varchar(255) ,\n"
            + "    outputindex bigint ,\n" + "    PRIMARY KEY (orderid) \n) ENGINE=InnoDB";

    private static final String CREATE_PAYMULTISIGNADDRESS_TABLE = "CREATE TABLE paymultisignaddress (\n"
            + "    orderid varchar(255) NOT NULL  ,\n" 
            + "    pubKey varchar(255),\n" 
            + "    sign int(11) NOT NULL,\n"
            + "    signIndex int(11) NOT NULL,\n" 
            + "    signInputData mediumblob,\n"
            + "    PRIMARY KEY (orderid, pubKey) \n) ENGINE=InnoDB";

    private static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n"
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    dataclassname varchar(255) NOT NULL,\n"
            + "    data mediumblob NOT NULL,\n" 
            + "    pubKey varchar(255),\n" 
            + "    blocktype bigint,\n"
            + "   CONSTRAINT userdata_pk PRIMARY KEY (dataclassname, pubKey) USING BTREE \n" 
            + ") ENGINE=InnoDB";

 
    private static final String CREATE_BATCHBLOCK_TABLE = "CREATE TABLE batchblock (\n"
            + "    hash binary(32) NOT NULL,\n" 
            + "    block mediumblob NOT NULL,\n"
            + "    inserttime datetime NOT NULL,\n" 
            + "   CONSTRAINT batchblock_pk PRIMARY KEY (hash)  \n"
            + ") ENGINE=InnoDB";

    private static final String CREATE_SUBTANGLE_PERMISSION_TABLE = "CREATE TABLE subtangle_permission (\n"
            + "    pubkey varchar(255) NOT NULL,\n" 
            + "    userdataPubkey varchar(255) NOT NULL,\n"
            + "    status varchar(255) NOT NULL,\n"
            + "   CONSTRAINT subtangle_permission_pk PRIMARY KEY (pubkey) USING BTREE \n" 
            + ") ENGINE=InnoDB";

    /*
     * indicate of a server created block
     */
    private static final String CREATE_MYSERVERBLOCKS_TABLE = "CREATE TABLE myserverblocks (\n"
            + "    prevhash binary(32) NOT NULL,\n" 
            + "    hash binary(32) NOT NULL,\n" 
            + "    inserttime bigint,\n"
            + "    CONSTRAINT myserverblocks_pk PRIMARY KEY (prevhash, hash) USING BTREE \n" 
            + ") ENGINE=InnoDB";
    
    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE exchange (\n"
            + "   orderid varchar(255) NOT NULL,\n" 
            + "   fromAddress varchar(255),\n"
            + "   fromTokenHex varchar(255),\n" 
            + "   fromAmount varchar(255),\n" 
            + "   toAddress varchar(255),\n"
            + "   toTokenHex varchar(255),\n" 
            + "   toAmount varchar(255),\n" 
            + "   data varbinary(5000) NOT NULL,\n"
            + "   toSign boolean,\n" 
            + "   fromSign integer,\n" 
            + "   toOrderId varchar(255),\n"
            + "   fromOrderId varchar(255),\n" 
            + "   market varchar(255),\n" 
            + "   memo varchar(255),\n" 
            + "   signInputData varbinary(5000),\n"
            + "   PRIMARY KEY (orderid) ) ENGINE=InnoDB";
    
    private static final String CREATE_EXCHANGE_MULTISIGN_TABLE = 
            "CREATE TABLE exchange_multisign (\n"
          + "   orderid varchar(255) ,\n" 
          + "   pubkey varchar(255),\n"
          + "   signInputData varbinary(5000),\n"
          + "   sign integer\n"
          + "    ) ENGINE=InnoDB";
    
    private static final String CREATE_ACCESS_PERMISSION_TABLE = 
            "CREATE TABLE access_permission (\n"
          + "   accessToken varchar(255) ,\n" 
          + "   pubKey varchar(255),\n"
          + "   refreshTime bigint,\n"
          + "   PRIMARY KEY (accessToken) ) ENGINE=InnoDB";
    
    private static final String CREATE_ACCESS_GRANT_TABLE = 
            "CREATE TABLE access_grant (\n"
          + "   address varchar(255),\n"
          + "   createTime bigint,\n"
          + "   PRIMARY KEY (address) ) ENGINE=InnoDB";

    

    private static final String CREATE_CONTRACT_EVENT_TABLE = "CREATE TABLE contractevent (\n"
                // initial issuing block  hash
            + "    blockhash binary(32) NOT NULL,\n" 
            + "    contracttokenid varchar(255),\n" 
             + "   targetcoinvalue mediumblob,\n" 
            + "    targettokenid varchar(255),\n" 
                // public address
            + "    beneficiaryaddress varchar(255),\n" 
                // the pubkey that will receive the targettokens
                // on completion or returned   tokens on cancels 
            + "    beneficiarypubkey binary(33),\n"
               //  valid until this time
            + "    validToTime bigint,\n" 
            + "    validFromTime bigint,\n" 
            // true iff a order block of this order is confirmed
            + "    confirmed boolean NOT NULL,\n" 
            // true if used by a confirmed  block (either
            // returned or used for another  )
            + "    spent boolean NOT NULL,\n" 
            + "    spenderblockhash  binary(32),\n" 
            + "    CONSTRAINT contractevent_pk PRIMARY KEY (blockhash) "
            + " USING HASH \n" + ") ENGINE=InnoDB \n";

    private static final String CREATE_CONTRACT_ACCOUNT_TABLE = "CREATE TABLE contractaccount (\n"
        + "    contracttokenid varchar(255)  NOT NULL,\n" 
        + "    tokenid varchar(255)  NOT NULL,\n" 
        + "    coinvalue mediumblob, \n" 
        //  block  hash of the last execution block
        + "    blockhash binary(32) NOT NULL,\n" 
        + "    CONSTRAINT contractaccount_pk PRIMARY KEY (contracttokenid, tokenid) "
        + ") ENGINE=InnoDB \n";

    private static final String CREATE_CONTRACT_EXECUTION_TABLE = "CREATE TABLE contractexecution (\n"
            + "   blockhash binary(32) NOT NULL,\n" 
            + "   contracttokenid varchar(255)  NOT NULL,\n" 
            + "   confirmed boolean NOT NULL,\n" 
            + "   spent boolean NOT NULL,\n"
            + "   spenderblockhash binary(32),\n" 
            + "   prevblockhash binary(32) NOT NULL,\n" 
            + "   difficulty bigint NOT NULL,\n" 
            + "   chainlength bigint NOT NULL,\n" 
            + "   resultdata blob NOT NULL,\n"
            + "   PRIMARY KEY (blockhash) ) ENGINE=InnoDB";
    // Cached block prototype for performance
    private static final String CREATE_BLOCKPROTOTYPE_TABLE = "CREATE TABLE blockprototype (\n"
            + "    prevblockhash  binary(32) NOT NULL,\n"
            + "    prevbranchblockhash  binary(32) NOT NULL,\n" 
            + "    inserttime bigint,\n" 
            + "    CONSTRAINT tips_pk PRIMARY KEY (prevblockhash,prevbranchblockhash ) USING BTREE \n" + ")\n";

    
    // Some indexes to speed up stuff
    private static final String CREATE_OUTPUTS_ADDRESS_MULTI_INDEX = "CREATE INDEX outputs_hash_index_toaddress_idx ON outputs (hash, outputindex, toaddress) USING HASH";
    private static final String CREATE_OUTPUTS_TOADDRESS_INDEX = "CREATE INDEX outputs_toaddress_idx ON outputs (toaddress) USING HASH";
    private static final String CREATE_OUTPUTS_FROMADDRESS_INDEX = "CREATE INDEX outputs_fromaddress_idx ON outputs (fromaddress) USING HASH";
    
    private static final String CREATE_PREVBRANCH_HASH_INDEX = "CREATE INDEX blocks_prevbranchblockhash_idx ON blocks (prevbranchblockhash) USING HASH";
    private static final String CREATE_PREVTRUNK_HASH_INDEX = "CREATE INDEX blocks_prevblockhash_idx ON blocks (prevblockhash) USING HASH";
    
    private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";

    private static final String CREATE_ORDERS_COLLECTINGHASH_TABLE_INDEX = "CREATE INDEX orders_collectinghash_idx ON orders (collectinghash) USING btree";
    private static final String CREATE_BLOCKS_MILESTONE_INDEX = "CREATE INDEX blocks_milestone_idx ON blocks (milestone)  USING btree ";
    private static final String CREATE_BLOCKS_HEIGHT_INDEX = "CREATE INDEX blocks_height_idx ON blocks (height)  USING btree ";
    private static final String CREATE_TXREARD_CHAINLENGTH_INDEX = "CREATE INDEX txreard_chainlength_idx ON txreward (chainlength)  USING btree ";
    private static final String CREATE_CONTRACT_EVENT_CONTRACTTOKENID_TABLE_INDEX = "CREATE INDEX contractevent_contracttokenid_idx ON contractevent (contracttokenid) USING btree";
    private static final String CREATE_CONTRACT_EXECUTION_CONTRACTTOKENID_TABLE_INDEX = "CREATE INDEX contractexecution_contracttokenid_idx ON contractexecution (contracttokenid) USING btree";

  
    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params,
                DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                fullStoreDepth, username, password, null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.addAll( getCreateTablesSQL1());
        sqlStatements.addAll( getCreateTablesSQL2());
        return sqlStatements;
    }

    protected List<String> getCreateTablesSQL1() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_BLOCKS_TABLE);
        sqlStatements.add(CREATE_UNSOLIDBLOCKS_TABLE);
        sqlStatements.add(CREATE_OUTPUT_TABLE);
        sqlStatements.add(CREATE_OUTPUT_MULTI_TABLE);
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_MATCHING_TABLE);
        sqlStatements.add(CREATE_MULTISIGNADDRESS_TABLE);
        sqlStatements.add(CREATE_MULTISIGNBY_TABLE);
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
        sqlStatements.add(CREATE_EXCHANGE_TABLE);
        sqlStatements.add(CREATE_EXCHANGE_MULTISIGN_TABLE);
 
        return sqlStatements;
    }

    protected List<String> getCreateTablesSQL2() {
        List<String> sqlStatements = new ArrayList<String>(); 
        sqlStatements.add(CREATE_ACCESS_PERMISSION_TABLE);
        sqlStatements.add(CREATE_ACCESS_GRANT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_EVENT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_ACCOUNT_TABLE);
        sqlStatements.add(CREATE_CONTRACT_EXECUTION_TABLE);
        sqlStatements.add(CREATE_BLOCKPROTOTYPE_TABLE);
        return sqlStatements;
    }

 
    public  void updateDatabse() throws BlockStoreException, SQLException  {
    
       byte[] settingValue = getSettingValue("version");
       String ver = "";
       if(settingValue!=null) ver= new String(settingValue);
       
       if("03".equals(ver)) {
           updateTables(getCreateTablesSQL2());
           updateTables(getCreateIndexesSQL2());
           dbupdateversion("05");
       }
      
    }
    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.addAll(getCreateIndexesSQL1());
        sqlStatements.addAll(getCreateIndexesSQL2());
        return sqlStatements;
    }
    
    protected List<String> getCreateIndexesSQL1() {
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
        sqlStatements.add( CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
        return sqlStatements;
    }
    protected List<String> getCreateIndexesSQL2() {
        List<String> sqlStatements = new ArrayList<String>(); 
        sqlStatements.add(CREATE_OUTPUTS_FROMADDRESS_INDEX); 
        sqlStatements.add(CREATE_CONTRACT_EVENT_CONTRACTTOKENID_TABLE_INDEX); 
        sqlStatements.add(CREATE_CONTRACT_EXECUTION_CONTRACTTOKENID_TABLE_INDEX); 
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

    @Override
    protected String getUpdateSettingsSLQ() {
        // return UPDATE_SETTINGS_SQL;
        return getUpdate() + " settings SET settingvalue = ? WHERE name = ?";
    }

    @Override
    protected String getUpdateBlockEvaluationCumulativeweightSQL() {
        return UPDATE_BLOCKEVALUATION_CUMULATIVEWEIGHT_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationDepthSQL() {
        return UPDATE_BLOCKEVALUATION_DEPTH_SQL;
    }

    @Override
    public String getUpdateBlockEvaluationMilestoneSQL() {
        return UPDATE_BLOCKEVALUATION_MILESTONE_SQL;
    }

    @Override
    protected String getUpdateBlockEvaluationRatingSQL() {
        return UPDATE_BLOCKEVALUATION_RATING_SQL;
    }

    @Override
    protected String getUpdateOutputsSpentSQL() {
        return UPDATE_OUTPUTS_SPENT_SQL;
    }

    @Override
    protected String getUpdateOutputsConfirmedSQL() {
        return UPDATE_OUTPUTS_CONFIRMED_SQL;
    }

    @Override
    protected String getUpdateOutputsSpendPendingSQL() {
        return UPDATE_OUTPUTS_SPENDPENDING_SQL;
    }

    @Override
    protected List<String> getDropIndexsSQL() {
        return new ArrayList<String>();
    }

  
}
