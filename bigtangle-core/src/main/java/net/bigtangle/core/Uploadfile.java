/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class Uploadfile {
    private String name;
    private long maxsize;
    private byte[] fileinfo;
    private String fileinfoHex;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMaxsize() {
        return maxsize;
    }

    public void setMaxsize(long maxsize) {
        this.maxsize = maxsize;
    }

    public byte[] getFileinfo() {
        return fileinfo;
    }

    public void setFileinfo(byte[] fileinfo) {
        this.fileinfo = fileinfo;
    }

    public String getFileinfoHex() {
        return fileinfoHex;
    }

    public void setFileinfoHex(String fileinfoHex) {
        this.fileinfoHex = fileinfoHex;
    }
}
