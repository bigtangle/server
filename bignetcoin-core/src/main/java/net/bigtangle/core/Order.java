package net.bigtangle.core;

import java.security.KeyStore.PrivateKeyEntry;

public class Order implements java.io.Serializable {

    private static final long serialVersionUID = 190060684620430983L;
    
    private String orderid;
    
    private String address;
    
    private String tokenid;
    
    private int type;
    
    private String validateto;
    
    private String validatefrom;
    
    private int state;
}
