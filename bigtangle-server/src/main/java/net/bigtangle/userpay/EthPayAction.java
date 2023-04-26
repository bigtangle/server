package net.bigtangle.userpay;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;
import org.web3j.utils.Numeric;

import com.google.common.math.LongMath;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.utils.MonetaryFormat;
import okhttp3.OkHttpClient;

public class EthPayAction implements Serializable {
	private final Log LOG = LogFactory.getLog(getClass().getName());
	/**
	 * 
	 */
	private static final long serialVersionUID = -541593686958202847L;

	private String tokenid = CryptoTokenlist.ETH;
	private BigDecimal quantity;
	private String address;
	private String gasLimit = "21000";
	private String gasPriceGwei = "30";
	protected String fee;
	private Boolean includefee = true;
	private String fromaddress;

	private String transactionHash;
	protected Web3j web3j;

	public String pay(List<ECKey> w) throws Exception {

		return pay(address, tokenid, quantity.toPlainString(), gasLimit, gasPriceInWei().toString(), w);

	}

	public BigInteger gasPriceInWei() {
		return new BigInteger(gasPriceGwei).multiply(BigInteger.valueOf(LongMath.pow(10, 9)));
	}

	public String gweiLimit(BigInteger price) {

		long p = price.longValue() / (LongMath.pow(10, 9));
		if (p > 200) {
			LOG.info("ETH Gasprice is set to 200 with large gas price " + p);
			return "200";
		} else {
			return p + "";
		}
	}

	public List<net.bigtangle.userpay.EthBalance> balanceList(List<ECKey> w) throws Exception {
		// for each positive token address
		List<net.bigtangle.userpay.EthBalance> balances = new ArrayList<EthBalance>();
		for (ECKey ecKey : w) {
			String ethaddress = Credentials.create(ecKey.getPrivateKeyAsHex()).getAddress();
			for (String token : CryptoTokenlist.ethtokens) {
				EthBalance ethBalance = new EthBalance();
				ethBalance.setEthaddress(ethaddress);
				ethBalance.setToken(token);
				if (token.equals(CryptoTokenlist.ETH)) {
					ethBalance.setBalance(getBalance(ethaddress));
				} else {
					ethBalance.setBalance(getBalanceContract(token, ethaddress));
				}
				if (ethBalance.getBalance() != null && ethBalance.getBalance().signum() > 0)
					balances.add(ethBalance);
			}
		}
		return balances;
	}

	private BigDecimal getBalanceContract(String token, String ethaddress) {
		TransactionManager m = new ReadonlyTransactionManager(web3j, ethaddress);
		ERC20 erc20 = ERC20.load(CryptoTokenlist.getContract(token, !prodSystem()), web3j, m, new DefaultGasProvider());
		BigInteger b;
		try {
			b = erc20.balanceOf(ethaddress).send();
			BigInteger decimals = erc20.decimals().send();
			// Value as base unit
			return new BigDecimal(b).divide(new BigDecimal("10").pow(decimals.intValue()));

		} catch (Exception e) {
			return null;
		}

	}

	public BigDecimal getBalance(String address) throws IOException {
		return Convert.fromWei(
				web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance().toString(),
				Unit.ETHER);
	}

	public String pay(String toAddress, String token, String quantity, String gasLimit, String gasPrice,
			List<ECKey> payWallet) throws Exception {

		balanceList(payWallet);
		// get the address to pay
		EthBalance pay = getBalancePay(token, quantity, gasLimit, gasPrice, payWallet);
		if (pay == null) {
			LOG.debug(" getBalancePay no token to pay from wallet ");
			throw new InsufficientMoneyException(getLocalMessage("insufficientMoney"));
		}
		// no
		for (ECKey ecKey : payWallet) {
			if (Credentials.create(ecKey.getPrivateKeyAsHex()).getAddress().equals(pay.getEthaddress())) {
				fromaddress = pay.getEthaddress();
				if (CryptoTokenlist.ETH.equals(token)) {
					if (Convert.toWei(pay.getBalance(), Unit.ETHER)
							.compareTo(checkAmountToPay(token, quantity, gasLimit, gasPrice)) >= 0) {
						return payDo(ecKey.getPrivateKeyAsHex(), toAddress, quantity, gasLimit, gasPrice);
					} else {
						throw new InsufficientMoneyException(getLocalMessage("insufficientMoney") + " " + fromaddress
								+ " " + pay.getBalance() + " < " + quantity + " + " + getFee());
					}
				}

				if (CryptoTokenlist.USDT.equals(token)) {
					// check account pay gas

					return payDoDirect(token, ecKey.getPrivateKeyAsHex(), toAddress, quantity, gasLimit, gasPrice);
				}

			}
		}
		throw new InsufficientMoneyException(getLocalMessage("insufficientMoney"));
	}

	/*
	 * automatic calc gas for eth call
	 */
	public String payWithAutoGas(String toAddress, String token, String quantity, List<ECKey> payWallet)
			throws Exception {
		calcGasprice(token);
		return pay(toAddress, token, quantity, gasLimit, gasPriceInWei().toString(), payWallet);

	}

