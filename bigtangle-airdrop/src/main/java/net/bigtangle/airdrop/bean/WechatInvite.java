package net.bigtangle.airdrop.bean;

import java.util.Date;

public class WechatInvite implements java.io.Serializable {

	private static final long serialVersionUID = -6728871579815689943L;

	private String id;
	
	private String wechatId;
	
	private String wechatinviterId;
	
	private Date createTime;
	
	private int status;

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getWechatId() {
		return wechatId;
	}

	public void setWechatId(String wechatId) {
		this.wechatId = wechatId;
	}

	public String getWechatinviterId() {
		return wechatinviterId;
	}

	public void setWechatinviterId(String wechatinviterId) {
		this.wechatinviterId = wechatinviterId;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
}
