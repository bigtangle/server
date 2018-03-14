package com.bignetcoin.server;

import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class DispatcherController {

    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            ByteBuffer resp = process(ReqCmd.valueOf(reqCmd), ByteBuffer.wrap(bodyByte));
            byte[] array = resp.array();
            ServletOutputStream servletOutputStream = httpServletResponse.getOutputStream();
            servletOutputStream.write(array);
            servletOutputStream.flush();
            servletOutputStream.close();
            logger.info("reqCmd : {}, reqHex : {}, respHex : {}", reqCmd, Utils.HEX.encode(bodyByte),
                    Utils.HEX.encode(array));
        } catch (Exception exception) {
            logger.error("reqCmd : {}, reqHex : {}", reqCmd, Utils.HEX.encode(bodyByte), exception);
        }
    }

    private ByteBuffer process(ReqCmd reqCmd, ByteBuffer byteBuffer) {
        switch (reqCmd) {
        case getBalances:
            System.out.println(byteBuffer.getInt());
            System.out.println(byteBuffer.getInt());
            System.out.println(byteBuffer.getShort());
            break;

        case askTransaction:

            break;

        case saveBlock:

            break;
        }
        ByteBuffer resp = ByteBuffer.allocate(0);
        return resp;
    }

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
}