	private EthBalance getBalancePay(String token, String quantity, String gasLimit, String gasPrice,
			List<ECKey> payWallet) throws Exception {

		for (EthBalance pay : balanceList(payWallet)) {
			if (pay.getToken().equals(token)) {
				if (CryptoTokenlist.ETH.equals(token)) {
					if (Convert.toWei(pay.getBalance(), Unit.ETHER)
							.compareTo(checkAmountToPay(token, quantity, gasLimit, gasPrice)) >= 0) {
						return pay;
					} else {
						throw new InsufficientMoneyException(getLocalMessage("insufficientMoney") + " "
								+ pay.getBalance() + " < " + quantity + " + " + getFee());
					}
				} else {
					return pay;
				}

			}
		}
		return null;
	}

	private String getLocalMessage(String string) {
		// TODO Auto-generated method stub
		return string;
	}

	public BigDecimal checkAmountToPay(String contractAddress, String quantity, String gasLimit, String gasPrice)
			throws IOException {

		BigInteger value = Convert.toWei(quantity, Unit.ETHER).toBigInteger();
		if (includefee) {
			return new BigDecimal(value);
		} else {
			return new BigDecimal(value).add((new BigDecimal(gasLimit)).multiply(new BigDecimal(gasPrice)));
		}

	}

	public String calcFee(BigInteger gasLimit, BigInteger gasPrice) throws IOException {
		return Convert.fromWei(gasLimit.multiply(gasPrice).toString(), Unit.ETHER).toPlainString();

	}

	private String payDo(String token, String fromPayPrivatekey, String toaddress, String quantity, String gasLimit,
			String gasPrice) throws Exception {

		Credentials credentials = Credentials.create(fromPayPrivatekey);

		ERC20 erc20 = ERC20.load(CryptoTokenlist.getContract(token, !prodSystem()), web3j, credentials,
				new StaticGasProvider(new BigInteger(gasPrice), new BigInteger(gasLimit)));
		BigInteger decimals = erc20.decimals().send();
		// quantity Value to base unit
		Coin amount = MonetaryFormat.FIAT.noCode().parse(quantity,
				Utils.HEX.decode(CryptoTokenlist.getTokenId(token, !prodSystem())), decimals.intValue());
		LOG.debug("erc20 pay: " + quantity + " fee " + calcFee(new BigInteger(gasPrice), new BigInteger(gasLimit))
				+ " limit=" + gasLimit + " gasPrice= " + gasPrice);
		BigInteger value = amount.getValue();
		// wait 600 seconds timeout, must be check later for
		TransactionReceipt tr = erc20.transfer(toaddress, value).sendAsync().get();
		return tr.getTransactionHash();

	}

	public String payDoDirect(String token, String fromPayPrivatekey, String toaddress, String quantity,
			String gasLimit, String gasPrice) throws Exception {

		Credentials credentials = Credentials.create(fromPayPrivatekey);

		ERC20 erc20 = ERC20.load(CryptoTokenlist.getContract(token, !prodSystem()), web3j, credentials,
				new StaticGasProvider(new BigInteger(gasPrice), new BigInteger(gasLimit)));
		BigInteger decimals = erc20.decimals().send();
		Coin amount = MonetaryFormat.FIAT.noCode().parse(quantity,
				Utils.HEX.decode(CryptoTokenlist.getTokenId(token, !prodSystem())), decimals.intValue());
		LOG.debug("erc20 pay: " + quantity + " fee " + calcFee(new BigInteger(gasPrice), new BigInteger(gasLimit))
				+ " limit=" + gasLimit + " gasPrice= " + gasPrice);
		BigInteger value = amount.getValue();
		List<Type> inputParam = new ArrayList<>();
		inputParam = new ArrayList<>();
		inputParam.add(new Address(toaddress));
		inputParam.add(new Uint(value));
		return sendSignedTransaction(CryptoTokenlist.getContract(token, !prodSystem()), credentials, "transfer",
				inputParam, ResultType.BOOL, new BigInteger(gasPrice), new BigInteger(gasLimit));

	}

	private boolean prodSystem() {

		return true;
	}

	public String payDo(String fromPayPrivatekey, String toaddress, String quantity, String gasLimit, String gasPrice)
			throws IOException, InterruptedException, ExecutionException {

		Credentials credentials = Credentials.create(fromPayPrivatekey);
		LOG.debug("Account address: " + credentials.getAddress());

		// Get the latest nonce
		EthGetTransactionCount ethGetTransactionCount = web3j
				.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
		BigInteger nonce = ethGetTransactionCount.getTransactionCount();

		// Value to transfer (in wei)
		BigInteger value = Convert.toWei(quantity, Unit.ETHER).toBigInteger();

		if (!includefee) {
			value = value.add(new BigInteger(gasLimit).multiply(new BigInteger(gasPrice)));
			Convert.fromWei(value.toString(), Unit.ETHER);
		}
		// Gas Parameters
		// Prepare the rawTransaction
		RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, new BigInteger(gasPrice),
				new BigInteger(gasLimit), toaddress, value);

