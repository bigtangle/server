/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import net.bigtangle.params.UnitTestParams;
import net.bigtangle.store.DatabaseFullPrunedBlockStore;
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
       
           ((PhoenixBlockStore) store) .create();
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
    

  
    public void showCreateTable() {
        for (String sql : this.getDropTablesSQL()) {
            System.out.println(sql + ";");
        }
        for (String sql : this.getCreateTablesSQL()) {
            System.out.println(sql);
        }
        for (String sql : this.getCreateIndexesSQL()) {
            System.out.println(sql);
        }
    }
    protected List<String> getDropTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_SETTINGS_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_HEADERS_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_OPEN_OUTPUT_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_TIPS_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_BLOCKEVALUATION_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_TOKENS_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_ORDERPUBLISH_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_ORDERMATCH_TABLE);
        sqlStatements.add(DatabaseFullPrunedBlockStore.DROP_EXCHANGE_TABLE);
        return sqlStatements;
    }

    protected List<String> getCreateTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(PhoenixBlockStore.CREATE_SETTINGS_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_HEADERS_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_OUTPUT_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_TIPS_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_BLOCKEVALUATION_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_TOKENS_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_ORDERPUBLISH_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_ORDERMATCH_TABLE);
        sqlStatements.add(PhoenixBlockStore.CREATE_EXCHANGE_TABLE);
        return sqlStatements;
    }
    
    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override public int getInterval() {
            return 10000;
        }
    };
    
    protected List<String> getCreateIndexesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add("CREATE LOCAL INDEX headers_prevblockhash_idx ON headers (prevblockhash)");
        sqlStatements.add("CREATE LOCAL INDEX headers_prevbranchblockhash_idx ON headers (prevbranchblockhash)");
        sqlStatements.add("CREATE LOCAL INDEX blockevaluation_solid ON blockevaluation (solid)");
        return sqlStatements;
    }
}