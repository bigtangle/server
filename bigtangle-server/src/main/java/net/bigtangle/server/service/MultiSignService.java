package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.SearchMultiSignResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.UUIDUtil;

@Service
public class MultiSignService {

    private static final Logger log = LoggerFactory.getLogger(MultiSignService.class);
    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected ValidatorService validatorService;
    @Autowired
    protected NetworkParameters params;

    public AbstractResponse getMultiSignListWithAddress(final String tokenid, String address) throws BlockStoreException {
        if (StringUtils.isBlank(tokenid)) {
            List<MultiSign> multiSigns = this.store.getMultiSignListByAddress(address);
            return MultiSignResponse.createMultiSignResponse(multiSigns);
        }
        else {
            List<MultiSign> multiSigns = this.store.getMultiSignListByTokenidAndAddress(tokenid, address);
            return MultiSignResponse.createMultiSignResponse(multiSigns);
        }
    }

    public AbstractResponse getCountMultiSign(String tokenid, long tokenindex, int sign) throws BlockStoreException {
        int count = this.store.getCountMultiSignNoSign(tokenid, tokenindex, sign);
        return MultiSignResponse.createMultiSignResponse(count);
    }

    public AbstractResponse getMultiSignListWithTokenid(String tokenid, List<String> addresses, boolean isSign)
            throws Exception {
      return   getMultiSignListWithTokenid(tokenid, new HashSet<String>(addresses), isSign);
    }
    public AbstractResponse getMultiSignListWithTokenid(String tokenid, Set<String> addresses, boolean isSign)
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
            TokenInfo tokenInfo = TokenInfo.parse(transaction.getData());
            map.put("signnumber", tokenInfo.getToken().getSignnumber());
            map.put("tokenname", tokenInfo.getToken().getTokenname());

