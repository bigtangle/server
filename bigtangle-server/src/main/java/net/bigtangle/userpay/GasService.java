package net.bigtangle.userpay;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.tx.gas.ContractGasProvider;

import io.reactivex.Single;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Created by JB on 18/11/2020.
 *
 */
public class GasService implements ContractGasProvider {
	private final static String GAS_NOW_API = "https://api.etherscan.io/api?module=gastracker&action=gasoracle&apikey=ZXC63FIZEZ4A551WIABHA5V14RVJ4IIWC3";
	// "https://www.etherchain.org/api/gasPriceOracle";
	public final static long FETCH_GAS_PRICE_INTERVAL_SECONDS = 15;

	private final OkHttpClient httpClient;

	private int currentChainId;
	private Web3j web3j;
	private BigInteger currentGasPrice;

	public GasService(OkHttpClient httpClient) {

		this.httpClient = httpClient;

		web3j = null;
	}

	@Override
	public BigInteger getGasPrice(String contractFunc) {
		return currentGasPrice;
	}

	@Override
	public BigInteger getGasPrice() {
		return currentGasPrice;
	}

	@Override
	public BigInteger getGasLimit(String contractFunc) {
		return null;
	}

	@Override
	public BigInteger getGasLimit() {
		return new BigInteger(C.DEFAULT_UNKNOWN_FUNCTION_GAS_LIMIT);
	}

	private Single<Boolean> useNodeEstimate() {

		final int nodeId = currentChainId;
		return Single.fromCallable(() -> web3j.ethGasPrice().send()).map(price -> updateGasPrice(price, nodeId));

	}

	private Boolean updateGasPrice(EthGasPrice ethGasPrice, int chainId) {
		currentGasPrice = fixGasPrice(ethGasPrice.getGasPrice(), chainId);

		return true;
	}

	private BigInteger fixGasPrice(BigInteger gasPrice, int chainId) {
		if (gasPrice.compareTo(BigInteger.ZERO) > 0) {
			return gasPrice;
		} else {
			// gas price from node is zero
			switch (chainId) {
			default:
				return gasPrice;
			}
		}
	}

	public GasPriceSpread getRemoteGasPriceSpread() throws IOException {

		Request request = new Request.Builder().url(GAS_NOW_API).get().build();
		okhttp3.Response response = httpClient.newCall(request).execute();
		try {
			if (response.code() / 200 == 1) {
				String result = response.body().string();
				GasPriceSpread gps = new GasPriceSpread(result);

				return gps;
			} else {

				throw new RuntimeException(response.message());
			}
		} finally {
			response.close();
			response.body().close();
		}
	}

	private Single<EthEstimateGas> ethEstimateGas(String fromAddress, BigInteger nonce, BigInteger gasPrice,
			BigInteger gasLimit, String toAddress, BigInteger amount, String txData) {
		final Transaction transaction = new Transaction(fromAddress, nonce, gasPrice, gasLimit, toAddress, amount,
				txData);

		return Single.fromCallable(() -> web3j.ethEstimateGas(transaction).send());
	}
}
