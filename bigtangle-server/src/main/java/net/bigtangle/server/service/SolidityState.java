package net.bigtangle.server.service;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionOutPoint;

public class SolidityState {
    
    public enum State {
        Success,
        Unfixable,
        MissingPredecessor,
        MissingTransactionOutput,
    }
    
    private static final SolidityState successState = new SolidityState(State.Success, null);
    private static final SolidityState failState = new SolidityState(State.Unfixable, null);
    
    private State state; 
    private byte[] missingDependency;
    
    private SolidityState(State state, byte[] missingDependency) {
        super();
        this.state = state;
        this.missingDependency = missingDependency;
    }

    public State getState() {
        return state;
    }

    public byte[] getMissingDependency() {
        return missingDependency;
    }

    public static SolidityState getSuccessState() {
        return successState;
    }

    public static SolidityState getFailState() {
        return failState;
    }

    public static SolidityState from(Sha256Hash prevBlockHash) {
        return new SolidityState(State.MissingPredecessor, prevBlockHash.getBytes());
    }

    public static SolidityState from(TransactionOutPoint outpoint) {
        return new SolidityState(State.MissingTransactionOutput, outpoint.bitcoinSerialize());
    }
}
