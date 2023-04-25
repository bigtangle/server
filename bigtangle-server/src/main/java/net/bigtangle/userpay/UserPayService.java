package net.bigtangle.userpay;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import net.bigtangle.store.FullBlockStore;

 
public class UserPayService  {
   
    public void save(UserPay v,   FullBlockStore store ) {
     
    }

    public void delete(UserPay v,FullBlockStore store) {
     
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
