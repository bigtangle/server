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

package wallettemplate;

import static com.google.common.base.Preconditions.checkState;
import static wallettemplate.utils.GuiUtils.checkGuiThread;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletWrapper;
import org.spongycastle.crypto.params.KeyParameter;

import com.squareup.okhttp.OkHttpClient;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import wallettemplate.controls.BitcoinAddressValidator;
import wallettemplate.utils.TextFieldValidator;
import wallettemplate.utils.WTUtils;

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

        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getBalances", ecKey.getPubKeyHash());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> outputs0 = (List<Map<String, Object>>) data.get("outputs");
        List<UTXO> outputs = new ArrayList<UTXO>();
        for (Map<String, Object> map : outputs0) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(map);
            outputs.add(utxo);
        }
        List<Map<String, Object>> tokens0 = (List<Map<String, Object>>) data.get("tokens");
        List<Coin> tokens = new ArrayList<Coin>();
        for (Map<String, Object> map : tokens0) {
            Coin coin = MapToBeanMapperUtil.parseCoin(map);
            tokens.add(coin);
        }
        // TODO xiaomi change ui
        Coin balance = Coin.valueOf(10000, NetworkParameters.BIGNETCOIN_TOKENID);
        // Main.bitcoin.wallet().getBalance();
        checkState(!balance.isZero());
        new BitcoinAddressValidator(Main.params, address, sendBtn);
        new TextFieldValidator(amountEdit, text -> !WTUtils.didThrow(
                () -> checkState(Coin.parseCoin(text, NetworkParameters.BIGNETCOIN_TOKENID).compareTo(balance) <= 0)));
        amountEdit.setText(balance.toPlainString());
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public WalletWrapper createWalletWrapper() {
        List<ECKey> keys = new ArrayList<ECKey>();
        KeyChainGroup group = new KeyChainGroup(Main.params);
        group.importKeys(keys);
        return new WalletWrapper(Main.params, group, CONTEXT_ROOT);
    }

    private String CONTEXT_ROOT = "http://localhost:14265/";

    public void send(ActionEvent event) throws Exception {
        CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        Address destination = Address.fromBase58(Main.params, address.getText());
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        Block r1 = nextBlockSerializer(byteBuffer);
        Block r2 = nextBlockSerializer(byteBuffer);
        Block block = r1.createNextBlock(destination, Block.BLOCK_VERSION_GENESIS, (TransactionOutPoint) null,
                Utils.currentTimeSeconds(), outKey.getPubKey(), Coin.ZERO, 1, r2.getHash(), outKey.getPubKey());

        WalletWrapper wallet = (WalletWrapper) Main.bitcoin.wallet();
        wallet.setContextRoot(CONTEXT_ROOT);

        Coin amount = Coin.parseCoin(amountEdit.getText(), NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        wallet.completeTx(request);
        block.addTransaction(request.tx);
        block.solve();
        OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());

        // TODO xiaomi change ui
        checkGuiThread();
        overlayUI.done();
        // sendBtn.setDisable(true);
        // address.setDisable(true);
        // ((HBox) amountEdit.getParent()).getChildren().remove(amountEdit);
        // ((HBox) btcLabel.getParent()).getChildren().remove(btcLabel);
        // updateTitleForBroadcast();
    }

    private Block nextBlockSerializer(ByteBuffer byteBuffer) {
        byte[] data = new byte[byteBuffer.getInt()];
        byteBuffer.get(data);
        Block r1 = (Block) Main.params.getDefaultSerializer().makeBlock(data);
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
