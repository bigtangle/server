package net.bigtangle.docker;

import java.io.Serializable;

 

public class ResultExecution implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String commandtext;
	private String result;

	public String getCommandtext() {
		return commandtext;
	}

	public void setCommandtext(String commandtext) {
		this.commandtext = commandtext;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	@Override
	public String toString() {
		return " [commandtext=" + commandtext + ", result=" + result + "]";
	}

	/*
	 * remote shell execution, if the call fails, raise exception different
	 * shell call has different return results, it is hard to detect error
	 */
	public void checkError() {
		if (getResult() != null && !getResult().trim().equals("")) {

			if (getResult().contains("error"))
				throw new RuntimeException(getResult());
		}
	}
}
