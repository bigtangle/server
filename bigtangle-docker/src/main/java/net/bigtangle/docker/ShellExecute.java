package net.bigtangle.docker;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class ShellExecute implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9033041315731670305L;

	private String cmd;
	private byte[] file;
	private String filelocation;

	 

	public String getCmd() {
		return cmd;
	}

	 

	public void setCmd(String cmd) {
		this.cmd = cmd;
	}

	 
	public byte[] getFile() {
		return file;
	}
 
	public void setFile(byte[] file) {
		this.file = file;
	}
	
	public void setFileFromString (String fileString) {
        this.file = fileString.getBytes(StandardCharsets.UTF_8); 
    }
 

	public String getFilelocation() {
		return filelocation;
	}

	 
	public void setFilelocation(String filelocation) {
		this.filelocation = filelocation;
	}



    @Override
    public String toString() {
        return "ShellExecute [cmd=" + cmd + ", filelocation=" + filelocation + "]";
    }

}
