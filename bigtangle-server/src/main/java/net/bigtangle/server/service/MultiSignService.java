package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.SearchMultiSignResponse;
import net.bigtangle.core.http.server.resp.TokenSerialIndexResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.UUIDUtil;

@Service
public class MultiSignService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getMultiSignListWithAddress(String address) throws BlockStoreException {
        List<MultiSign> multiSigns = this.store.getMultiSignListByAddress(address);
        return MultiSignResponse.createMultiSignResponse(multiSigns);
    }

    public AbstractResponse getCountMultiSign(String tokenid, long tokenindex, int sign) throws BlockStoreException {
        int count = this.store.getCountMultiSignNoSign(tokenid, tokenindex, sign);
        return MultiSignResponse.createMultiSignResponse(count);
    }

    public AbstractResponse getMultiSignListWithTokenid(String tokenid, List<String> addresses, boolean isSign)
            throws Exception {
        List<MultiSign> multiSigns = this.store.getMultiSignListByTokenid(tokenid, addresses, isSign);
        List<Map<String, Object>> multiSignList = new ArrayList<Map<String, Object>>();
        for (MultiSign multiSign : multiSigns) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("id", multiSign.getId());
            map.put("tokenid", multiSign.getTokenid());
            map.put("tokenindex", multiSign.getTokenindex());
            map.put("blockhashHex", multiSign.getBlockhashHex());
            map.put("sign", multiSign.getSign());
            map.put("address", multiSign.getAddress());
            Block block = this.networkParameters.getDefaultSerializer().makeBlock(multiSign.getBlockhash());
            Transaction transaction = block.getTransactions().get(0);
            TokenInfo tokenInfo = new TokenInfo().parse(transaction.getData());
            map.put("signnumber", tokenInfo.getTokens().getSignnumber());
            map.put("tokenname", tokenInfo.getTokens().getTokenname());
            Coin fromAmount = Coin.valueOf(tokenInfo.getTokenSerial().getAmount(), multiSign.getTokenid());
            map.put("amount", fromAmount.toPlainString());
            int signcount = 0;
            if (transaction.getDataSignature() == null) {
                signcount = 0;
            } else {
                String jsonStr = new String(transaction.getDataSignature());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> multiSignBies = Json.jsonmapper().readValue(jsonStr, List.class);
                signcount = multiSignBies.size();
            }
            map.put("signcount", signcount);
            multiSignList.add(map);
        }
        return SearchMultiSignResponse.createSearchMultiSignResponse(multiSignList);
    }

    @Autowired
    private NetworkParameters networkParameters;

    public AbstractResponse getNextTokenSerialIndex(String tokenid) throws BlockStoreException {
        int count = this.store.getCountTokenSerialNumber(tokenid);
        return TokenSerialIndexResponse.createTokenSerialIndexResponse(count + 1);
    }

    /*
     * check unique as conflicts
     */
    // TODO change to unified logic (see mining rewards)
    public boolean checkMultiSignPre(Block block, boolean allowConflicts) throws BlockStoreException, Exception {

        // Check these TODOs and make sure they are implemented
        // TODO token ids of tx must be equal to blocks token id
        // TODO token issuance sum must not overflow
        // TODO signature for coinbases must be correct (equal to pubkey
        // hash of tokenid)

        if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
            throw new BlockStoreException("block transaction is empty");
        }
        Transaction transaction = block.getTransactions().get(0);
        if (transaction.getData() == null) {
            // FIXME this transaction data is not serialized properly.
            throw new BlockStoreException("block transaction data is null");
        }
        byte[] buf = transaction.getData();
        TokenInfo tokenInfo = new TokenInfo().parse(buf);
        final Tokens tokens = tokenInfo.getTokens();
        if (tokens == null) {
            throw new BlockStoreException("tokeninfo is null");
        }
        Tokens tokens_ = store.getTokensInfo(tokens.getTokenid());
        if (!allowConflicts && tokens_ != null && tokens_.isTokenstop()) {
            throw new BlockStoreException("tokeninfo can not reissue");
        }

        if (tokens.getSignnumber() <= 0)
            throw new BlockStoreException("signnumber value <= 0");

        final TokenSerial tokenSerial = tokenInfo.getTokenSerial();
        if (tokenSerial == null) {
            throw new BlockStoreException("tokenserial is null");
        }
        // as conflict
        if (!allowConflicts && (tokens_ != null && tokenSerial.getTokenindex() == 1L)) {
            throw new BlockStoreException("tokens already existed");
        }

        List<MultiSignAddress> multiSignAddresses = store.getMultiSignAddressListByTokenid(tokens.getTokenid());
        if (multiSignAddresses.size() == 0) {
            multiSignAddresses = tokenInfo.getMultiSignAddresses();
        }
        if (multiSignAddresses.size() == 0) {
            throw new BlockStoreException("multisignaddresse list size = 0");
        }

        for (MultiSignAddress multiSignAddress : multiSignAddresses) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            multiSignAddress.setAddress(ECKey.fromPublicOnly(pubKey).toAddress(networkParameters).toBase58());
        }
        for (MultiSignAddress multiSignAddress : multiSignAddresses) {
            String tokenid = multiSignAddress.getTokenid();
            long tokenindex = tokenSerial.getTokenindex();
            String address = multiSignAddress.getAddress();
            int count = store.getCountMultiSignAlready(tokenid, tokenindex, address);
            if (count == 0) {
                MultiSign multiSign = new MultiSign();
                multiSign.setTokenid(tokenid);
                multiSign.setTokenindex(tokenindex);
                multiSign.setAddress(address);
                multiSign.setBlockhash(block.bitcoinSerialize());
                multiSign.setId(UUIDUtil.randomUUID());
                store.saveMultiSign(multiSign);
            } else {
                store.updateMultiSignBlockHash(tokenid, tokenindex, address, block.bitcoinSerialize());
            }
        }

        if (tokenInfo.getTokens().getSignnumber() > multiSignAddresses.size()) {
            throw new BlockStoreException("signnumber multisignaddresse list size not eq");
        }

        HashMap<String, MultiSignAddress> multiSignAddressRes = new HashMap<String, MultiSignAddress>();
        for (MultiSignAddress multiSignAddress : multiSignAddresses) {
            multiSignAddressRes.put(multiSignAddress.getAddress(), multiSignAddress);
        }
        int signCount = 0;
        if (transaction.getDataSignature() != null) {
            String jsonStr = new String(transaction.getDataSignature());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> multiSignBies = Json.jsonmapper().readValue(jsonStr, List.class);
            for (Map<String, Object> multiSignBy : multiSignBies) {
                String address = (String) multiSignBy.get("address");
                if (!multiSignAddressRes.containsKey(address)) {
                    throw new BlockStoreException("multisignby address not in address list");
                }
            }
            HashMap<String, Map<String, Object>> multiSignBiesRes = new HashMap<String, Map<String, Object>>();
            for (Map<String, Object> multiSignBy : multiSignBies) {
                String address = (String) multiSignBy.get("address");
                multiSignBiesRes.put(address, multiSignBy);
            }
            for (Map<String, Object> multiSignBy : multiSignBiesRes.values()) {
                byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                byte[] data = transaction.getHash().getBytes();
                byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                boolean success = ECKey.verify(data, signature, pubKey);
                if (success) {
                    signCount++;
                } else {
                    throw new BlockStoreException("multisign signature error");
                }
            }
            for (Map<String, Object> multiSignBy : multiSignBiesRes.values()) {
                String tokenid = (String) multiSignBy.get("tokenid");
                int tokenindex = (Integer) multiSignBy.get("tokenindex");
                String address = (String) multiSignBy.get("address");
                store.updateMultiSign(tokenid, tokenindex, address, block.bitcoinSerialize(), 1);
            }
            store.updateMultiSignBlockBitcoinSerialize(tokens.getTokenid(), tokenSerial.getTokenindex(),
                    block.bitcoinSerialize());
            for (MultiSignAddress multiSignAddress : multiSignAddressRes.values()) {
                String address = multiSignAddress.getAddress();
                if (!multiSignBiesRes.containsKey(address)) {
                    signCount = 0;
                    break;
                }
            }
        }
        int signnumber = (int) (tokens_ == null ? tokens.getSignnumber() : tokens_.getSignnumber());
        return signCount >= signnumber;
    }

    public void multiSign(Block block, boolean allowConflicts) throws Exception {
        if (this.checkMultiSignPre(block, allowConflicts)) {
            blockService.saveBlock(block);
        }
    }

    @Autowired
    private BlockService blockService;
}
