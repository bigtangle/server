/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.server.model.TipsViewModel;
import net.bigtangle.store.FullPrunedBlockStore;

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
	
	public Sha256Hash getRatingTip() throws Exception {
		SecureRandom seed = new SecureRandom();		
		return getMCMCSelectedBlock(getRatingUpdateEntryPoint(), seed);
	}
	
	public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPairToApprove() throws Exception {
		SecureRandom seed = new SecureRandom();		
		Pair<Sha256Hash, TreeSet<BlockEvaluation>> b1 = getSingleValidatedBlock(seed);
		Pair<Sha256Hash, TreeSet<BlockEvaluation>> b2 = getSingleValidatedBlock(seed);
		
		//TODO validate dynamic validity here and if not, try to reverse until no conflicts
		//Specifically, we only need to check for candidate-candidate conflicts in the union of both approved blocks and reverse the nearest one
		// TODO for now just copy from milestoneservice, afterwards refactor maybe
		
		return Pair.of(b1.getLeft(), b2.getLeft());
	}
	
	private Pair<Sha256Hash, TreeSet<BlockEvaluation>> getSingleValidatedBlock(Random seed) throws Exception {
		Sha256Hash blockHash = getMCMCSelectedBlock(getValidationEntryPoint(), seed);
		BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(blockHash);
		
		//TODO validate dynamic validity here and if not, try to reverse until no conflicts
		//Specifically, we check for milestone-candidate-conflicts + candidate-candidate-conflicts and reverse until there are no such conflicts
		//Also returns all approved non-milestone blocks in topological ordering
		// TODO for now just copy resolveundoableconflicts+co from milestoneservice, afterwards refactor 
		
		return Pair.of(blockHash, null);
	}

	private Sha256Hash getMCMCSelectedBlock(Sha256Hash entryPoint, Random seed) throws Exception {
		List<BlockEvaluation> blockEvaluations = blockService.getSolidBlockEvaluations();
		Map<Sha256Hash, Long> cumulativeWeights = blockEvaluations.stream()
				.collect(Collectors.toMap(BlockEvaluation::getBlockhash, BlockEvaluation::getCumulativeWeight));
		return randomWalk(entryPoint, cumulativeWeights, seed);
	}

	private Sha256Hash getRatingUpdateEntryPoint() throws Exception {
		// TODO entry points select from further back for rating (milestonedepth y1-y2)
		return networkParameters.getGenesisBlock().getHash();
	}

	private Sha256Hash getValidationEntryPoint() throws Exception {
		// TODO entry points select from milestone (milestonedepth x-0)
		return networkParameters.getGenesisBlock().getHash();
	}

//	Sha256Hash markovChainMonteCarlo(final Sha256Hash entryPoint, final Map<Sha256Hash, Long> cumulativeWeights,
//			final int iterations, final Random seed) throws Exception {
//
//		// Perform MCMC tip selection iterations-times 
//		Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
//		for (int i = 0; i < iterations; i++) {
//			Sha256Hash tail = randomWalk(entryPoint, cumulativeWeights, seed);
//			if (monteCarloIntegrations.containsKey(tail)) {
//				monteCarloIntegrations.put(tail, monteCarloIntegrations.get(tail) + 1);
//			} else {
//				monteCarloIntegrations.put(tail, 1);
//			}
//		}
//
//		// Randomly select one of the found tips weighted by their selection count
//		int selectionRealization = seed.nextInt(iterations);
//		for (Sha256Hash tip : monteCarloIntegrations.keySet()) {
//			selectionRealization -= monteCarloIntegrations.get(tip);
//			if (selectionRealization <= 0) {
//				return tip;
//			}
//		}
//
//		throw new Exception("Tip selection algorithm failed.");
//	}

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

	// TODO move this somewhere else
	public void addTip(Sha256Hash blockhash) throws BlockStoreException {
		StoredBlock block = store.get(blockhash);
		store.deleteTip(block.getHeader().getPrevBlockHash());
		store.deleteTip(block.getHeader().getPrevBranchBlockHash());
		store.deleteTip(blockhash);
		store.insertTip(blockhash);
	}
}
