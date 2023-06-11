/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.response;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.ContractExecution;
import net.bigtangle.core.Token;

public class GetContractEventInfoResponse extends AbstractResponse {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private List<ContractEventInfo> outputs;

	private ContractExecution contractExecution;
 

	private Map<String, Token> tokennames;
  

	public List<ContractEventInfo> getOutputs() {
		return outputs;
	}

	public void setOutputs(List<ContractEventInfo> outputs) {
		this.outputs = outputs;
	}

	public ContractExecution getContractExecution() {
		return contractExecution;
	}

	public void setContractExecution(ContractExecution contractExecution) {
		this.contractExecution = contractExecution;
	}
 

	public Map<String, Token> getTokennames() {
		return tokennames;
	}

	public void setTokennames(Map<String, Token> tokennames) {
		this.tokennames = tokennames;
	}

}
