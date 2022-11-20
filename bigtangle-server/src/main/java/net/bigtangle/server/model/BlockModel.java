package net.bigtangle.server.model;

import java.io.Serializable;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.utils.Gzip;

public class BlockModel implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    byte[] hash;
    Long height;
    byte[] block;
    byte[] prevblockhash;
    byte[] prevbranchblockhash;
    byte[] mineraddress;
    Long blocktype;
    Long milestone;
    Long milestonelastupdate;
    Long inserttime;
    Long solid;
    Boolean confirmed;

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public byte[] getBlock() {
        return block;
    }

    public void setBlock(byte[] block) {
        this.block = block;
    }

    public byte[] getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(byte[] prevblockhash) {
        this.prevblockhash = prevblockhash;
    }

    public byte[] getPrevbranchblockhash() {
        return prevbranchblockhash;
    }

    public void setPrevbranchblockhash(byte[] prevbranchblockhash) {
        this.prevbranchblockhash = prevbranchblockhash;
    }

    public byte[] getMineraddress() {
        return mineraddress;
    }

    public void setMineraddress(byte[] mineraddress) {
        this.mineraddress = mineraddress;
    }

    public Long getBlocktype() {
        return blocktype;
    }

    public void setBlocktype(Long blocktype) {
        this.blocktype = blocktype;
    }

    public Long getMilestone() {
        return milestone;
    }

    public void setMilestone(Long milestone) {
        this.milestone = milestone;
    }

    public Long getMilestonelastupdate() {
        return milestonelastupdate;
    }

    public void setMilestonelastupdate(Long milestonelastupdate) {
        this.milestonelastupdate = milestonelastupdate;
    }

    public Long getInserttime() {
        return inserttime;
    }

    public void setInserttime(Long inserttime) {
        this.inserttime = inserttime;
    }

    public Long getSolid() {
        return solid;
    }

    public void setSolid(Long solid) {
        this.solid = solid;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public static BlockModel from(Block block, BlockEvaluation blockEvaluation) {
        BlockModel s = new BlockModel();
        s.setHash(block.getHash().getBytes());
        s.setHeight(block.getHeight());
        s.setBlock(Gzip.compress(block.unsafeBitcoinSerialize()));

        s.setPrevblockhash(block.getPrevBlockHash().getBytes());
        s.setPrevbranchblockhash(block.getPrevBranchBlockHash().getBytes());
        s.setMineraddress(block.getMinerAddress());
        s.setBlocktype(new Long(block.getBlockType().ordinal()));

        s.setMilestone(blockEvaluation.getMilestone());
        s.setMilestonelastupdate(blockEvaluation.getMilestoneLastUpdateTime());

        s.setInserttime(blockEvaluation.getInsertTime());

        s.setSolid(blockEvaluation.getSolid());
        s.setConfirmed(blockEvaluation.isConfirmed());

        return s;
    }

}
