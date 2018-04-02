package com.bignetcoin.server;

import java.util.List;

public class User implements java.io.Serializable {

    private static final long serialVersionUID = -2278303221905201177L;

    private int id;
    
    private boolean skip;
    
    private String username;
    
    private List<String> password;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getPassword() {
        return password;
    }

    public void setPassword(List<String> password) {
        this.password = password;
    }
}
