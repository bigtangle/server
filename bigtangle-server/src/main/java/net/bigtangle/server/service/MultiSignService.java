package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
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

            Coin fromAmount = Coin.valueOf(tokenInfo.getTokens().getAmount(), multiSign.getTokenid());
            map.put("amount", fromAmount.toPlainString());
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
            TokenInfo tokenInfo = new TokenInfo().parse(buf);
            final Token tokens = tokenInfo.getTokens();

            String prevblockhash = tokens.getPrevblockhash();
            List<MultiSignAddress> multiSignAddresses = store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokens.getTokenid(), prevblockhash);
            if (multiSignAddresses.size() == 0) {
                multiSignAddresses = tokenInfo.getMultiSignAddresses();
            }

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

    /*
     * TODO move to fullP...
     * check unique as conflicts
     */
    public boolean checkToken(Block block, boolean allowConflicts) throws VerificationException {
        try {
            // TODO stop using json parser, also either drop null checks
            // entirely or get all of them
            // Unnecessary checks
            if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
                throw new VerificationException("block has no transaction");
            }
            Transaction tx = block.getTransactions().get(0);
            if (tx.getData() == null) {
                throw new VerificationException("transaction has no data");
            }
            TokenInfo currentToken = new TokenInfo().parse(tx.getData());
            if (currentToken.getTokens() == null) {
                throw new VerificationException("getTokens is null");
            }
            if (currentToken.getMultiSignAddresses() == null) {
                throw new VerificationException("getMultiSignAddresses is null");
            }

            // No BIGs
            if (currentToken.getTokens().getTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
                throw new VerificationException("Not allowed");
            }
            

            // Check all token fields
            if (currentToken.getTokens().getAmount() > Long.MAX_VALUE) {
                // TODO check that amount of all tokens is lower than Long.MAX_VALUE
                throw new VerificationException("Too many tokens");
            }
            if (currentToken.getTokens().getDescription().length() > 500) {
                // TODO define max values
                throw new VerificationException("Too long");
            }
            if (currentToken.getTokens().getSignnumber() < 0) {
                // TODO define max values
                throw new VerificationException("Invalid sign number");
            }
            if (currentToken.getTokens().getTokenname().length() > 50) {
                // TODO define max values
                throw new VerificationException("Too long");
            }
            if (currentToken.getTokens().getUrl() != null && currentToken.getTokens().getUrl().length() > 100) {
                // TODO define max values
                throw new VerificationException("Too long");
            }
            

            // Check previous issuance hash exists or initial issuance
            if ((currentToken.getTokens().getPrevblockhash().equals("")
                    && currentToken.getTokens().getTokenindex() != 0)
                    || (!currentToken.getTokens().getPrevblockhash().equals("")
                            && currentToken.getTokens().getTokenindex() == 0)) {
                throw new VerificationException("Must reference a previous block if not index 0");
            }

            if (currentToken.getTokens().getTokenindex() != 0) {
            }
            

            // Must define enough permissioned addresses
            if (currentToken.getTokens().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
                throw new VerificationException("Cannot fulfill required sign number from multisign address list");
            }

            // Get permissioned addresses
            Token prevToken = null;
            List<MultiSignAddress> permissionedAddresses = null;
            // If not initial issuance, we check according to the previous token
            if (currentToken.getTokens().getTokenindex() != 0) {
                // Previous issuance must exist to check solidity
                prevToken = store.getToken(currentToken.getTokens().getPrevblockhash());
                if (prevToken == null) {
                    throw new VerificationException("Previous token does not exist");
                }

                // TODO what is multiserial?
                // Compare members of previous and current issuance
                if (!currentToken.getTokens().getTokenid().equals(prevToken.getTokenid())) {
                    throw new VerificationException("Wrong token ID");
                }
                if (currentToken.getTokens().getTokenindex() != prevToken.getTokenindex() + 1) {
                    throw new VerificationException("Wrong token index");
                }
                if (!currentToken.getTokens().getTokenname().equals(prevToken.getTokenname())) {
                    throw new VerificationException("Cannot change token name");
                }
                if (currentToken.getTokens().getTokentype() != prevToken.getTokentype()) {
                    throw new VerificationException("Cannot change token type");
                }

                // Must allow more issuances
                if (prevToken.isTokenstop()) {
                    throw new VerificationException("Previous token does not allow further issuance");
                }

                // Get addresses allowed to reissue
                permissionedAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(prevToken.getTokenid(),
                        prevToken.getPrevblockhash());
            } else {
                permissionedAddresses = new ArrayList<>();
                
                
                // TODO add pubkey tokenid instead:
                //permissionedAddresses.add(){
                permissionedAddresses = currentToken.getMultiSignAddresses();
            }

            for (MultiSignAddress multiSignAddress : permissionedAddresses) {
                byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
                multiSignAddress.setAddress(ECKey.fromPublicOnly(pubKey).toAddress(networkParameters).toBase58());
            }
            HashMap<String, MultiSignAddress> multiSignAddressRes = new HashMap<String, MultiSignAddress>();
            for (MultiSignAddress multiSignAddress : permissionedAddresses) {
                multiSignAddressRes.put(multiSignAddress.getAddress(), multiSignAddress);
            }

            int signatureCount = 0;
            // Get signatures from transaction
            String jsonStr = new String(tx.getDataSignature());
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
            for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
                String address = multiSignBy.getAddress();
                if (!multiSignAddressRes.containsKey(address)) {
                    throw new VerificationException("multisignby address not in permissioned address list");
                }
            }

            // Count successful signature verifications
            for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
                byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
                byte[] data = tx.getHash().getBytes();
                byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());
                if (ECKey.verify(data, signature, pubKey))
                    signatureCount++;
            }

            // Return whether sufficient signatures exist
            int requiredSignatureCount = prevToken == null ? currentToken.getTokens().getSignnumber()
                    : prevToken.getSignnumber();
            return signatureCount >= requiredSignatureCount;
        } catch (Exception e) {
            log.info("token error", e);
            throw new VerificationException("token error", e);
        }
    }

    public boolean checkMultiSignPre(Block block, boolean allowConflicts) throws BlockStoreException, Exception {
        try {
            // TODO this has nothing to do with tokens, remove any mentions of
            // tokens

            if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
                throw new BlockStoreException("block transaction is empty");
            }
            Transaction transaction = block.getTransactions().get(0);
            if (transaction.getData() == null) {
                throw new BlockStoreException("block transaction data is null");
            }
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = new TokenInfo().parse(buf);
            final Token tokens = tokenInfo.getTokens();
            if (tokens == null) {
                throw new BlockStoreException("tokeninfo is null");
            }
            Token tokens0 = store.getToken(tokens.getTokenid());
            if (!allowConflicts && tokens0 != null && tokens0.isTokenstop()) {
                throw new BlockStoreException("tokeninfo can not reissue");
            }

            if (tokens.getSignnumber() <= 0) {
                throw new BlockStoreException("signnumber value <= 0");
            }
            // as conflict
            if (!allowConflicts && (tokens0 != null && tokens.getTokenindex() <= 1L)) {
                throw new BlockStoreException("tokens already existed");
            }

            String prevblockhash = tokens.getPrevblockhash();
            List<MultiSignAddress> multiSignAddresses = store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokens.getTokenid(), prevblockhash);
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

                for (MultiSignAddress multiSignAddress : multiSignAddressRes.values()) {
                    String address = multiSignAddress.getAddress();
                    if (!multiSignBiesRes.containsKey(address)) {
                        signCount = 0;
                        break;
                    }
                }
            }
            int signnumber = (int) (tokens0 == null ? tokens.getSignnumber() : tokens0.getSignnumber());
            return signCount >= signnumber;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BlockStoreException("multisign error");
        }
    }

    public void multiSign(Block block, boolean allowConflicts) throws Exception {
        // TODO rethink multisign, cannot save unsolid incomplete, use own table

        if (this.checkMultiSignPre(block, allowConflicts)) {
            // data save only on this server, not in block.
            this.saveMultiSign(block);
            blockService.saveBlock(block);
        } else {
            this.saveMultiSign(block);
        }
    }

    @Autowired
    private BlockService blockService;
}
