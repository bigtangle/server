package net.bigtangle.airdrop.bean;

import java.util.Date;

public class WechatReward implements java.io.Serializable {

	private static final long serialVersionUID = 2099963748713607464L;

	private String id;
	
	private String pubKeyHex;
	
	private String wechatInviterId;
	
	private Date createTime;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPubKeyHex() {
		return pubKeyHex;
	}

	public void setPubKeyHex(String pubKeyHex) {
		this.pubKeyHex = pubKeyHex;
	}

	public String getWechatInviterId() {
		return wechatInviterId;
	}

	public void setWechatInviterId(String wechatInviterId) {
		this.wechatInviterId = wechatInviterId;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
}
