/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
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
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TipsService {
	private final Logger log = LoggerFactory.getLogger(TipsService.class);

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;
	
	public List<Sha256Hash> getRatingTips(int count) throws Exception {
		SecureRandom seed = new SecureRandom();		
		List<Sha256Hash> entryPoints = getRatingUpdateEntryPoints(count, seed);
		List<Sha256Hash> results = new ArrayList<>();
		
		for (Sha256Hash entryPoint : entryPoints) {
			results.add(getMCMCResultBlock(entryPoint, seed));			
		}
		return results;
	}
	
	public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws Exception {
		List<Pair<Sha256Hash, Sha256Hash>> pairs = getValidatedBlockPairs(1);
		return pairs.get(0);
	}
	
	public List<Pair<Sha256Hash, Sha256Hash>> getValidatedBlockPairs(int count) throws Exception {
		SecureRandom seed = new SecureRandom();		
		List<Pair<Sha256Hash, TreeSet<BlockEvaluation>>> blocks = getValidatedBlocks(2 * count, seed);
		List<Pair<Sha256Hash, Sha256Hash>> results = new ArrayList<>();
		
		for (int index = 0; index < count; index++) {
			Pair<Sha256Hash, TreeSet<BlockEvaluation>> b1 = blocks.get(index);
			Pair<Sha256Hash, TreeSet<BlockEvaluation>> b2 = blocks.get(count + index);
			
			//TODO validate dynamic validity here and if not, try to reverse until no conflicts
			//Specifically, we only need to check for candidate-candidate conflicts in the union of both approved blocks and reverse the nearest one
			// TODO for now just copy from milestoneservice, afterwards refactor maybe
			results.add(Pair.of(b1.getLeft(), b2.getLeft()));
		}
		
		
		return results;
	}
	
	private List<Pair<Sha256Hash, TreeSet<BlockEvaluation>>> getValidatedBlocks(int count, Random seed) throws Exception {
		List<Pair<Sha256Hash, TreeSet<BlockEvaluation>>> results = new ArrayList<>();
		List<Sha256Hash> entryPoints = getValidationEntryPoints(count, seed);

		for (Sha256Hash entryPoint : entryPoints) {
			BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(entryPoint);
			
			//TODO validate dynamic validity here and if not, try to reverse until no conflicts
			//Specifically, we check for milestone-candidate-conflicts + candidate-candidate-conflicts and reverse until there are no such conflicts
			//Also returns all approved non-milestone blocks in topological ordering
			// TODO for now just copy resolveundoableconflicts+co from milestoneservice, afterwards refactor 
			results.add(Pair.of(entryPoint, null));
		}
		
		return results;
	}

	private Sha256Hash getMCMCResultBlock(Sha256Hash entryPoint, Random seed) throws Exception {
		List<BlockEvaluation> blockEvaluations = blockService.getSolidBlockEvaluations();
		Map<Sha256Hash, Long> cumulativeWeights = blockEvaluations.stream()
				.collect(Collectors.toMap(BlockEvaluation::getBlockhash, BlockEvaluation::getCumulativeWeight));
		return randomWalk(entryPoint, cumulativeWeights, seed);
	}

	private List<Sha256Hash> getRatingUpdateEntryPoints(int count, Random seed) throws Exception {
		List<BlockEvaluation> candidates = store.getBlocksInMilestoneDepthInterval(NetworkParameters.ENTRYPOINT_RATING_LOWER_DEPTH_CUTOFF, NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF);
		return getRandomsByCumulativeWeight(candidates, count, seed);
	}

	private List<Sha256Hash> getValidationEntryPoints(int count, Random seed) throws Exception {
		List<BlockEvaluation> candidates = store.getBlocksInMilestoneDepthInterval(0, NetworkParameters.ENTRYPOINT_VALIDATION_DEPTH_CUTOFF);
		return getRandomsByCumulativeWeight(candidates, count, seed);
	}

	private List<Sha256Hash> getRandomsByCumulativeWeight(List<BlockEvaluation> candidates, int count, Random seed) {
		double maxBlockWeight = candidates.stream().mapToLong(e -> e.getCumulativeWeight()).max().orElse(1L);
		double normalizedBlockWeightSum = candidates.stream().mapToDouble(e -> e.getCumulativeWeight() / maxBlockWeight).sum();
		List<Sha256Hash> results = new ArrayList<>();
		
		for (int i = 0; i < count; i++) {
			if (candidates.isEmpty()) {
				results.add(networkParameters.getGenesisBlock().getHash());
			} else {
				// Randomly select one of the candidates weighted by their cumulative weights
				double selectionRealization = seed.nextDouble() * normalizedBlockWeightSum;
				for (int selection = 0; selection < candidates.size(); selection++) {
					BlockEvaluation selectedBlock = candidates.get(selection);
					selectionRealization -= selectedBlock.getCumulativeWeight() / maxBlockWeight;
					if (selectionRealization <= 0) {
						results.add(selectedBlock.getBlockhash());
						break;
					}
				}
			}
		}
		
		return results;
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
}
