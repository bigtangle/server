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

package com.bignetcoin.ui.wallet;

import static com.bignetcoin.ui.wallet.utils.GuiUtils.checkGuiThread;
import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.spongycastle.crypto.params.KeyParameter;

import com.bignetcoin.ui.wallet.utils.GuiUtils;
import com.bignetcoin.ui.wallet.utils.TextFieldValidator;
import com.bignetcoin.ui.wallet.utils.WTUtils;
import com.squareup.okhttp.OkHttpClient;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;
    public TextField amountEdit;
    public Label btcLabel;

    public Main.OverlayUI<?> overlayUI;

    private Wallet.SendResult sendResult;
    private KeyParameter aesKey;

    OkHttpClient client = new OkHttpClient();

    // Called by FXMLLoader
    @SuppressWarnings({ "unchecked" })
    public void initialize() throws Exception {
        // KeyBag maybeDecryptingKeyBag = new
        // DecryptingKeyBag(Main.bitcoin.wallet(), Main.aesKey);
        DeterministicKey ecKey = Main.bitcoin.wallet().currentReceiveKey();

        // TODO xiaomi change ui
        // Coin balance = Coin.valueOf(10000,
        // NetworkParameters.BIGNETCOIN_TOKENID);
        // Main.bitcoin.wallet().getBalance();
        // checkState(!balance.isZero());
        // new BitcoinAddressValidator(Main.params, address, sendBtn);
        address.setText(ecKey.toAddress(Main.params).toBase58());
       
          new TextFieldValidator(amountEdit, text -> !WTUtils.didThrow( () ->
          checkState(Coin.parseCoin(text,
          NetworkParameters.BIGNETCOIN_TOKENID).isPositive())));
         /* amountEdit.setText(balance.toPlainString());
         */
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    private String CONTEXT_ROOT = "http://localhost:14265/";

    public void send(ActionEvent event) {
        try {
            CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            Address destination = // Address.getParametersFromAddress(address)address.getText()
                    Address.fromBase58(Main.params, address.getText());
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);
            Block r1 = nextBlockSerializer(byteBuffer);
            Block r2 = nextBlockSerializer(byteBuffer);
            Main.params.getDefaultSerializer().makeBlock(r2.bitcoinSerialize());
            // ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();
            // r1.setNetworkParameters(Main.params);
            // r2.setNetworkParameters(Main.params);
            Block rollingBlock = new Block(Main.params, r1.getHash(), r2.getHash(),
                    NetworkParameters.BIGNETCOIN_TOKENID);

            Wallet wallet = Main.bitcoin.wallet();
            wallet.setServerURL(CONTEXT_ROOT);

            Coin amount = Coin.parseCoin(amountEdit.getText(), NetworkParameters.BIGNETCOIN_TOKENID);
            SendRequest request = SendRequest.to(destination, amount);
            wallet.completeTx(request);
            rollingBlock.addTransaction(request.tx);
            rollingBlock.solve();

            // Block block = (Block)
            // Main.params.getDefaultSerializer().deserialize(ByteBuffer.wrap(rollingBlock.bitcoinSerialize()));

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());

            // TODO xiaomi change ui
            checkGuiThread();
            overlayUI.done();
            // sendBtn.setDisable(true);
            // address.setDisable(true);
            // ((HBox) amountEdit.getParent()).getChildren().remove(amountEdit);
            // ((HBox) btcLabel.getParent()).getChildren().remove(btcLabel);
            // updateTitleForBroadcast();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    private Block nextBlockSerializer(ByteBuffer byteBuffer) {
        int len = byteBuffer.getInt();
        byte[] data = new byte[len];
        byteBuffer.get(data);
        Block r1 = Main.params.getDefaultSerializer().makeBlock(data);
        System.out.print(r1.toString());
        System.out.println("block len : " + len + " conv : " + r1.getHashAsString());
        return r1;
    }

    @SuppressWarnings("unused")
    private void askForPasswordAndRetry() {
        Main.OverlayUI<WalletPasswordController> pwd = Main.instance.overlayUI("wallet_password.fxml");
        final String addressStr = address.getText();
        final String amountStr = amountEdit.getText();
        pwd.controller.aesKeyProperty().addListener((observable, old, cur) -> {
            // We only get here if the user found the right password. If they
            // don't or they cancel, we end up back on
            // the main UI screen. By now the send money screen is history so we
            // must recreate it.
            checkGuiThread();
            Main.OverlayUI<SendMoneyController> screen = Main.instance.overlayUI("send_money.fxml");
            screen.controller.aesKey = cur;
            screen.controller.address.setText(addressStr);
            screen.controller.amountEdit.setText(amountStr);
            try {
                screen.controller.send(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SuppressWarnings("unused")
    private void updateTitleForBroadcast() {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }
}
