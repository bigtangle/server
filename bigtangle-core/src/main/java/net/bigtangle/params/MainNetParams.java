/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bigtangle.params;

import java.math.BigInteger;

import com.google.common.collect.ImmutableList;

/**
 * Parameters for the main production network on which people trade goods and
 * services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        // !!!this is initial value and used in genesis block hash, it can be
        // changed only for height
        maxTarget = new BigInteger("578960377169117509212217050695880916496095398817113098493422368414323410");
        // !!!this is initial value and used in genesis block hash, it can be
        // changed only for height
        maxTargetReward = new BigInteger("5789603771691175092122170506958809164960953988171130984934223684143236");

        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        packetMagic = 0xf9beb4d9L;
        bip32HeaderPub = 0x0488B21E; // The 4 byte header that serializes in
                                     // base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; // The 4 byte header that serializes in
                                      // base58 to "xprv"

        genesisPub = "5505d17290943f725e84a75f0ae459c6f00e2ebf16222ef4ec9afa13dd34d53ac5729342b7929d5986f7f058433eedbf1a18a03f549c6a115a3cbe2617c35221e992884e0ab7d15eb7b7495f6733221984b8bc3300aae4e0fd875b4a9857d3da8d39950fa2b1232bc7750aaa432c50dfbbff2db6eab70ed203abe97b153237a4c0232570dade53a7ef38833b3e34131d9bb2e6f61875c7bbe4a268c00e3bbf9214a5d85dbb41b5e0c17f143138747a5d05f4f7b7d9a5240bb6b731d71377de5908634b902ae6131774af1da59bfe58940b35ef890906a35e8891880b8165fb0db1442dd64dcc88e1d50a447918f1e8688c68016437bcc5cb71ee90de5d00b443ffe7fb7f3fd03d2c0bf585b7ee2e7d46e8bc5c458261733dbb23e9006195ffbaec34ab6d98121bfababd4b365b0d185dbf07371b085798b269829e1c5d94787ea703350024589ece887e270e0ebb2a39bb5b399dbc8e51ca3cbd1e43b0c64f4f0c25d104971778356af6c92449cdbcbdc300dc0f38cbe02f19e6400b2984bfcd7fce4baff3fe8bb209301308f0d7319355a1a52eacddd7ff836a42b445b0c54f4cceba2e094f7e3e82ad6f803636875d720445154a6ce85ce924c142c2387c658a39cca26edc3edcf5661d509af76670277e38fd8c39601d2998d24460b269a108e42ae23ef03ed281bdb591c7a4252c8a30783d5addf8abbfbf60c204f0f1b3318f8bd51b3d26169e041304bc7025d00a29d9334d12c09d81002c6a2b889c9cc441f3774ef70d5d4e6b2077fb6b67c1ebc7d2cd700ab3b9d0e46b2e1baccd7ee94b820c04742344fa65c7e66a1b62d5f2589889069de16d339194da9a113a1492b91286bddd931c5550b85aacc98445ae22e00203751fb9d2690bc93c7ba25991185cb71d38e7324575b44e384d15fde3eb5a2611c0dab53dd02cde871eaaf560119a917a9bb2982b44170814a6a39e042d971209552725d38c7ab6394f252335b6b3ee355136c8a7e29bf1ea85b9a09b87bde851d707882b2669ebe7be653d22b38ec5b0195e5896da71de1db4473d000eb8f8b88eab7a64f23e00b5accf05d7b24ad766aa611f1ec203a592e20e8be7d9503cec89038420b4fa9b6d1ca35448549aa14ee80fb10bf59c4c3943bae120dfe35bbd44a78f8039c7c9e58e1991497b1c3b0a89575fe0376ae10af9dcc63d38fe2aa237489c0f3abd5679132a2971b6fbd0bfd1139fae85e8f4219e0551c51609be3fd87ee503a9153fa5ef273868e4378d19cccb65ebc5895e8c231e2ca03b6a74b1a049101026d64d92452c7254243544e6f74fc10487d5041977a23cbce86b12ce0e351c442fc6ffd7d169c5fffe5488f1d7fb0fcec9cef5c161b9556eaae8097cef5f8699296e924a71fd45110e479107f2821d4664aa813e5e1646d60c64f7b9e9f4223a4d895368943da79d958f091873d42bcad32b65ca03cf41e3a603dc3ac2eb1ad822bc43529b2864569ecd6b9931b8ed6a3755e29be8aed7348cbb65c4908adc598ee62ad68cd0423457606bdf74fb9b933263ccf621e1169ddf3cc38d62f1f0ec9101fa8d9d5f38d7374577cd3407beb165aae705d3534092a48c07a994aff4bac14f3a88b840e253be30a7fdec0e5e61d3164e0382c860fd8dd588bfbda9a205f01449bfe1d62578f4d46a952f22fcd5b25bf1733dfe5ecf6da7a75d1751b36731fb1f0e15f80a90553bbff3d608dae67be34cab85ab26946326eb97ee220204c379792d85108bfa380a785d8039286c3b0404dd7e21405231ce0914a6327c6b7c18b4eacf7f17c1998e37d33030831cbefd165ecf782845a440e3e235fae1a8b410db57b796ff8f0b9decc0ca150656d48da4a76c12e784e335ac846bdbe978a08f5c7077d3828ed32e2e9f0dbddd10e4b40f7c65f42a9df1cc93de6159cafaa7415ac7de17503a5bb39d10e05dcaa07b6c7e19498bc9a645c3d5fb897bb334f35db30d260ee458e4d93d4de14b95beb4296db6fd29ea5ec158e3f642a31615a079c4a9a963ab4571b4e968ceb17be71399f0c1d6ba0109fb67dddc3382e9a4e2d322f2b94e371b7dd7924cd06de65085c29a8b2732e3eaf83daa557584ff89007f3902022e59cfc5514821ec475f46aa52f71ddd55e11dd0ba03caf85ca601bff66ba4789f59c2ce54a0f4a65eca82d283052ed6089942b794a04373a2d6f5cad518196dd7717a74d4de31d423df089796735385b4439ee30cc9a4a9213d9473828cf1bf9a146f42ca7f87a6dd7a46fb7f40b45acc83e7f38faf2865d08263dc5c2d97cfd6c3174afd54ae114703697fac344c3f13b1bfd962176149597222e3b2f2c113d1b5cc6bc06389225ff0850a498b3839215dca7a148770dbb9f48b16b2141b3a68d3981111561ccdc9c909d0904ecc799c44be087d0a4ef8e47e7ec053263606dbacbf41159facd1b5c61a36a6f7b2939bd14d05dbb3b93d19340f2967f6c2d83c1458415971074b4f85fcbc37d381d4ec6a03360e11c7697fb8a9ff901389be2561ba730b9530f0bf386007ca3fdf92173bc59ab03fc2c2ae92e669117bb6409f3d6e3969f2d5c0a26dbde0b40a8e2259e120aa04e1c351caaf200f0b102e11f3a4965bf628d2b7c9a0e054d5b30ac00fd3bd4f6e5780e061e9899108bc3ee487e745cf589799e1803232786db34183dd7b31920d57002141fce2b2f0fba6e886914e8f7dd6a721b8fec9e3d1a2683848fae74e4efae84544bbe377fb6acf6cf3de";

        permissionDomainname = ImmutableList.of("0222c35110844bf00afd9b7f08788d79ef6edc0dce19be6182b44e07501e637a58");
     
        // Equihash Settings
        equihashN = 100;
        equihashK = 4;

        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;

        dnsSeeds = new String[] {};
        // httpSeeds = new HttpDiscovery.Details[] {
        // // Andreas Schildbach
        // new HttpDiscovery.Details(
        // ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
        // URI.create("http://httpseed.bitcoin.schildbach.de/peers")
        // )
        // };

        addrSeeds = new int[] {};

        genesisBlock = createGenesis(this);
        // seeds for servers

    }

    public String[] serverSeeds() {
        return new String[] { "https://81.169.156.203:8088/", "https://61.181.128.236:8088/",
                "https://61.181.128.230:8088/" };

    }

    private static MainNetParams instance;

    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }


}
