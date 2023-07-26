/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.checkpoint;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.types.DataTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Coin;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Tokensums;
import net.bigtangle.core.TokensumsMap;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.config.SparkConfig;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.store.FullBlockStore;

@Service
public class CheckpointService {
	private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

	@Autowired
	private ServerConfiguration serverConfiguration;

	@Autowired
	private DBStoreConfiguration dbStoreConfiguration;

	@Autowired
	private SparkSession sparkSession;

	@Autowired
	private SparkConfig sparkConfig;

	@Autowired
	protected StoreService storeService;

	String SELECT_OUTPUTS_SQL = "SELECT coinvalue, scriptbytes, coinbase, toaddress,"
			+ " addresstargetable, blockhash, tokenid, fromaddress, memo, spent, confirmed, "
			+ "spendpending , spendpendingtime, minimumsign, time, spenderblockhash "
			+ " FROM outputs WHERE spent=false ";

	public void readData() {
		// Class.forName("com.mysql.jdbc.Driver").newInstance();

		Dataset<Row> outputs = sparkSession.read().format("jdbc").option("url", "jdbc:mysql://localhost:3306/info")
				.option("user", "root").option("password", dbStoreConfiguration.getPassword())
				.option("dbtable", "outputs").load();
		outputs.printSchema();

		outputs.createOrReplaceTempView("outputs");

		Dataset<Row> table = sparkSession.sql(SELECT_OUTPUTS_SQL);
		log.debug(" unspent outputs ={} ", table.count());
		// Concatenate all columns into a single string for each row 
		// Calculate the hash for each column
		Dataset<Row> columnHashes = table
		                                 .select(columnHash.apply(functions.col("columns")).as("column_hash"));

 
	 
	 
	//	df.write().format("delta").mode("append").save(sparkConfig.getAppPath() + "/" + outputs);
	}
	UserDefinedFunction columnHash = functions.udf(new UDF1<Row, Long>() {
	    @Override
	    public Long call(Row row) {
	        long hash = 1;
	        for (int i = 0; i < row.size(); i++) {
	            hash = 31 * hash + (row.isNullAt(i) ? 0 : row.get(i).hashCode());
	        }
	        return hash;
	    }
	}, DataTypes.LongType);
	
	UserDefinedFunction concatenateColumns = functions.udf(new UDF1<Row, String>() {
		@Override
		public String call(Row row) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < row.size(); i++) {
				sb.append(row.get(i));
				if (i < row.size() - 1) {
					sb.append("|");
				}
			}
			return sb.toString();
		}
	}, DataTypes.StringType);

	// private static final Logger log =
	// LoggerFactory.getLogger(CheckpointService.class);

	private List<UTXO> getOutputs(String tokenid, FullBlockStore store)
			throws UTXOProviderException, BlockStoreException {
		// Must be sorted with the key of
		return store.getOpenAllOutputs(tokenid);
	}

	public Coin ordersum(String tokenid, List<OrderRecord> orders) throws JsonProcessingException, Exception {
		Coin sumUnspent = Coin.valueOf(0l, tokenid);
		for (OrderRecord orderRecord : orders) {
			if (orderRecord.getOfferTokenid().equals(tokenid)) {
				sumUnspent = sumUnspent.add(Coin.valueOf(orderRecord.getOfferValue(), tokenid));
			}
		}
		return sumUnspent;
	}

	private List<OrderRecord> orders(String tokenid, FullBlockStore store) throws BlockStoreException {
		return store.getAllOpenOrdersSorted(null, tokenid);

	}

	public Map<String, BigInteger> tokensumInitial(FullBlockStore store) throws BlockStoreException {

		return store.getTokenAmountMap();
	}

	public TokensumsMap checkToken(FullBlockStore store) throws BlockStoreException, UTXOProviderException {

		TokensumsMap tokensumset = new TokensumsMap();

		Map<String, BigInteger> tokensumsInitial = tokensumInitial(store);
		Set<String> tokenids = tokensumsInitial.keySet();
		for (String tokenid : tokenids) {
			Tokensums tokensums = new Tokensums();
			tokensums.setTokenid(tokenid);
			tokensums.setUtxos(getOutputs(tokenid, store));
			tokensums.setOrders(orders(tokenid, store));
			tokensums.setInitial(tokensumsInitial.get(tokenid));
			tokensums.calculate();
			tokensumset.getTokensumsMap().put(tokenid, tokensums);
		}
		return tokensumset;
	}
}
