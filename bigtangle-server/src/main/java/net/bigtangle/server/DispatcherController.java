/*******************************************************************************
 *  
 *  Copyright   2018  Inasset GmbH. 
 *******************************************************************************/
package net.bigtangle.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.ErrorResponse;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.OkResponse;
import net.bigtangle.core.response.PermissionedAddressesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.AccessPermissionedService;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.ExchangeService;
import net.bigtangle.server.service.MultiSignService;
import net.bigtangle.server.service.OrderTickerService;
import net.bigtangle.server.service.OrderdataService;
import net.bigtangle.server.service.OutputService;
import net.bigtangle.server.service.PayMultiSignService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.SubtanglePermissionService;
import net.bigtangle.server.service.TokenDomainnameService;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.server.service.UserDataService;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Gzip;

@RestController
@RequestMapping("/")
public class DispatcherController {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

    @Autowired
    private NetworkParameters networkParameters;
    @Autowired
    private UserDataService userDataService;
    @Autowired
    private OutputService walletService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private TokensService tokensService;
    @Autowired
    private MultiSignService multiSignService;
    @Autowired
    private PayMultiSignService payMultiSignService;

    @Autowired
    private SubtanglePermissionService subtanglePermissionService;
    @Autowired
    private OrderdataService orderdataService;
    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    private OrderTickerService orderTickerService;
    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private TokenDomainnameService tokenDomainnameService;
    @Autowired
    private ExchangeService exchangeService;
    @Autowired
    private RewardService rewardService;
    @Autowired
    private AccessPermissionedService accessPermissionedService;

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] contentBytes,
            HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {
        byte[] bodyByte = new byte[0];
        try {

            logger.trace("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
                    contentBytes.length);

            bodyByte = Gzip.decompress(contentBytes);
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            if (serverConfiguration.getPermissioned())
                checkPermission(httpServletResponse, httprequest);
            checkReady(httpServletResponse, httprequest);
            switch (reqCmd0000) {
            case getTip: {
                Block rollingBlock = blockService.getBlockPrototype();
                register(rollingBlock);
                byte[] data = rollingBlock.bitcoinSerialize();
                this.outPointBinaryArray(httpServletResponse, data);
            }
                break;
            case saveBlock: {
                saveBlock(bodyByte, httpServletResponse);
            }
                break;
            case batchBlock: {
                batchBlock(bodyByte, httpServletResponse);
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
            case getOutputsHistory: {
                outputHistory(bodyByte, httpServletResponse);
            }
                break;
            case outputsOfTokenid: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = walletService.getOpenAllOutputsResponse((String) request.get("tokenid"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case searchTokens: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                GetTokensResponse response = tokensService.searchTokens((String) request.get("name"));
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getOTCMarkets: {
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
            case findBlockEvaluation: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = this.blockService.searchBlock(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case searchBlockByBlockHashs: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = this.blockService.searchBlockByBlockHashs(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case getBlockByHash: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                if (request.get("hashHex") != null) {
                    Block block = this.blockService.getBlock(Sha256Hash.wrap(request.get("hashHex").toString()));
                    if (block != null)
                        this.outPointBinaryArray(httpServletResponse, block.bitcoinSerialize());
                } else {
                    throw new NoBlockException();
                }
            }
                break;
            case adjustHeight: {
                Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bodyByte);
                this.blockService.adjustHeightRequiredBlocks(block);
                this.outPointBinaryArray(httpServletResponse, block.bitcoinSerialize());
            }
                break;

            case blocksFromChainLength: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                GetBlockListResponse response = this.blockService.blocksFromChainLength(
                        Long.valueOf((String) request.get("start")), Long.valueOf((String) request.get("end")));

                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokenSignByAddress: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String address = (String) request.get("address");
                String tokenid = (String) request.get("tokenid");
                AbstractResponse response = this.multiSignService.getMultiSignListWithAddress(tokenid, address);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokenSigns: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                long tokenindex = Long.parseLong(request.get("tokenindex") + "");
                int sign = Integer.parseInt(request.get("sign") + "");
                AbstractResponse response = this.multiSignService.getCountMultiSign(tokenid, tokenindex, sign);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokenSignByTokenid: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                Boolean isSign = (Boolean) request.get("isSign");
                AbstractResponse response = this.multiSignService.getMultiSignListWithTokenid(tokenid,
                        (List<String>) request.get("addresses"), isSign == null ? false : isSign);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case signToken: {
                Block block = networkParameters.getDefaultSerializer().makeBlock(bodyByte);
                this.multiSignService.signTokenAndSaveBlock(block, false);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
            case signOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.signTransaction(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getTokenIndex: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String tokenid = (String) request.get("tokenid");
                AbstractResponse response = this.multiSignService.getNextTokenSerialIndex(tokenid);
                this.outPrintJSONString(httpServletResponse, response);
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
            }
                break;
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
            case getOutputByKey: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String hexStr = (String) request.get("hexStr");
                AbstractResponse response = walletService.getOutputsWithHexStr(hexStr);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            case regSubtangle: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String pubkey = (String) request.get("pubkey");
                String signHex = (String) request.get("signHex");
                boolean flag = subtanglePermissionService.savePubkey(pubkey, signHex);
                if (flag) {
                    this.outPrintJSONString(httpServletResponse, OkResponse.create());
                } else {
                    this.outPrintJSONString(httpServletResponse, ErrorResponse.create(0));
                }
            }
                break;
            case updateSubtangle: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String pubkey = (String) request.get("pubkey");
                String userdataPubkey = (String) request.get("userdataPubkey");
                String status = (String) request.get("status");
                subtanglePermissionService.updateSubtanglePermission(pubkey, "", userdataPubkey, status);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
                break;
            case getOrders: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String spentStr = (String) request.get("spent");
                String address = (String) request.get("address");
                String tokenid = (String) request.get("tokenid");
                boolean spent = false;
                if (spentStr != null && spentStr.equals("true"))
                    spent = true;
                List<String> addresses = (List<String>) request.get("addresses");
                AbstractResponse response = orderdataService.getOrderdataList(spent, address, addresses, tokenid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getOrdersTicker: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                Long startDate = (Long) request.get("startDate");
                Long endDate = (Long) request.get("endDate");
                Integer count = (Integer) request.get("count");
                Set<String> tokenids = new HashSet<String>((List<String>) request.get("tokenids"));
                if (count != null) {
                    AbstractResponse response = orderTickerService.getLastMatchingEvents(tokenids, count);
                    this.outPrintJSONString(httpServletResponse, response);
                } else if (startDate == null || endDate == null) {
                    AbstractResponse response = orderTickerService.getLastMatchingEvents(tokenids);
                    this.outPrintJSONString(httpServletResponse, response);
                } else {
                    AbstractResponse response = orderTickerService.getTimeBetweenMatchingEvents(tokenids,
                            startDate / 1000, endDate / 1000);
                    this.outPrintJSONString(httpServletResponse, response);
                }
            }
                break;
            case getTokenPermissionedAddresses: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                final String domainNameBlockHash = (String) request.get("domainNameBlockHash");
                PermissionedAddressesResponse response = this.tokenDomainnameService
                        .queryDomainnameTokenPermissionedAddresses(domainNameBlockHash);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getDomainNameBlockHash: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                final String domainname = (String) request.get("domainname");
                final String token = (String) request.get("token");
                if (token == null || "".equals(token)) {
                    this.outPrintJSONString(httpServletResponse,
                            this.tokenDomainnameService.queryParentDomainnameBlockHash(domainname));
                } else {
                    this.outPrintJSONString(httpServletResponse,
                            this.tokenDomainnameService.queryDomainnameBlockHash(domainname));
                }

            }
                break;

            case getExchangeByOrderid: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.exchangeService.getExchangeByOrderid(orderid);
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
            case deleteExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = exchangeService.deleteExchange(request);

                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getMaxConfirmedReward: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = rewardService.getMaxConfirmedReward(request);

                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getBatchExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                List<String> address = Json.jsonmapper().readValue(reqStr, List.class);
                AbstractResponse response = exchangeService.getBatchExchangeListByAddressListA(address);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
            case getAllConfirmedReward: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                AbstractResponse response = rewardService.getAllConfirmedReward(request);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case getSessionRandomNum: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String pubKey = (String) request.get("pubKey");
                AbstractResponse response = this.accessPermissionedService.getSessionRandomNumResp(pubKey);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;

            default:
                break;
            }
        } catch (BlockStoreException e) {
            logger.info("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
                    bodyByte.length);
            logger.error("", e);
            AbstractResponse resp = ErrorResponse.create(101);
            resp.setErrorcode(101);
            resp.setMessage(e.getLocalizedMessage());
            this.outPrintJSONString(httpServletResponse, resp);
        } catch (NoBlockException e) {
            logger.info("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
                    bodyByte.length);
            logger.error("", e);
            AbstractResponse resp = ErrorResponse.create(404);
            resp.setErrorcode(404);
            resp.setMessage(e.getLocalizedMessage());
            this.outPrintJSONString(httpServletResponse, resp);
        } catch (Throwable exception) {
            logger.info("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
                    bodyByte.length);
            logger.error("", exception);
            logger.error("reqCmd : {}, reqHex : {}, error.", reqCmd, Utils.HEX.encode(bodyByte));
            AbstractResponse resp = ErrorResponse.create(100);
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));

            resp.setMessage(sw.toString());
            this.outPrintJSONString(httpServletResponse, resp);
        }
    }

    private void outputHistory(byte[] bodyByte, HttpServletResponse httpServletResponse)
            throws UnsupportedEncodingException, IOException, JsonParseException, JsonMappingException, Exception {
        String reqStr = new String(bodyByte, "UTF-8");
        @SuppressWarnings("unchecked")
        Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
        String fromaddress = request.get("fromaddress") == null ? "" : request.get("fromaddress").toString();
        String toaddress = request.get("toaddress") == null ? "" : request.get("toaddress").toString();
        Long starttime = request.get("starttime") == null ? null : Long.valueOf(request.get("starttime").toString());
        Long endtime = request.get("endtime") == null ? null : Long.valueOf(request.get("endtime").toString());
        AbstractResponse response = walletService.getOutputsHistory(fromaddress, toaddress, starttime, endtime);
        this.outPrintJSONString(httpServletResponse, response);
    }

    private void batchBlock(byte[] bodyByte, HttpServletResponse httpServletResponse)
            throws BlockStoreException, Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bodyByte);
        if (serverConfiguration.getMyserverblockOnly()) {
            if (!blockService.existMyserverblocks(block.getPrevBlockHash())) {
                AbstractResponse resp = ErrorResponse.create(101);
                resp.setErrorcode(403);
                resp.setMessage("server accept only his tip selection for validation");
                this.outPrintJSONString(httpServletResponse, resp);
            } else {
                blockService.batchBlock(block);
                deleteRegisterBlock(block);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
        } else {
            blockService.batchBlock(block);
            deleteRegisterBlock(block);
            this.outPrintJSONString(httpServletResponse, OkResponse.create());
        }
    }

    private void saveBlock(byte[] bodyByte, HttpServletResponse httpServletResponse)
            throws BlockStoreException, Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bodyByte);
        if (serverConfiguration.getMyserverblockOnly()) {
            if (!blockService.existMyserverblocks(block.getPrevBlockHash())) {
                AbstractResponse resp = ErrorResponse.create(101);
                resp.setErrorcode(403);
                resp.setMessage("server accept only his tip selection for validation");
                this.outPrintJSONString(httpServletResponse, resp);
            } else {
                blockService.saveBlock(block);
                deleteRegisterBlock(block);
                this.outPrintJSONString(httpServletResponse, OkResponse.create());
            }
        } else {
            blockService.saveBlock(block);
            deleteRegisterBlock(block);
            this.outPrintJSONString(httpServletResponse, OkResponse.create());
        }
    }

    private void checkPermission(HttpServletResponse httpServletResponse, HttpServletRequest httprequest)
            throws BlockStoreException, Exception {
        if (httprequest.getRequestURI().endsWith("getSessionRandomNum")) {
            return;
        }
        if (serverConfiguration.getPermissioned()) {
            if (!checkAuth(httpServletResponse, httprequest)) {
                AbstractResponse resp = ErrorResponse.create(100);
                resp.setMessage("no auth");
                this.outPrintJSONString(httpServletResponse, resp);
                return;
            }
        }
    }

    private void checkReady(HttpServletResponse httpServletResponse, HttpServletRequest httprequest)
            throws BlockStoreException, Exception {
        if (!serverConfiguration.checkService()) {

            AbstractResponse resp = ErrorResponse.create(103);
            resp.setMessage("service is not ready.");
            this.outPrintJSONString(httpServletResponse, resp);
            return;

        }
    }

    public boolean checkAuth(HttpServletResponse httpServletResponse, HttpServletRequest httprequest) {
        String header = httprequest.getHeader("accessToken");
        boolean flag = false;
        if (header != null && !header.trim().isEmpty()) {
            HttpSession session = httprequest.getSession(true);
            if ("key_verified".equals(session.getAttribute("key_verify_flag"))) {
                flag = true;
                return flag;
            }
            String pubkey = header.split(",")[0];
            String signHex = header.split(",")[1];
            String accessToken = header.split(",")[2];
            ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));

            byte[] buf = Utils.HEX.decode(accessToken);
            byte[] signature = Utils.HEX.decode(signHex);
            flag = key.verify(buf, signature);

            if (flag) {
                int count = this.accessPermissionedService.checkSessionRandomNumResp(pubkey, accessToken);
                flag = count > 0;
            }
            if (flag) {
                HttpSession a = httprequest.getSession(true);
                if (a != null) {
                    a.setAttribute("key_verify_flag", "key_verified");
                }
            }
        }
        return flag;
    }

    public void gzipBinary(HttpServletResponse httpServletResponse, AbstractResponse response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void outPointBinaryArray(HttpServletResponse httpServletResponse, byte[] data) throws Exception {
        httpServletResponse.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("dataHex", Utils.HEX.encode(data));
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(result));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response)
            throws Exception {
        gzipBinary(httpServletResponse, response);
    }

    // server may accept only block from his server
    public void register(Block block) throws BlockStoreException {
        if (serverConfiguration.getMyserverblockOnly())
            blockService.insertMyserverblocks(block.getPrevBlockHash(), block.getHash(), System.currentTimeMillis());
    }

    public void deleteRegisterBlock(Block block) throws BlockStoreException {
        if (serverConfiguration.getMyserverblockOnly()) {
            blockService.deleteMyserverblocks(block.getPrevBlockHash());
        }
    }

}
