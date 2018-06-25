/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch;

import java.io.PrintWriter;
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
import net.bigtangle.server.ordermatch.service.ExchangeService;
import net.bigtangle.server.ordermatch.service.OrderPublishService;
import net.bigtangle.server.ordermatch.service.response.AbstractResponse;

@RestController
@RequestMapping("/")
public class DispatcherController {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

    @Autowired
    private OrderPublishService orderPublishService;

    @Autowired
    private ExchangeService exchangeService;

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] bodyByte,
            HttpServletResponse httpServletResponse) throws Exception {
        try {
            logger.info("reqCmd : {}, reqHex : {}, started.", reqCmd, Utils.HEX.encode(bodyByte));
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);
            switch (reqCmd0000) {
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
                
            case getBatchExchange: {
                String reqStr = new String(bodyByte, "UTF-8");
                List<String> address = Json.jsonmapper().readValue(reqStr, List.class);
                AbstractResponse response = exchangeService.getBatchExchangeListByAddressList(address);
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
                
            case exchangeInfo: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                AbstractResponse response = this.exchangeService.getExchangeByOrderid(orderid);
                this.outPrintJSONString(httpServletResponse, response);
            }
                break;
                
            case cancelOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String orderid = (String) request.get("orderid");
                this.exchangeService.cancelOrderSign(orderid);
                AbstractResponse response = AbstractResponse.createEmptyResponse();
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
}
