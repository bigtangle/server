/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.encrypt;

import org.spongycastle.crypto.KeyEncoder;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.ECPublicKeyParameters;

/**
 * Created by Anton Nashatyrev on 01.10.2015.
 */
public class ECIESPublicKeyEncoder implements KeyEncoder {
    @Override
    public byte[] getEncoded(AsymmetricKeyParameter asymmetricKeyParameter) {
        return ((ECPublicKeyParameters) asymmetricKeyParameter).getQ().getEncoded(false);
    }
}