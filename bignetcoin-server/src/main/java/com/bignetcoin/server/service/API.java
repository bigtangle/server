/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import static org.bitcoinj.core.Utils.HEX;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bignetcoin.server.config.ServerConfiguration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;
import com.iota.iri.service.dto.ExceptionResponse;
import com.iota.iri.service.dto.GetBalancesResponse;
import com.iota.iri.service.dto.GetNodeInfoResponse;
import com.iota.iri.service.dto.GetTransactionsToApproveResponse;
import com.iota.iri.service.dto.wereAddressesSpentFrom;

@RestController
@RequestMapping("/")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);
    public static final String REFERENCE_TRANSACTION_NOT_FOUND = "reference transaction not found";
    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    // private static final Logger log = LoggerFactory.getLogger(API.class);

    private final Gson gson = new GsonBuilder().create();
    private final static int HASH_SIZE = 81;
    private final static int TRYTES_SIZE = 2673;

    private final static long MAX_TIMESTAMP_VALUE = (3 ^ 27 - 1) / 2;

    private final static String overMaxErrorMessage = "Could not complete request";
    private final static String invalidParams = "Invalid parameters";

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private ServerConfiguration serverConfiguration;
    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private TipsService tipsService;

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
            switch (command) {

            case "getBalances": {
                final List<String> addresses = getParameterAsList(request, "addresses", HASH_SIZE);
                final List<String> tips = request.containsKey("tips") ? getParameterAsList(request, "tips ", HASH_SIZE)
                        : null;
                final int threshold = getParameterAsInt(request, "threshold");
                return getBalancesStatement(addresses, tips, threshold);
            }

            case "getNodeInfo": {

                return GetNodeInfoResponse.create("", "", Runtime.getRuntime().availableProcessors(),
                        Runtime.getRuntime().freeMemory(), System.getProperty("java.version"),
                        Runtime.getRuntime().maxMemory(), Runtime.getRuntime().totalMemory(),
                        milestoneService.latestMilestone, milestoneService.latestMilestoneIndex,
                        milestoneService.latestSolidSubtangleMilestone,
                        milestoneService.latestSolidSubtangleMilestoneIndex, 0, 0, System.currentTimeMillis(), 0, 0);
            }

            case "getBlocksToApprove": {

                final String reference = request.containsKey("reference")
                        ? getParameterAsStringAndValidate(request, "reference", HASH_SIZE)
                        : null;
                final int depth = getParameterAsInt(request, "depth");
                if (depth < 0 || (reference == null && depth == 0)) {
                    return ErrorResponse.create("Invalid depth input");
                }
                int numWalks = request.containsKey("numWalks") ? getParameterAsInt(request, "numWalks") : 1;
                if (numWalks < serverConfiguration.getMinRandomWalks()) {
                    numWalks = serverConfiguration.getMinRandomWalks();
                }
                try {
                    final List<Sha256Hash> tips = getBlockToApproveStatement(depth, reference, numWalks);
                    if (tips == null) {
                        return ErrorResponse.create("The subtangle is not solid");
                    }
                    return GetTransactionsToApproveResponse.create(tips.get(0), tips.get(1));
                } catch (RuntimeException e) {
                    log.info("Tip selection failed: " + e.getLocalizedMessage());
                    return ErrorResponse.create(e.getLocalizedMessage());
                }
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

        } catch (final Exception e) {
            log.error("API Exception: ", e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    private List<Sha256Hash> getBlockToApproveStatement(int depth, String reference, int numWalks) {

        return tipsService.blockToApprove(depth, reference,numWalks );
    }

    private AbstractResponse wereAddressesSpentFromStatement(List<String> addressesStr) throws Exception {
        final List<Sha256Hash> addresses = addressesStr.stream().map(Sha256Hash::new).collect(Collectors.toList());
        final boolean[] states = new boolean[addresses.size()];
        int index = 0;

        for (Sha256Hash address : addresses) {
            states[index++] = wasAddressSpentFrom(address);
        }
        return wereAddressesSpentFrom.create(states);
    }

    private boolean wasAddressSpentFrom(Sha256Hash address) throws Exception {

        return false;
    }

    private int getParameterAsInt(Map<String, Object> request, String paramName) {
        validateParamExists(request, paramName);
        final int result;

        result = ((Double) request.get(paramName)).intValue();

        return result;
    }

    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size) {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        // validateTrytes(paramName, size, result);
        return result;
    }

    private void validateParamExists(Map<String, Object> request, String paramName) {
        if (!request.containsKey(paramName)) {
            throw new RuntimeException(invalidParams);
        }
    }

    private List<String> getParameterAsList(Map<String, Object> request, String paramName, int size) {
        validateParamExists(request, paramName);
        final List<String> paramList = (List<String>) request.get(paramName);

        if (size > 0) {
            // validate
            for (final String param : paramList) {
                // validateTrytes(paramName, size, param);
            }
        }

        return paramList;

    }

    public void storeTransactionStatement(final List<String> trys) throws Exception {

    }

    private HashSet<String> getParameterAsSet(Map<String, Object> request, String paramName, int size) {

        HashSet<String> result = getParameterAsList(request, paramName, size).stream()
                .collect(Collectors.toCollection(HashSet::new));

        return result;
    }

    private AbstractResponse getBalancesStatement(final List<String> addrss, final List<String> tips,
            final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Sha256Hash> addresses = addrss.stream().map(address -> (new Sha256Hash(address)))
                .collect(Collectors.toCollection(LinkedList::new));
        final List<Sha256Hash> hashes;
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

}
