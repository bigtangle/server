package net.bigtangle.tools.config;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.UnitTestParams;

public class Configure {
    
    public final static String CONTEXT_ROOT = "http://localhost:8088/";
    
    public final static NetworkParameters PARAMS = UnitTestParams.get();
    
    public final static ECKey OUT_KEY = new ECKey();
}
