/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import net.bigtangle.server.service.OrderService;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.server.service.WalletService;

@RestController
@RequestMapping("/")
public class DispatcherController {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private TokensService tokensService;
    
    @Autowired
    private OrderService orderService;

    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            logger.info("reqCmd : {}, reqHex : {}, started.", reqCmd, Utils.HEX.encode(bodyByte));
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {
            case getBalances: {
                List<byte[]> pubKeyHashs = new ArrayList<byte[]>();
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
                this.outPrintJSONString(httpServletResponse, AbstractResponse.createEmptyResponse());
            }
                break;

            case getOutputs: {
                List<byte[]> pubKeyHashs = new ArrayList<byte[]>();
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
                byte[] pubKey =  new byte[byteBuffer.getInt()];
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
                AbstractResponse response = orderService.saveOrder(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case getOrders: {
                // tokenid
                // state
                // address
                String reqStr = new String(bodyByte, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = orderService.getOrderList(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            }
        } catch (Exception exception) {
            logger.error("reqCmd : {}, reqHex : {}, error.", reqCmd, Utils.HEX.encode(bodyByte), exception);
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

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
}
