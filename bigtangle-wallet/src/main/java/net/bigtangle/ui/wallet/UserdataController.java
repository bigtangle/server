/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.Main.bitcoin;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.stage.FileChooser;
import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.MyHomeAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Uploadfile;
import net.bigtangle.core.UploadfileInfo;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.FileUtil;
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
    public TableView<Map<String, Object>> fileTable;
    @FXML
    public TableColumn<Map<String, Object>, String> filenameColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> filesizeColumn;

    @FXML
    public TextField dataclassTF;
    @FXML
    public TextField nameTF;
    @FXML
    public TextField addressTF;

    @FXML
    public TextField filepathTF;
    @FXML
    public TextField filenameTF;

    @FXML
    public TextField countryTF;
    @FXML
    public TextField provinceTF;
    @FXML
    public TextField cityTF;
    @FXML
    public TextField streetTF;
    @FXML
    public TextField emailTF;
    @FXML
    public TextArea remarkTA;
    @FXML
    public TabPane tabPane;
    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            tabPane.getSelectionModel().selectedIndexProperty().addListener((ov, t, t1) -> {
                int index = t1.intValue();
                switch (index) {
                case 0: {
                    initContactTableView();
                }

                    break;
                case 1: {
                    initTokenTableView();
                }

                    break;
                case 2: {
                    initMyAddress();
                }

                    break;
                case 3: {
                    initFileTableView();
                }

                    break;
                }
            });
            initContactTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveUserdata(ActionEvent event) {
        String CONTEXT_ROOT = Main.getContextRoot();
        try {
            addContact(CONTEXT_ROOT);
            initContactTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public Serializable getUserdata(String type) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
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
        } else if (DataClassName.MYHOMEADDRESS.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new MyHomeAddress();
            }
            MyHomeAddress myHomeAddress = new MyHomeAddress().parse(bytes);
            return myHomeAddress;
        } else if (DataClassName.UPLOADFILE.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new UploadfileInfo();
            }
            UploadfileInfo uploadfileInfo = new UploadfileInfo().parse(bytes);
            return uploadfileInfo;
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

        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

        ECKey pubKeyTo = null;
        if (bitcoin.wallet().isEncrypted()) {
            pubKeyTo = issuedKeys.get(0);
        } else {
            pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        }
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
        try {
            Map<String, Object> rowdata = wachtedTokenTableview.getSelectionModel().getSelectedItem();
            if (rowdata == null) {
                return;
            }
            String name = (String) rowdata.get("tokenname");
            String tokenid = (String) rowdata.get("tokenid");
            String CONTEXT_ROOT = Main.getContextRoot();
            HashMap<String, String> requestParam = new HashMap<String, String>();

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));

            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            block.setBlocktype(NetworkParameters.BLOCKTYPE_USERDATA);

            Transaction coinbase = new Transaction(Main.params);

            TokenInfo tokenInfo = (TokenInfo) getUserdata(DataClassName.TOKEN.name());
            List<Tokens> list = tokenInfo.getPositveTokenList();
            List<Tokens> tempList = new ArrayList<Tokens>();
            for (Tokens tokens : list) {
                if (name.trim().equals(tokens.getTokenname().trim())
                        && tokenid.trim().equals(tokens.getTokenid().trim())) {
                    continue;
                }
                tempList.add(tokens);
            }
            tokenInfo.setPositveTokenList(tempList);

            coinbase.setDataclassname(DataClassName.TOKEN.name());
            byte[] buf1 = tokenInfo.toByteArray();
            coinbase.setData(buf1);

            block.addTransaction(coinbase);
            block.solve();

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
            initTokenTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initMyAddress() {
        try {
            String CONTEXT_ROOT = Main.getContextRoot();
            MyHomeAddress myHomeAddress = (MyHomeAddress) getUserdata(DataClassName.MYHOMEADDRESS.name());
            countryTF.setText(myHomeAddress.getCountry());
            provinceTF.setText(myHomeAddress.getProvince());
            cityTF.setText(myHomeAddress.getCity());
            streetTF.setText(myHomeAddress.getStreet());
            emailTF.setText(myHomeAddress.getEmail());
            remarkTA.setText(myHomeAddress.getRemark());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveMyAddress(ActionEvent event) {
        try {
            String CONTEXT_ROOT = Main.getContextRoot();
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            block.setBlocktype(NetworkParameters.BLOCKTYPE_USERDATA);
            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            ECKey pubKeyTo = null;
            if (bitcoin.wallet().isEncrypted()) {
                pubKeyTo = issuedKeys.get(0);
            } else {
                pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
            }

            Transaction coinbase = new Transaction(Main.params);
            coinbase.setDataclassname(DataClassName.MYHOMEADDRESS.name());
            MyHomeAddress myhomeaddress = new MyHomeAddress();
            myhomeaddress.setCountry(countryTF.getText());
            myhomeaddress.setProvince(provinceTF.getText());
            myhomeaddress.setCity(cityTF.getText());
            myhomeaddress.setStreet(streetTF.getText());
            myhomeaddress.setEmail(emailTF.getText());
            myhomeaddress.setRemark(remarkTA.getText());
            coinbase.setData(myhomeaddress.toByteArray());

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

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void removeLinkman(ActionEvent event) {
        try {
            Map<String, Object> rowdata = linkmanTableview.getSelectionModel().getSelectedItem();
            if (rowdata == null) {
                return;
            }
            String name = (String) rowdata.get("name");
            String address = (String) rowdata.get("address");
            String CONTEXT_ROOT = Main.getContextRoot();
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
            coinbase.setData(contactInfo.toByteArray());

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            ECKey pubKeyTo = null;
            if (bitcoin.wallet().isEncrypted()) {
                pubKeyTo = issuedKeys.get(0);
            } else {
                pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
            }

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

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
            initContactTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initContactTableView() {
        try {
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
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void initFileTableView() {
        try {
            UploadfileInfo uploadfileInfo = (UploadfileInfo) getUserdata(DataClassName.UPLOADFILE.name());
            List<Uploadfile> list = uploadfileInfo.getfUploadfiles();
            ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();
            if (list != null && !list.isEmpty()) {
                for (Uploadfile contact : list) {
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("name", contact.getName());
                    map.put("size", contact.getMaxsize());
                    map.put("fileinfo", contact.getFileinfo());
                    allData.add(map);
                }
                fileTable.setItems(allData);
                filenameColumn.setCellValueFactory(new MapValueFactory("name"));
                filesizeColumn.setCellValueFactory(new MapValueFactory("size"));
            }
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initTokenTableView() {
        try {
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
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void uploadFile(ActionEvent event) {
        try {
            String CONTEXT_ROOT = Main.getContextRoot();
            final FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(null);
            if (filenameTF.getText() == null || filenameTF.getText().isEmpty()) {
                filenameTF.setText(file.getName());
            }

            filepathTF.setText(file.getAbsolutePath());
            if (file.length() > Block.MAX_BLOCK_SIZE - 20 * 1000) {
                GuiUtils.informationalAlert("", Main.getText("fileTooLarge"), "");
                return;
            }
            byte[] buf = FileUtil.readFile(file);
            if (buf == null) {
                return;
            }
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            block.setBlocktype(NetworkParameters.BLOCKTYPE_USERDATA);

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            ECKey pubKeyTo = null;
            if (bitcoin.wallet().isEncrypted()) {
                pubKeyTo = issuedKeys.get(0);
            } else {
                pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
            }

            Transaction coinbase = new Transaction(Main.params);
            Uploadfile uploadfile = new Uploadfile();
            uploadfile.setName(filenameTF.getText());
            uploadfile.setMaxsize(file.length());

            UploadfileInfo uploadfileInfo = (UploadfileInfo) getUserdata(DataClassName.UPLOADFILE.name());
            List<Uploadfile> uploadfiles = uploadfileInfo.getfUploadfiles();
            uploadfiles.add(uploadfile);
            uploadfileInfo.setfUploadfiles(uploadfiles);
            uploadfile.setFileinfo(buf);
            uploadfile.setFileinfoHex(Utils.HEX.encode(buf));
            coinbase.setDataclassname(DataClassName.UPLOADFILE.name());
            coinbase.setData(uploadfileInfo.toByteArray());

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

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
            initFileTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void downloadFile(ActionEvent event) {
        try {
            Map<String, Object> rowdata = fileTable.getSelectionModel().getSelectedItem();
            if (rowdata == null || rowdata.isEmpty()) {
                return;
            }
            byte[] fileinfo = (byte[]) rowdata.get("fileinfo");
            final FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showSaveDialog(null);
            if (file == null) {
                return;
            }
            // wirte file
            FileUtil.writeFile(file, fileinfo);
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }
}
