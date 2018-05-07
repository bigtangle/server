package net.bigtangle.store;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;import org.omg.PortableServer.THREAD_POLICY_ID;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.UnitTestParams;

public class PhoenixBlockStoreTest {

    @Test
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
