/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mysql.cj.jdbc.exceptions.CommunicationsException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.docker.DockerHelper;
import net.bigtangle.server.DispatcherController;
import net.bigtangle.store.MySQLFullBlockStore;

@Configuration
public class DBStoreConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

    @Value("${db.dbtype:mysql}")
    private String dbtype;

    @Value("${db.hostname:localhost}")
    private String hostname;

    @Value("${db.dbName:bitcoinj_test}")
    private String dbName = "bitcoinj_test";

    @Value("${db.username:root}")
    private String username = "root";

    @Value("${db.password:adminroot}")
    private String password;

    @Value("${db.port:3306}")
    private String port;

    @Autowired
    NetworkParameters networkParameters;
    @Autowired
    ServerConfiguration serverConfiguration;

    @Autowired
    private DBStoreConfiguration dbStoreConfiguration;

    @Bean
    public DataSource dataSource() throws BlockStoreException, IOException, InterruptedException, ExecutionException {
        createDatabase();

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(MySQLFullBlockStore.DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.setMaximumPoolSize(100);
        config.setLeakDetectionThreshold(300000);
        return new HikariDataSource(config);

    }

    private void createDatabase() throws IOException, InterruptedException, ExecutionException {
        if (serverConfiguration.getDockerCreateDBHost()) {
            DockerHelper  dockerHelper= new DockerHelper();
            try {
           
           //     dockerHelper.shellExecute(" service docker start  "   );
                String data = " /data/vm/" + serverConfiguration.getDockerDBHost() + "/var/lib/mysql"; 
                
                dockerHelper.shellExecute(" mkdir -p  " + data  );
                dockerHelper.shellExecute("   docker run -d  -t " + "-p 3306:3306  " + "-v " + data
                        + ":/var/lib/mysql " + " -e MYSQL_ROOT_PASSWORD=" + dbStoreConfiguration.getPassword()
                        + " -e MYSQL_DATABASE=" + dbStoreConfiguration.getDbName() + " --name="
                        + serverConfiguration.getDockerDBHost() + "     mysql:8.0.23 ");
                // check database available
                checkConnectionWait(120);
            } catch (Exception e) {
                if(e.getMessage().contains("Conflict")){
                    dockerHelper.shellExecute("docker start " + serverConfiguration.getDockerDBHost());
                    checkConnectionWait(120);
                }
                logger.warn("",e);
            }
        }
    }

    private boolean checkConnectionWait() throws InterruptedException, SQLException {
        boolean rating = false;
        while (!rating) {
            try {
                Properties connectionProps = new Properties();
                connectionProps.put("user", dbStoreConfiguration.getUsername());
                connectionProps.put("password", dbStoreConfiguration.getPassword());

                DriverManager.getConnection(MySQLFullBlockStore.DATABASE_CONNECTION_URL_PREFIX + hostname + "/" + dbName
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC", connectionProps);
                rating = true;
            } catch (CommunicationsException e) {
                Thread.sleep(1000);
            }
        }
        return rating;
    }

    // check with maximum timeout
    public boolean checkConnectionWait(Integer seconds) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Boolean> handler = executor.submit(() -> {
            return checkConnectionWait();
        });
        try {
            return handler.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            handler.cancel(true);
            return false;
        } finally {
            executor.shutdownNow();
        }

    }

    public String getDbtype() {
        return dbtype;
    }

    public void setDbtype(String dbtype) {
        this.dbtype = dbtype;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

}
