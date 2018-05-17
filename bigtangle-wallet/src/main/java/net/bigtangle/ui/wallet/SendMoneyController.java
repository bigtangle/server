/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.InsufficientMoneyException;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public TextField linknameTextField;
    @FXML
    public TextField memoTF;

    public Label titleLabel;
    public TextField amountEdit;
    @FXML
    public Label btcLabel;
    // @FXML
    // public ToggleGroup unitToggleGroup;
    // @FXML
    // public RadioButton basicRadioButton;
    // @FXML
    // public RadioButton kiloRadioButton;
    // @FXML
    // public RadioButton milionRadioButton;

    @FXML
    public ChoiceBox<Object> tokeninfo;

    public Main.OverlayUI<?> overlayUI;

    private String mOrderid;
    private Transaction mTransaction;

    public void initChoicebox() {
        // basicRadioButton.setUserData(1 + "");
        // kiloRadioButton.setUserData(1000 + "");
        // milionRadioButton.setUserData(1000 * 1000 + "");
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Object> tokenData = FXCollections.observableArrayList();

        try {
            Map<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("name", "");
            String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
            List<String> names = new ArrayList<String>();
            List<String> tokens = Main.initToken4file();
            for (Map<String, Object> map : list) {

                String tokenHex = (String) map.get("tokenid");
                if (tokens != null && !tokens.isEmpty()) {
                    for (String temp : tokens) {
                        // ONLY log System.out.println("temp:" + temp);
                        if ((!temp.equals("") && temp.contains(tokenHex))
                                || NetworkParameters.BIGNETCOIN_TOKENID_STRING.equalsIgnoreCase(tokenHex)
                                || isMyTokens(tokenHex)) {
                            if (!tokenData.contains(tokenHex)) {
                                tokenData.add(tokenHex);
                                names.add(map.get("tokenname").toString());
                            }

                        }
                    }
                }

            }
            tokeninfo.setItems(tokenData);
            tokeninfo.getSelectionModel().selectedIndexProperty().addListener((ov, oldv, newv) -> {
                btcLabel.setText(names.get(newv.intValue()));
            });
            tokeninfo.getSelectionModel().selectFirst();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public boolean isMyTokens(String tokenHex) {
        ObservableList<CoinModel> list = Main.instance.getCoinData();
        if (list != null && !list.isEmpty()) {
            for (CoinModel coinModel : list) {
                String temp = coinModel.getTokenid();
                if (tokenHex.equalsIgnoreCase(temp)) {
                    return true;
                }
            }
        }
        return false;

    }

    @FXML
    public void initialize() throws Exception {
        initChoicebox();
        List<String> list = Main.initAddress4file();

        ObservableList<String> addressData = FXCollections.observableArrayList(list);
        addressComboBox.setItems(addressData);
        // new BitcoinAddressValidator(Main.params, addressComboBox, sendBtn);
        new TextFieldValidator(amountEdit, text -> !WTUtils
                .didThrow(() -> checkState(Coin.parseCoin(text, NetworkParameters.BIGNETCOIN_TOKENID).isPositive())));

    }

    public void importSign(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        byte[] buf = FileUtil.readFile(file);
        if (buf == null) {
            return;
        }
        reloadTransaction(buf);
    }

    private void reloadTransaction(byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);

        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            addressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            tokeninfo.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            amountEdit.setText(new String(dst));
        }
        byte[] orderid = new byte[byteBuffer.getInt()];
        byteBuffer.get(orderid);

        mOrderid = new String(orderid);
        // log.debug ("orderid : " + new String(orderid));

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

    public void exportSign(ActionEvent event) {
        String toAddress = !addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                : addressComboBox.getValue().split(",")[1];
        String toTokenHex = tokeninfo.getValue().toString().trim();
        String toAmount = amountEdit.getText();
        this.mOrderid = UUIDUtil.randomUUID();
        boolean decial = true;

        byte[] buf = this.makeSignTransactionBuffer(toAddress, getCoin(toAmount, toTokenHex, decial));
        if (buf == null) {
            return;
        }

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file == null) {
            return;
        }

        FileUtil.writeFile(file, buf);
        overlayUI.done();
    }

    public Coin getCoin(String toAmount, String toTokenHex, boolean decimal) {
        if (decimal) {
            return Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
        } else {
            return Coin.valueOf(Long.parseLong(toAmount), Utils.HEX.decode(toTokenHex));
        }
    }

    private byte[] makeSignTransactionBuffer(String toAddress, Coin toCoin) {
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
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
            outputs.addAll(Main.getUTXOWithPubKeyHash(toAddress00.getHash160(), null));
            outputs.addAll(Main.getUTXOWithECKeyList(Main.bitcoin.wallet().walletKeys(aesKey), toCoin.getTokenHex()));

            SendRequest req = SendRequest.to(toAddress00, toCoin);

            req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

            HashMap<String, Address> addressResult = new HashMap<String, Address>();
            addressResult.put(toCoin.getTokenHex(), toAddress00);

            List<TransactionOutput> candidates = Main.bitcoin.wallet().transforSpendCandidates(outputs);
            Main.bitcoin.wallet().setServerURL(ContextRoot);
            Main.bitcoin.wallet().completeTx(req, candidates, false, addressResult);
            Main.bitcoin.wallet().signTransaction(req);

            this.mTransaction = req.tx;
            buf = mTransaction.bitcoinSerialize();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
            return null;
        }
        return makeSignTransactionBuffer(toAddress, toCoin, buf);
    }

    private byte[] makeSignTransactionBuffer(String toAddress, Coin toCoin, byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer
                .allocate(buf.length + 4 + toAddress.getBytes().length + 4 + toCoin.getTokenHex().getBytes().length + 4
                        + toCoin.toPlainString().getBytes().length + 4 + this.mOrderid.getBytes().length + 4);

        byteBuffer.putInt(toAddress.getBytes().length).put(toAddress.getBytes());
        byteBuffer.putInt(toCoin.getTokenHex().getBytes().length).put(toCoin.getTokenHex().getBytes());
        byteBuffer.putInt(toCoin.toPlainString().getBytes().length).put(toCoin.toPlainString().getBytes());
        byteBuffer.putInt(this.mOrderid.getBytes().length).put(this.mOrderid.getBytes());
        byteBuffer.putInt(buf.length).put(buf);
        // System.out.println("tx len : " + buf.length);
        return byteBuffer.array();
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void send(ActionEvent event) {
        try {

            // Main.addAddress2file(linknameTextField.getText(),
            // !addressComboBox.getValue().contains(",") ?
            // addressComboBox.getValue()
            // : addressComboBox.getValue().split(",")[1]);
            // checkGuiThread();
            // overlayUI.done();
            if (addressComboBox.getValue() == null) {
                GuiUtils.informationalAlert(Main.getText("address_empty"), "", "");
                return;
            }
            String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            Address destination = // Address.getParametersFromAddress(address)address.getText()
                    Address.fromBase58(Main.params,
                            !addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                                    : addressComboBox.getValue().split(",")[1]);
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));

            Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);

            Wallet wallet = Main.bitcoin.wallet();
            wallet.setServerURL(CONTEXT_ROOT);

            Coin amount = Coin.parseCoin(amountEdit.getText(), Utils.HEX.decode(tokeninfo.getValue().toString()));
            // long factor =
            // Long.valueOf(unitToggleGroup.getSelectedToggle().getUserData().toString()).longValue();
            long factor = 1;
            amount = amount.multiply(factor);
            SendRequest request = SendRequest.to(destination, amount);
            request.memo = memoTF.getText();
            try {
                wallet.completeTx(request);
                rollingBlock.addTransaction(request.tx);
                rollingBlock.solve();
            } catch (InsufficientMoneyException e) {

                GuiUtils.informationalAlert(Main.getText("m_n_e"), "", "");
                return;
            }
            Main.instance.sendMessage(rollingBlock.bitcoinSerialize());
            checkContact(event);
            // Main.instance.sentEmpstyBlock(2);
            overlayUI.done();
            Main.instance.controller.initTableView();

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    // TODO Fix problem after send return to main
    public void checkContact(ActionEvent event) throws Exception {

        String homedir = Main.keyFileDirectory;
        String addresses = Main.getString4file(homedir + Main.contactFile);
        if (!addresses.contains(!addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                : addressComboBox.getValue().split(",")[1])) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(Main.getText("linkman"));
            dialog.setHeaderText(!addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                    : addressComboBox.getValue().split(",")[1]);
            dialog.setContentText(Main.getText("linkman"));
            dialog.setWidth(900);
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                Main.addAddress2file(result.get(),
                        !addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                                : addressComboBox.getValue().split(",")[1]);
            }
        }

    }
}
