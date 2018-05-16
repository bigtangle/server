/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.utils;

import java.util.Map;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.script.Script;

public abstract class MapToBeanMapperUtil {

    public static Coin parseCoin(Map<String, Object> map) {
        if (map == null)
            return null;
        long value = 0l;
        if (map.get("value") instanceof Integer) {
            value = (Integer) map.get("value");
        } else if (map.get("value") instanceof Long) {
            value = (Long) map.get("value");
        } else {
            value = new Long(map.get("value").toString());
        }

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
        // System.out.println("tokenHex==" + tokenHex);
        boolean spent = (Boolean) map.get("spent");

        boolean confirmed = (Boolean) map.get("confirmed");
        boolean spendPending = (Boolean) map.get("spendPending");

        UTXO output = new UTXO(hash, index, amount, height, coinbase, new Script(Utils.HEX.decode(scriptHex)), address,
                blockhash, fromaddress, description, tokenHex, spent, confirmed, spendPending);
        return output;
    }

    public static BlockEvaluation parseBlockEvaluation(Map<String, Object> map) {
        if (map == null)
            return null;
        String blockHexStr = (String) map.get("blockHexStr");
        Sha256Hash hash = Sha256Hash.wrap(Utils.HEX.decode(blockHexStr));

        long rating = Long.parseLong(map.get("rating").toString());
        long depth = Long.parseLong(map.get("depth").toString());
        long cumulativeWeight = Long.parseLong(map.get("cumulativeWeight").toString());
        long height = Long.parseLong(map.get("height").toString());

        boolean solid = (boolean) map.get("solid");
        boolean milestone = (boolean) map.get("milestone");
        boolean maintained = (boolean) map.get("maintained");
        boolean rewardValid = (boolean) map.get("rewardValid");
        long milestoneDepth = Long.parseLong(map.get("milestoneDepth").toString());
        long milestoneLastUpdateTime = (long) map.get("milestoneLastUpdateTime");
        long insertTime = (long) map.get("insertTime");
        return BlockEvaluation.build(hash, rating, depth, cumulativeWeight, solid, height, milestone,
                milestoneLastUpdateTime, milestoneDepth, insertTime, maintained, rewardValid);

    }
}
