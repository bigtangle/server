package net.bigtangle.tools.config;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;

public class Configure {

    public final static String SIMPLE_SERVER_CONTEXT_ROOT =  "https://bigtangle.de/";

    public final static String ORDER_MATCH_CONTEXT_ROOT = "http://localhost:8089/";

    public final static NetworkParameters PARAMS = MainNetParams.get();
}
