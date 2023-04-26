/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.userpay;

import java.util.List;

import net.bigtangle.core.response.AbstractResponse;

public class UserPayResponse extends AbstractResponse {

	private List<UserPay> userPayList;

	public static AbstractResponse createUserPayResponse(List<UserPay> userPayList) {
		UserPayResponse res = new UserPayResponse();
		res.userPayList = userPayList;
		return res;
	}

	public List<UserPay> getUserPayList() {
		return userPayList;
	}

	public void setUserPayList(List<UserPay> userPayList) {
		this.userPayList = userPayList;
	}

}
