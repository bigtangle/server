package net.bigtangle.userpay;

import org.apache.commons.lang3.ArrayUtils;

public class CryptoTokenlist {

    public static String ETH = "ETH";
    public static String USDT = "USDT";
    public static String BTC = "BTC";
    public static String ADA = "ADA";
    public static String SOL = "SOL";
    public static String DOT = "DOT";
    public static String DOGE = "DOGE";

    public static String UNI = "UNI";
    public static String LTC = "LTC";
    public static String FIL = "FIL";
    public static String XRP = "XRP";

    public static String LUNC = "LUNC";
    public static String LUNA = "LUNA";
    // public static String WBTC = "WBTC";
    public static String AVAX = "AVAX";
    public static String LINK = "LINK";
    public static String EOS = "EOS";
    // public static String MATIC = "MATIC";
    // public static String XLM = "XLM";
    // public static String ICP = "ICP";
    public static String TRX = "TRX";
    // public static String VET = "VET";
    // public static String ATOM = "ATOM";
    public static String SHIB = "SHIB";

    public static String HNT = "HNT";

    public static String HT = "HT";

    public static String BNB = "BNB";

    public static final String[] ethtokens = { ETH, USDT };
    public static final String[] usdttokens = { USDT };
    public static final String[] tradetokensHuobi = { ETH, BTC, FIL, SHIB, LUNC, LUNA, EOS, TRX, XRP, DOT, AVAX, DOGE,
            BNB, HT };
    public static final String[] transferHuobi = ArrayUtils.addAll(usdttokens, tradetokensHuobi);
    public static final String[] tradetokensBinance = { ETH, BTC, FIL, SHIB, LUNC, LUNA, EOS, TRX, XRP, DOT, AVAX, DOGE,
            BNB };
    public static final String[] transferBinance = ArrayUtils.addAll(usdttokens, tradetokensBinance);
    public static final String[] transfertokenslist = ArrayUtils.addAll(usdttokens, tradetokensHuobi);
    public static final String[] bestTradeTokenlist = { USDT, ETH, BTC, FIL, SHIB, EOS, TRX, XRP, DOT, AVAX, DOGE };

    public static final String[] payofftokenslist = { BTC, USDT, ETH ,FIL};
    public static final String[] payofftokenslistNoBTC = { USDT, ETH };
    public static final String[] btctokens = { BTC };

    // this is production token id
    public static String ETHTOKENIDPROD = "026fa143b1cc091aa95efbbab3f1458e84454e5c4aae3a0ccb47cff9cd31945055";
    public static String USDTOKENIDPROD = "03d3df82d1e5fe2167f4c540873e0522cfb92c6af843eeb00f58a1e6354fc78560";

    public static String BTCPROD = "020f7a1a6bc5586b615a81ad4f963e448c624f0de86e0c06f8d57a273cbf51827d";

    public static String FILPROD = "030c21fc8f7876bc302b66bc718637446c40a192e666a21056b4bf07b72aafc066";
    public static String SHIBPROD = "025a1eb03b0d1b441c95da17594abd3ce24fba2b9e84c92247f1ecddadd2e86627";

    public static String EOSPROD = "0288696f0df3a082925bce37172379edc31abca17cb2e2283ffbffe2be53a11459";

    public static String TRXPROD = "0304675d25c8d51a4d57bf3e42dd04210478552f4c19b6106050e0dfc727f9d892";

    public static String XRPPROD = "030bd06d802576d6b564d575554e6850624928558bdef13a0c284ec349e9c285be";

    public static String DOTPROD = "034c4199c8ca916408bbc90b74053525297c0614df72656f489ac1f7be63c287ec";

    public static String LUNCPROD = "039f608fadcc4955986267b5e27bbb469c7ad48f1ca1670182011309a998673aea";
    public static String LUNAPROD = "02e92363183b31b24468a3d8be0f7fa17ed239bfecf00e613b9f9677256c794062";

    public static String AVAXPROD = "02431e891dd5fdc76aa3ccd847288b35d764f7bdbe757fd88aacfa04416cca7ed5";

