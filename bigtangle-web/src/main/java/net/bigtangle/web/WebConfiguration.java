package net.bigtangle.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebConfiguration {

 
    @Value("${bigtangleweb.bigtangle}")
    private String bigtangle;

	public String getBigtangle() {
		return bigtangle;
	}

	public void setBigtangle(String bigtangle) {
		this.bigtangle = bigtangle;
	}
 
}
