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
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.MultiSignResponse;
import net.bigtangle.server.response.SearchMultiSignResponse;
import net.bigtangle.server.response.TokenSerialIndexResponse;
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
//        HashSet<String> tokenIdSet = new HashSet<String>();
//        for (MultiSign multiSign : multiSigns) tokenIdSet.add(multiSign.getTokenid());
//        HashSet<Tokens> tokensSet = this.store.getTokensHashsetByIdSet(tokenIdSet);
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
            if (transaction.getDatasignatire() == null) {
                signcount = 0;
            }
            else {
                String jsonStr = new String(transaction.getDatasignatire());
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

    public void multiSign(Block block) throws Exception {
        if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
            return;
        }
        Transaction transaction = block.getTransactions().get(0);
        if (transaction.getData() == null) {
            return;
        }
        byte[] buf = transaction.getData();
        TokenInfo tokenInfo = new TokenInfo().parse(buf);
        final Tokens tokens = tokenInfo.getTokens();
        if (tokens == null) {
            return;
        }
        Tokens tokens_ = this.store.getTokensInfo(tokens.getTokenid());
        if (tokens_ != null && tokens_.isTokenstop()) {
            return;
        }
        final TokenSerial tokenSerial = tokenInfo.getTokenSerial();
        if (tokenSerial == null) {
            return;
        }
        List<MultiSignAddress> multiSignAddresses = this.store.getMultiSignAddressListByTokenid(tokens.getTokenid());
        if (multiSignAddresses.size() == 0) {
            multiSignAddresses = tokenInfo.getMultiSignAddresses();
        }
        if (multiSignAddresses.size() == 0) {
            return;
        }
        HashMap<String, MultiSignAddress> multiSignAddressRes = new HashMap<String, MultiSignAddress>();
        for (MultiSignAddress multiSignAddress : multiSignAddresses) {
            multiSignAddressRes.put(multiSignAddress.getAddress(), multiSignAddress);
        }
        for (MultiSignAddress multiSignAddress : multiSignAddressRes.values()) {
            String tokenid = multiSignAddress.getTokenid();
            long tokenindex = tokenSerial.getTokenindex();
            String address = multiSignAddress.getAddress();
            int count = this.store.getCountMultiSignAlready(tokenid, tokenindex, address);
            if (count == 0) {
                MultiSign multiSign = new MultiSign();
                multiSign.setTokenid(tokenid);
                multiSign.setTokenindex(tokenindex);
                multiSign.setAddress(address);
                multiSign.setBlockhash(block.bitcoinSerialize());
                multiSign.setId(UUIDUtil.randomUUID());
                this.store.saveMultiSign(multiSign);
            } else {
                this.store.updateMultiSignBlockHash(tokenid, tokenindex, address, block.bitcoinSerialize());
            }
        }

        int signCount = 0;
        if (transaction.getDatasignatire() != null) {
            try {
                String jsonStr = new String(transaction.getDatasignatire());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> multiSignBies = Json.jsonmapper().readValue(jsonStr, List.class);
                HashMap<String, Map<String, Object>> multiSignBiesRes = new HashMap<String, Map<String, Object>>();
                for (Map<String, Object> multiSignBy : multiSignBies) {
                    String address = (String) multiSignBy.get("address");
                    multiSignBiesRes.put(address, multiSignBy);
                }
                this.store.updateMultiSignBlockBitcoinSerialize(tokens.getTokenid(), tokenSerial.getTokenindex(), block.bitcoinSerialize());
                for (Map<String, Object> multiSignBy : multiSignBiesRes.values()) {
                    String tokenid = (String) multiSignBy.get("tokenid");
                    int tokenindex = (Integer) multiSignBy.get("tokenindex");
                    String address = (String) multiSignBy.get("address");
                    this.store.updateMultiSign(tokenid, tokenindex, address, block.bitcoinSerialize(), 1);

                    byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                    byte[] data = transaction.getHash().getBytes();
                    byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                    boolean success = ECKey.verify(data, signature, pubKey);
                    if (success) {
                        signCount++;
                    }
                }
                for (MultiSignAddress multiSignAddress : multiSignAddressRes.values()) {
                    String address = multiSignAddress.getAddress();
                    if (!multiSignBiesRes.containsKey(address)) {
                        signCount = 0;
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // check sign number
        if (multiSignAddresses.size() == signCount) {
            // save block
            blockService.saveBlock(block);
        }
    }

    @Autowired
    private BlockService blockService;
}
