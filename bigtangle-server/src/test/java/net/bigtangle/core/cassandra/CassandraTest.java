package net.bigtangle.core.cassandra;

import java.io.IOException;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
// @ContextConfiguration(classes = { CassandraConfiguration.class })
@TestPropertySource(locations = "classpath:embeded_cassandra_test.properties")

public class CassandraTest {

    @BeforeClass
    public static void startCassandraEmbedded()
            throws InterruptedException, TTransportException, ConfigurationException, IOException {
        CassandraTestHelper.startCassandraEmbedded();
        CassandraTestHelper.initializeDB();
    }

    @AfterClass
    public static void cleanEmbeddedCassandra() {
        CassandraTestHelper.cleanEmbeddedCassandra();
    }

    @Test
    public void test() throws IOException {

    }

}
