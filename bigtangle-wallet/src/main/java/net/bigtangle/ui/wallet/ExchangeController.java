/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class ExchangeController {
    private static final Logger log = LoggerFactory.getLogger(ExchangeController.class);
    @FXML
    public TextField toAmountTextField;

    @FXML
    public ComboBox<String> toTokenHexComboBox;

    @FXML
    public ComboBox<String> toAddressComboBox;

    @FXML
    public TextField fromAmountTextField;

    @FXML
    public ComboBox<String> fromTokenHexComboBox;

    @FXML
    public ComboBox<String> fromAddressComboBox;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public TableView<Map<String, Object>> exchangeTable;

    @FXML
    public TableColumn<Map<String, Object>, String> orderidsCol;
    @FXML
    public TableColumn<Map<String, Object>, String> dataHexCol;

    @FXML
    public TableColumn<Map<String, Object>, String> fromAddressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> fromTokenidCol;
    @FXML
    public TableColumn<Map<String, Object>, String> fromAmountCol;
    @FXML
    public TableColumn<Map<String, Object>, String> toAddressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> toTokenidCol;
    @FXML
    public TableColumn<Map<String, Object>, String> toAmountCol;
    public TableColumn<Map<String, Object>, String> toSignCol;
    public TableColumn<Map<String, Object>, String> fromSignCol;
    public TableColumn<Map<String, Object>, String> marketCol;

    public Transaction mTransaction;

    public String mOrderid;
    // public String mTokenid;

    @FXML
    public void initialize() {
        try {
            List<String> list = Main.initAddress4block();
            ObservableList<String> addressData = FXCollections.observableArrayList(list);
            toAddressComboBox.setItems(addressData);

            initComboBox();
            initTable();
        } catch (Exception e) {
            e.printStackTrace();
            GuiUtils.crashAlert(e);
        }
        mTransaction = null;
        mOrderid = "";
        // mTokenid = "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void initTable() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        String response0 = OkHttp3Util.postString(CONTEXT_ROOT + "getMarkets", "{}");
        final Map<String, Object> getTokensResult = Json.jsonmapper().readValue(response0, Map.class);
        List<Map<String, Object>> tokensList = (List<Map<String, Object>>) getTokensResult.get("tokens");
        ObservableList<Map<String, Object>> exchangeData = FXCollections.observableArrayList();
        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }

        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
        List<String> addressList = new ArrayList<String>();
        for (ECKey ecKey : keys) {
            String address = ecKey.toAddress(Main.params).toString();
            addressList.add(address);
        }
        for (Map<String, Object> tokenResult : tokensList) {
            boolean asmarket = (boolean) tokenResult.get("asmarket");
            if (!asmarket) {
                continue;
            }
            String url = (String) tokenResult.get("url");

            log.debug(url);
            if (url == null || url.isEmpty()) {
                continue;
            }

            String response = OkHttp3Util.post(url + "/" + "getBatchExchange",
                    Json.jsonmapper().writeValueAsString(addressList).getBytes());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("exchanges");
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (Map<String, Object> map : list) {
                if ((Integer) map.get("toSign") == 1) {
                    map.put("toSign", "*");
                } else {
                    map.put("toSign", "-");
                }
                if ((Integer) map.get("fromSign") == 1) {
                    map.put("fromSign", "*");
                } else {
                    map.put("fromSign", "-");
                }
                Coin fromAmount = Coin.valueOf(Long.parseLong((String) map.get("fromAmount")),
                        Utils.HEX.decode((String) map.get("fromTokenHex")));
                Coin toAmount = Coin.valueOf(Long.parseLong((String) map.get("toAmount")),
                        Utils.HEX.decode((String) map.get("toTokenHex")));

                map.put("fromAmount", fromAmount.toPlainString());

                map.put("toAmount", toAmount.toPlainString());
                exchangeData.add(map);
            }
        }
        orderidsCol.setCellValueFactory(new MapValueFactory("orderid"));
        dataHexCol.setCellValueFactory(new MapValueFactory("dataHex"));
        fromAddressCol.setCellValueFactory(new MapValueFactory("fromAddress"));
        fromTokenidCol.setCellValueFactory(new MapValueFactory("fromTokenHex"));
        fromAmountCol.setCellValueFactory(new MapValueFactory("fromAmount"));
        toAddressCol.setCellValueFactory(new MapValueFactory("toAddress"));
        toTokenidCol.setCellValueFactory(new MapValueFactory("toTokenHex"));
        toAmountCol.setCellValueFactory(new MapValueFactory("toAmount"));
        toSignCol.setCellValueFactory(new MapValueFactory("toSign"));
        fromSignCol.setCellValueFactory(new MapValueFactory("fromSign"));
        marketCol.setCellValueFactory(new MapValueFactory("market"));

        fromAddressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        fromTokenidCol.setCellFactory(TextFieldTableCell.forTableColumn());
        toAddressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        toTokenidCol.setCellFactory(TextFieldTableCell.forTableColumn());

        exchangeTable.setItems(exchangeData);
    }

    @SuppressWarnings("unchecked")
    public void initComboBox() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> tokens = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : tokens) {
            String tokenHex = (String) map.get("tokenid");
            String tokenname = (String) map.get("tokenname");
            tokenData.add(tokenname + " : " + tokenHex);
        }
        toTokenHexComboBox.setItems(tokenData);
        fromTokenHexComboBox.setItems(tokenData);

        KeyParameter aesKey = null;
        // Main.initAeskey(aesKey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            addresses.add(key.toAddress(Main.params).toString());
        }
        fromAddressComboBox.setItems(addresses);
    }

    public void exchangeCoin(ActionEvent event) throws Exception {
        exchange("");
        overlayUI.done();
    }

    public void cancelOrder(ActionEvent event) throws Exception {
        String ContextRoot = Main.getContextRoot();
        Map<String, Object> rowData = exchangeTable.getSelectionModel().getSelectedItem();
        String orderid = stringValueOf(rowData.get("orderid"));

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);

        OkHttp3Util.post(ContextRoot + "cancelOrder", Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    private void exchange(String marketURL) throws Exception, JsonProcessingException {
        if (mTransaction == null) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
            return;
        }
        SendRequest request = SendRequest.forTx(mTransaction);
        Main.bitcoin.wallet().signTransaction(request);

        String ContextRoot = Main.getContextRoot();
        byte[] data = OkHttp3Util.post(ContextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
        Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(mTransaction);
        rollingBlock.solve();
        OkHttp3Util.post(ContextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        // System.out.println(marketURL);
        if (marketURL != null && !marketURL.equals("")) {
            HashMap<String, Object> exchangeResult = this.getExchangeInfoResult(marketURL, this.mOrderid);
            int toSign = (int) exchangeResult.get("toSign");
            int fromSign = (int) exchangeResult.get("fromSign");
            String toAddress = (String) exchangeResult.get("toAddress");
            String fromAddress = (String) exchangeResult.get("fromAddress");
            String fromTokenHex = (String) exchangeResult.get("fromTokenHex");
            String fromAmount = (String) exchangeResult.get("fromAmount");
            String toTokenHex = (String) exchangeResult.get("toTokenHex");
            String toAmount = (String) exchangeResult.get("toAmount");

            String signtype = "";
            if (toSign == 0 && calculatedAddressHit(toAddress)) {
                signtype = "to";
            } else if (fromSign == 0 && calculatedAddressHit(fromAddress)) {
                signtype = "from";
            }
            byte[] buf = this.makeSignTransactionBuffer(fromAddress, getCoin(fromAmount, fromTokenHex, true), toAddress,
                    getCoin(toAmount, toTokenHex, true), mTransaction.bitcoinSerialize());
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String orderid = stringValueOf(mOrderid);
            requestParam.put("orderid", orderid);
            requestParam.put("dataHex", Utils.HEX.encode(buf));
            requestParam.put("signtype", signtype);
            OkHttp3Util.post(ContextRoot + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        }
    }

    public void importBlock(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        byte[] buf = FileUtil.readFile(file);
        if (buf == null) {
            return;
        }
        reloadTransaction(buf);
        // overlayUI.done();
    }

    private void reloadTransaction(byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromAmountTextField.setText(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toAmountTextField.setText(new String(dst));
        }
        byte[] orderid = new byte[byteBuffer.getInt()];
        byteBuffer.get(orderid);
        mOrderid = new String(orderid);

        // byte[] tokenid = new byte[byteBuffer.getInt()];
        // byteBuffer.get(tokenid);
        // mTokenid = new String(orderid);

        // log.debug("orderid : " + new String(orderid));

        int len = byteBuffer.getInt();
        // log.debug("tx len : " + len);
        byte[] data = new byte[len];
        byteBuffer.get(data);
        try {
            mTransaction = (Transaction) Main.params.getDefaultSerializer().makeTransaction(data);
            // mTransaction = (Transaction)
            // Main.params.getDefaultSerializer().makeTransaction(data);
            if (mTransaction == null) {
                GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
            }
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void exportBlock(ActionEvent event) {
        // String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port +
        // "/";
        String fromAddress = fromAddressComboBox.getValue();
        String fromTokenHex = fromTokenHexComboBox.getValue().split(":")[1].trim();
        String fromAmount = fromAmountTextField.getText();
        String toAddress = !toAddressComboBox.getValue().contains(",") ? toAddressComboBox.getValue()
                : toAddressComboBox.getValue().split(",")[1];
        String toTokenHex = toTokenHexComboBox.getValue().split(":")[1].trim();
        String toAmount = toAmountTextField.getText();

        this.mOrderid = UUIDUtil.randomUUID();
        // this.mTokenid = fromTokenHex;
        byte[] buf = this.makeSignTransactionBuffer(fromAddress, getCoin(fromAmount, fromTokenHex, true), toAddress,
                getCoin(toAmount, toTokenHex, true));
        if (buf == null) {
            return;
        }

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file == null) {
            return;
        }
        // wirte file
        FileUtil.writeFile(file, buf);
        overlayUI.done();
    }

    public void refund(ActionEvent event) throws Exception {
        if (mTransaction == null) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
            return;
        }
        String tokenHex = null, address = null;
        if (calculatedAddressHit(this.toAddressComboBox.getValue())) {
            tokenHex = this.toTokenHexComboBox.getValue();
            address = this.toAddressComboBox.getValue();
        }
        if (calculatedAddressHit(this.fromAddressComboBox.getValue())) {
            tokenHex = this.fromTokenHexComboBox.getValue();
            address = this.fromAddressComboBox.getValue();
        }
        if (tokenHex == null || address == null) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
        }
        HashMap<String, Coin> refundAmount = new HashMap<String, Coin>();
        for (TransactionOutput transactionOutput : this.mTransaction.getOutputs()) {
            Coin value = transactionOutput.getValue();
            String tokenid = value.getTokenHex();
            if (tokenid.equals(tokenHex)) {
                continue;
            }
            Coin coinbase = refundAmount.get(tokenid);
            if (coinbase == null) {
                coinbase = Coin.valueOf(0, value.getTokenid());
            }
            coinbase = coinbase.add(value);
            refundAmount.put(tokenid, coinbase);
        }

        if (refundAmount.isEmpty())
            return;

        Coin amount = refundAmount.values().iterator().next();
        Transaction transaction = new Transaction(Main.params);
        transaction.addOutput(amount, Address.fromBase58(Main.params, address));
        for (TransactionInput transactionInput : this.mTransaction.getInputs()) {
            TransactionOutput transactionOutput = transactionInput.getConnectedOutput();
            if (transactionOutput.getValue().getTokenHex().equals(amount.getTokenHex())
                    && transactionOutput.getValue().value == amount.value) {
                // System.out.println(amount + "," +
                // transactionOutput.getValue());
                transaction.addInput(transactionInput);
            }
        }
        SendRequest request = SendRequest.forTx(transaction);
        Main.bitcoin.wallet().signTransaction(request);

        String ContextRoot = Main.getContextRoot();

        byte[] data = OkHttp3Util.post(ContextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
        Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();
        OkHttp3Util.post(ContextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        // overlayUI.done();
    }

    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getExchangeInfoResult(String url, String orderid) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String respone = OkHttp3Util.postString(url + "/" + "exchangeInfo",
                Json.jsonmapper().writeValueAsString(requestParam));
        HashMap<String, Object> result = Json.jsonmapper().readValue(respone, HashMap.class);
        HashMap<String, Object> exchange = (HashMap<String, Object>) result.get("exchange");
        return exchange;
    }

    @SuppressWarnings("unchecked")
    public void signExchange(ActionEvent event) throws Exception {
        Map<String, Object> rowData = exchangeTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("pleaseSelect"), "");
            return;
        }
        String tokenid = (String) rowData.get("market");

        String ContextRoot = Main.getContextRoot();
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(ContextRoot + "getTokenById",
                Json.jsonmapper().writeValueAsString(requestParam0));
        HashMap<String, Object> res = Json.jsonmapper().readValue(resp, HashMap.class);
        HashMap<String, Object> token_ = (HashMap<String, Object>) res.get("token");
        String marketURL = (String) token_.get("url");

        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
        }
        this.mOrderid = stringValueOf(rowData.get("orderid"));
        // this.mTokenid = tokenid;
        HashMap<String, Object> exchangeResult = getExchangeInfoResult(marketURL, this.mOrderid);
        String dataHex = (String) exchangeResult.get("dataHex");
        if (dataHex.isEmpty()) {
            byte[] buf = null;
            String fromAddress = stringValueOf(exchangeResult.get("fromAddress"));
            String toAddress = stringValueOf(exchangeResult.get("toAddress"));
            String toTokenHex = stringValueOf(exchangeResult.get("toTokenHex"));
            String fromTokenHex = stringValueOf(exchangeResult.get("fromTokenHex"));
            String toAmount = stringValueOf(exchangeResult.get("toAmount"));
            String fromAmount = stringValueOf(exchangeResult.get("fromAmount"));
            buf = this.makeSignTransactionBufferCheckSwap(fromAddress, getCoin(fromAmount, fromTokenHex, false),
                    toAddress, getCoin(toAmount, toTokenHex, false));
            if (buf == null) {
                return;
            }
            int toSign = (int) exchangeResult.get("toSign");
            int fromSign = (int) exchangeResult.get("fromSign");
            String signtype = "";
            if (toSign == 0 && calculatedAddressHit(toAddress)) {
                signtype = "to";
            } else if (fromSign == 0 && calculatedAddressHit(fromAddress)) {
                signtype = "from";
            }
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("orderid", this.mOrderid);
            requestParam.put("dataHex", Utils.HEX.encode(buf));
            requestParam.put("signtype", signtype);
            OkHttp3Util.post(marketURL + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam));
            this.initTable();
            return;
        }
        byte[] buf = Utils.HEX.decode(dataHex);
        this.reloadTransaction(buf);
        if (mTransaction == null) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
            return;
        }
        this.exchange(marketURL);
        this.initTable();
        // overlayUI.done();
    }

    public boolean calculatedAddressHit(String address) throws Exception {
        KeyParameter aesKey = null;
        // Main.initAeskey(aesKey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
        for (ECKey key : keys) {
            String n = key.toAddress(Main.params).toString();
            if (n.equalsIgnoreCase(address)) {
                return true;
            }
        }
        return false;
    }

    private byte[] makeSignTransactionBuffer(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin,
            byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(buf.length + 4 + fromAddress.getBytes().length + 4
                + fromCoin.getTokenHex().getBytes().length + 4 + fromCoin.toPlainString().getBytes().length + 4
                + toAddress.getBytes().length + 4 + toCoin.getTokenHex().getBytes().length + 4
                + toCoin.toPlainString().getBytes().length + 4 + this.mOrderid.getBytes().length + 4);

        byteBuffer.putInt(fromAddress.getBytes().length).put(fromAddress.getBytes());
        byteBuffer.putInt(fromCoin.getTokenHex().getBytes().length).put(fromCoin.getTokenHex().getBytes());
        byteBuffer.putInt(fromCoin.toPlainString().getBytes().length).put(fromCoin.toPlainString().getBytes());
        byteBuffer.putInt(toAddress.getBytes().length).put(toAddress.getBytes());
        byteBuffer.putInt(toCoin.getTokenHex().getBytes().length).put(toCoin.getTokenHex().getBytes());
        byteBuffer.putInt(toCoin.toPlainString().getBytes().length).put(toCoin.toPlainString().getBytes());
        byteBuffer.putInt(this.mOrderid.getBytes().length).put(this.mOrderid.getBytes());
        byteBuffer.putInt(buf.length).put(buf);
        // log.debug("tx len : " + buf.length);
        return byteBuffer.array();
    }

    public void swap(Coin fromCoin, Coin toCoin) {
        Coin t = fromCoin;
        fromCoin = toCoin;
        toCoin = t;
    }

    private byte[] makeSignTransactionBufferCheckSwap(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin)
            throws Exception {
        String fromAddress00, toAddress00;
        Coin fromCoin00, toCoin00;
        if (this.calculatedAddressHit(fromAddress)) {
            fromAddress00 = fromAddress;
            toAddress00 = toAddress;
            fromCoin00 = fromCoin;
            toCoin00 = toCoin;
        } else {
            fromAddress00 = toAddress;
            toAddress00 = fromAddress;
            fromCoin00 = toCoin;
            toCoin00 = fromCoin;
        }
        return makeSignTransactionBuffer(fromAddress00, fromCoin00, toAddress00, toCoin00);
    }

    @SuppressWarnings("deprecation")
    private byte[] makeSignTransactionBuffer(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin) {
        String ContextRoot = Main.getContextRoot();
        Address fromAddress00 = new Address(Main.params, fromAddress);
        Address toAddress00 = new Address(Main.params, toAddress);
        KeyParameter aesKey = null;
        // Main.initAeskey(aesKey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        byte[] buf = null;
        try {
            List<UTXO> outputs = new ArrayList<UTXO>();
            outputs.addAll(Main.getUTXOWithPubKeyHash(toAddress00.getHash160(), fromCoin.getTokenHex()));
            outputs.addAll(Main.getUTXOWithECKeyList(Main.bitcoin.wallet().walletKeys(aesKey), toCoin.getTokenHex()));

            SendRequest req = SendRequest.to(toAddress00, toCoin);
            req.tx.addOutput(fromCoin, fromAddress00);

            // SendRequest req = SendRequest.to(fromAddress00,fromAmount );
            // req.tx.addOutput(toAmount , toAddress00 );

            req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

            HashMap<String, Address> addressResult = new HashMap<String, Address>();
            addressResult.put(fromCoin.getTokenHex(), toAddress00);
            addressResult.put(toCoin.getTokenHex(), fromAddress00);

            // addressResult.put((String) exchangemap.get("fromTokenHex"),
            // toAddress00);
            // addressResult.put((String) exchangemap.get("toTokenHex"),
            // fromAddress00);

            List<TransactionOutput> candidates = Main.bitcoin.wallet().transforSpendCandidates(outputs);
            Main.bitcoin.wallet().setServerURL(ContextRoot);
            Main.bitcoin.wallet().completeTx(req, candidates, false, addressResult);
            Main.bitcoin.wallet().signTransaction(req);

            // walletAppKit.wallet().completeTx(req,
            // walletAppKit.wallet().transforSpendCandidates(ulist), false,
            // addressResult);
            // walletAppKit.wallet().signTransaction(req);
            this.mTransaction = req.tx;
            buf = mTransaction.bitcoinSerialize();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
            return null;
        }
        return makeSignTransactionBuffer(fromAddress, fromCoin, toAddress, toCoin, buf);
    }

    private String stringValueOf(Object object) {
        if (object == null) {
            return "";
        } else {
            return String.valueOf(object);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public Coin getCoin(String toAmount, String toTokenHex, boolean decimal) {
        if (decimal) {
            return Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
        } else {
            return Coin.valueOf(Long.parseLong(toAmount), Utils.HEX.decode(toTokenHex));
        }
    }
}
