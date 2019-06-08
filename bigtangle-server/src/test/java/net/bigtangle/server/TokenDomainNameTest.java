package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.PermissionedAddressesResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.utils.DomainnameUtil;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TokenDomainNameTest extends AbstractIntegrationTest {

    @Autowired
    private ServerConfiguration serverConfiguration;

    public void checkTokenAssertTrue(String tokenid, String domainname) throws Exception {
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        Token token_ = getTokensResponse.getTokens().get(0);
        assertTrue(token_.getDomainname().equals(domainname));
    }

    @Test
    public void testDomainnameSubstr() {
        String domainname = "bigtangle.de";
        String str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("de"));

        domainname = "www.bigtangle.de";
        str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("bigtangle.de"));

        domainname = ".de";
        str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("de"));
    }

    private final KeyParameter aesKey = null;

    public TokenIndexResponse getServerCalTokenIndex(String tokenid) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp, TokenIndexResponse.class);
        return tokenIndexResponse;
    }

    public PermissionedAddressesResponse getPrevTokenMultiSignAddressList(Token token) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("tokenid", token.getTokenid());
        requestParam.put("prevblockhash", token.getBlockhash());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.queryPermissionedAddresses.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        PermissionedAddressesResponse permissionedAddressesResponse = Json.jsonmapper().readValue(resp,
                PermissionedAddressesResponse.class);
        return permissionedAddressesResponse;
    }

    public void upstreamToken2LocalServer(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
    }

    public void pullBlockDoMultiSign(ECKey outKey, KeyParameter aesKey) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        String address = outKey.toAddress(networkParameters).toBase58();
        requestParam.put("address", address);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        MultiSign multiSign = multiSignResponse.getMultiSigns().get(0);

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign.getBlockhashHex());
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();

        multiSignBy0.setTokenid(multiSign.getTokenid());
        multiSignBy0.setTokenindex(multiSign.getTokenindex());
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize());
    }

    @Test
    public void testCreateDomainTokenBatch() throws Exception {
        store.resetStore();

        wallet1();
        wallet2();
        Map<String, List<ECKey>> linkMap = new LinkedHashMap<String, List<ECKey>>();
        linkMap.put("de", this.walletKeys);
        linkMap.put("bigtangle.de", this.wallet1Keys);
        linkMap.put("m.bigtangle.de", this.wallet2Keys);

        int amount = 678900000;
        int index = 0;
        for (Map.Entry<String, List<ECKey>> entry : linkMap.entrySet()) {
            System.out.println("domainname : " + entry.getKey() + ", values : " + entry.getValue().size());
            List<ECKey> walletKeys = entry.getValue();
            String tokenid = this.walletKeys.get(1).getPublicKeyAsHex();
            final String domainname = entry.getKey();
            this.createDomainToken(tokenid, "中央银行token - 00" + (++index), domainname, amount, walletKeys);
            System.out.println(tokenid);
            this.checkTokenAssertTrue(tokenid, domainname);
        }
    }

    @Test
    public void testCreateDomainToken() throws Exception {
        store.resetStore();
        String tokenid = walletKeys.get(1).getPublicKeyAsHex();
        int amount = 678900000;
        final String domainname = "de";
        this.createDomainToken(tokenid, "中央银行token - 000", "de", amount, this.walletKeys);
        this.checkTokenAssertTrue(tokenid, domainname);
    }

    public void createDomainToken(String tokenid, String tokenname, String domainname, final int amount,
            List<ECKey> walletKeys) throws Exception {

        Coin basecoin = Coin.valueOf(amount, tokenid);
        TokenIndexResponse tokenIndexResponse = this.getServerCalTokenIndex(tokenid);

        long tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        int signnumber = 3;

        Token tokens = Token.buildDomainnameTokenInfo(true, prevblockhash, tokenid, tokenname, "de domain name",
                signnumber, tokenindex_, amount, false, domainname,0);
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setToken(tokens);

        List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
        tokenInfo.setMultiSignAddresses(multiSignAddresses);

        for (int i = 1; i <= 3; i++) {
            ECKey ecKey = walletKeys.get(i);
            multiSignAddresses.add(new MultiSignAddress(tokenid, "", ecKey.getPublicKeyAsHex()));
        }

        // if token index eq 0
        if (tokens.getTokenindex() == 0) {
            for (Iterator<PermissionDomainname> iterator = this.serverConfiguration.getPermissionDomainname()
                    .iterator(); iterator.hasNext();) {
                PermissionDomainname permissionDomainname = iterator.next();
                ECKey ecKey = permissionDomainname.getOutKey();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", ecKey.getPublicKeyAsHex()));
            }
        } else {
            PermissionedAddressesResponse permissionedAddressesResponse = this.getPrevTokenMultiSignAddressList(tokens);
            if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                    && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
                for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                    final String pubKeyHex = multiSignAddress.getPubKeyHex();
                    multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
                }
            }
        }

        signnumber++;
        tokens.setSignnumber(signnumber);

        upstreamToken2LocalServer(tokenInfo, basecoin, walletKeys.get(1), aesKey);

        for (int i = 2; i <= 3; i++) {
            ECKey outKey = walletKeys.get(i);
            pullBlockDoMultiSign(outKey, aesKey);
        }

        if (tokens.getTokenindex() == 0) {
            PermissionDomainname permissionDomainname = this.serverConfiguration.getPermissionDomainname().get(0);
            ECKey outKey = permissionDomainname.getOutKey();
            this.pullBlockDoMultiSign(outKey, aesKey);
        } else {
            ECKey outKey = this.walletKeys.get(1);
            pullBlockDoMultiSign(outKey, aesKey);
        }
    }
}
