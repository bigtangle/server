/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.airdrop.store;

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
    private static final String DATABASE_CONNECTION_URL_PREFIX = "jdbc:mysql://";

  
    private static final String CREATE_WECHAT_INVITE_TABLE = "CREATE TABLE wechatinvite (\n"
            + "   id varchar(255) NOT NULL,\n" 
            + "   wechatId varchar(255),\n" 
            + "   wechatinviterId varchar(255),\n"
            + "   createtime datetime,\n" 
            + "   status int(11) not null,\n"
            + "   PRIMARY KEY (id) )";
    
    private static final String CREATE_WECHAT_REWARD_TABLE = "CREATE TABLE wechatreward (\n"
            + "   id varchar(255) NOT NULL,\n" 
            + "   pubKeyHex varchar(255),\n" 
            + "   wechatInviterId varchar(255),\n"
            + "   createtime datetime,\n" 
            + "   PRIMARY KEY (id) )";

    public MySQLFullPrunedBlockStore(NetworkParameters params, int fullStoreDepth, String hostname, String dbName,
            String username, String password) throws BlockStoreException {
        super(params, DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName, fullStoreDepth, username, password, null);
    }

    @Override
    protected String getDuplicateKeyErrorCode() {
        return MYSQL_DUPLICATE_KEY_ERROR_CODE;
    }

    @Override
    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(CREATE_WECHAT_INVITE_TABLE);
        sqlStatements.add(CREATE_WECHAT_REWARD_TABLE);
        return sqlStatements;
    }

    @Override
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
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