    public static String DOGEPROD = "024baeebd26eb5bd2315186e46c4f9c8674108165fc48787a1cc95655601f4b7cb";

    public static String HNTPROD = "02243482ef043924ce46ffb0c5b4b55f730ef50fd2f45c5689d0293d2f1b94e9ce";

    public static String HTPROD = "03ad75811568cb81e11344966d2a6b742749842f7b2aeab6451e64f5adbdcc6971";

    public static String BNBPROD = "03c56e7118e117975f9448f0a75f6e76bffc9704fdab025513249b975e62f55ef2";

    public static String BinanceETHPayADDRESS = "0xf8395101d6361e325ae1e53693408bfb5ba8d7b7";

    // Tokenid in Bigtangle
    public static String getTokenId(String token, boolean test) {

        if (CryptoTokenlist.ETH.equals(token)) {
            return test ? ETHTOKENIDPROD : ETHTOKENIDPROD;
        }
        if (CryptoTokenlist.USDT.equals(token)) {
            return test ? USDTOKENIDPROD : USDTOKENIDPROD;
        }
        if (CryptoTokenlist.BTC.equals(token)) {
            return test ? BTCPROD : BTCPROD;
        }

        if (CryptoTokenlist.FIL.equals(token)) {
            return test ? FILPROD : FILPROD;
        }

        if (CryptoTokenlist.SHIB.equals(token)) {
            return test ? SHIBPROD : SHIBPROD;
        }

        if (CryptoTokenlist.EOS.equals(token)) {
            return test ? EOSPROD : EOSPROD;
        }

        if (CryptoTokenlist.TRX.equals(token)) {
            return test ? TRXPROD : TRXPROD;
        }

        if (CryptoTokenlist.XRP.equals(token)) {
            return test ? XRPPROD : XRPPROD;
        }
        if (CryptoTokenlist.DOT.equals(token)) {
            return test ? DOTPROD : DOTPROD;
        }
        if (CryptoTokenlist.LUNC.equals(token)) {
            return test ? LUNCPROD : LUNCPROD;
        }
        if (CryptoTokenlist.LUNA.equals(token)) {
            return test ? LUNAPROD : LUNAPROD;
        }
        if (CryptoTokenlist.AVAX.equals(token)) {
            return test ? AVAXPROD : AVAXPROD;
        }

        if (CryptoTokenlist.DOGE.equals(token)) {
            return test ? DOGEPROD : DOGEPROD;
        }
        if (CryptoTokenlist.HNT.equals(token)) {
            return test ? HNTPROD : HNTPROD;
        }

        if (CryptoTokenlist.HT.equals(token)) {
            return test ? HTPROD : HTPROD;
        }
        if (CryptoTokenlist.BNB.equals(token)) {
            return test ? BNBPROD : BNBPROD;
        }
        return  "";

    }

    public static boolean isETHToken(String token) {
        return CryptoTokenlist.ETH.equals(token) || CryptoTokenlist.USDT.equals(token);
    }

    public static String getTokenname(String token) {
        if (CryptoTokenlist.FIL.equals(token)) {
            return "filecoin@etf.com";
        }
        if (CryptoTokenlist.SHIB.equals(token)) {
            return "shiba@etf.com";
        }
        if (CryptoTokenlist.TRX.equals(token)) {
            return "tron@etf.com";
        }
        if (CryptoTokenlist.LUNC.equals(token)) {
            return "luna@etf.com";
        }
        if (CryptoTokenlist.LUNA.equals(token)) {
            return "luna2@etf.com";
        }
        return token.toLowerCase() + "@etf.com";
    }

    public static String getNetTokenname(String token) {
        if ("filecoin".equals(token)) {
            return FIL.toLowerCase();
        }

        return token.toLowerCase();
    }

    /*
     * Ethereum Contract Address
     */
    public static String getContract(String token, boolean test) {

        if (CryptoTokenlist.ETH.equals(token)) {
            return "";
        }

        if (CryptoTokenlist.USDT.equals(token)) {
            return test ? "0x7e2f5874326b0ba1b7ed3432ba9d3de1b6169737" : "0xdac17f958d2ee523a2206206994597c13d831ec7";
        }

        return token;

    }

}
