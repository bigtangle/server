/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class UserdataController {

    @FXML
    public TableView<Map<String, Object>> wachtedTokenTableview;
    @FXML
    public TableColumn<Map<String, Object>, String> tokennameColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> tokenidColumn;
    @FXML
    public TableView<Map<String, Object>> linkmanTableview;
    @FXML
    public TableColumn<Map<String, Object>, String> linkmanColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> linkaddressColumn;

    @FXML
    public TextField dataclassTF;
    @FXML
    public TextField nameTF;
    @FXML
    public TextField addressTF;
    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveUserdata(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        try {
            HashMap<String, String> requestParam = new HashMap<String, String>();
            String resp000 = OkHttp3Util.postString(CONTEXT_ROOT + "getGenesisBlockLR",
                    Json.jsonmapper().writeValueAsString(requestParam));

            HashMap<String, Object> result000 = Json.jsonmapper().readValue(resp000, HashMap.class);
            String leftBlockHex = (String) result000.get("leftBlockHex");
            String rightBlockHex = (String) result000.get("rightBlockHex");

            Block r1 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(leftBlockHex));
            Block r2 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(rightBlockHex));
            long blocktype0 = NetworkParameters.BLOCKTYPE_USERDATA;
            Block block = new Block(Main.params, r1.getHash(), r2.getHash(), blocktype0,
                    Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

            Transaction coinbase = new Transaction(Main.params);
            Contact contact = new Contact();
            contact.setName(nameTF.getText());
            contact.setAddress(addressTF.getText());
            ContactInfo contactInfo = getUserdata();
            List<Contact> list = contactInfo.getContactList();
            list.add(contact);
            contactInfo.setContactList(list);

            coinbase.setDataclassname(DataClassName.USERDATA.name());
            byte[] buf1 = contactInfo.toByteArray();
            coinbase.setData(buf1);

            block.addTransaction(coinbase);
            block.solve();

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
            initContactTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public ContactInfo getUserdata() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        requestParam.put("pubKey", pubKeyTo.getPublicKeyAsHex());
        requestParam.put("dataclassname", DataClassName.USERDATA.name());
        byte[] bytes = OkHttp3Util.post(CONTEXT_ROOT + "getUserData",
                Json.jsonmapper().writeValueAsString(requestParam));
        ContactInfo contactInfo = new ContactInfo().parse(bytes);
        return contactInfo;

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void removeToken(ActionEvent event) {
    }

    public void removeLinkman(ActionEvent event) {
    }

    public void initTableView() throws Exception {
        initContactTableView();
    }

    public void initContactTableView() throws Exception {
        ContactInfo contactInfo = getUserdata();
        List<Contact> list = contactInfo.getContactList();
        ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();
        if (list != null && !list.isEmpty()) {
            for (Contact contact : list) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("name", contact.getName());
                map.put("address", contact.getAddress());
                allData.add(map);
            }
            linkmanTableview.setItems(allData);
        }

    }
}
