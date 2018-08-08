package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.SubtangleConfiguration;
import net.bigtangle.utils.OkHttp3Util;

@Service
public class SubtangleService {

	public void giveMoneyToTargetAccount() throws Exception {
		ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(subtangleConfiguration.getPubKeyHex()));
		List<ECKey> keys = new ArrayList<>();
		keys.add(ecKey);

		List<UTXO> outputs = this.getRemoteBalances(false, keys);
		if (outputs.isEmpty()) {
			return;
		}

		for (UTXO output : outputs) {
			String blockHashHex = output.getBlockHashHex();
			Block block = this.getRemoteBlock(blockHashHex);
			for (Transaction transaction : block.getTransactions()) {
				if (transaction.getSubtangleID() == null) {
					continue;
				}
			}
			Coin coinbase = output.getValue();
			
		}
	}

	public List<UTXO> getRemoteBalances(boolean withZero, List<ECKey> keys) throws Exception {
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		List<String> keyStrHex000 = new ArrayList<String>();

		for (ECKey ecKey : keys) {
			keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
		}

		String contextRoot = subtangleConfiguration.getParentContextRoot();
		String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

		for (UTXO utxo : getBalancesResponse.getOutputs()) {
			if (withZero) {
				listUTXO.add(utxo);
			} else if (utxo.getValue().getValue() > 0) {
				listUTXO.add(utxo);
			}
		}

		return listUTXO;
	}
	
	public Block getRemoteBlock(String blockHashHex) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", blockHashHex);
        String contextRoot = subtangleConfiguration.getParentContextRoot();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        return block;
	}
	
	@Autowired
	private NetworkParameters networkParameters;

	@Autowired
	private SubtangleConfiguration subtangleConfiguration;
}
