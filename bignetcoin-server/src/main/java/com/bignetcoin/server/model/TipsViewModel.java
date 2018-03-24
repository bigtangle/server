/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.model;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TipsViewModel {
    final Logger log = LoggerFactory.getLogger(TipsViewModel.class);

    public static final int MAX_TIPS = 5000;
    private FifoHashCache<Sha256Hash> tips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);
    private FifoHashCache<Sha256Hash> solidTips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);

    private SecureRandom seed = new SecureRandom();
    public final Object sync = new Object();

    public boolean addTipHash (Sha256Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            return tips.add(hash);
        }
    }

    public boolean removeTipHash (Sha256Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            if(!tips.remove(hash)) {
                return solidTips.remove(hash);
            }
        }
        return true;
    }

    public void setSolid(Sha256Hash tip) {
        synchronized (sync) {
            if(tips.remove(tip)) {
                solidTips.add(tip);
            }
        }
    }

    public Set<Sha256Hash> getTips() {
        Set<Sha256Hash> hashes = new HashSet<>();
        synchronized (sync) {
            Iterator<Sha256Hash> hashIterator;
            hashIterator = tips.iterator();
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next());
            }

            hashIterator = solidTips.iterator();
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next());
            }
        }
        return hashes;
    }

    public Sha256Hash getRandomSolidTipHash() {
        synchronized (sync) {
            int size = solidTips.size();
            if(size == 0) {
                return getRandomNonSolidTipHash();
            }
            int index = seed.nextInt(size);
            Iterator<Sha256Hash> hashIterator;
            hashIterator = solidTips.iterator();
            Sha256Hash hash = null;
            while(index-- >= 0 && hashIterator.hasNext()){ hash = hashIterator.next();}
            return hash;
            //return solidTips.size() != 0 ? solidTips.get(seed.nextInt(solidTips.size())) : getRandomNonSolidTipHash();
        }
    }

    public Sha256Hash getRandomNonSolidTipHash() {
        synchronized (sync) {
            int size = tips.size();
            if(size == 0) {
                return null;
            }
            int index = seed.nextInt(size);
            Iterator<Sha256Hash> hashIterator;
            hashIterator = tips.iterator();
            Sha256Hash hash = null;
            while(index-- >= 0 && hashIterator.hasNext()){ hash = hashIterator.next();}
            return hash;
            //return tips.size() != 0 ? tips.get(seed.nextInt(tips.size())) : null;
        }
    }

    public int nonSolidSize() {
        synchronized (sync) {
            return tips.size();
        }
    }

    public int size() {
        synchronized (sync) {
            return tips.size() + solidTips.size();
        }
    }

//    public Sha256Hash getRandomTipHash() throws ExecutionException, InterruptedException {
//        synchronized (sync) {
//            if(size() == 0) {
//                return null;
//            }
//            int index = seed.nextInt(size());
//            if(index >= tips.size()) {
//                return getRandomSolidTipHash();
//            } else {
//                return getRandomNonSolidTipHash();
//            }
//        }
//    }


//    public void loadTipHashes(Tangle tangle) throws Exception {
//        Set<Indexable> hashes = tangle.keysWithMissingReferences(Transaction.class, Approvee.class);
//        if(hashes != null) {
//            synchronized (sync) {
//                for (Indexable h: hashes) {
//                    tips.add((Sha256Hash) h);
//                }
//            }
//        }
//    }
//
//    public Set<Sha256Hash> getTipsHashesFromDB (Tangle tangle) throws Exception {
//        Set<Sha256Hash> tipsFromDB = new HashSet<>();
//        Set<Indexable> hashes = tangle.keysWithMissingReferences(Transaction.class, Approvee.class);
//        if(hashes != null) {
//            tipsFromDB.addAll(hashes.stream().map(h -> (Sha256Hash) h).collect(Collectors.toList()));
//        }
//        return tipsFromDB;
//    }

    public class FifoHashCache<K> {

        private int capacity;
        private LinkedHashSet<K> set;

        public FifoHashCache(int capacity) {
            this.capacity = capacity;
            this.set = new LinkedHashSet<>();
        }

        public boolean add(K key) {
            final int vacancy = this.capacity - this.set.size();
            if (vacancy <= 0) {
                Iterator<K> it = this.set.iterator();
                for (int i = vacancy; i <= 0 ; i++) {
                    it.next();
                    it.remove();
                }
            }
            return this.set.add(key);
        }

        public boolean remove(K key) {
            return this.set.remove(key);
        }
        public int size() {
            return this.set.size();
        }
        public boolean addAll(Collection<K> c) {
            return this.set.addAll(c);
        }
        public Iterator<K> iterator() {
            return this.set.iterator();
        }
    }

}
