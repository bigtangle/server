package net.bigtangle.server.config;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration

public class SparkConfig {

    @Value("${spark.appname:bigtangle}")
    private String appName;

    @Value("${spark.home:localhost}")
    private String sparkHome;

    @Value("${spark.masteruri:local}")
    private String masterUri;

    @Value("${spark.apppath:/data/deltalake}")
    private String appPath;

    @Bean
    public SparkSession sparkSession() {
        SparkConf conf = new SparkConf();

        conf.set("spark.master", "local[*]").set("spark.driver.bindAddress", "localhost")
                // .set("spark.sql.shuffle.partitions",
                // "3").set("spark.default.parallelism", "3")
                .set("spark.debug.maxToStringFields", "100")
                .set("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
                .set("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog");

        return SparkSession.builder().appName("test").config(conf).getOrCreate();
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSparkHome() {
        return sparkHome;
    }

    public void setSparkHome(String sparkHome) {
        this.sparkHome = sparkHome;
    }

    public String getMasterUri() {
        return masterUri;
    }

    public void setMasterUri(String masterUri) {
        this.masterUri = masterUri;
    }

    public String getAppPath() {
        return appPath;
    }

    public void setAppPath(String appPath) {
        this.appPath = appPath;
    }

}