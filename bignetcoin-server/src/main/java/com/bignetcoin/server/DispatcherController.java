package com.bignetcoin.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
import com.bignetcoin.server.service.WalletService;

@RestController
@RequestMapping("/")
public class DispatcherController {
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private WalletService walletService;
    
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
