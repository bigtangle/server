package net.bigtangle.airdrop;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class IndexController {

	@RequestMapping(value = "/index.jsp")
	public String index() {
		System.out.println("index");
		return "index";
	}
}
