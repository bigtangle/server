/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.ErrorResponse;
import net.bigtangle.core.http.OkResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.LogResultService;
import net.bigtangle.server.service.MultiSignService;
import net.bigtangle.server.service.PayMultiSignService;
import net.bigtangle.server.service.SettingService;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.server.service.UserDataService;
import net.bigtangle.server.service.VOSExecuteService;
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

    public static int numberOfEmptyBlocks = 3;

    @Autowired
    private MultiSignService multiSignService;
    
    @Autowired
    private PayMultiSignService payMultiSignService;
    
    @Autowired
    private VOSExecuteService vosExecuteService;
    
    @Autowired
    private SettingService settingService;
    
    @Autowired
    private LogResultService logResultService;

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte, 
            HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {
        try {
            logger.info("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),  bodyByte.length);
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {

            case getTip: {
                byte[] data = transactionService.askTransaction().array();
                this.outPointBinaryArray(httpServletResponse, data);
            }   
                break;

            case saveBlock: {
                blockService.saveBinaryArrayToBlock(bodyByte);
            
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
  
//            case exchangeToken: {
//                String reqStr = new String(bodyByte, "UTF-8");
//                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
//                logger.debug("exchangeToken, {}", request);
//            }
//                break;

            case getTokens: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = tokensService.getTokensList((String) request.get("name"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokensNoMarket: {
                AbstractResponse response = tokensService.getTokensList();
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getMarkets: {
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
//            case getAllEvaluations: {
//                AbstractResponse response = GetBlockEvaluationsResponse.create(blockService.getAllBlockEvaluations());
//                this.outPrintJSONString(httpServletResponse, response);
//            }
//                break;
            case getBalances: {
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
                this.multiSignService.multiSign(block,true);
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
                
            case getUserData: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String dataclassname = (String) request.get("dataclassname");
                String pubKey = (String) request.get("pubKey");
                byte[] buf = this.userDataService.getUserData(dataclassname, pubKey);
                this.outPointBinaryArray(httpServletResponse, buf);
            }
                break;
                
            case userDataList: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                int blocktype = (int) request.get("blocktype");
                List<String> pubKeyList = (List<String>) request.get("pubKeyList");
                AbstractResponse response = this.userDataService.getUserDataList(blocktype, pubKeyList);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case launchPayMultiSign: {
                this.payMultiSignService.launchPayMultiSign(bodyByte);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
                
            case payMultiSign: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = this.payMultiSignService.payMultiSign(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case getPayMultiSignList: {
                String reqStr = new String(bodyByte, "UTF-8");
                List<String> keyStrHex000 = Json.jsonmapper().readValue(reqStr, List.class);
                AbstractResponse response = this.payMultiSignService.getPayMultiSignList(keyStrHex000);
                this.outPrintJSONString(httpServletResponse, response);
                break;
            }
            case getPayMultiSignAddressList: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.payMultiSignService.getPayMultiSignAddressList(orderid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case payMultiSignDetails: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.payMultiSignService.getPayMultiSignDetails(orderid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case getOutputWithKey: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String hexStr = (String) request.get("hexStr");
                AbstractResponse response = walletService.getOutputsWithHexStr(hexStr);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case getVOSExecuteList: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String vosKey = (String) request.get("vosKey");
                AbstractResponse response = vosExecuteService.getVOSExecuteList(vosKey);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case version: {
                AbstractResponse response = settingService.clientVersion();
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case submitLogResult: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                logResultService.submitLogResult(request);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
            }
        } catch (BlockStoreException e) {
            logger.error("", e);
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
        httpServletResponse.setCharacterEncoding("UTF-8");
//        ServletOutputStream servletOutputStream = httpServletResponse.getOutputStream();
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("dataHex", Utils.HEX.encode(data));
        /*servletOutputStream.write(data);
        servletOutputStream.flush();
        servletOutputStream.close();*/
        PrintWriter printWriter = httpServletResponse.getWriter();
        printWriter.append(Json.jsonmapper().writeValueAsString(result));
        printWriter.flush();
        printWriter.close();
    }

    public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response)
            throws Exception {
        httpServletResponse.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = httpServletResponse.getWriter();
        printWriter.append(Json.jsonmapper().writeValueAsString(response));
        printWriter.flush();
        printWriter.close();
    }

    @Autowired
    private NetworkParameters networkParameters;
    
    @Autowired
    private UserDataService userDataService;

  
}
