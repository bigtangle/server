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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bignetcoin.server.config.ServerConfiguration;
import com.bignetcoin.server.response.AbstractResponse;
import com.bignetcoin.server.response.AskTransactionResponse;
import com.bignetcoin.server.response.ErrorResponse;
import com.bignetcoin.server.response.ExceptionResponse;
import com.bignetcoin.server.response.GetBalancesResponse;

@RestController
@RequestMapping("/")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    private final static int HASH_SIZE = 81;

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
    public AbstractResponse process(@RequestBody byte[] bodyByte) throws UnsupportedEncodingException {
        String body = new String(bodyByte, Charset.forName("UTF-8"));
        log.debug(" requestString ", body);

        return process(body);

    }

    private AbstractResponse process(final String requestString) throws UnsupportedEncodingException {

        try {

            final Map<String, Object> request = Json.jsonmapper().readValue(requestString, Map.class);
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
            case "askTransaction": {
                final String pubkey = (String) request.get("pubkey");
                final String toaddressPubkey = (String) request.get("toaddressPubkey");
                final String amount = (String) request.get("amount");
                final long tokenid = getParameterAsInt(request, "tokenid");
                return askTransaction(pubkey, toaddressPubkey, amount, tokenid);
            }
            case "askTransaction4address": {
                final String pubkey = (String) request.get("pubkey");
                final String toaddressPubkey = (String) request.get("toaddress");
                final String amount = (String) request.get("amount");
                final long tokenid = getParameterAsInt(request, "tokenid");
                return askTransaction4address(pubkey, toaddressPubkey, amount, tokenid);
            }
            case "saveBlock": {
                final String blockString = (String) request.get("blockString");
                List<String> list = new ArrayList<String>();
                list.add(String.valueOf(transactionService.getBlock2save(blockString)));
                return GetBalancesResponse.create(list, null, 0);

            }
            case "signBlock": {
                final String blockString = (String) request.get("blockString");

                Block block = transactionService.getBlock2sign(blockString);
                block.solve();
                for (Transaction t : block.getTransactions()) {
                    t.addSigned(new ECKey());
                }
                List<String> list = new ArrayList<String>();
                list.add(blockString);
                return GetBalancesResponse.create(list, null, 0);

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

    private int getParameterAsInt(Map<String, Object> request, String paramName) {
        validateParamExists(request, paramName);
        final int result;

        result = ((Integer) request.get(paramName)).intValue();

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

    private AbstractResponse getBalancesStatement(final List<String> addrss, final List<String> tips,
            final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final Map<String, Coin> balances = new HashMap<>();

        for (final String address : addrss) {
            List<byte[]> l = new ArrayList<byte[]>();
            log.debug("addr:" + address);
            byte[] bytes = HEX.decode(address);
            ECKey key = ECKey.fromPublicOnly(bytes);
            log.debug("bytes:" + key.getPubKeyHash().length);
            l.add(key.getPubKeyHash());
            Coin value = transactionService.getBalance(BalanceType.ESTIMATED, l);

            balances.put(address, value);
        }

        final List<String> elements = addrss.stream().map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, null, 0);
    }

    @Autowired
    private NetworkParameters networkParameters;

    private AbstractResponse askTransaction(String pubkey, String toaddressPubkey, String amount, long tokenid)
            throws Exception {
        HashMap<String, Block> result = transactionService.askTransaction(pubkey, toaddressPubkey, amount, tokenid);
        return AskTransactionResponse.create(result);
    }

    private AbstractResponse askTransaction4address(String pubkey, String toaddress, String amount, long tokenid)
            throws Exception {
        Block block = transactionService.askTransaction4address(pubkey, toaddress, amount, tokenid);

        // Block block00 = (Block)
        // networkParameters.getDefaultSerializer().deserialize(ByteBuffer.wrap(block.bitcoinSerialize()));
        // Block b =
        // networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());

        return AskTransactionResponse.create(block);
    }

}
