/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.util.HashSet;

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

        // Create pairs of blocks and used non-coinbase utxos from block
        // Dynamic conflicts: conflicting transactions
        this.getBlock().getTransactions().stream().flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase())
                .map(in -> new ConflictCandidate(this, in.getOutpoint())).forEach(c -> blockConflicts.add(c));

        // Dynamic conflicts: mining reward height intervals
        if (this.getBlock().getBlockType() == Block.BLOCKTYPE_REWARD)
            blockConflicts
                    .add(new ConflictCandidate(this, Utils.readInt64(this.getBlock().getTransactions().get(0).getData(), 0)));

        // Dynamic conflicts: token issuance ids
        if (this.getBlock().getBlockType() == Block.BLOCKTYPE_TOKEN_CREATION) {
            try {
                TokenInfo tokenInfo;
                    tokenInfo = new TokenInfo().parse(this.getBlock().getTransactions().get(0).getData());
                Tokens tokens = tokenInfo.getTokens();
                TokenSerial tokenSerial = new TokenSerial(tokens.getTokenid(), tokens.getTokenindex(), tokens.getAmount());
                blockConflicts.add(new ConflictCandidate(this, tokenSerial));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return blockConflicts;
    }
}
