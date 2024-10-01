/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.base;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.ContractExecutionResult;
import net.bigtangle.server.data.OrderExecutionResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.service.CacheBlockService;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Json;

public class ServiceBase {
	protected ServerConfiguration serverConfiguration;
	protected NetworkParameters networkParameters;
	protected CacheBlockService cacheBlockService;

	public ServiceBase(ServerConfiguration serverConfiguration, NetworkParameters networkParameters,
			CacheBlockService cacheBlockService) {
		super();
		this.serverConfiguration = serverConfiguration;
		this.networkParameters = networkParameters;
		this.cacheBlockService = cacheBlockService;
	}

	public boolean enableFee(Block block) {
		return (block.getLastMiningRewardBlock() > 1424626
				&& networkParameters.getId().equals(NetworkParameters.ID_MAINNET))
				|| networkParameters.getId().equals(NetworkParameters.ID_UNITTESTNET);
	}

 
	/*
	 * Enable each order execution in own chain and not part of reward chain
	 */
	public boolean enableOrderMatchExecutionChain(Block block) {
		return enableFee(block);
	}

	/**
	 * get domainname token multi sign address
	 * 
	 * @param domainNameBlockHash
	 * @return
	 * @throws BlockStoreException
	 */
	public List<MultiSignAddress> queryDomainnameTokenMultiSignAddresses(Sha256Hash domainNameBlockHash,
			FullBlockStore store) throws BlockStoreException {
		if (domainNameBlockHash.equals(networkParameters.getGenesisBlock().getHash())) {
			List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
			for (Iterator<PermissionDomainname> iterator = networkParameters.getPermissionDomainnameList()
					.iterator(); iterator.hasNext();) {
				PermissionDomainname permissionDomainname = iterator.next();
				ECKey ecKey = permissionDomainname.getOutKey();
				multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
			}
			return multiSignAddresses;
		} else {
			Token token = store.queryDomainnameToken(domainNameBlockHash);
			if (token == null)
				throw new BlockStoreException("token not found");

			final String tokenid = token.getTokenid();
			List<MultiSignAddress> multiSignAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(tokenid,
					token.getBlockHash());
			return multiSignAddresses;
		}
	}

	public List<BlockWrap> getAllBlocks(Block block, Set<Sha256Hash> allBlockHashes, FullBlockStore store)
			throws BlockStoreException {
		List<BlockWrap> result = new ArrayList<>();
		for (Sha256Hash pred : allBlockHashes)
			result.add(new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
					.getBlockWrap(pred, store));
		return result;
	}

	/**
	 * Returns all blocks that must be confirmed if this block is confirmed. All
	 * transactions related to this block are include as required includePredecessor
	 * is false = not required the two getPrevBlockHash and getPrevBranchBlockHash
	 * The sync may does not check this two Predecessors
	 */

	public Set<Sha256Hash> getAllRequiredBlockHashes(Block block, boolean includePredecessor) {
		Set<Sha256Hash> allrequireds = new HashSet<>();
		if (includePredecessor) {
			allrequireds.add(block.getPrevBlockHash());
			allrequireds.add(block.getPrevBranchBlockHash());
		}
		// contract execution add all referenced blocks
		if (Block.Type.BLOCKTYPE_CONTRACT_EXECUTE.equals(block.getBlockType())
				|| Block.Type.BLOCKTYPE_ORDER_EXECUTE.equals(block.getBlockType())) {
			allrequireds.addAll(getReferrencedBlockHashes(block));
		}
		// All used transaction outputs

		final List<Transaction> transactions = block.getTransactions();

		for (final Transaction tx : transactions) {
			if (!tx.isCoinBase()) {
				for (int index = 0; index < tx.getInputs().size(); index++) {
					TransactionInput in = tx.getInputs().get(index);
					// due to virtual txs from order/reward
					allrequireds.add(in.getOutpoint().getBlockHash());
				}
			}

		}
		switch (block.getBlockType()) {
		case BLOCKTYPE_CROSSTANGLE:
			break;
		case BLOCKTYPE_FILE:
			break;
		case BLOCKTYPE_GOVERNANCE:
			break;
		case BLOCKTYPE_INITIAL:
			break;
		case BLOCKTYPE_REWARD:
			RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());
			allrequireds.add(rewardInfo.getPrevRewardHash());
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			TokenInfo currentToken = new TokenInfo().parseChecked(transactions.get(0).getData());
			allrequireds.add(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
			if (currentToken.getToken().getPrevblockhash() != null)
				allrequireds.add(currentToken.getToken().getPrevblockhash());
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
			break;
		default:
			throw new RuntimeException("No Implementation");
		}

		return allrequireds;
	}

	public Set<BlockWrap> getReferrencedBlockWrap(Block block, FullBlockStore store) throws BlockStoreException {
		Set<BlockWrap> wraps = new HashSet<>();
		for (Sha256Hash hash : getReferrencedBlockHashes(block)) {
			wraps.add(getBlockWrap(hash, store));
		}
		return wraps;
	}

	public Set<Sha256Hash> getReferrencedBlockHashes(Block block) {
		if (block.getBlockType().equals(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE)) {
			return new ContractExecutionResult().parseChecked(block.getTransactions().get(0).getData())
					.getReferencedBlocks();
		}
		if (block.getBlockType().equals(Block.Type.BLOCKTYPE_ORDER_EXECUTE)) {
			return new OrderExecutionResult().parseChecked(block.getTransactions().get(0).getData())
					.getReferencedBlocks();
		}
		return new HashSet<Sha256Hash>();
	}

