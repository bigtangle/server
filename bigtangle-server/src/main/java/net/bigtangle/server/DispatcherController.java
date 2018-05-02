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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetBlockEvaluationsResponse;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.ExchangeService;
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

    // @Autowired private KafkaMessageProducer kafkaMessageProducer;
    
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            logger.info("reqCmd : {}, reqHex : {}, started.", reqCmd, Utils.HEX.encode(bodyByte));
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {
            case getBalances: {
                Set<byte[]> pubKeyHashs = new HashSet<byte[]>();
                pubKeyHashs.add(bodyByte);
                AbstractResponse response = walletService.getAccountBalanceInfo(pubKeyHashs);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case askTransaction: {
                byte[] data = transactionService.askTransaction().array();
                this.outPointBinaryArray(httpServletResponse, data);
            }
                break;

            case saveBlock: {
                blockService.saveBinaryArrayToBlock(bodyByte);
                saveEmptyBlock(numberOfEmptyBlocks);
                this.outPrintJSONString(httpServletResponse, AbstractResponse.createEmptyResponse());
            }
                break;

            case getOutputs: {
                Set<byte[]> pubKeyHashs = new HashSet<byte[]>();
                pubKeyHashs.add(bodyByte);
                AbstractResponse response = walletService.getAccountOutputs(pubKeyHashs);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case createGenesisBlock: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                byte[] data = transactionService.createGenesisBlock(request);
                saveEmptyBlock(numberOfEmptyBlocks);
                this.outPointBinaryArray(httpServletResponse, data);
            }
                break;

            case exchangeToken: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                logger.debug("exchangeToken, {}", request);
            }
                break;

            case exchangeInfo: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.exchangeService.getExchangeByOrderid(orderid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getTokens: {
                AbstractResponse response = tokensService.getTokensList();
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
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = orderPublishService.saveOrderPublish(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getOrders: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = orderPublishService.getOrderPublishListWithCondition(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case batchGetBalances: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
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
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.saveExchange(request);
                saveEmptyBlock(numberOfEmptyBlocks);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String address = (String) request.get("address");
                AbstractResponse response = exchangeService.getExchangeListWithAddress(address);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case signTransaction: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.signTransaction(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case searchBlock: {
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = this.blockService.searchBlock(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            }
        } catch (Exception exception) {
            logger.error("reqCmd : {}, reqHex : {}, error.", reqCmd, Utils.HEX.encode(bodyByte), exception);
            AbstractResponse resp = AbstractResponse.createEmptyResponse();
            resp.setDuration(100);
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

    public void saveEmptyBlock(int number) {
       // transactionService. saveEmptyBlockTask(number);
    }
   

   
    
}
