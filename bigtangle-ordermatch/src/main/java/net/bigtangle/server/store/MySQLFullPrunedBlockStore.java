/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;

/**
 * <p>
 * A full pruned block store using the MySQL database engine. As an added bonus
 * an address index is calculated, so you can use
 * {@link #calculateBalanceForAddress(net.bigtangle.core.Address)} to quickly
 * look up the quantity of bitcoins controlled by that address.
 * </p>
 */

public class MySQLFullPrunedBlockStore extends DatabaseFullPrunedBlockStore {
    private static final String MYSQL_DUPLICATE_KEY_ERROR_CODE = "23000";
    private static final String DATABASE_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:log4jdbc:mysql://";

    private static final String CREATE_TOKENS_TABLE = "CREATE TABLE tokens (\n"
            + "    tokenid varchar(255) NOT NULL  ,\n" 
            + "    tokenname varchar(255) ,\n"
            + "    description varchar(255) ,\n" 
            + "    url varchar(255) ,\n" 
            + "    signnumber bigint NOT NULL   ,\n"
            + "    multiserial boolean,\n" 
            + "    asmarket boolean,\n" 
            + "   tokenstop boolean,\n"
            + "    PRIMARY KEY (tokenid) \n)";


    private static final String CREATE_ORDERPUBLISH_TABLE = "CREATE TABLE orderpublish (\n"
            + "   orderid varchar(255) NOT NULL,\n" 
            + "   address varchar(255),\n" 
            + "   tokenid varchar(255),\n"
            + "   type integer,\n"
            + "   validateto datetime,\n" 
            + "   validatefrom datetime,\n" 
            + "   price bigint,\n"
            + "   amount bigint,\n" 
            + "   state integer,\n" 
            + "   market varchar(255),\n"
            + "   PRIMARY KEY (orderid) )";

    private static final String CREATE_ORDERMATCH_TABLE = "CREATE TABLE ordermatch (\n"
            + "   matchid varchar(255) NOT NULL,\n" 
            + "   restingOrderId varchar(255),\n"
            + "   incomingOrderId varchar(255),\n" 
            + "   type integer,\n" 
            + "   price bigint,\n"
            + "   executedQuantity bigint,\n" 
            + "   remainingQuantity bigint,\n" 
            + "   PRIMARY KEY (matchid) )";

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
            + "   PRIMARY KEY (orderid) )";

    private static final String CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX = "CREATE INDEX exchange_fromAddress_idx ON exchange (fromAddress) USING btree";
    private static final String CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX = "CREATE INDEX exchange_toAddress_idx ON exchange (toAddress) USING btree";
    private static final String CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_restingOrderId_idx ON ordermatch (restingOrderId) USING btree";
    private static final String CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX = "CREATE INDEX ordermatch_incomingOrderId_idx ON ordermatch (incomingOrderId) USING btree";

    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName, fullStoreDepth, username, password,
                null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_TOKENS_TABLE);
        sqlStatements.add(CREATE_ORDERPUBLISH_TABLE);
        sqlStatements.add(CREATE_ORDERMATCH_TABLE);
        sqlStatements.add(CREATE_EXCHANGE_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_EXCHANGE_FROMADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_EXCHANGE_TOADDRESS_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERMATCH_INCOMINGORDERID_TABLE_INDEX);
        sqlStatements.add(CREATE_ORDERMATCH_RESTINGORDERID_TABLE_INDEX);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateSchemeSQL() {
        return Collections.emptyList();
    }

    @Override
    protected String getDatabaseDriverClass() {
        return DATABASE_DRIVER_CLASS;
    }
}
