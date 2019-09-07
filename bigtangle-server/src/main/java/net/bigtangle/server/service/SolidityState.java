package net.bigtangle.server.service;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionOutPoint;

public class SolidityState {

    public enum State {
        Success, Invalid, MissingPredecessor, MissingCalculation
    }

    private static final SolidityState successState = new SolidityState(State.Success, null);
    private static final SolidityState failState = new SolidityState(State.Invalid, null);

    private State state;
    private Sha256Hash missingDependency;

    private SolidityState(State state, Sha256Hash missingDependency) {
        super();
        this.state = state;
        this.missingDependency = missingDependency;
    }

    public State getState() {
        return state;
    }

    public Sha256Hash getMissingDependency() {
        return missingDependency;
    }

    public boolean isSuccessState() {
        return this.state == successState.state;
    }

    public boolean isFailState() {
        return this.state == failState.state;
    }

    public static SolidityState getSuccessState() {
        return successState;
    }

    public static SolidityState getFailState() {
        return failState;
    }

    public static SolidityState fromMissingCalculation(Sha256Hash prevBlockHash) {
        return new SolidityState(State.MissingCalculation, prevBlockHash);
    }

    public static SolidityState from(Sha256Hash prevBlockHash) {
        return new SolidityState(State.MissingPredecessor, prevBlockHash);
    }

    public static SolidityState from(TransactionOutPoint outpoint) {
        return new SolidityState(State.MissingPredecessor, outpoint.getBlockHash());
    }
}
