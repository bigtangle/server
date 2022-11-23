package net.bigtangle.server.model;

import java.io.Serializable;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.utils.Gzip;

public class BlockModel implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    String hash;
    Long height;
    String block;
    String prevblockhash;
    String prevbranchblockhash;
    String mineraddress;
    Integer blocktype;
    Long milestone;
    Long milestonelastupdate;
    Long inserttime;
    Long solid;
    Boolean confirmed;

    public byte[] getBlockBytes() {
        return Utils.HEX.decode(block);
    }

    
    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public String getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(String prevblockhash) {
        this.prevblockhash = prevblockhash;
    }

    public String getPrevbranchblockhash() {
        return prevbranchblockhash;
    }

    public void setPrevbranchblockhash(String prevbranchblockhash) {
        this.prevbranchblockhash = prevbranchblockhash;
    }

    public String getMineraddress() {
        return mineraddress;
    }

    public void setMineraddress(String mineraddress) {
        this.mineraddress = mineraddress;
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

    public Integer getBlocktype() {
        return blocktype;
    }


    public void setBlocktype(Integer blocktype) {
        this.blocktype = blocktype;
    }


    public static BlockModel from(Block block, BlockEvaluation blockEvaluation) {
        BlockModel s = new BlockModel();
        s.setHash(block.getHash().toString());
        s.setHeight(block.getHeight());
        s.setBlock(Utils.HEX.encode(Gzip.compress(block.unsafeBitcoinSerialize())));

        s.setPrevblockhash( block.getPrevBlockHash().toString());
        s.setPrevbranchblockhash(block.getPrevBranchBlockHash().toString());
        s.setMineraddress(Utils.HEX.encode(block.getMinerAddress()));
        s.setBlocktype( block.getBlockType().ordinal( ) );

        s.setMilestone(blockEvaluation.getMilestone());
        s.setMilestonelastupdate(blockEvaluation.getMilestoneLastUpdateTime());

        s.setInserttime(blockEvaluation.getInsertTime());

        s.setSolid(blockEvaluation.getSolid());
        s.setConfirmed(blockEvaluation.isConfirmed());

        return s;
    }

    public BlockEvaluation toBlockEvaluation() {

        BlockEvaluation blockEvaluation = BlockEvaluation.build(Sha256Hash.wrap(getHash()),
                getHeight(), getMilestone(), getMilestonelastupdate(), getInserttime(), getSolid(), getConfirmed());
        return blockEvaluation;

    }
    public BlockEvaluationDisplay toBlockEvaluationDisplay( long latestchainnumber) {

        return   BlockEvaluationDisplay.build(Sha256Hash.wrap(getHash()),
                getHeight(), getMilestone(), getMilestonelastupdate(), getInserttime(), getBlocktype(), getSolid(), getConfirmed(), latestchainnumber);
      

    }

}
