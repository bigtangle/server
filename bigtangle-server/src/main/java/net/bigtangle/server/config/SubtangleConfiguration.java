package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "subtangle")
public class SubtangleConfiguration {

	@Value("${active:false}")
	private boolean active;


	private String pubKeyHex;


	private String priKeyHex;

	
	private String parentContextRoot;

	public String getPubKeyHex() {
		return pubKeyHex;
	}

	public void setPubKeyHex(String pubKeyHex) {
		this.pubKeyHex = pubKeyHex;
	}

	public String getPriKeyHex() {
		return priKeyHex;
	}

	public void setPriKeyHex(String priKeyHex) {
		this.priKeyHex = priKeyHex;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getParentContextRoot() {
		return parentContextRoot;
	}

	public void setParentContextRoot(String parentContextRoot) {
		this.parentContextRoot = parentContextRoot;
	}
}
