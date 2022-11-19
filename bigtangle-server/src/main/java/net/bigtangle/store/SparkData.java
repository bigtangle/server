package net.bigtangle.store;

import org.apache.spark.sql.SparkSession;

import io.delta.tables.DeltaTable;

public class SparkData {

    public static DeltaTable blocks;
    public static DeltaTable settings;
    public static DeltaTable mcmc;

    public static DeltaTable outputs;
    public static DeltaTable outputsmulti;
    public static DeltaTable txreward;
    public static DeltaTable orders;
    public static DeltaTable ordercancel;
    public static DeltaTable matching;
    public static DeltaTable matchingdaily;
    public static DeltaTable matchinglast;
    public static DeltaTable matchinglastday;
    public static DeltaTable tokens;
    public static DeltaTable multisignaddress;
    public static DeltaTable multisign;
    public static DeltaTable paymultisign;
    public static DeltaTable paymultisignaddress;
    public static DeltaTable userdata;
    public static DeltaTable batchblock;
    public static DeltaTable myserverblocks;

    public static DeltaTable access_permission;
    public static DeltaTable chainblockqueue;
    public static DeltaTable lockobject;

    public static void loadDeltaTable(SparkSession sparkSession, String directory) {
        
        blocks= DeltaTable.forPath(sparkSession, directory+ "blocks");
        
    }
}
