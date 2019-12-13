package net.bigtangle.server.service;

import java.io.IOException;
import java.util.UUID;

import org.spongycastle.crypto.InvalidCipherTextException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.SessionRandomNumResponse;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class AccessPermissionedService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getSessionRandomNumResp(String pubKey) throws Exception {
        ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKey));

        String message = UUID.randomUUID().toString();
        byte[] buf = message.getBytes();
        Sha256Hash hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(buf, 0, buf.length));
        byte[] payload = hash.getBytes();

        byte[] bytes = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
        String verifyHex = Utils.HEX.encode(bytes);
        this.store.insertAccessPermission(pubKey, Utils.HEX.encode(payload));
        return SessionRandomNumResponse.create(verifyHex);
    }

    public int checkSessionRandomNumResp(String pubKey, String accessToken) {
        try {
            int count = this.store.getCountAccessPermissionByPubKey(pubKey, accessToken);
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
