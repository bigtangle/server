/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.util.HashSet;

import org.apache.commons.lang3.NotImplementedException;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the
 * blockstore
 */
public class BlockWrap {
    protected Block block;
    protected BlockEvaluation blockEvaluation;
    protected NetworkParameters params;
    
    protected BlockWrap() {
        super();
    }

    public BlockWrap(Block block, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super();
        this.block = block;
        this.blockEvaluation = blockEvaluation;
        this.params = params;
    }

    public Block getBlock() {
        return block;
    }

    public BlockEvaluation getBlockEvaluation() {
        return blockEvaluation;
    }

    public NetworkParameters getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getBlock().equals(((BlockWrap) o).getBlock());
    }

    @Override
    public int hashCode() {
        return getBlock().hashCode();
    }

    public Sha256Hash getBlockHash() {
        return block.getHash();
    }
    
    public HashSet<ConflictCandidate> toConflictCandidates() {
        HashSet<ConflictCandidate> blockConflicts = new HashSet<>();

        // Dynamic conflicts: conflicting transaction outpoints
        this.getBlock().getTransactions().stream().flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase())
                .map(in -> ConflictCandidate.fromTransactionOutpoint(this, in.getOutpoint())).forEach(c -> blockConflicts.add(c));
        
        addTypeSpecificConflictCandidates(blockConflicts);

        return blockConflicts;
    }

    private void addTypeSpecificConflictCandidates(HashSet<ConflictCandidate> blockConflicts) {
        switch (this.getBlock().getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            // Dynamic conflicts: mining rewards require the previous reward
            try {
                RewardInfo rewardInfo = RewardInfo.parse(this.getBlock().getTransactions().get(0).getData());
                blockConflicts.add(ConflictCandidate.fromRewardBlockHash(this, rewardInfo.getPrevRewardHash()));
            } catch (IOException e) {
                // Cannot happen since any blocks added already were checked.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            try {
                TokenInfo tokenInfo = new TokenInfo().parse(this.getBlock().getTransactions().get(0).getData());
                // Dynamic conflicts: token issuances with index>0 require the previous issuance, while index=0 uses the tokenid as conflict point
                if (tokenInfo.getTokens().getTokenindex() != 0)
                    blockConflicts.add(ConflictCandidate.fromToken(this, tokenInfo.getTokens()));
                else 
                    blockConflicts.add(ConflictCandidate.fromToken(this, tokenInfo.getTokens()));
                    
            } catch (IOException e) {
                // Cannot happen since any blocks added already were checked.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
        default:
            throw new NotImplementedException("Blocktype not implemented!");
        
        }
    }
}
