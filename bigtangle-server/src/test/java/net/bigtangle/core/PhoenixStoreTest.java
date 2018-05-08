/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.PhoenixBlockStore;

/**
 * A MySQL implementation of the {@link AbstractFullPrunedBlockChainTest}
 */

 //("enable the mysql driver dependency in the maven POM")
public class PhoenixStoreTest extends AbstractFullPrunedBlockChainTest {

    @After
    public void tearDown() throws Exception {
    //      ((MySQLFullPrunedBlockStore)store).deleteStore();
    }

    // Replace these with your mysql location/credentials and remove @Ignore to test
    private static final String DB_HOSTNAME = "cn.phoenix.bigtangle.net:8765";
    private static final String DB_NAME = "info";
    private static final String DB_USERNAME = null;
    private static final String DB_PASSWORD = null;

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            store = new PhoenixBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        //resetStore(store);
        return store;
    }
  
    @Test
    public void test() throws SQLException, BlockStoreException {
        store = createStore(PARAMS, 10);
        ((PhoenixBlockStore)store).getConnection().get().setAutoCommit(true);
        Statement s = ((PhoenixBlockStore)store).getConnection().get().createStatement();
//        s.executeUpdate("DROP TABLE test");
//        s.executeUpdate("CREATE TABLE TEST (IDCardNum INTEGER not null, Name varchar(20), Age INTEGER not null, CONSTRAINT test_pk PRIMARY KEY (IDCardNum,Age))");
        s.executeUpdate("UPSERT INTO TEST (IDCardNum, Name, Age) VALUES(1,'THIS IS TEST',3333)");
//        s.executeUpdate("UPSERT INTO TEST (Name, IDCardNum) VALUES('THIS IS 112222222222', 1)");
        s.close();
//        ((PhoenixBlockStore)store).getConnection().get().commit();
    }

    @Override
    public void resetStore(FullPrunedBlockStore store) throws BlockStoreException {
        // ((PhoenixBlockStore)store).resetStore();
    }
}