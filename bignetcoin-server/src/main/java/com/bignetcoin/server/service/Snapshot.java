/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Snapshot {
    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);
    private static String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    private static int SNAPSHOT_PUBKEY_DEPTH = 6;
    private static int SNAPSHOT_INDEX = 1;

    public static final Map<Sha256Hash, Long> initialState = new HashMap<>();
    public static final Snapshot initialSnapshot;

    public static final long SUPPLY = 2779530283277761L; // = (3^33 - 1) / 2
    
    static {

        InputStream in = Snapshot.class.getResourceAsStream("/Snapshot.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
       
        try {
            while((line = reader.readLine()) != null) {
       
                String[] parts = line.split(";", 2);
                if (parts.length >= 2)
                {
                    String key = parts[0];
                    String value = parts[1];
                    initialState.put(new Sha256Hash(key), Long.valueOf(value));
                }
            }
            { }
        } catch (IOException e) {
            System.out.println("Failed to load snapshot.");
            System.exit(-1);
        }

        initialSnapshot = new Snapshot(initialState, 0);
        if(!initialSnapshot.isConsistent()) {
            System.out.println("Initial Snapshot inconsistent.");
            System.exit(-1);
        }
    }

    public final Object snapshotSyncObject = new Object();
    public final Object approvalsSyncObject = new Object();
    public final Set<Sha256Hash> approvedHashes = new HashSet<>();
    private final Map<Sha256Hash, Long> state;
    private int index;

    public int index() {
        return index;
    }

    public Snapshot(Snapshot snapshot) {
        state = new HashMap<>(snapshot.state);
        this.index = snapshot.index;
    }

    private Snapshot(Map<Sha256Hash, Long> initialState, int index) {
        state = new HashMap<>(initialState);
        this.index = index;
    }

    public Map<Sha256Hash, Long> getState() {
        return state;
    }

    public Map<Sha256Hash, Long> diff(Map<Sha256Hash, Long> newState) {
        return newState.entrySet().parallelStream()
                .map(hashLongEntry ->
                        new HashMap.SimpleEntry<>(hashLongEntry.getKey(),
                                hashLongEntry.getValue() -
                                        (state.containsKey(hashLongEntry.getKey()) ?
                                                state.get(hashLongEntry.getKey()): 0) ))
                .filter(e -> e.getValue() != 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Snapshot patch(Map<Sha256Hash, Long> diff, int index) {
        Map<Sha256Hash, Long> patchedState = state.entrySet().parallelStream()
                .map( hashLongEntry ->
                        new HashMap.SimpleEntry<>(hashLongEntry.getKey(),
                                hashLongEntry.getValue() +
                                        (diff.containsKey(hashLongEntry.getKey()) ?
                                         diff.get(hashLongEntry.getKey()) : 0)) )
                .filter(e -> e.getValue() != 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        diff.entrySet().stream()
                .filter(e -> e.getValue() > 0L)
                .forEach(e -> patchedState.putIfAbsent(e.getKey(), e.getValue()));
        return new Snapshot(patchedState, index);
    }

    void merge(Snapshot snapshot) {
        state.clear();
        state.putAll(snapshot.state);
        index = snapshot.index;
    }

    boolean isConsistent() {
        long stateValue = state.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if(stateValue != SUPPLY) {
            long difference = SUPPLY - stateValue;
            log.info("Transaction resolves to incorrect ledger balance: {}", difference);
            return false;
        }
        final Iterator<Map.Entry<Sha256Hash, Long>> stateIterator = state.entrySet().iterator();
        while (stateIterator.hasNext()) {

            final Map.Entry<Sha256Hash, Long> entry = stateIterator.next();
            if (entry.getValue() <= 0) {

                if (entry.getValue() < 0) {
                    log.info("Skipping negative value for address: " + entry.getKey() + ": " + entry.getValue());
                    return false;
                }

                stateIterator.remove();
            }
            //////////// --Coo only--
                /*
                 * if (entry.getValue() > 0) {
                 *
                 * System.out.ln("initialState.put(new Sha256Hash(\"" + entry.getKey()
                 * + "\"), " + entry.getValue() + "L);"); }
                 */
            ////////////
        }
        return true;
    }
}
