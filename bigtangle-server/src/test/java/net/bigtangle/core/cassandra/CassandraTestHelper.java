package net.bigtangle.core.cassandra;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Session;

public class CassandraTestHelper {
    private static final Logger log = LoggerFactory.getLogger(CassandraTestHelper.class);

    public static void cleanEmbeddedCassandra() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }

    public static void startCassandraEmbedded()
            throws InterruptedException, IOException, TTransportException, ConfigurationException {
        // default timeout of 10000 ms was not enough.
        EmbeddedCassandraServerHelper.startEmbeddedCassandra(60000);
    }

    public static void initializeDB() throws IOException {
        List<String> cqlCmds = new ArrayList<>();

        List<String> filePaths = asList("./src/test/resources/create_keyspace_if_not_exists_vdv_fahrtwissen.cql"
        // "../as-vdv-infokanal-cassandra-migration/etc/migrations/V1_0__baseline_2018-11-28.cql"
        );

        for (String filePath : filePaths) {
            String[] cmds = String.join("\n", Files.readAllLines(Paths.get(filePath))).trim()
                    .replaceAll("@@replication_strategy@@", "{'class': 'SimpleStrategy', 'replication_factor': '1'}")
                    .split(";");
            cqlCmds.addAll(asList(cmds));
        }

         Session cassSess = EmbeddedCassandraServerHelper.getSession();
          for (String cqlCmd : cqlCmds) {
              log.debug("CQL Statement: " + cqlCmd);
              cassSess.execute(cqlCmd);
          }
    }

}
