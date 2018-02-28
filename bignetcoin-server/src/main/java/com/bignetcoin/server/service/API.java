/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import static org.bitcoinj.core.Utils.HEX;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.TagViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Neighbor;
import com.iota.iri.service.ValidationException;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.AccessLimitedResponse;
import com.iota.iri.service.dto.AddedNeighborsResponse;
import com.iota.iri.service.dto.AttachToTangleResponse;
import com.iota.iri.service.dto.CheckConsistency;
import com.iota.iri.service.dto.ErrorResponse;
import com.iota.iri.service.dto.ExceptionResponse;
import com.iota.iri.service.dto.FindTransactionsResponse;
import com.iota.iri.service.dto.GetBalancesResponse;
import com.iota.iri.service.dto.GetInclusionStatesResponse;
import com.iota.iri.service.dto.GetNeighborsResponse;
import com.iota.iri.service.dto.GetNodeInfoResponse;
import com.iota.iri.service.dto.GetTipsResponse;
import com.iota.iri.service.dto.GetTransactionsToApproveResponse;
import com.iota.iri.service.dto.RemoveNeighborsResponse;
import com.iota.iri.service.dto.wereAddressesSpentFrom;
import com.iota.iri.utils.Converter;

@RestController
@RequestMapping("/")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);
    public static final String REFERENCE_TRANSACTION_NOT_FOUND = "reference transaction not found";
    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    // private static final Logger log = LoggerFactory.getLogger(API.class);
    // private final IXI ixi;
    public static final String MAINNET_NAME = "IRI";
    public static final String TESTNET_NAME = "IRI Testnet";
    public static final String VERSION = "1.4.2.1";
    private final Gson gson = new GsonBuilder().create();
    private volatile PearlDiver pearlDiver = new PearlDiver();

    private final AtomicInteger counter = new AtomicInteger(0);

    private final static int HASH_SIZE = 81;
    private final static int TRYTES_SIZE = 2673;

    private final static long MAX_TIMESTAMP_VALUE = (3 ^ 27 - 1) / 2;

    private final int minRandomWalks;
    private final int maxRandomWalks;
    private final int maxFindTxs;
    private final int maxRequestList;

    private final static String overMaxErrorMessage = "Could not complete request";
    private final static String invalidParams = "Invalid parameters";

    private Bignet instance;
    private TransactionService transactionService;

    @Autowired
    public API(Bignet instance, TransactionService transactionService) {
        this.instance = instance;
        this.transactionService = transactionService;
        // this.ixi = ixi;
        minRandomWalks = instance.configuration.integer(DefaultConfSettings.MIN_RANDOM_WALKS);
        maxRandomWalks = instance.configuration.integer(DefaultConfSettings.MAX_RANDOM_WALKS);
        maxFindTxs = instance.configuration.integer(DefaultConfSettings.MAX_FIND_TRANSACTIONS);
        maxRequestList = instance.configuration.integer(DefaultConfSettings.MAX_REQUESTS_LIST);

    }

    @RequestMapping(method = { RequestMethod.POST, RequestMethod.GET })
    public AbstractResponse process(@RequestBody byte[] vdvAnfrageBytes) throws UnsupportedEncodingException {
        String body = new String(vdvAnfrageBytes, Charset.forName("UTF-8"));
        log.debug(" requestString ", body);

        return process(body);

    }

    private AbstractResponse process(final String requestString) throws UnsupportedEncodingException {

        try {

            final Map<String, Object> request = gson.fromJson(requestString, Map.class);
            if (request == null) {
                return ExceptionResponse.create("Invalid request payload: '" + requestString + "'");
            }

            final String command = (String) request.get("command");
            if (command == null) {
                return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
            }

            if (instance.configuration.string(DefaultConfSettings.REMOTE_LIMIT_API).contains(command)) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            switch (command) {

            case "addNeighbors": {
                List<String> uris = getParameterAsList(request, "uris", 0);
                log.debug("Invoking 'addNeighbors' with {}", uris);
                return addNeighborsStatement(uris);
            }
            case "attachToTangle": {
                final Hash trunkTransaction = new Hash(
                        getParameterAsStringAndValidate(request, "trunkTransaction", HASH_SIZE));
                final Hash branchTransaction = new Hash(
                        getParameterAsStringAndValidate(request, "branchTransaction", HASH_SIZE));
                final int minWeightMagnitude = getParameterAsInt(request, "minWeightMagnitude");

                final List<String> trytes = getParameterAsList(request, "trytes", TRYTES_SIZE);

                List<String> elements = attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude,
                        trytes);
                return AttachToTangleResponse.create(elements);
            }
            case "broadcastTransactions": {
                final List<String> trytes = getParameterAsList(request, "trytes", TRYTES_SIZE);
                broadcastTransactionStatement(trytes);
                return AbstractResponse.createEmptyResponse();
            }
            case "findTransactions": {
                return findTransactionStatement(request);
            }
            case "getBalances": {
                final List<String> addresses = getParameterAsList(request, "addresses", HASH_SIZE);
                final List<String> tips = request.containsKey("tips") ? getParameterAsList(request, "tips ", HASH_SIZE)
                        : null;
                final int threshold = getParameterAsInt(request, "threshold");
                return getBalancesStatement(addresses, tips, threshold);
            }
            case "getInclusionStates": {
                if (invalidSubtangleStatus()) {
                    return ErrorResponse
                            .create("This operations cannot be executed: The subtangle has not been updated yet.");
                }
                final List<String> transactions = getParameterAsList(request, "transactions", HASH_SIZE);
                final List<String> tips = getParameterAsList(request, "tips", HASH_SIZE);

                return getNewInclusionStateStatement(transactions, tips);
            }
            case "getNeighbors": {
                return getNeighborsStatement();
            }
            case "getNodeInfo": {
                String name = instance.configuration.booling(Configuration.DefaultConfSettings.TESTNET) ? TESTNET_NAME
                        : MAINNET_NAME;
                return GetNodeInfoResponse.create(name, VERSION, Runtime.getRuntime().availableProcessors(),
                        Runtime.getRuntime().freeMemory(), System.getProperty("java.version"),
                        Runtime.getRuntime().maxMemory(), Runtime.getRuntime().totalMemory(),
                        instance.milestone.latestMilestone, instance.milestone.latestMilestoneIndex,
                        instance.milestone.latestSolidSubtangleMilestone,
                        instance.milestone.latestSolidSubtangleMilestoneIndex, instance.node.howManyNeighbors(),
                        instance.node.queuedTransactionsSize(), System.currentTimeMillis(),
                        instance.tipsViewModel.size(), instance.transactionRequester.numberOfTransactionsToRequest());
            }
            case "getTips": {
                return getTipsStatement();
            }
            case "getTransactionsToApprove": {
                if (invalidSubtangleStatus()) {
                    return ErrorResponse
                            .create("This operations cannot be executed: The subtangle has not been updated yet.");
                }

                final String reference = request.containsKey("reference")
                        ? getParameterAsStringAndValidate(request, "reference", HASH_SIZE)
                        : null;
                final int depth = getParameterAsInt(request, "depth");
                if (depth < 0 || (reference == null && depth == 0)) {
                    return ErrorResponse.create("Invalid depth input");
                }
                int numWalks = request.containsKey("numWalks") ? getParameterAsInt(request, "numWalks") : 1;
                if (numWalks < minRandomWalks) {
                    numWalks = minRandomWalks;
                }
                try {
                    final Hash[] tips = getTransactionToApproveStatement(depth, reference, numWalks);
                    if (tips == null) {
                        return ErrorResponse.create("The subtangle is not solid");
                    }
                    return GetTransactionsToApproveResponse.create(tips[0], tips[1]);
                } catch (RuntimeException e) {
                    log.info("Tip selection failed: " + e.getLocalizedMessage());
                    return ErrorResponse.create(e.getLocalizedMessage());
                }
            }

            case "interruptAttachingToTangle": {
                pearlDiver.cancel();
                return AbstractResponse.createEmptyResponse();
            }
            case "removeNeighbors": {
                List<String> uris = getParameterAsList(request, "uris", 0);
                log.debug("Invoking 'removeNeighbors' with {}", uris);
                return removeNeighborsStatement(uris);
            }

            case "storeTransactions": {
                try {
                    final List<String> trytes = getParameterAsList(request, "trytes", TRYTES_SIZE);
                    storeTransactionStatement(trytes);
                    return AbstractResponse.createEmptyResponse();
                } catch (RuntimeException e) {
                    // transaction not valid
                    return ErrorResponse.create("Invalid trytes input");
                }
            }
            case "getMissingTransactions": {
                // TransactionRequester.instance().rescanTransactionsToRequest();
                synchronized (instance.transactionRequester) {
                    List<String> missingTx = Arrays.stream(instance.transactionRequester.getRequestedTransactions())
                            .map(Hash::toString).collect(Collectors.toList());
                    return GetTipsResponse.create(missingTx);
                }
            }
            case "checkConsistency": {
                if (invalidSubtangleStatus()) {
                    return ErrorResponse
                            .create("This operations cannot be executed: The subtangle has not been updated yet.");
                }
                final List<String> transactions = getParameterAsList(request, "tails", HASH_SIZE);
                return checkConsistencyStatement(transactions);
            }
            case "wereAddressesSpentFrom": {
                final List<String> addresses = getParameterAsList(request, "addresses", HASH_SIZE);
                return wereAddressesSpentFromStatement(addresses);
            }
            default: {
                /*
                 * AbstractResponse response = ixi.processCommand(command,
                 * request); return response == null ?
                 * ErrorResponse.create("Command [" + command + "] is unknown")
                 * : response;
                 */
                return ExceptionResponse.create("");
            }
            }

        } catch (final ValidationException e) {
            log.info("API Validation failed: " + e.getLocalizedMessage(), e);
            return ErrorResponse.create(e.getLocalizedMessage());
        } catch (final Exception e) {
            log.error("API Exception: ", e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    private AbstractResponse wereAddressesSpentFromStatement(List<String> addressesStr) throws Exception {
        final List<Hash> addresses = addressesStr.stream().map(Hash::new).collect(Collectors.toList());
        final boolean[] states = new boolean[addresses.size()];
        int index = 0;

        for (Hash address : addresses) {
            states[index++] = wasAddressSpentFrom(address);
        }
        return wereAddressesSpentFrom.create(states);
    }

    private boolean wasAddressSpentFrom(Hash address) throws Exception {

        Set<Hash> hashes = AddressViewModel.load(instance.tangle, address).getHashes();
        for (Hash hash : hashes) {
            final TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
            // spend
            if (tx.value() < 0) {
                // confirmed
                if (tx.snapshotIndex() != 0) {
                    return true;
                }
                // pending
                Hash tail = findTail(hash);
                if (tail != null && BundleValidator.validate(instance.tangle, tail).size() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private Hash findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        boolean foundApprovee = false;
        while (index-- > 0 && tx.getBundleHash().equals(bundleHash)) {
            Set<Hash> approvees = tx.getApprovers(instance.tangle).getHashes();
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(instance.tangle, approvee);
                if (nextTx.getBundleHash().equals(bundleHash)) {
                    tx = nextTx;
                    foundApprovee = true;
                    break;
                }
            }
            if (!foundApprovee) {
                break;
            }
        }
        if (tx.getCurrentIndex() == 0) {
            return tx.getHash();
        }
        return null;
    }

    private AbstractResponse checkConsistencyStatement(List<String> transactionsList) throws Exception {
        final List<Hash> transactions = transactionsList.stream().map(Hash::new).collect(Collectors.toList());
        boolean state = true;
        String info = "";

        // check transactions themselves are valid
        for (Hash transaction : transactions) {
            TransactionViewModel txVM = TransactionViewModel.fromHash(instance.tangle, transaction);
            if (txVM.getType() == TransactionViewModel.PREFILLED_SLOT) {
                return ErrorResponse.create("Invalid transaction, missing: " + transaction);
            }
            if (txVM.getCurrentIndex() != 0) {
                return ErrorResponse.create("Invalid transaction, not a tail: " + transaction);
            }

            if (!instance.transactionValidator.checkSolidity(txVM.getHash(), false)) {
                state = false;
                info = "tails are not solid (missing a referenced tx): " + transaction;
                break;
            } else if (BundleValidator.validate(instance.tangle, txVM.getHash()).size() == 0) {
                state = false;
                info = "tails are not consistent (bundle is invalid): " + transaction;
                break;
            }
        }

        if (state) {
            instance.milestone.latestSnapshot.rwlock.readLock().lock();
            try {

                if (!instance.ledgerValidator.checkConsistency(transactions)) {
                    state = false;
                    info = "tails are not consistent (would lead to inconsistent ledger state)";
                }
            } finally {
                instance.milestone.latestSnapshot.rwlock.readLock().unlock();
            }
        }

        return CheckConsistency.create(state, info);
    }

    private int getParameterAsInt(Map<String, Object> request, String paramName) throws ValidationException {
        validateParamExists(request, paramName);
        final int result;
        try {
            result = ((Double) request.get(paramName)).intValue();
        } catch (ClassCastException e) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size)
            throws ValidationException {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        // validateTrytes(paramName, size, result);
        return result;
    }

    private void validateParamExists(Map<String, Object> request, String paramName) throws ValidationException {
        if (!request.containsKey(paramName)) {
            throw new ValidationException(invalidParams);
        }
    }

    private List<String> getParameterAsList(Map<String, Object> request, String paramName, int size)
            throws ValidationException {
        validateParamExists(request, paramName);
        final List<String> paramList = (List<String>) request.get(paramName);
        if (paramList.size() > maxRequestList) {
            throw new ValidationException(overMaxErrorMessage);
        }

        if (size > 0) {
            // validate
            for (final String param : paramList) {
                // validateTrytes(paramName, size, param);
            }
        }

        return paramList;

    }

    public boolean invalidSubtangleStatus() {
        return (instance.milestone.latestSolidSubtangleMilestoneIndex == MilestoneService.MILESTONE_START_INDEX);
    }

    private AbstractResponse removeNeighborsStatement(List<String> uris) {
        int numberOfRemovedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Removing neighbor: " + uriString);
                if (instance.node.removeNeighbor(new URI(uriString), true)) {
                    numberOfRemovedNeighbors++;
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
        }
        return RemoveNeighborsResponse.create(numberOfRemovedNeighbors);
    }

    private static int counter_getTxToApprove = 0;

    public static int getCounter_getTxToApprove() {
        return counter_getTxToApprove;
    }

    public static void incCounter_getTxToApprove() {
        counter_getTxToApprove++;
    }

    private static long ellapsedTime_getTxToApprove = 0L;

    public static long getEllapsedTime_getTxToApprove() {
        return ellapsedTime_getTxToApprove;
    }

    public static void incEllapsedTime_getTxToApprove(long ellapsedTime) {
        ellapsedTime_getTxToApprove += ellapsedTime;
    }

    public synchronized Hash[] getTransactionToApproveStatement(int depth, final String reference, final int numWalks)
            throws Exception {
        int tipsToApprove = 2;
        Hash[] tips = new Hash[tipsToApprove];
        final SecureRandom random = new SecureRandom();
        final int randomWalkCount = numWalks > maxRandomWalks || numWalks < 1 ? maxRandomWalks : numWalks;
        Hash referenceHash = null;
        int maxDepth = instance.tipsManager.getMaxDepth();
        if (depth > maxDepth) {
            depth = maxDepth;
        }
        if (reference != null) {
            referenceHash = new Hash(reference);
            if (!TransactionViewModel.exists(instance.tangle, referenceHash)) {
                throw new RuntimeException(REFERENCE_TRANSACTION_NOT_FOUND);
            } else {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle,
                        referenceHash);
                if (transactionViewModel.snapshotIndex() != 0 && transactionViewModel
                        .snapshotIndex() < instance.milestone.latestSolidSubtangleMilestoneIndex - depth) {
                    throw new RuntimeException(REFERENCE_TRANSACTION_TOO_OLD);
                }
            }
        }

        instance.milestone.latestSnapshot.rwlock.readLock().lock();
        try {
            Set<Hash> visitedHashes = new HashSet<>();
            Map<Hash, Long> diff = new HashMap<>();
            for (int i = 0; i < tipsToApprove; i++) {
                tips[i] = instance.tipsManager.transactionToApprove(visitedHashes, diff, referenceHash, tips[0], depth,
                        randomWalkCount, random);
                if (tips[i] == null) {
                    return null;
                }
            }
            API.incCounter_getTxToApprove();
            if ((getCounter_getTxToApprove() % 100) == 0) {
                String sb = "Last 100 getTxToApprove consumed " + API.getEllapsedTime_getTxToApprove() / 1000000000L
                        + " seconds processing time.";
                log.info(sb);
                counter_getTxToApprove = 0;
                ellapsedTime_getTxToApprove = 0L;
            }

            if (instance.ledgerValidator.checkConsistency(Arrays.asList(tips))) {
                return tips;
            }
        } finally {
            instance.milestone.latestSnapshot.rwlock.readLock().unlock();
        }
        throw new RuntimeException("inconsistent tips pair selected");
    }

    private synchronized AbstractResponse getTipsStatement() throws Exception {
        return GetTipsResponse
                .create(instance.tipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList()));
    }

    public void storeTransactionStatement(final List<String> trys) throws Exception {
        final List<TransactionViewModel> elements = new LinkedList<>();
        int[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        for (final String trytes : trys) {
            // validate all trytes
            Converter.trits(trytes, txTrits, 0);
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(txTrits,
                    instance.transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            // store transactions
            if (transactionViewModel.store(instance.tangle)) {
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                instance.transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(instance.tangle, "sender");
            }
        }
    }

    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(instance.node.getNeighbors());
    }

    private AbstractResponse getNewInclusionStateStatement(final List<String> trans, final List<String> tps)
            throws Exception {
        final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());
        int numberOfNonMetTransactions = transactions.size();
        final int[] inclusionStates = new int[numberOfNonMetTransactions];

        List<Integer> tipsIndex = new LinkedList<>();
        {
            for (Hash tip : tips) {
                TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, tip);
                if (tx.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    tipsIndex.add(tx.snapshotIndex());
                }
            }
        }
        int minTipsIndex = tipsIndex.stream().reduce((a, b) -> a < b ? a : b).orElse(0);
        if (minTipsIndex > 0) {
            int maxTipsIndex = tipsIndex.stream().reduce((a, b) -> a > b ? a : b).orElse(0);
            int count = 0;
            for (Hash hash : transactions) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(instance.tangle, hash);
                if (transaction.getType() == TransactionViewModel.PREFILLED_SLOT || transaction.snapshotIndex() == 0) {
                    inclusionStates[count] = -1;
                } else if (transaction.snapshotIndex() > maxTipsIndex) {
                    inclusionStates[count] = -1;
                } else if (transaction.snapshotIndex() < maxTipsIndex) {
                    inclusionStates[count] = 1;
                }
                count++;
            }
        }

        Set<Hash> analyzedTips = new HashSet<>();
        Map<Integer, Integer> sameIndexTransactionCount = new HashMap<>();
        Map<Integer, Queue<Hash>> sameIndexTips = new HashMap<>();
        for (final Hash tip : tips) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                return ErrorResponse.create("One of the tips absents");
            }
            int snapshotIndex = transactionViewModel.snapshotIndex();
            sameIndexTips.putIfAbsent(snapshotIndex, new LinkedList<>());
            sameIndexTips.get(snapshotIndex).add(tip);
        }
        for (int i = 0; i < inclusionStates.length; i++) {
            if (inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle,
                        transactions.get(i));
                int snapshotIndex = transactionViewModel.snapshotIndex();
                sameIndexTransactionCount.putIfAbsent(snapshotIndex, 0);
                sameIndexTransactionCount.put(snapshotIndex, sameIndexTransactionCount.get(snapshotIndex) + 1);
            }
        }
        for (Integer index : sameIndexTransactionCount.keySet()) {
            Queue<Hash> sameIndexTip = sameIndexTips.get(index);
            if (sameIndexTip != null) {
                // has tips in the same index level
                if (!exhaustiveSearchWithinIndex(sameIndexTip, analyzedTips, transactions, inclusionStates,
                        sameIndexTransactionCount.get(index), index)) {
                    return ErrorResponse.create("The subtangle is not solid");
                }
            }
        }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for (int i = 0; i < inclusionStates.length; i++) {
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }
        {
            return GetInclusionStatesResponse.create(inclusionStatesBoolean);
        }
    }

    private boolean exhaustiveSearchWithinIndex(Queue<Hash> nonAnalyzedTransactions, Set<Hash> analyzedTips,
            List<Hash> transactions, int[] inclusionStates, int count, int index) throws Exception {
        Hash pointer;
        MAIN_LOOP: while ((pointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedTips.add(pointer)) {
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle,
                        pointer);
                if (transactionViewModel.snapshotIndex() == index) {
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        return false;
                    } else {
                        for (int i = 0; i < inclusionStates.length; i++) {
                            if (inclusionStates[i] < 1 && pointer.equals(transactions.get(i))) {
                                inclusionStates[i] = 1;
                                if (--count <= 0) {
                                    break MAIN_LOOP;
                                }
                            }
                        }
                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        }
        return true;
    }

    private synchronized AbstractResponse findTransactionStatement(final Map<String, Object> request) throws Exception {
        final Set<Hash> foundTransactions = new HashSet<>();
        boolean containsKey = false;

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final HashSet<String> addresses = getParameterAsSet(request, "addresses", HASH_SIZE);
            for (final String address : addresses) {
                addressesTransactions.addAll(AddressViewModel.load(instance.tangle, new Hash(address)).getHashes());
            }
            foundTransactions.addAll(addressesTransactions);
            containsKey = true;
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            final HashSet<String> tags = getParameterAsSet(request, "tags", 0);
            for (String tag : tags) {
                tag = padTag(tag);
                tagsTransactions.addAll(TagViewModel.load(instance.tangle, new Hash(tag)).getHashes());
            }
            foundTransactions.addAll(tagsTransactions);
            containsKey = true;
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            final HashSet<String> approvees = getParameterAsSet(request, "approvees", HASH_SIZE);
            for (final String approvee : approvees) {
                approveeTransactions.addAll(TransactionViewModel.fromHash(instance.tangle, new Hash(approvee))
                        .getApprovers(instance.tangle).getHashes());
            }
            foundTransactions.addAll(approveeTransactions);
            containsKey = true;
        }

        if (!containsKey) {
            throw new ValidationException(invalidParams);
        }

        if (request.containsKey("addresses")) {
            foundTransactions.retainAll(addressesTransactions);
        }
        if (request.containsKey("tags")) {
            foundTransactions.retainAll(tagsTransactions);
        }
        if (request.containsKey("approvees")) {
            foundTransactions.retainAll(approveeTransactions);
        }
        if (foundTransactions.size() > maxFindTxs) {
            return ErrorResponse.create(overMaxErrorMessage);
        }

        final List<String> elements = foundTransactions.stream().map(Hash::toString)
                .collect(Collectors.toCollection(LinkedList::new));

        return FindTransactionsResponse.create(elements);
    }

    private String padTag(String tag) throws ValidationException {
        while (tag.length() < HASH_SIZE) {
            tag += Converter.TRYTE_ALPHABET.charAt(0);
        }
        if (tag.equals(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid tag input");
        }
        return tag;
    }

    private HashSet<String> getParameterAsSet(Map<String, Object> request, String paramName, int size)
            throws ValidationException {

        HashSet<String> result = getParameterAsList(request, paramName, size).stream()
                .collect(Collectors.toCollection(HashSet::new));
        if (result.contains(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    public void broadcastTransactionStatement(final List<String> trytes2) {
        final List<TransactionViewModel> elements = new LinkedList<>();
        int[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        for (final String tryte : trytes2) {
            // validate all trytes
            Converter.trits(tryte, txTrits, 0);
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(txTrits,
                    instance.transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            // push first in line to broadcast
            transactionViewModel.weightMagnitude = Curl.HASH_LENGTH;
            instance.node.broadcast(transactionViewModel);
        }
    }

    private AbstractResponse getBalancesStatement(final List<String> addrss, final List<String> tips,
            final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addresses = addrss.stream().map(address -> (new Hash(address)))
                .collect(Collectors.toCollection(LinkedList::new));
        final List<Hash> hashes;
        final Map<String, Coin> balances = new HashMap<>();

        for (final String address : addrss) {
            List<byte[]> l = new ArrayList<byte[]>();
            l.add(HEX.decode(address));
            Coin value = transactionService.getBalance(l);

            balances.put(address, value);
        }

        final List<String> elements = addresses.stream().map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, null, 0);
    }

    private static int counter_PoW = 0;

    public static int getCounter_PoW() {
        return counter_PoW;
    }

    public static void incCounter_PoW() {
        counter_PoW++;
    }

    private static long ellapsedTime_PoW = 0L;

    public static long getEllapsedTime_PoW() {
        return ellapsedTime_PoW;
    }

    public static void incEllapsedTime_PoW(long ellapsedTime) {
        ellapsedTime_PoW += ellapsedTime;
    }

    public synchronized List<String> attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
            final int minWeightMagnitude, final List<String> trytes) {
        final List<TransactionViewModel> transactionViewModels = new LinkedList<>();

        Hash prevTransaction = null;
        pearlDiver = new PearlDiver();

        int[] transactionTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);

        for (final String tryte : trytes) {
            long startTime = System.nanoTime();
            long timestamp = System.currentTimeMillis();
            try {
                Converter.trits(tryte, transactionTrits, 0);
                // branch and trunk
                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);

                // attachment fields: tag and timestamps
                // tag - copy the obsolete tag to the attachment tag field only
                // if tag isn't set.
                if (Arrays
                        .stream(transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET,
                                TransactionViewModel.TAG_TRINARY_OFFSET + TransactionViewModel.TAG_TRINARY_SIZE)
                        .allMatch(s -> s == 0)) {
                    System.arraycopy(transactionTrits, TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET,
                            transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET,
                            TransactionViewModel.TAG_TRINARY_SIZE);
                }

                Converter.copyTrits(timestamp, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_SIZE);
                Converter.copyTrits(0, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE);
                Converter.copyTrits(MAX_TIMESTAMP_VALUE, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE);

                if (!pearlDiver.search(transactionTrits, minWeightMagnitude, 0)) {
                    transactionViewModels.clear();
                    break;
                }
                // validate PoW - throws exception if invalid
                final TransactionViewModel transactionViewModel = instance.transactionValidator
                        .validate(transactionTrits, instance.transactionValidator.getMinWeightMagnitude());

                transactionViewModels.add(transactionViewModel);
                prevTransaction = transactionViewModel.getHash();
            } finally {
                API.incEllapsedTime_PoW(System.nanoTime() - startTime);
                API.incCounter_PoW();
                if ((API.getCounter_PoW() % 100) == 0) {
                    String sb = "Last 100 PoW consumed " + API.getEllapsedTime_PoW() / 1000000000L
                            + " seconds processing time.";
                    log.info(sb);
                    counter_PoW = 0;
                    ellapsedTime_PoW = 0L;
                }
            }
        }

        final List<String> elements = new LinkedList<>();
        for (int i = transactionViewModels.size(); i-- > 0;) {
            elements.add(Converter.trytes(transactionViewModels.get(i).trits()));
        }
        return elements;
    }

    private AbstractResponse addNeighborsStatement(final List<String> uris) {
        int numberOfAddedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Adding neighbor: " + uriString);
                final Neighbor neighbor = instance.node.newNeighbor(new URI(uriString), true);
                if (!instance.node.getNeighbors().contains(neighbor)) {
                    instance.node.getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
        }
        return AddedNeighborsResponse.create(numberOfAddedNeighbors);
    }

}
