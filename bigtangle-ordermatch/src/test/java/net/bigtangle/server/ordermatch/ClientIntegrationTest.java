/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ordermatch.ReqCmd;
import net.bigtangle.server.ordermatch.service.schedule.ScheduleOrderMatchService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);

    @Autowired
    private ScheduleOrderMatchService scheduleOrderMatchService;

    @Test
    public void saveOrder() throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", "111111111111111111111111111111111111111111111111111111");
        request.put("tokenid", "222222222222222222222222222222222222222222222222222222");
        request.put("type", 1);
        request.put("price", 1);
        request.put("amount", 100);
        request.put("validateto", simpleDateFormat.format(new Date()));
        request.put("validatefrom", simpleDateFormat.format(new Date()));

        String response = OkHttp3Util.post(contextRoot + ReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());
        logger.info("saveOrder resp : " + response);
        this.getOrders();
    }

    @Test
    public void getOrders() throws Exception {
        HashMap<String, Object> request = new HashMap<String, Object>();

        String response = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        logger.info("getOrders resp : " + response);
    }

}
