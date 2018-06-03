/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.ErrorResponse;
import net.bigtangle.server.response.GetBlockEvaluationsResponse;
import net.bigtangle.server.response.OkResponse;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.ExchangeService;
import net.bigtangle.server.service.MultiSignService;
import net.bigtangle.server.service.OrderPublishService;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.server.service.WalletService;

@RestController
@RequestMapping("/")
public class DispatcherController {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private TokensService tokensService;

    @Autowired
    private OrderPublishService orderPublishService;

    @Autowired
    private ExchangeService exchangeService;

    public static int numberOfEmptyBlocks = 3;

    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private MultiSignService multiSignService;

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            logger.info("reqCmd : {}, reqHex : {}, started.", reqCmd, Utils.HEX.encode(bodyByte));
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {

            case askTransaction: {
                byte[] data = transactionService.askTransaction().array();
                this.outPointBinaryArray(httpServletResponse, data);
            }
                break;

            case saveBlock: {
                blockService.saveBinaryArrayToBlock(bodyByte);
                brodcastBlock(bodyByte);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;

            case getOutputs: {
                String reqStr = new String(bodyByte, "UTF-8");
                List<String> keyStrHex000 = Json.jsonmapper().readValue(reqStr, List.class);
                Set<byte[]> pubKeyHashs = new HashSet<byte[]>();
                for (String keyStrHex : keyStrHex000) {
                    pubKeyHashs.add(Utils.HEX.decode(keyStrHex));
                }
                AbstractResponse response = walletService.getAccountOutputs(pubKeyHashs);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case createGenesisBlock: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                byte[] data = transactionService.createGenesisBlock(request);
                brodcastBlock(data);
                this.outPointBinaryArray(httpServletResponse, data);
            }
                break;

            case exchangeToken: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                logger.debug("exchangeToken, {}", request);
            }
                break;

            case exchangeInfo: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.exchangeService.getExchangeByOrderid(orderid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getTokens: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getTokensList((String) request.get("name"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokensNoMarket: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getTokensList();
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getMarkets: {
                // String reqStr = new String(bodyByte, "UTF-8");
                // Map<String, Object> request =
                // Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getMarketTokensList();
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokenById: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getTokenById((String) request.get("tokenid"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getAllEvaluations: {
                AbstractResponse response = GetBlockEvaluationsResponse.create(blockService.getAllBlockEvaluations());
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case outputsWiteToken: {
                ByteBuffer byteBuffer = ByteBuffer.wrap(bodyByte);
                byte[] pubKey = new byte[byteBuffer.getInt()];
                byteBuffer.put(pubKey);
                byte[] tokenid = new byte[byteBuffer.getInt()];
                byteBuffer.put(tokenid);
                AbstractResponse response = walletService.getAccountOutputsWithToken(pubKey, tokenid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case saveOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = orderPublishService.saveOrderPublish(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getOrders: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = orderPublishService.getOrderPublishListWithCondition(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case batchGetBalances: {
                String reqStr = new String(bodyByte, "UTF-8");
                List<String> keyStrHex000 = Json.jsonmapper().readValue(reqStr, List.class);
                Set<byte[]> pubKeyHashs = new HashSet<byte[]>();
                for (String keyStrHex : keyStrHex000) {
                    pubKeyHashs.add(Utils.HEX.decode(keyStrHex));
                }
                AbstractResponse response = walletService.getAccountBalanceInfo(pubKeyHashs);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case saveExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.saveExchange(request);

                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String address = (String) request.get("address");
                AbstractResponse response = exchangeService.getExchangeListWithAddress(address);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case signTransaction: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.signTransaction(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case searchBlock: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = this.blockService.searchBlock(request);
                this.outPrintJSONString(httpServletResponse, response);
            }

            case getBlock: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                if (request.get("hashHex") != null) {
                    Block block = this.blockService.getBlock(Sha256Hash.wrap(request.get("hashHex").toString()));
                    this.outPointBinaryArray(httpServletResponse, block.bitcoinSerialize());
                }
            }
            case streamBlocks: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                if (request.get("heightstart") != null) {
                    this.transactionService.streamBlocks(Long.valueOf((String) request.get("heightstart")),
                            (String) request.get("kafka"));
                    this.outPrintJSONString(httpServletResponse, OkResponse.create());
                }
            }
                break;

            case getMultiSignWithAddress: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String address = (String) request.get("address");
                AbstractResponse response = this.multiSignService.getMultiSignListWithAddress(address);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getCountSign: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                long tokenindex = Long.parseLong(request.get("tokenindex") + "");
                int sign = Integer.parseInt(request.get("sign") + "");
                AbstractResponse response = this.multiSignService.getCountMultiSign(tokenid, tokenindex, sign);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getMultiSignWithTokenid: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                Boolean isSign = (Boolean) request.get("isSign");
                AbstractResponse response = this.multiSignService.getMultiSignListWithTokenid(tokenid,
                        (List<String>) request.get("addresses"), isSign == null ? false : isSign);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case multiSign: {
                Block block = networkParameters.getDefaultSerializer().makeBlock(bodyByte);
                this.multiSignService.multiSign(block);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
            case getTokenSerials: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getTokenSerialListById((String) request.get("tokenid"),
                        (List<String>) request.get("addresses"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getGenesisBlockLR: {
                AbstractResponse response = this.blockService.getGenesisBlockLR();
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getCalTokenIndex: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                AbstractResponse response = this.multiSignService.getNextTokenSerialIndex(tokenid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case updateTokenInfo: {
                Block block = networkParameters.getDefaultSerializer().makeBlock(bodyByte);
                this.tokensService.updateTokenInfo(block);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
            }
        } catch (BlockStoreException e) {
            // e.printStackTrace();
            logger.error("reqCmd : {}, reqHex : {}, block store ex.", reqCmd, Utils.HEX.encode(bodyByte));
            AbstractResponse resp = ErrorResponse.create(101);
            resp.setErrorcode(101);
            resp.setMessage(e.getLocalizedMessage());
            this.outPrintJSONString(httpServletResponse, resp);
        } catch (Throwable exception) {
            logger.error("", exception);
            logger.error("reqCmd : {}, reqHex : {}, error.", reqCmd, Utils.HEX.encode(bodyByte));
            AbstractResponse resp = ErrorResponse.create(100);
            resp.setMessage(exception.getLocalizedMessage());
            this.outPrintJSONString(httpServletResponse, resp);
        }
    }

    public void outPointBinaryArray(HttpServletResponse httpServletResponse, byte[] data) throws Exception {
        ServletOutputStream servletOutputStream = httpServletResponse.getOutputStream();
        servletOutputStream.write(data);
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response)
            throws Exception {
        PrintWriter printWriter = httpServletResponse.getWriter();
        printWriter.append(Json.jsonmapper().writeValueAsString(response));
        printWriter.flush();
        printWriter.close();
    }

    @Autowired
    private NetworkParameters networkParameters;

    public void brodcastBlock(byte[] data) {
        try {
            if ("".equalsIgnoreCase(kafkaConfiguration.getBootstrapServers()))
                return;
            KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
            kafkaMessageProducer.sendMessage(data);
        } catch (InterruptedException | ExecutionException e) {
            Log.warn("", e);
        }
    }

}
