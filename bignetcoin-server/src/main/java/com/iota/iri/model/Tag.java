/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.iota.iri.model;

/**
 * Created by paul on 5/15/17.
 */
public class Tag extends Hashes {
    public Tag(Hash hash) {
        set.add(hash);
    }

    public Tag() {

    }
}
