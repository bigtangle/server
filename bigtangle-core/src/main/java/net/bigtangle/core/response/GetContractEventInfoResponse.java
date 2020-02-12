/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.response;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.ContractAccount;
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
	private List<ContractAccount> listContractAccount;

	private Map<String, Token> tokennames;

	public static AbstractResponse create(List<ContractEventInfo> outputs, ContractExecution contractExecution,
			List<ContractAccount> listContractAccount, Map<String, Token> tokennames) {
		GetContractEventInfoResponse res = new GetContractEventInfoResponse();
		res.outputs = outputs;
		res.contractExecution = contractExecution;
		res.tokennames = tokennames;
		return res;
	}

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

	public List<ContractAccount> getListContractAccount() {
		return listContractAccount;
	}

	public void setListContractAccount(List<ContractAccount> listContractAccount) {
		this.listContractAccount = listContractAccount;
	}

	public Map<String, Token> getTokennames() {
		return tokennames;
	}

	public void setTokennames(Map<String, Token> tokennames) {
		this.tokennames = tokennames;
	}

}
