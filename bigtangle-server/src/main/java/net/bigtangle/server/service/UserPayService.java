/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.userpay.UserPay;

@Service
public class UserPayService {

	private static final Logger logger = LoggerFactory.getLogger(UserPayService.class);

	public void saveUserpay(UserPay userpay, FullBlockStore store) throws BlockStoreException {
		store.insertUserpay(userpay);
	}

	public void updateUserpay(String hash, String status, Long payid, FullBlockStore store) throws BlockStoreException {
		store.updateUserpay(hash, status, payid);
	}

	public void deleteUserpay(Long payid, FullBlockStore store) throws BlockStoreException {
		store.deleteUserpay(payid);
	}

	private RowMapper<UserPay> userpayMapper = new RowMapper<UserPay>() {
		public UserPay mapRow(ResultSet rs, int row) throws SQLException {
			return new UserPay(rs.getLong("payid"), rs.getString("status"), rs.getString("tokenname"),
					rs.getString("tokenid"), rs.getString("fromaddress"), rs.getString("fromsystem"),
					rs.getString("toaddress"), rs.getString("tosystem"), rs.getString("amount"),
					rs.getString("gaslimit"), rs.getString("gasprice"), rs.getString("fee"), rs.getLong("userid"),
					rs.getString("fromblockhash"), rs.getString("transactionhash"), rs.getString("toblockhash"),
					rs.getString("remark"));

		}
	};

}
