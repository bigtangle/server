/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.protocols.channels;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A class representing a state machine, with limited transitions between states.
 * @param <State> An enum of states to use
 */
public class StateMachine<State extends Enum<State>> {
    private State currentState;

    private final Multimap<State, State> transitions;

    public StateMachine(State startState, Multimap<State, State> transitions) {
        currentState = checkNotNull(startState);
        this.transitions = checkNotNull(transitions);
    }

    /**
     * Checks that the machine is in the given state. Throws if it isn't.
     * @param requiredState
     */
    public synchronized void checkState(State requiredState) throws IllegalStateException {
        if (requiredState != currentState) {
            throw new IllegalStateException(String.format(Locale.US,
                    "Expected state %s, but in state %s", requiredState, currentState));
        }
    }

    /**
     * Checks that the machine is in one of the given states. Throws if it isn't.
     * @param requiredStates
     */
    public synchronized void checkState(State... requiredStates) throws IllegalStateException {
        for (State requiredState : requiredStates) {
            if (requiredState.equals(currentState)) {
                return;
            }
        }
        throw new IllegalStateException(String.format(Locale.US,
                "Expected states %s, but in state %s", Lists.newArrayList(requiredStates), currentState));
    }

    /**
     * Transitions to a new state, provided that the required transition exists
     * @param newState
     * @throws IllegalStateException If no state transition exists from oldState to newState
     */
    public synchronized void transition(State newState) throws IllegalStateException {
        if (transitions.containsEntry(currentState, newState)) {
            currentState = newState;
        } else {
            throw new IllegalStateException(String.format(Locale.US,
                    "Attempted invalid transition from %s to %s", currentState, newState));
        }
    }

    public synchronized State getState() {
        return currentState;
    }

    @Override
    public String toString() {
        return new StringBuilder().append('[').append(getState()).append(']').toString();
    }
}