	public Block getBlock(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {

		byte[] re = cacheBlockService.getBlock(blockhash, store);
		if (re == null)
			return null;
		try {
			return networkParameters.getDefaultSerializer().makeZippedBlock(re);
		} catch (Exception e) {

			throw new BlockStoreException(e);
		}
	}

	public BlockWrap getBlockWrap(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
		try {
			Block block = getBlock(blockhash, store);
			if (block == null)
				return null;
			byte[] be = cacheBlockService.getBlockEvaluation(blockhash, store);
			BlockEvaluation v = BlockEvaluation.buildInitial(block);
			if (be != null)
				v = Json.jsonmapper().readValue(be, BlockEvaluation.class);
			if (v == null)
				v = BlockEvaluation.buildInitial(block);
			byte[] blockMCMC = cacheBlockService.getBlockMCMC(blockhash, store);

			return new BlockWrap(block, v, Json.jsonmapper().readValue(blockMCMC, BlockMCMC.class), networkParameters);
		} catch (Exception e) {
			throw new BlockStoreException(e);
		}
	}

	public List<Sha256Hash> getEntryPointCandidates(long currChainLength, FullBlockStore store)
			throws BlockStoreException {
		long minChainLength = Math.max(0, currChainLength - NetworkParameters.MILESTONE_CUTOFF);
		return getBlocksInMilestoneInterval(minChainLength, currChainLength, store);
	}

	public List<Sha256Hash> getBlocksInMilestoneInterval(long minChainLength, long currChainLength,
			FullBlockStore store) throws BlockStoreException {
		// long minChainLength = Math.max(0, currChainLength -
		// NetworkParameters.MILESTONE_CUTOFF);
		return store.getBlocksInMilestoneInterval(minChainLength, currChainLength);

	}

	public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime, FullBlockStore store)
			throws BlockStoreException {

		store.insertMyserverblocks(prevhash, hash, inserttime);
	}

	public boolean existMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

		return store.existMyserverblocks(prevhash);
	}

	public void deleteMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

		store.deleteMyserverblocks(prevhash);
	}

	public GetBlockListResponse blocksFromChainLength(Long start, Long end, FullBlockStore store)
			throws BlockStoreException {

		return GetBlockListResponse.create(store.blocksFromChainLength(start, end));
	}

	public long getRewardMaxHeight(Sha256Hash prevRewardHash) {
		return Long.MAX_VALUE;
		// Block rewardBlock = store.get(prevRewardHash);
		// return rewardBlock.getHeight() +
		// NetworkParameters.FORWARD_BLOCK_HORIZON;
	}

	public long getRewardCutoffHeight(Sha256Hash prevRewardHash, FullBlockStore store) throws BlockStoreException {

		Sha256Hash currPrevRewardHash = prevRewardHash;
		for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
			Block currRewardBlock;
			currRewardBlock = getBlock(currPrevRewardHash, store);
			RewardInfo currRewardInfo = new RewardInfo()
					.parseChecked(currRewardBlock.getTransactions().get(0).getData());
			if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
				return 0;

			currPrevRewardHash = currRewardInfo.getPrevRewardHash();

		}
		return getBlock(currPrevRewardHash, store).getHeight();
	}

	public long calcHeightRequiredBlocks(Block block, List<BlockWrap> allPredecessors, FullBlockStore store)
			throws BlockStoreException {
		// List<BlockWrap> requires = getAllRequirements(block, store);
		long height = 0;
		for (BlockWrap b : allPredecessors) {
			height = Math.max(height, b.getBlock().getHeight());
		}
		return height + 1;
	}

	public SolidityState getMinPredecessorSolidity(Block block, boolean throwExceptions,
			List<BlockWrap> allPredecessors, FullBlockStore store, boolean predecessorsSolid)
			throws BlockStoreException {
		// final List<BlockWrap> allPredecessors = getAllRequirements(block, store);
		// SolidityState missingCalculation = null;
		SolidityState missingDependency = null;
		for (BlockWrap predecessor : allPredecessors) {
			if (predecessor.getBlockEvaluation().getSolid() == 2) {
				continue;
			} else if (predecessor.getBlockEvaluation().getSolid() == 1 && predecessorsSolid) {
				SolidityState solidityState = SolidityState.getSuccessState();
				new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
						.solidifyBlock(predecessor.getBlock(), solidityState, true, store);
				// missingCalculation =
				// SolidityState.fromMissingCalculation(predecessor.getBlockHash());

			} else {
				// TODO check logger.warn("predecessor.getBlockEvaluation().getSolid() = "
				// + predecessor.getBlockEvaluation().getSolid() + " " + block.toString());
				continue;
				// throw new RuntimeException("not implemented");
			}
		}

		if (missingDependency == null) {

			return SolidityState.getSuccessState();

		} else {
			return missingDependency;
		}
	}

	public Set<Sha256Hash> getHashSet(Set<BlockWrap> alist) {
		Set<Sha256Hash> hashs = new HashSet<>();
		for (BlockWrap o : alist) {
			hashs.add(o.getBlockHash());
		}
		return hashs;
	}

}
