/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.Serializable;
import java.util.ArrayList;
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
import javafx.scene.control.cell.MapValueFactory;
import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class UserdataController {

    @FXML
    public TableView<Map<String, Object>> myTokenTableview;
    @FXML
    public TableColumn<Map<String, Object>, String> mytokennameColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> mytokenidColumn;

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
            addContact(CONTEXT_ROOT);
            initContactTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public Serializable getUserdata(String type) throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        requestParam.put("pubKey", pubKeyTo.getPublicKeyAsHex());
        requestParam.put("dataclassname", type);
        byte[] bytes = OkHttp3Util.post(CONTEXT_ROOT + "getUserData",
                Json.jsonmapper().writeValueAsString(requestParam));
        if (DataClassName.ContactInfo.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new ContactInfo();
            }
            ContactInfo contactInfo = new ContactInfo().parse(bytes);
            return contactInfo;
        } else if (DataClassName.TOKEN.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new TokenInfo();
            }
            TokenInfo tokenInfo = new TokenInfo().parse(bytes);
            return tokenInfo;
        } else {
            return null;
        }

    }

    public void addContact(String contextRoot) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlocktype(NetworkParameters.BLOCKTYPE_USERDATA);
        ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();

        Transaction coinbase = new Transaction(Main.params);
        Contact contact = new Contact();
        contact.setName(nameTF.getText());
        contact.setAddress(addressTF.getText());
        ContactInfo contactInfo = (ContactInfo) getUserdata(DataClassName.ContactInfo.name());

        List<Contact> list = contactInfo.getContactList();
        list.add(contact);
        contactInfo.setContactList(list);

        coinbase.setDataclassname(DataClassName.ContactInfo.name());
        coinbase.setData(contactInfo.toByteArray());

        Sha256Hash sighash = coinbase.getHash();
        ECKey.ECDSASignature party1Signature = pubKeyTo.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(pubKeyTo.toAddress(Main.params).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(pubKeyTo.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        coinbase.setDatasignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(coinbase);
        block.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", block.bitcoinSerialize());
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void removeToken(ActionEvent event) {
    }

    public void removeLinkman(ActionEvent event) {
        try {
            Map<String, Object> rowdata = linkmanTableview.getSelectionModel().getSelectedItem();
            if (rowdata == null) {
                return;
            }
            String name = (String) rowdata.get("name");
            String address = (String) rowdata.get("address");
            String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            HashMap<String, String> requestParam = new HashMap<String, String>();

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));

            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            block.setBlocktype(NetworkParameters.BLOCKTYPE_USERDATA);

            Transaction coinbase = new Transaction(Main.params);

            ContactInfo contactInfo = (ContactInfo) getUserdata(DataClassName.ContactInfo.name());
            List<Contact> list = contactInfo.getContactList();
            List<Contact> tempList = new ArrayList<Contact>();
            for (Contact contact : list) {
                if (name.trim().equals(contact.getName().trim())
                        && address.trim().equals(contact.getAddress().trim())) {
                    continue;
                }
                tempList.add(contact);
            }
            contactInfo.setContactList(tempList);

            coinbase.setDataclassname(DataClassName.ContactInfo.name());
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

    public void initTableView() throws Exception {
        initContactTableView();
    }

    public void initContactTableView() throws Exception {
        ContactInfo contactInfo = (ContactInfo) getUserdata(DataClassName.ContactInfo.name());
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
            linkmanColumn.setCellValueFactory(new MapValueFactory("name"));
            linkaddressColumn.setCellValueFactory(new MapValueFactory("address"));
        }

    }

    public void initTokenTableView() throws Exception {
        TokenInfo tokenInfo = (TokenInfo) getUserdata(DataClassName.TOKEN.name());
        List<Tokens> list = tokenInfo.getPositveTokenList();
        ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();
        if (list != null && !list.isEmpty()) {
            for (Tokens tokens : list) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("tokenname", tokens.getTokenname());
                map.put("tokenid", tokens.getTokenid());
                allData.add(map);
            }
            wachtedTokenTableview.setItems(allData);
            tokennameColumn.setCellValueFactory(new MapValueFactory("tokenname"));
            tokenidColumn.setCellValueFactory(new MapValueFactory("tokenid"));
        }
    }
}
