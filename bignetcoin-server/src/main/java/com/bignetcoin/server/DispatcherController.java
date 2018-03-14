package com.bignetcoin.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.bitcoinj.core.Json;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bignetcoin.server.utils.HttpRequestParamUtil;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@RestController
@RequestMapping("/")
public class DispatcherController {

    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            ByteBuffer resp = process(ReqCmd.valueOf(reqCmd), bodyByte);
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

    private ByteBuffer process(ReqCmd reqCmd, byte[] bodyByte) throws Exception {
        switch (reqCmd) {
        case getBalances:
            String body = new String(bodyByte, Charset.forName("UTF-8"));
            @SuppressWarnings("unchecked") final Map<String, Object> request = Json.jsonmapper().readValue(body, Map.class);
            final List<String> addresses = HttpRequestParamUtil.getParameterAsList(request, "addresses");
//            return getBalancesStatement(addresses);
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
