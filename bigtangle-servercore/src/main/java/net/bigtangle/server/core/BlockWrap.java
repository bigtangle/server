/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.core;

import java.io.IOException;
import java.util.HashSet;

import org.apache.commons.lang3.NotImplementedException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatchingInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;

/**
 * Wraps a {@link Block} object with extra data from the db
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
    public String toString() {
        return block.toString();
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
                .map(in -> ConflictCandidate.fromTransactionOutpoint(this, in.getOutpoint()))
                .forEach(c -> blockConflicts.add(c));

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
            // Dynamic conflicts: mining rewards spend the previous reward
            try {
                RewardInfo rewardInfo = RewardInfo.parse(this.getBlock().getTransactions().get(0).getData());
                blockConflicts.add(ConflictCandidate.fromReward(this, rewardInfo));
            } catch (IOException e) {
                // Cannot happen since any blocks added already were checked.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Dynamic conflicts: tokens of same id and index conflict
            try {
                TokenInfo tokenInfo = TokenInfo.parse(this.getBlock().getTransactions().get(0).getData());
                blockConflicts.add(ConflictCandidate.fromToken(this, tokenInfo.getToken()));
                
                // Dynamic conflicts: if defining new domain, this domain name is also a conflict
                if (tokenInfo.getToken().getTokentype() == TokenType.domainname.ordinal())
                	blockConflicts.add(ConflictCandidate.fromDomainToken(this, tokenInfo.getToken()));
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
        case BLOCKTYPE_ORDER_OPEN:
            break;
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            try {
                OrderReclaimInfo orderInfo = OrderReclaimInfo.parse(this.getBlock().getTransactions().get(0).getData());
                blockConflicts.add(ConflictCandidate.fromOrder(this, orderInfo));
            } catch (IOException e) {
                // Cannot happen since any blocks added already were checked.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            break;
        case BLOCKTYPE_ORDER_MATCHING:
            // Dynamic conflicts: require the previous matching
            try {
                OrderMatchingInfo info = OrderMatchingInfo.parse(this.getBlock().getTransactions().get(0).getData());
                blockConflicts.add(ConflictCandidate.fromOrderMatching(this, info));
            } catch (IOException e) {
                // Cannot happen since any blocks added already were checked.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            break;

        default:
            throw new NotImplementedException("Blocktype not implemented!");

        }
    }
}
