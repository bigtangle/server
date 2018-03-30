package org.bitcoinj.utils;

import java.util.Map;

import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;

public abstract class MapToBeanMapperUtil {

    public static Coin parseCoin(Map<String, Object> map) {
        if (map == null)
            return null;
        long value = (Integer) map.get("value");
        String tokenHex = (String) map.get("tokenHex");
        return Coin.valueOf(value, Utils.HEX.decode(tokenHex));
    }

    @SuppressWarnings("unchecked")
    public static UTXO parseUTXO(Map<String, Object> map) {
        if (map == null)
            return null;
        Sha256Hash hash = Sha256Hash.wrap((String) map.get("hashHex"));
        long index = (Integer) map.get("index");
        Coin amount = parseCoin((Map<String, Object>) map.get("value"));
        long height = (Integer) map.get("height");
        boolean coinbase = (Boolean) map.get("coinbase");
        String scriptHex = (String) map.get("scriptHex");
        String address = (String) map.get("address");
        String fromaddress = (String) map.get("fromaddress");
        Sha256Hash blockhash = Sha256Hash.wrap((String) map.get("blockHashHex"));
        String description = (String) map.get("description");
        String tokenHex = (String) amount.getTokenHex();
        System.out.println("tokenHex==" + tokenHex);
        boolean spent = (Boolean) map.get("spent");
        
        // FIXME
        boolean confirmed = (Boolean) map.get("confirmed");
        boolean spendPending = (Boolean) map.get("spendpending");
        
        UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(Utils.HEX.decode(scriptHex)), address,
                blockhash, fromaddress, description, Utils.HEX.decode(tokenHex), spent, confirmed, spendPending);
        return output;
    }

    public static BlockEvaluation parseBlockEvaluation(Map<String, Object> map) {
        if (map == null)
            return null;
        String blockHexStr = (String) map.get("blockHexStr");
        Sha256Hash hash = Sha256Hash.wrap(Utils.HEX.decode(blockHexStr));
        long rating = (Integer) map.get("rating");
        long depth = (Integer) map.get("depth");
        long cumulativeWeight = (Integer) map.get("cumulativeWeight");
        long height = (Integer) map.get("height");
        return BlockEvaluation.build(hash, rating, depth, cumulativeWeight, true, height, true, 0);

    }
}
