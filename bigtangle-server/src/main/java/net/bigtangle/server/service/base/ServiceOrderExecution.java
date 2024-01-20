package net.bigtangle.server.service.base;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.OrderExecutionResult;
import net.bigtangle.server.service.CacheBlockService;
import net.bigtangle.server.utils.OrderBook;
import net.bigtangle.store.FullBlockStore;

public class ServiceOrderExecution extends ServiceBaseConnect {

	public ServiceOrderExecution(ServerConfiguration serverConfiguration, NetworkParameters networkParameters,
			CacheBlockService cacheBlockService) {
		super(serverConfiguration, networkParameters, cacheBlockService);

	}

	//private static final Logger log = LoggerFactory.getLogger(ServiceOrderExecution.class);
 

	public OrderExecutionResult orderMatching(Block block, Sha256Hash prevHash, Set<Sha256Hash> collectedBlocks,
			FullBlockStore blockStore) throws BlockStoreException {
		TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts = new TreeMap<>();

		// Deterministic randomization
		byte[] randomness = Utils.xor(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes());

		// Collect all orders approved by this block in the interval
		List<OrderCancelInfo> cancels = new ArrayList<>();
		Map<Sha256Hash, OrderRecord> sortedNewOrders = new TreeMap<>(
				Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));

		HashMap<Sha256Hash, OrderRecord> remainingOrders = new HashMap<Sha256Hash, OrderRecord>();
		if (!Sha256Hash.ZERO_HASH.equals(prevHash)) {
			// new order must be from collectedBlocks, not this write as
			// Sha256Hash.ZERO_HASH in orders
			remainingOrders = blockStore.getOrderMatchingIssuedOrders(prevHash);
		}

		Set<OrderRecord> toBeSpentOrders = new HashSet<>();
		Set<OrderRecord> cancelledOrders = new HashSet<>();
		for (OrderRecord r : remainingOrders.values()) {
			toBeSpentOrders.add(OrderRecord.cloneOrderRecord(r));
		}
		collectOrdersWithCancel(block, collectedBlocks, cancels, sortedNewOrders, toBeSpentOrders, blockStore);
		// sort order for execute in deterministic randomness
		Map<Sha256Hash, OrderRecord> sortedOldOrders = new TreeMap<>(
				Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
		sortedOldOrders.putAll(remainingOrders);
		remainingOrders.putAll(sortedNewOrders);

		// Issue timeout cancels, set issuing order blockhash
		setIssuingBlockHash(block, remainingOrders);
		timeoutOrdersToCancelled(block, remainingOrders, cancelledOrders);
		cancelOrderstoCancelled(cancels, remainingOrders, cancelledOrders);

		// Remove the now cancelled orders from rest of orders
		for (OrderRecord c : cancelledOrders) {
			remainingOrders.remove(c.getBlockHash());
			sortedOldOrders.remove(c.getBlockHash());
			sortedNewOrders.remove(c.getBlockHash());
		}

		// Add to proceeds all cancelled orders going back to the beneficiary
		payoutCancelledOrders(payouts, cancelledOrders);

		// From all orders and ops, begin order matching algorithm by filling
		// order books
		int orderId = 0;
		ArrayList<OrderRecord> orderId2Order = new ArrayList<>();
		TreeMap<TradePair, OrderBook> orderBooks = new TreeMap<TradePair, OrderBook>();

		// Add old orders first without not valid yet
		for (OrderRecord o : sortedOldOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()) && o.isValidYet(block.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}

		// Now orders not valid before but valid now
		for (OrderRecord o : sortedOldOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()) && !o.isValidYet(block.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}
		// Now new orders that are valid yet
		for (OrderRecord o : sortedNewOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}

		// Collect and process all matching events
		Map<TradePair, List<Event>> tokenId2Events = new HashMap<>();
		for (Entry<TradePair, OrderBook> orderBook : orderBooks.entrySet()) {
			processOrderBook(payouts, remainingOrders, orderId2Order, tokenId2Events, orderBook);
		}

		for (OrderRecord o : remainingOrders.values()) {
			o.setDefault();
		}

		// Make deterministic tx with proceeds
		Transaction tx = createOrderPayoutTransaction(block, payouts);

		return new OrderExecutionResult(block.getHash(), getOrderRecordHash(toBeSpentOrders), tx.getHash(), tx,
				prevHash, getOrderRecordHash(cancelledOrders), remainingOrders.keySet(), block.getTimeSeconds(),
				remainingOrders.values(), toBeSpentOrders,collectedBlocks,tokenId2Events);

	}

}
