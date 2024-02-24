/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.core;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Token;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.server.data.ContractExecutionResult;
import net.bigtangle.server.data.OrderExecutionResult;

public class ConflictPoint {
	private ConflictType type;

	/** Null if not conflict of corresponding type */
	@Nullable
	private TransactionOutPoint connectedOutpoint;
	@Nullable
	private RewardInfo connectedReward;
	@Nullable
	private Token connectedToken;
	@Nullable
	private Token connectedDomainToken;

	@Nullable
	private ContractExecutionResult connectedContracExecute;
	@Nullable
	private OrderExecutionResult connectedOrderExecute;

	private ConflictPoint(ConflictType type, TransactionOutPoint connectedOutpoint, RewardInfo reward,
			Token connectedToken, Token connectedDomainToken, ContractExecutionResult connectedContracExecute,
			OrderExecutionResult connectedOrderExecute) {
		super();
		this.type = type;
		this.connectedOutpoint = connectedOutpoint;
		this.connectedReward = reward;
		this.connectedToken = connectedToken;
		this.connectedDomainToken = connectedDomainToken;
		this.connectedContracExecute = connectedContracExecute;
		this.connectedOrderExecute = connectedOrderExecute;

	}

	public static ConflictPoint fromTransactionOutpoint(TransactionOutPoint connectedOutpoint) {
		return new ConflictPoint(ConflictType.TXOUT, connectedOutpoint, null, null, null, null, null);
	}

	public static ConflictPoint fromReward(RewardInfo reward) {
		return new ConflictPoint(ConflictType.REWARDISSUANCE, null, reward, null, null, null, null);
	}

	public static ConflictPoint fromToken(Token token) {
		return new ConflictPoint(ConflictType.TOKENISSUANCE, null, null, token, null, null, null);
	}

	public static ConflictPoint fromDomainToken(Token token) {
		return new ConflictPoint(ConflictType.DOMAINISSUANCE, null, null, null, token, null, null);
	}

	public static ConflictPoint fromContractExecute(ContractExecutionResult contractResult) {
		return new ConflictPoint(ConflictType.CONTRACTEXECUTE, null, null, null, null, contractResult, null);
	}

	public static ConflictPoint fromOrderExecute(OrderExecutionResult orderExecutionResult) {
		return new ConflictPoint(ConflictType.ORDEREXECUTE, null, null, null, null, null, orderExecutionResult);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ConflictPoint other = (ConflictPoint) o;

		if (other.type != type)
			return false;

		switch (type) {
		case REWARDISSUANCE:
			return getConnectedReward().getPrevRewardHash().equals(other.getConnectedReward().getPrevRewardHash());
		case TOKENISSUANCE:
			return getConnectedToken().getTokenid().equals(other.getConnectedToken().getTokenid())
					&& getConnectedToken().getTokenindex() == other.getConnectedToken().getTokenindex();
		case TXOUT:
			return getConnectedOutpoint().getIndex() == other.getConnectedOutpoint().getIndex()
					&& getConnectedOutpoint().getHash().equals(other.getConnectedOutpoint().getHash());
		case DOMAINISSUANCE:
			return getConnectedDomainToken().getDomainNameBlockHash()
					.equals(other.getConnectedDomainToken().getDomainNameBlockHash())
					&& getConnectedDomainToken().getTokenname().equals(other.getConnectedDomainToken().getTokenname())
					&& getConnectedDomainToken().getTokenindex() == other.getConnectedDomainToken().getTokenindex();
		case CONTRACTEXECUTE:
			return getConnectedContracExecute().getPrevblockhash()
					.equals(other.getConnectedContracExecute().getPrevblockhash());
		case ORDEREXECUTE:
			return getConnectedOrderExecute().getPrevblockhash()
					.equals(other.getConnectedOrderExecute().getPrevblockhash());

		default:
			throw new RuntimeException("Conflicts not implemented.");
		}
	}

	@Override
	public int hashCode() {
		switch (type) {
		case REWARDISSUANCE:
			return Objects.hashCode(type, getConnectedReward().getPrevRewardHash());
		case TOKENISSUANCE:
			return Objects.hashCode(type, getConnectedToken().getTokenid(), getConnectedToken().getTokenindex());
		case TXOUT:
			return Objects.hashCode(type, getConnectedOutpoint().getIndex(), getConnectedOutpoint().getHash());
		case DOMAINISSUANCE:
			return Objects.hashCode(type, getConnectedDomainToken().getDomainNameBlockHash(),
					getConnectedDomainToken().getTokenname(), getConnectedDomainToken().getTokenindex());
		case CONTRACTEXECUTE:
			return Objects.hashCode(type, getConnectedContracExecute().getPrevblockhash());
		case ORDEREXECUTE:
			return Objects.hashCode(type, getConnectedOrderExecute().getPrevblockhash());

		default:
			throw new RuntimeException("Conflicts not implemented.");
		}
	}

	public enum ConflictType {
		TXOUT, TOKENISSUANCE, REWARDISSUANCE, DOMAINISSUANCE, CONTRACTEXECUTE, ORDEREXECUTE
	}

	public ConflictType getType() {
		return type;
	}

	public TransactionOutPoint getConnectedOutpoint() {
		return connectedOutpoint;
	}

	public RewardInfo getConnectedReward() {
		return connectedReward;
	}

	public Token getConnectedToken() {
		return connectedToken;
	}

	public Token getConnectedDomainToken() {
		return connectedDomainToken;
	}

	public ContractExecutionResult getConnectedContracExecute() {
		return connectedContracExecute;
	}
	public   OrderExecutionResult getConnectedOrderExecute() {
		return connectedOrderExecute;
	}

	@Override
	public String toString() {
		return "ConflictPoint [type=" + type + ", connectedOutpoint=" + connectedOutpoint + ", connectedReward="
				+ connectedReward + ", connectedToken=" + connectedToken + ", connectedDomainToken="
				+ connectedDomainToken + ", connectedContracExecute=" + connectedContracExecute + "]";
	}

}
