/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.model.TipsViewModel;
import com.bignetcoin.store.FullPrunedBlockStore;

@Service
public class TipsService {

	private final Logger log = LoggerFactory.getLogger(TipsService.class);
	@Autowired
	private MilestoneService milestone;

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	private TipsViewModel tipsViewModel;
	@Autowired
	private BlockValidator blockValidator;
	@Autowired
	protected NetworkParameters networkParameters;
	
	public Pair<Sha256Hash, Sha256Hash> blockPairToApprove(final int iterations, Random seed) throws Exception {
		Sha256Hash b1 = blockToApprove(iterations, seed);
		Sha256Hash b2 = blockToApprove(iterations, seed);
		
		//TODO validate dynamic validity here and if not, try to reverse until no conflicts
		
		return Pair.of(b1, b2);
	}

	public Sha256Hash blockToApprove(final int iterations, Random seed) throws Exception {
		List<BlockEvaluation> blockEvaluations = blockService.getSolidBlockEvaluations();
		Map<Sha256Hash, Long> cumulativeWeights = blockEvaluations.stream()
				.collect(Collectors.toMap(BlockEvaluation::getBlockhash, BlockEvaluation::getCumulativeWeight));
		Sha256Hash entryPointTipSha256Hash = entryPoint();
		return markovChainMonteCarlo(entryPointTipSha256Hash, cumulativeWeights, iterations, seed);
	}

	Sha256Hash entryPoint() throws Exception {
		return networkParameters.getGenesisBlock().getHash();
		//TODO use multiple (iterations many) entry points in depth interval
	}

	Sha256Hash markovChainMonteCarlo(final Sha256Hash entryPoint, final Map<Sha256Hash, Long> cumulativeWeights,
			final int iterations, final Random seed) throws Exception {

		// Perform MCMC tip selection iterations-times 
		Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
		for (int i = 0; i < iterations; i++) {
			Sha256Hash tail = randomWalk(entryPoint, cumulativeWeights, seed);
			if (monteCarloIntegrations.containsKey(tail)) {
				monteCarloIntegrations.put(tail, monteCarloIntegrations.get(tail) + 1);
			} else {
				monteCarloIntegrations.put(tail, 1);
			}
		}

		// Randomly select one of the found tips weighted by their selection count
		int selectionRealization = seed.nextInt(iterations);
		for (Sha256Hash tip : monteCarloIntegrations.keySet()) {
			selectionRealization -= monteCarloIntegrations.get(tip);
			if (selectionRealization <= 0) {
				return tip;
			}
		}

		throw new Exception("Tip selection algorithm failed.");
	}

	Sha256Hash randomWalk(Sha256Hash tip, final Map<Sha256Hash, Long> cumulativeWeights, Random seed)
			throws Exception {
		
		// Repeatedly perform transitions until the final tip is found
		while (tip != null) {
			List<Sha256Hash> approvers = blockService.getSolidApproverBlockHashes(tip);
			if (approvers.size() == 0) {
				return tip;
			} else if (approvers.size() == 1) {
				tip = approvers.get(0);
			} else {
				Sha256Hash[] tipApprovers = approvers.toArray(new Sha256Hash[approvers.size()]);
				double[] transitionWeights = new double[tipApprovers.length];
				double transitionWeightSum = 0;
				long tipCumulativeweight = cumulativeWeights.containsKey(tip) ? cumulativeWeights.get(tip) : 1;
				
				// Calculate the unnormalized transition weights of all approvers as ((Hx-Hy)^-3)
				for (int i = 0; i < tipApprovers.length; i++) {
					// transition probability = 
					transitionWeights[i] = Math
							.pow(tipCumulativeweight - cumulativeWeights.get(tipApprovers[i]), -3);
					transitionWeightSum += transitionWeights[i];
				}
				
				// Randomly select one of the approvers weighted by their transition probabilities
				double transitionRealization = seed.nextDouble() * transitionWeightSum;
				for (int i = 0; i < tipApprovers.length; i++) {
					transitionRealization -= transitionWeights[i];
					if (transitionRealization <= 0) {
						tip = tipApprovers[i];
						break;
					}
				}
			}
		}
		
		return tip;
	}

	public void addTip(Sha256Hash blockhash) throws BlockStoreException {
		StoredBlock block = store.get(blockhash);
		store.deleteTip(block.getHeader().getPrevBlockHash());
		store.deleteTip(block.getHeader().getPrevBranchBlockHash());
		store.deleteTip(blockhash);
		store.insertTip(blockhash);
	}
}
