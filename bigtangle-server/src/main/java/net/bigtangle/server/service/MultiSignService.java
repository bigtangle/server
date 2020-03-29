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
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException.InsufficientSignaturesException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.core.response.MultiSignResponse;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.core.response.TokenIndexResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.data.SolidityState;
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
    @Autowired
    protected TokenDomainnameService tokenDomainnameService;
    @Autowired
    private BlockService blockService;

    public AbstractResponse getMultiSignListWithAddress(final String tokenid, String address)
            throws BlockStoreException {
        if (StringUtils.isBlank(tokenid)) {
            List<MultiSign> multiSigns = this.store.getMultiSignListByAddress(address);
            return MultiSignResponse.createMultiSignResponse(multiSigns);
        } else {
            List<MultiSign> multiSigns = this.store.getMultiSignListByTokenidAndAddress(tokenid, address);
            return MultiSignResponse.createMultiSignResponse(multiSigns);
        }
    }

    public AbstractResponse getCountMultiSign(String tokenid, long tokenindex, int sign) throws BlockStoreException {
        int count = this.store.getCountMultiSignNoSign(tokenid, tokenindex, sign);
        return MultiSignResponse.createMultiSignResponse(count);
    }

    public AbstractResponse getMultiSignListWithTokenid(String tokenid, Integer tokenindex, List<String> addresses,
            boolean isSign) throws Exception {
        HashSet<String> a = new HashSet<String>();
        if (addresses != null) {
            a = new HashSet<String>(addresses);
        }
        return getMultiSignListWithTokenid(tokenid, tokenindex, a, isSign);
    }

    public AbstractResponse getMultiSignListWithTokenid(String tokenid, Integer tokenindex, Set<String> addresses,
            boolean isSign) throws Exception {
        List<MultiSign> multiSigns = this.store.getMultiSignListByTokenid(tokenid, tokenindex, addresses, isSign);
        List<Map<String, Object>> multiSignList = new ArrayList<Map<String, Object>>();
        for (MultiSign multiSign : multiSigns) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("id", multiSign.getId());
            map.put("tokenid", multiSign.getTokenid());
            map.put("tokenindex", multiSign.getTokenindex());
            map.put("blockhashHex", multiSign.getBlockhashHex());
            map.put("sign", multiSign.getSign());
            map.put("address", multiSign.getAddress());
            Block block = this.networkParameters.getDefaultSerializer().makeBlock(multiSign.getBlockbytes());
            Transaction transaction = block.getTransactions().get(0);
            TokenInfo tokenInfo = new TokenInfo().parse(transaction.getData());
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
        return TokenIndexResponse.createTokenSerialIndexResponse(tokens.getTokenindex() + 1, tokens.getBlockHash());
    }

    public void saveMultiSign(Block block) throws BlockStoreException, Exception {
        // blockService.checkBlockBeforeSave(block);
        try {
            this.store.beginDatabaseBatchWrite();
            Transaction transaction = block.getTransactions().get(0);
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = new TokenInfo().parse(buf);
            final Token tokens = tokenInfo.getToken();

            // Enter the required multisign addresses
            List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();

            // Always needs domain owner signature

            multiSignAddresses.addAll(tokenDomainnameService
                    .queryDomainnameTokenMultiSignAddresses(Sha256Hash.wrap(tokens.getDomainNameBlockHash())));

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
                    multiSign.setBlockbytes(block.bitcoinSerialize());
                    multiSign.setId(UUIDUtil.randomUUID());
                    store.saveMultiSign(multiSign);
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
        } finally {
            this.store.defaultDatabaseBatchWrite();
        }
    }

    public void deleteMultiSign(Block block) throws BlockStoreException, Exception {
        try {

            Transaction transaction = block.getTransactions().get(0);
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = new TokenInfo().parse(buf);
            final Token token = tokenInfo.getToken();
            this.store.deleteMultiSign(token.getTokenid());
        } catch (Exception e) {
            // ignore
        }
    }

    public void signTokenAndSaveBlock(Block block, boolean allowConflicts) throws Exception {
        try {
            validatorService.checkTokenUnique(block);
            if (validatorService.checkFullTokenSolidity(block, 0, true) == SolidityState.getSuccessState()) {
                this.saveMultiSign(block);
                // check the block prototype and may do update

                blockService.saveBlock(checkBlockPrototype(block));
                deleteMultiSign(block);
            } else {
                // data save only on this server for multi signs, not in block.
                this.saveMultiSign(block);
            }
        } catch (InsufficientSignaturesException e) {
            this.saveMultiSign(block);

        }
    }

    private Block checkBlockPrototype(Block oldBlock) throws BlockStoreException, NoBlockException {

        int time = 60 * 60 * 8;
        if (System.currentTimeMillis() / 1000 - oldBlock.getTimeSeconds() > time) {
            Block block = blockService.getBlockPrototype();
            block.setBlockType(oldBlock.getBlockType());
            for (Transaction transaction : oldBlock.getTransactions()) {
                block.addTransaction(transaction);
            }
            block.solve();
            return block;
        } else {
            return oldBlock;
        }
    }

}