            Coin fromAmount = new Coin(tokenInfo.getToken().getAmount(), multiSign.getTokenid());
            map.put("amount", fromAmount);
            int signcount = 0;
            if (transaction.getDataSignature() == null) {
                signcount = 0;
            } else {
                String jsonStr = new String(transaction.getDataSignature());
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
                signcount = multiSignByRequest.getMultiSignBies().size();
            }
            map.put("signcount", signcount);
            multiSignList.add(map);
        }
        return SearchMultiSignResponse.createSearchMultiSignResponse(multiSignList);
    }

    @Autowired
    private NetworkParameters networkParameters;

    public AbstractResponse getNextTokenSerialIndex(String tokenid) throws BlockStoreException {
        Token tokens = this.store.getCalMaxTokenIndex(tokenid);
        return TokenIndexResponse.createTokenSerialIndexResponse(tokens.getTokenindex() + 1, tokens.getBlockhash());
    }

    public void saveMultiSign(Block block) throws BlockStoreException, Exception {
        try {
            this.store.beginDatabaseBatchWrite();
            Transaction transaction = block.getTransactions().get(0);
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = TokenInfo.parse(buf);
            final Token tokens = tokenInfo.getToken();

            // Enter the required multisign addresses
            List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
            
            // Always needs domain owner signature
            Token prevToken = store.getToken(tokens.getDomainPredecessorBlockHash());
            List<MultiSignAddress> permissionedAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(
                    prevToken.getTokenid(), prevToken.getBlockhash());
            for (MultiSignAddress permissionedAddress : permissionedAddresses) {
            	permissionedAddress.setTokenid(tokens.getTokenid());
            }
            multiSignAddresses.addAll(permissionedAddresses);

            // Add the entries to DB
            for (MultiSignAddress multiSignAddress : multiSignAddresses) {
                byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
                multiSignAddress.setAddress(ECKey.fromPublicOnly(pubKey).toAddress(networkParameters).toBase58());

                String tokenid = multiSignAddress.getTokenid();
                long tokenindex = tokens.getTokenindex();
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
            if (transaction.getDataSignature() != null) {
                String jsonStr = new String(transaction.getDataSignature());
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
                for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
                    String tokenid = multiSignBy.getTokenid();
                    int tokenindex = (int) multiSignBy.getTokenindex();
                    String address = multiSignBy.getAddress();
                    store.updateMultiSign(tokenid, tokenindex, address, block.bitcoinSerialize(), 1);
                }
            }
            store.updateMultiSignBlockBitcoinSerialize(tokens.getTokenid(), tokens.getTokenindex(),
                    block.bitcoinSerialize());
            this.store.commitDatabaseBatchWrite();

        } catch (Exception e) {
            log.error("", e);
            this.store.abortDatabaseBatchWrite();
        }
    }

    public boolean checkMultiSignPre(Block block, boolean allowConflicts) throws BlockStoreException, Exception {
        try {
            
            // TODO delete this method, replace calls with validatorService.checkTokenSolidity
            
            if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
                throw new BlockStoreException("block transaction is empty");
            }
            Transaction transaction = block.getTransactions().get(0);
            if (transaction.getData() == null) {
                throw new BlockStoreException("block transaction data is null");
            }
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = TokenInfo.parse(buf);
            final Token tokens = tokenInfo.getToken();
            if (tokens == null) {
                throw new BlockStoreException("tokeninfo is null");
            }
            Token tokens0 = store.getToken(tokens.getBlockhash());
            if (!allowConflicts && tokens0 != null && tokens0.isTokenstop()) {
                throw new BlockStoreException("tokeninfo can not reissue");
            }

            if (tokens.getSignnumber() <= 0) {
                throw new BlockStoreException("signnumber value <= 0");
            }
            // as conflict
            if (!allowConflicts && (tokens0 != null && tokens0.isConfirmed() && tokens.getTokenindex() <= 1L)) {
                throw new BlockStoreException("tokens already exist");
            }
            
            if (StringUtils.isBlank(tokens.getDomainName())) {
                throw new BlockStoreException("tokens domainname empty");
            }
            
            List<MultiSignAddress> multiSignAddresses;
            if (tokens.getTokentype() == TokenType.domainname.ordinal()) {
                multiSignAddresses = tokenInfo.getMultiSignAddresses();
            } else {
                String prevblockhash = tokens.getPrevblockhash();
                multiSignAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(tokens.getTokenid(),
                        prevblockhash);
            }

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
            if (tokenInfo.getToken().getSignnumber() > multiSignAddresses.size()) {
                throw new BlockStoreException("signnumber multisignaddresse list size not eq");
            }

            HashMap<String, MultiSignAddress> multiSignAddressRes = new HashMap<String, MultiSignAddress>();
            for (MultiSignAddress multiSignAddress : multiSignAddresses) {
                multiSignAddressRes.put(multiSignAddress.getAddress(), multiSignAddress);
            }
            int signCount = 0;
            if (transaction.getDataSignature() != null) {
                String jsonStr = new String(transaction.getDataSignature());
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
                for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
                    String address = multiSignBy.getAddress();
                    if (!multiSignAddressRes.containsKey(address)) {
                        throw new BlockStoreException("multisignby address not in address list");
                    }
                }
                HashMap<String, MultiSignBy> multiSignBiesRes = new HashMap<String, MultiSignBy>();
                for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
                    String address = multiSignBy.getAddress();
                    multiSignBiesRes.put(address, multiSignBy);
                }
                for (MultiSignBy multiSignBy : multiSignBiesRes.values()) {
                    byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
                    byte[] data = transaction.getHash().getBytes();
                    byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());
                    boolean success = ECKey.verify(data, signature, pubKey);
                    if (success) {
                        signCount++;
                    } else {
                        throw new BlockStoreException("multisign signature error");
                    }
                }

                /*for (MultiSignAddress multiSignAddress : multiSignAddressRes.values()) {
                    String address = multiSignAddress.getAddress();
                    if (!multiSignBiesRes.containsKey(address)) {
                        signCount = 0;
                        break;
                    }
                }*/
            }
            int signnumber = (int) (tokens0 == null ? tokens.getSignnumber() : tokens0.getSignnumber());
            return signCount >= signnumber;
        } catch (Exception e) {
            log.error(" error", e);
            throw new BlockStoreException("multisign error");
        }
    }

    public void multiSign(Block block, boolean allowConflicts) throws Exception {
        if (validatorService.checkFullTokenSolidity(block, 0, false) == SolidityState.getSuccessState()) {
            this.saveMultiSign(block);
            blockService.saveBlock(block);
        } else {
            // data save only on this server, not in block.
            this.saveMultiSign(block);
        }
    }

    @Autowired
    private BlockService blockService;
}