		// Sign the transaction
		byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
		String hexValue = Numeric.toHexString(signedMessage);

		// Send transaction
		EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
		transactionHash = ethSendTransaction.getTransactionHash();
		// System.out.println("error: " +
		if (ethSendTransaction.getError() != null)
			throw new RuntimeException(ethSendTransaction.getError().getMessage());
		LOG.debug("transactionHash: " + transactionHash);
		return transactionHash;
	}

	public void calcGasprice() {
		calcGasprice(tokenid);
	}

	protected void calcGasprice(String token) {

		GasService a = new GasService(new OkHttpClient());
		try {
			if (CryptoTokenlist.ETH.equals(token)) {
				gasLimit = C.GAS_LIMIT_MIN + "";
			} else {
				gasLimit = C.DEFAULT_GAS_LIMIT;
			}
			GasPriceSpread b = a.getRemoteGasPriceSpread();
			gasPriceGwei = b.standard.longValue() + "";
			getFee();
		} catch (IOException e) {
			LOG.debug(e);
		}

	}

	public Optional<TransactionReceipt> transactionReceipt(String transactionHash)
			throws IOException, InterruptedException {

		EthGetTransactionReceipt ethGetTransactionReceiptResp = web3j.ethGetTransactionReceipt(transactionHash).send();
		return ethGetTransactionReceiptResp.getTransactionReceipt();

	}

	public String sendSignedTransaction(String CONTRACT_ADDRESS, Credentials credentials, String functionName,
			List<Type> args, ResultType type, BigInteger gasprice, BigInteger gasLimit)
			throws IOException, ExecutionException, InterruptedException {

		Function function = new Function(functionName, args, Arrays.<TypeReference<?>>asList(getTypeReference(type)));

		String encodedFunction = FunctionEncoder.encode(function);
		// Get nonce value
		EthGetTransactionCount ethTransactionCount = web3j
				.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
		BigInteger nonce = ethTransactionCount.getTransactionCount();

		// GasService a = new GasService(new OkHttpClient());
		// GasPriceSpread b = a.getRemoteGasPriceSpread();

		// Transaction generation
		RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasprice, gasLimit, CONTRACT_ADDRESS,
				encodedFunction);

		// signature
		byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
		String hexValue = Numeric.toHexString(signedMessage);

		// send
		EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();

		String transactionHash = ethSendTransaction.getTransactionHash();
		if (ethSendTransaction.getError() != null) {
			System.out.println("error: " + ethSendTransaction.getError().getMessage());
			throw new RuntimeException(ethSendTransaction.getError().getMessage());
		}

		// Wait for transaction to be mined

		return transactionHash;
	}

	public TypeReference<?> getTypeReference(ResultType type) {

		TypeReference<?> typeReference = null;
		switch (type) {
		case ADDRESS:
			typeReference = new TypeReference<Address>() {
			};
			break;
		case BOOL:
			typeReference = new TypeReference<Bool>() {
			};
			break;
		case STRING:
			typeReference = new TypeReference<Utf8String>() {
			};
			break;
		case INT:
			typeReference = new TypeReference<Uint>() {
			};
			break;
		default:
			break;
		}
		return typeReference;
	}

	public List<String> completeETHToken(String query) {
		return Arrays.asList(CryptoTokenlist.ethtokens);
	}

	public void reset() {
		address = "";

	}

	public String getTokenid() {
		return tokenid;
	}

	public void setTokenid(String tokenid) {
		this.tokenid = tokenid;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getGasLimit() {
		return gasLimit;
	}

	public void setGasLimit(String gasLimit) {
		this.gasLimit = gasLimit;
	}

	public String getGasPriceGwei() {
		return gasPriceGwei;
	}

	public void setGasPriceGwei(String gasPriceGwei) {
		this.gasPriceGwei = gasPriceGwei;
	}

	public String getTransactionHash() {
		return transactionHash;
	}

	public void setTransactionHash(String transactionHash) {
		this.transactionHash = transactionHash;
	}

	public String getFromaddress() {
		return fromaddress;
	}

	public void setFromaddress(String fromaddress) {
		this.fromaddress = fromaddress;
	}

	public String getFee(String token) throws IOException {
		calcGasprice(CryptoTokenlist.ETH);
		return calcFee(new BigInteger(gasLimit), gasPriceInWei());
	}

	public String getFee() {
		try {
			fee = calcFee(new BigInteger(gasLimit), gasPriceInWei());
		} catch (IOException e) {
		}
		return fee;
	}

	public void setFee(String fee) {
		this.fee = fee;
	}

	public Boolean getIncludefee() {
		return includefee;
	}

	public void setIncludefee(Boolean includefee) {
		this.includefee = includefee;
	}

}
