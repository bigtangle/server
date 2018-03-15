package com.bignetcoin.server;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.bitcoinj.core.Json;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bignetcoin.server.response.AbstractResponse;
import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.TransactionService;
import com.bignetcoin.server.service.WalletService0;
import com.bignetcoin.server.utils.HttpRequestParamUtil;

@RestController
@RequestMapping("/")
public class DispatcherController {
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private WalletService0 walletService0;
    
    @Autowired
    private BlockService blockService;

    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            logger.info("reqCmd : {}, reqHex : {}, started.", reqCmd, Utils.HEX.encode(bodyByte));
            ReqCmd reqCmd0000  = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {
            case getBalances: {
                    String body = new String(bodyByte, Charset.forName("UTF-8"));
                    @SuppressWarnings("unchecked") final Map<String, Object> request = Json.jsonmapper()
                            .readValue(body, Map.class);
                    final List<String> addresses = HttpRequestParamUtil.getParameterAsList(request, "addresses");
                    AbstractResponse response =  walletService0.getRealBalanceCoin(addresses);
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
    
    public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response) throws Exception {
        PrintWriter printWriter = httpServletResponse.getWriter();
        printWriter.append(Json.jsonmapper().writeValueAsString(response));
        printWriter.flush();
        printWriter.close();
    }

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
}
