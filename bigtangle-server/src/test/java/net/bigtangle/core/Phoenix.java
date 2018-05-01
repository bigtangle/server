/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Phoenix {

    private static String driver = "org.apache.phoenix.jdbc.PhoenixDriver";

    public static void main(String[] args) throws SQLException {
        System.setProperty("hadoop.home.dir", "D:\\environment\\hadoop-2.6.3");
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Statement stmt = null;
        ResultSet rs = null;

        Connection con = DriverManager.getConnection("jdbc:phoenix:cn.phoenix.bigtangle.net:8765");
        stmt = con.createStatement();
        String sql = "select * from test";
        rs = stmt.executeQuery(sql);
        while (rs.next()) {
            System.out.print("id:" + rs.getString("id"));
            System.out.println(",name:" + rs.getString("name"));
        }
        stmt.close();
        con.close();
    }
}
