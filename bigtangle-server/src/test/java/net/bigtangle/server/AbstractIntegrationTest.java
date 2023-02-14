/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.core.response.MultiSignResponse;
import net.bigtangle.core.response.PermissionedAddressesResponse;
import net.bigtangle.core.response.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MCMCService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.server.service.TipsService;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    public String contextRoot;
    public List<ECKey> walletKeys;
    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    public WalletAppKit walletAppKit;
    public WalletAppKit walletAppKit1;
    public WalletAppKit walletAppKit2;

    protected final KeyParameter aesKey = null;

    private HashMap<String, ECKey> walletKeyData = new HashMap<String, ECKey>();

    @Autowired
    protected FullBlockGraph blockGraph;
    @Autowired
    protected BlockService blockService;
    @Autowired
    protected MCMCService mcmcService;
    @Autowired
    protected RewardService rewardService;

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected StoreService storeService;

    @Autowired
    protected TipsService tipsService;
    @Autowired
    protected SyncBlockService syncBlockService;

    @Autowired
    protected ServerConfiguration serverConfiguration;
    
    @Autowired
    protected void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }

    protected static ECKey outKey = new ECKey();
    protected static ECKey outKey2 = new ECKey();
    public static String testPub = "7ef6e9371fcdcb807e1b1e4f921390dfefaf1dec1690fa0c00ad41e99ffef12452c6d94a4eda1a56282000c2c0b49f4b6bde0efa891b3513e4c86e6c770c466941abfc5c0c2aca3b2c1a9333bc8ccc24393c201a4362d491e41140e4c8c27e90775a41f16ab6e80f24ec7ad8ca36d09e6062be9f685e34a59f19ef9f3578f3c343363de78e23f6e0ded5666b54e47288f2f363071d39f6abe64cc782c1818588b0d74c51be65743473d0445988e6fc49f7f514d41ffb94f66c7d4d678e404eb88fc08855724992df9eb5e1095c5aaa13b04e0eb681df2c17bb46bbc7b66cdee1e76fae55e0a120afe29a95b076c0a8a991c509866aa7498745c397c0e310202e9c2d50df27b3665865b1415b1a9d4a7faab306a70f267201a12bc1e0192e9a13c82d30e1cbc309c71dc5a5857712dcc2fb6a008fc1e30b5e589bdf551c65f990a1bc2478474b1ebd0e1081ede8fed3a7c5dc8f5836279aceff6e3b0c1698902675dfa8958c881fd8d190826efa29cf4640751a9ac83d7d503271d23ae5308525b449b2020d90d8611ed0fea968a2722aa954ffda190167806da63254d4a76840f2cdc1e93e5b549af20275a63974bd420a2b1a7aed3c2fdfd7624ad38767bd796fa8fc798289bb6cacaa867426f27b0441dc5ae646d1e2000697ad27a21a6d72a1ad31a3d674bf05b28bc2b7309d7da4d2db6f09b91f965a1f33611f244cb108a681b32f43b79f3220e09ed060859178ce0145b1b3f2aadd300a765a5fadbc9db9d959f94338199fdfff27160752e85ada634daf8192857dcff14af2e89595a08a04923879a2429fae129a6d4ed67c96c5afc8351b86958adfe424ad0c176c9397fbd45c9d403d06404b0912b492c97cb6bb4a3fd61d891be62fca3865382768dea7a3200ea5a0d35f35e2b87a327afbd426383441fd208a7989f4cc31390bc57b54c1a4f43390abad06c8edc512f7dcc51c25a787745da8b8eb84e177b26d52d51aaf33d2468f351d444dc1a2e74a97affb69860ef11c383c30e97bfdaa26a1f06629605bdfd05c5f236e8fb155421e47606aba2c2935fde0e19f8f2a8349235c537d04997a731d286a5d87d7c26fa855cf2af6b88067afa96626295c0aadd9bbecd93f9766b6b098fe6bc056b8240736e5e512b5dc46a8abd8cbafc3cae570a068ecd8c282e3d17347092bab5233b15f4397a9f6ad1fa8e3b1386b067e35355478e244bb5aff63977846379c627a84f60a46b836019d31046e631cccf9481ce7eb7ebabadb9a5f8168ea87a9b8eb8b18f7ad15b4d71c7318504dcdb001d294f7accca2da260961e1d94c173a29f30db55e127a715e302201363014eae9acab121bd89891c9253a81d690bafd761cc49c50d7eb87770f8bbe1498cc129e6ee32e327a6ad1949ee1be83e75c64eb7375b4e93545f58d763eb4b32b4d0d927bd7f7fb5ccac02ad910755e5f8f1190a176539ff41aff434f4a1a7a3fe551f8c9e99cd5e035fa2d11a705746e0474588a999ea25b20087c1deb5eac99ab49c8734d7ba3e378851b84231ef32bfa229708e76f633a71e86f6856362d5afe56de0f6d28d56599a7fda5e4ee2aa94693e15ca4671207007bf1492f701a524421a6a99c9e866158155fe940ee9eaef94b6148a93618cfaba74b0c99ea315386490b6052a6b90dc8105c007fcd56c1707b2aac08936cba30bca0c624335ca2bf7e18da9f1a3acda5f3e325c917dbf34ba2cab06016c118ebdc450b4acf8a33aee5942430dad2829cc5cac5df7d53f1224f405c56a431480ab5b2cf1c5d52ef58f439ec96499e7bef2ee67532d1d519e5e45a8162ebc04a15f1e61a8472b85a5fee4f89576874dac93e7c1fa462ae630e1be7bf5c7d7511ad54e7e5dce95130eb79b3c83599b743e8caa757aa65e741f66073331e09325a504786cb8af188b2397a85b6e6340305613c9d9dbe8e96f43032473a8b6e929d3263f8b7a38495ee3452ff21d782ad43e0351ba4090f6e21d6414f71b68e9710ab7ad5b94f26de079fb3b4ba313c595a0545c2c778db6a601da9c4e9579ed7a8177b0640d639ce6c07a304db9caa4e68c3db4a0365d422fb516c48fc8ddd210809627f36d0661ded59e5b7ae29787b0fe5786b3ea6e74045d4ae4ac80468258db6d22fa55bd700c260c15dd8261c8a956ac6e1070a773444fd0e998ab36318f95e4f70f83964353d2a9f0d0f16b9cfd1d4a6d0dbf240045711b9b554880992b67b038198f30ba4b43624f89423807f5519b83d140289650deef072d6d4e9c9c93261516b519e79bb912d5a090418170e63b6eb4283a3dbf681732f12d4380ba4926920162ed79c93a3bb55e2b62390bd5775e210227ecbbb599cf9b60b6cd26a2e6653bef071af377474048239fecd859fe5d2d070451b50c4ace818e76c1837292ed5e06d4bd5be9114a51dd7ded41728308b8dfa2f602e3d92d732996f74aca038f256179093dd8b3752f78152610f3ae7579ea9c27cf0308a91827cab88f8c352ef04d07c11c9dad727303a1affc8d8528b1b7cef8f58a07d5879b381514eb03cbb59406e42094f0cb9b6866ff213568f6b2a1dc7a6e3b4674df0c03477e5552162e0c902356e4770d818e28a28400e6f7dd628aec18a2372e8a2f66182bd1f01f6286ca5403c9d8dfae167690725edb4c450ddb353104a4c1c85c72b431fca27c175e548b97c90168f61dab100df348764afe11a1d63673ad1593a33db65e2a04ba165453a26bd4caa43d4";
    public static String testPriv = "bbdfa59883f87dd9e6a7dd3137e83452299ea844872ecb981b1d56fca9c581ef803c4538bbe858d4b559bb1c3f3dc1bf948c9b18c0bc93636e055a6ca5b49c6a7489882848183fcc4ec1896c87892bca4e1395af9f93a99a99d00bd99c03f5eed47a7e96d2987e8b1ef383454b220aa757585153246127156526548547288715253405648233803642455363425017584424138266175111431143007250100813582210813313373338323552178714683846843456055884135161571404424745461623063863613023122470884640051650373758500474153074063473263261248707863365485866411616266723107637640646635754175346536762774116871030736726004526463360870412765658115856448143243427444226118650023521400051554722063516835746020375757854862308375024483361015037604171880282472680408265885881212608356217303885635081288502326133651542052716750320440238217418474744857406534165010187572683037278475326052453603282577833840643764703271456066607807380176321354107665617137568002020075557172777811355463830687706545500121544874280150866241175330340072268535184031687225757037436433363284266556240531870381622552583757336407552150245586205406678377188241005423808442876865436701256162302386724183843237873478173430324638335614743464184514326300485044804634411051647471165164456415540235487343333555318703562764363147652177082175054815184276525702361673030267341068327332440556147271712158335265540616470353283648066730252587867673556238204528667257602574677248277460762220104737042384430340727218728048238240513021615556411216633085027441782546785554875646271752688048381461104234632566584182652042353854142240121706436810023445852858430101730018888212467317858028212704337403027626718501320753181401631433462761185745258642386588317082827456683441013874015178760350177254167748761833156087705577847636418535781274750777365434554805112408414377038277445630548664621804016040361035511362322107068456361158536360545588287062761704078223542640005018050360082355170073718626305206681224668656377741626486217652370726680781388875877834404074434250518860064447021557746560728178364585233105035336060205875570148243434342027014003848103237483685242488884576270416581253313288601454106804748534535111064353044154685467521075064641226402607626160866438210813270362621176184254077602726301180316008066117500152625137658117370221305665325466523815733061343310247331143078181878222463417146316058866020123104877662714464222287613200102611305878865505515025727657551533634182052687827070011676412083277114184262277012877512701048121401342234561316122108454424618147787342564057658612140860534860143536632763728133017443530435608278623145727233874603204871766132183261751212704650206165705200343605487306260658436183314200162015674502077240227453377355521578248316572848068187011776235466457582274437112754811742663037025034214705864186771061875513186102784684188738230228825435230354360515106103033304341270175601437085441521456875023214737451687166627680718234643062563385128635130537053244152705613581740444757665040644856427210681252082135328453631570335476238786360720531138745651121050076754768073600167421135543123852388704253713511852662603774052801346261167645f9ff763647bcd311a5256d2315ec7d24497ff3a11bffd4a57ebaa89c8d0652a0832a6e9a968cdb46ae5184ab09781e3d454df5697eb8bf0f7cee8c34809b390eae18608468ba9e4ade3d04c54e21d081d4ed2ee191b4c414d2ba634418444d01ecaa67702b1dd4343ca1c5020c90fae72ae2393409e8021adfc53cd1f6ad16647aad6800100c4e7266814f3e88a4b08bf37bb7640ce8595fdfde469425a84178a202313ad9caff949c5d7bdeb01f2d903849bcaa37cb7907427b0015dbff4283369269716a9c846d8ada34313b437686b0f42ca1c68a3d54d5a86b6e34ec9a4df2e7627b3c06fb2fb650ce3a22aec673dad5996b1048da06f5136d64cdda95fb298fb83179deaf040bde21502c3006d58155752668233fa1634e1a571a52a9730f2325fb98fa216e71d2cd2cade48569f30a9097a689b6bb5fe3194fb4dac23ae924da3d92e5254a381fbefe010dd32946253e98c8fc7d62a301a99531634ecb942e478e12a66ba28b68448754af13774e37208010b9b70edd0645546021caeca2347cbfdbfad80e180e35fe5ffab9c337d6f504535fde88cae02308dee55aae245cfb0055e42e4a06a72218708670a1d0b7d052212f29716352b2f6f5010ba50f2300e4dbaa2ca1541c489e65e0cd481f28908fe274e58e6127deacce8530437959fee15637e666dd006f20d143c577014c06f4b7bb3663a67a91cc69891d542378a825332d7a3720aae68984dd240143e308a8bf947c52a646c690914275bad6a4fe0c1fb6cca0f88121da68be0d1e61cb3249ec02fd6d638ce2186961c8575aece9d37b6fc8abf4e662323b335fe69a223b3dc57a83328670f7161d0376f11c934c428b6949581cebcd2b4c36853fa53c630343dd9fa50ec35c179b445788fbf2e305f526f1ba300426a3fa4b60a68f6c19a055437d474b2ea05e949ed677ebe5b316ea46db0e626e2fae714f8c34b9b615ac28934004117bff02ceff5ab394b99dc29047ca7a95b0c936cd8eef8b37dc782bb50fd79d01652fabfa38d248da3d7d03802b6cfff3b8414435d03862ed06b2353ee178d7f56fbd1cef79c994dc6b0150ebfcef207715a75b4a53627e09b35314d3f2765dfd2830d1c7da905a0d13c566ad7953b0b41925934e089ff0c676075a467ea4bd862aa1fc72b3c86cb212a2beefa7389c039a7c2bbd01cd9ad3f2e9b47144ed61707d641363acc10d29f4ed0881c0174a442366c8d521ae91489f3fadabb69f3689a7a5ac77f74fd045c06d65f4090581a30c9b9da714976436d82941d2201ac8f4b60531308795de0f7256a5bef11d0f1f4463b0a7652a9ce832a519aa1cfba8c2a092af96c1c4c2a151b03a037c2af2731dc05da893093e59f791eb8869db30a63a7dc026d508955e0b00ec703881e4073e2b5f9257496f46c8839eb75cb89db8e8796ebb2697d403d23335d2bb6ab541ab37a0c5a6322b6df995af2e648c20feb624c8f3519e98a70dfaadaa71bdb9144ab0f21cead223299102d43a399871306b048a2353c9e4f340e717f933f1c57c9ad8427432ae6ee591eff564fb0bbbbac8f34e46712ec1a397d824d02e192e148d305bae45df39353feb225ac2ae4b1744b339cf478f46fda346a5c98965d99f5abd8246a42f7467e6ac8916e5539e26dbdc40ce9f33233d077cfa289e849f177d6b3a1bde0f0853bba88c81974c94159bd3e75e815db984e37d208976dd5aed9c3872f2992c3bae80dde0ca51ab724a94f7b629e2ba8f5c9bacfecb704d2301c4566cd6f592eefb123120874f2920a4fcbd301c1e504b3280a790e86ee67af35245e4dce971c6677dc84d514c7d30dd3f8c94c385685bcc1013b2f7bf22c0d2d8733574a190d24cb4e7454a0c34ea2632da1ae54cfc8df2bd5dcafc18d158846a4696feb2bb0adeaf2bcf84b31fb48496fe33fbba02e639612a81530128282d979ae207c8445d9376dbcc5c475beca65acac4d1fad25f8042183a06a5f428e5594e05f2ae11b2989f47dc90d37fa8a9fb470c31c4d330264c1cfe54303861d643bd61c478743c196fe8b330468a0a9ba897c5e907f27d30006de8f23daadf3772e78ab9a7f31f0d63e7118484d1f57bced3fe3f299c21b7b5b7baf590ff0440dccbd788b67ea0ef29425a9fa0d9deadda2c6c11e90e7a3090c9ba3abd2909da473cf3c3c562c26f87ccac7b8dfa9d4900b47e2c972c5b9df43c319a2ffaf3c36130efe43adfa406655c8a98032a272819c39e1efb63924987adad6316b211f42cf7ad0e78019052bb02db7f424fafcd3f44d61c0464dee77422e63f277a26020a680ebe322c7193f24bc48b3c6cb4fbdcfafaff994fb8146ff00a6e2202133c165e65aa6cc300baa1e21dc6afcc9eaba4fba62723a3961282759763f14396ca250ab80f21cdbb72a70740c53e0c57df5000fc4d1d19c4c1e09d7c23048385bb7ff23abb935ba7e72d8a10fed162a5fa95ac4f988e18b65d57dbdc2985acaf79ee96ebc462ac5171f3f27f5269594433883c833bf5e10c0439e51e6a986c83089590eb41d43d8093677ca2684942e540fe263b7aa370c404882e283cbfccfbd015d8545deb8606e8fd512c06ffa62cdbf23eb38f3561c56f98a44650bd1eebab35d56079c4ff7cb01545fa88f8d3de4d2b852cc9ea0b28464d362a88ab2c5925c9f5073ee0c1efd1810bbf807d7671e4ff3cce164f0f6adac3e023a5988fa6ab76ab6f6d79bd11dcc837b49c87cb148e2ce24661ab76901730cc5637922e31ef914165046d799c4ac28dfec0d6a1e7f2f78d395d1dd5e7f745e02709dc9d332bd2731e20ad1f6234501537d8e2b72e1d08a4a82905c8d18d4e6a64de0b968d428d781a4e3d6923c7a9d68447ffb6016d059fdd844453b3954b1aeaad361e23a6c52255dcfecab327ad72b1ba8870e40198556bd17821d3f9e6ee957664ab375640df0e931aef182489691d748bf0158634305b574d0bae5be0fc2fd84157a016f4893c4f8af900ca30d78d95d652f2ceb1a36625c538c1df368f4e7b1a9f81d61c2fbe3d268825963039e5fb7dc4cf0c89ac1687648eba7293d50f9c0ff7ae09873699de6232fa3531ef83e5b5e1b9d1d6fc876b6ecbf9f8d5a4f6d40445e57188cb9517c739316c97e7ee0916b744ba1b17f32933f3f4017070e623cca57904adf1c24630900afc9bd1c5ef54852b93c5ae1d6ca2a7c94056893894ec940b6dd1f62545c7a18472a877fcf2d2af077ed5059b00d2cd11b539a4a776c9d6edb31a92a7b1e200287427a6f0bd802cd611f060c0d0ccf7fab2334d8dbe9d08cb6620fb460168f6012bc8da4f7a9d0c056e684bb48e99cd7f2c91de9040a7d882629bd745bcd8a21725c61ec8d5a14e9d590a20054b1b9fbe7be520fdc1ab0b512010803568149d9b8bc91b0fd8e29b4f7f107bd0f0433a7d8271a89ad215c66715d7d704d94dd00628b06439bc1a6de36f48ba653571d446910d4601ed4959f3d31f921889dad46a59c219d40af91b0cb91cbc3d3e3ef5ec";
    public static String yuanTokenPub = "70f9809e1ef7e2aa2c082ccc2de3b87617a47559d175f64b38ea7ea277125232f8562c77992943609b4beeded21786b0ac220f17079d309a547d0fe520d50459bca5ee9aef67f14a6a69dfb4d8656be8deb3a27cd43c7ad039df965127703cde970b05b13ae9d589cea59742dd4864ce677daf54f3bce15abfd3aff4c11b7d6ad9e924e15a5c98fc26d51c0f9a5a5158d7e352603d1828ea80657050283dc76d84eae563df4247ee7203bff751804b88fbacfb44b7d112e5da6e0a4d1a5edc0abf4fadcaa26fe6a57893b4a18d1f2706968a24dacbe8e1650d857072556da9c66e82bf98d1f5981f4f0981e35c89f38fc152d9f195a8ab20561d7183d44780e900c252eec99c8f12d407e2bd8519abc05b9286d1c3a1168feadefe1da7c231edc1e38e77813374cf933996e71d8e19d3d892f070369d4a9b29ef374aa9184bb1cdc1deb26b9f0a6daf1835b1ee5ae2cc2894ffaef921467507bfd3db147ec3d6bc0381092fe97c2e19245424f494f9525519bf5073d36e9cf5053d1f3896f02fdc8368d102169ab2e3d2957b801354bd4d15506507d9683c9ba7c3eb48dec9dd8702f66d5bd4428b8ed79136fdfdfd8155a8145b901707b4fb3a023e82dc1501f9793a166f2d6a36a02f5e5a91ba8a533589b33f1434b580eebc29c9ca478835664e59d1555848b6c640f6e80ac75bf1c7a84004f45f860e640aea5c07e620971eef31ac5cfb87e40016e4b39f544237a0e85b236acf79257c247ae9297e3abf792ef2ff4d476fc57007b2003e34e8201649e40f84aa013e1ff7a39a9f106232d2f94428d94b5c0ad74c777fb1581a411b846e036fd2dd026ea42aff21d59b5573cb7f71f9372f57bb0191ac772018637c648e8acd2e650806c11f4480f53308dc592dcb8186a3c4b8161b84035fae62c4fd38635887f666c9d322967bd5b0277dd8244a76a11bb569103eaeeb4f947eec3028605d3246803b233833978cb871da681f9dd017628cc0ef44435f9e2c11b9e96d98956246269cc24a0fc0c14c436329a42010479746f33d70b574f410418c93ecba1e253d0ffff13d6789eed998bdfd91b5f39336d6e6bd578de9274e6cf1dc7d531952bcc496829fa05cb55f6915ead1022799cbd82eddeba44857fa458e134b0376537546450d65e308b1bac0e45def11af3dddd1f0cad3fdec6ad7d40c3594ce282d1fb6692f0950909b51f95e39bb18aed45890aef390fa2b9c88c6ab3754ed42c4ed0c1529e9fb691027025945642aa17dcaf37a7c3527d73d447320c43f6ff4441174ea5e286b9ca1e22b58d431a9ff651afab824e289f723e0fb1f4a1476a972e190e39518e2b85e06e0b7517680e7364b67bf415cedbb72d57bf52ea531617a8bfb24f452c8dc6b80963d458896a9b83402e424e04dd3fb98ae88f3418ef98b8fb906cd1405393c2b6602ad0f83284f7de6530b68e540aa4ba8cb7b10d9307107a668d4dff4867bc9c2d87b60b180e023f4bd3b9073eb00359fe85f8cc909600088bc1c69896fec791eb3702905e0cb0a2ff13603b5ac0dbb9d27d9b8816bfc3241614d2e16d76f9005cc7de1b71a8c48bdcdddfacfbdf374b7fd7cc7a2007a1ca40a9b1ac0175a0a4f26134345a3694f86216e7669ca957da800ba1bc946451e8564cb2ed2bc86a98a14bc26bf5b05377a84fb08c686b913f6f286c14e16b33901daed34bfea7f61cbe4f0dc20fde1d93de7103cc9288e0972da93618fd4a3f1c56ed44b6df442e58e56e200f2e1fc3c551867c7575a4d987db7624b89a8fe010bb52ddf85587b392ca3288cefd3efac09e4eb08322632b6a67ef52926ffc916e42961028c5bb8be90d7a6015dbf8260656dc6b722501a4fdfcedb3be7452eadcb00b6d5f7998ced0d0cb58c4768d50f1c6bb70390ff5714a4cac5f05fcdcdb13e30ecf30657d5fa30e1f27ab177926ed05609f8b4620698b062123d18c6a27020d38def0ac8b73ed1d480dbac9174c03153d7e89868ed88fc27611e80fa8861a53796f6283b88e79f2584b691b028884e754d8c838955f6249b1aea329a6d30a27ef4a35744835955b26dcab5f32099ccb1d43b19984761752aebab0cf2b1827a6b38f81bec5348921ac42feb61487efc010e1e32ae5f9041aa25ff58b7bbe7a78c51cba0f0ebd8f1b0d8e31d382a8033dadd832f6c6dfa04de0a447bc2f6c5a5b7e3e3d9b6103240c01a21bb193e230037af087256d8562a704c9ecf02675c363a2b4688f2d656dbe5611a4fdc5559b352aebb1c841be76be96c8e1413e62bdfc658aeab7a5ce00fe9a7f799b48e327f423a178ff6ef4bd7fffbe965ff7bae6a3843683eb9d3852f2012ba813cc213c44015ace3ee75e9f7816c1c17efa34ae4dac338944bc64ad70b50ba4a80127b0046649967842028d0c06bdde9152c25fd781bd776b8b41de6ce400f1c9336055bc215ea0c9b3b0341ae69be32f5d0c9ff23224fecb7effb4faa61dfeb9cf10176c4ccdba53c847ab527371f647a0a8e6c316cb8e532eaad90aaf8bc933bd2269bf161da0630175c6598e10296584340e22ff3fdcbd93e1a1dabc66d3edb1373f27bd85298875563f061d040d4b347fca9fb98f0cf88ab79e410e049ea8c051f1881837f360a8dc1d1003ecf8c713a290054cd3db495af659a7c52ef4c44924af01ce89084bc4996e959b604d251a917127e755736ceca72fb3c5aa68b3a9a7383adc200ac4885babf23341f1201fd690ea04d37b885d85278";
    public static String yuanTokenPriv = "70f9809e1ef7e2aa2c082ccc2de3b87617a47559d175f64b38ea7ea277125232b3f06b95a4099d733fdfe896deb8648b0419e746c454dcc160b6199a3d691084c21c1c8731dad24d7ac0d543a984785bae611ac441b55c5ac46cf014c1cffe3246aa7ae91e5f2632a505db364f0c0199528267016618718447716867066265277344054775870587452846747073782212217762710433428601536147005352147582571161480025416217645200726678768268627683140335206033023003433236233155447484687560140687551724453605013856383014085880257041023580327360620151363483840315737848885127532507367701274732757551026533103247547408355780887753560632671013675621603016136810604170168481567267182340171268568272506272806636273755746561326046874066274010487622526203277783151404134282618878365083386545471154644458240126541708502327224201774154484372065263100756534356056837758663373276233572051318820164245147836500824057107388645417858512180344045810414033205102423324302865328101421834613205775060835108875560551556478623205020274778185304230211117434414811665038256513236558575517768623480434576024461303456046373576732581433524430855477754548145371217816725564172867452888008802338662647045030163775436236547004347477118672316020653170842871746554357425741740776840085287887383150046577825175023874727212574020477338870465850663118542487607633708555444423130041762254742544346867088468731553317155100125107322304525838481240641747828244328734027756177006278814747821135822367482762520286082723246508144110341184827328034718513768404428680430218322731383316083054125506038645663382142461682828130411146727447413270072536105765253570512212420422862351671573118242053136184675346200113441156338474411687334104556571581556442804160174580648026115843132664101632485874864783044018128510756455465581454484042745513148551070857782504635871086860127683582017068503552860515405804837761528875160716872072153118528680325323741674087616882077822684780703504252572064414404830617126200654320282128376568408304536671823710300127184503406323603175115268283320627643624574015318636631804058342251540567264134848065130134283803517864851122174470735863664530251016505733214116881811424482065257577082840884377036803175607804175007541423523068063110162840187817555721101674608504145132413648188842203102084828365126656805718188674135482706431660061633560686654888138712147562815603752082006630773040464871037575158443348713166243244700443348628840546768135745250112142872347026828161760382343240577645244530826678683300453046557251812823741730575717805840143440717754218500348554063234803503336476181274023112100363825130131678335142200540382012317674428722015503068613173123876121834162853156671516371605016612808000586731577144323111666301720881021740742010737065857767633804128805415305586800130085750847174324066407525514061256031243442604883107830507435220200707625135802428881247341617583186302181800082563054560102400653553355080445023161643854444831081383054277800158217267272601146645476701713773672188836288142500755823141334615876878328826441537733240168867860321780136504875668748612157474073583473103235477781353071403532833755637802221457f3568ee5fa7122ed0016bb41a22546bf6182369d39dab3eaf9f9ccd950507217b02f59c603a95f2d312c8c7c7246c30313cc35138e0c64d210d9493e97b1be44370fd94d8040b1cd362b4d82f7e1db91abe177edfc76e24a7600b9fed0ab91e6c20daa18f5b7b3b8074c63e4db5e4deb49edf19436e00933e6e9488069544efe5fb5dea77b7710299ee17a4a278cd8ef3f4c7d8523187d5d5726b8aae4377e4ea3a1702c9ab8953db88a964f2138c072d2bb3ee4726119fd206b2ace1d6bf031af928e16c829723294015704cb2ee0e97f86b10a117a0d995442473b05bca5422279ba759552478704f6aa686a543ac56fcd92ddab92ad4d652e85ed5f68e1fa8d12f3f3d680e3146c2c83ee39aed2f80676d5c47bfb8c9ec7ffae43cf94c7d453b28630f91ae56f2064fd4a247cbf61c4b6433f6bf0687bcfc5d4555f91e9ad049924a8d3cc5961f1e794f3e6aa938393983a99d50d6bbc62d062cc44888bcd8f09160c2c765104f88ba775de3643571b115f09833345d83b06032cee3f20548529c6643ae9e10d575a5985b420536dd7816b09cb2a3f271ecd198c323d863627129f7697cba7915bad746ea7983fbb7ac0f028014d44a95ef20e1493968ac0b181d0d1635dec3a6414154c13af1304eae1837091b6042717cbc236703a464999eb2ace222165545e746fc52757b64c91b3a8b0e2b86045009cdb4c2889d7aa5dd55c8c7cf48234720d545b5d41de2201653d7ba4ac59f94a9769e7081934821eddb29a13efd6c6e5a2dc5910e5c08b1a95f79e3ba96168cfc7a5af7884cf1781bbf389a62ef4982366c9bc691717be7c9ead94873d3fc12262092f723219c81f2154a53e82380743812be92d8dacf89fa95560e72aeefc2bc4c301ea10fb412e00c4299323b3b13d7a627d48bec13db94dd5f9e4ccd9b8845de99b7e4a8b55d3fa0877b9b00b239daa35a452a9f06119700f5930f4c24b5bfca97440f0423ca95f99e1612dc369070592435a2131324e08edc2bdfef29733ed009424ba17fe9cf05c0b2245ea83aaaabad2720d7b7c9b490fc64bca4829b226ff2742158810a772d13c7fe0937da8ad7b7d7c60468e811aefc89adde6bfc2e6e5ff0739c2066eb3d3dc3ab6528462e3c6def05d2eb29aa7fda3ef06c69244b1933a1db860f882bdfea4f2fb98fabf79e6e733aef95f0a56d3c5df789d22e1885ac80b25f44a531e709a4d175116e456c4ff8ff04baea951c1e078042b60842c5d2838cc747617d6b5f8fddffd1f38da9d76286115df3bcbcdcc2000fb78d594d3dcaca5b57b7c0f107eecb86178ecec38bfdb0c873ef1a44d2ff5237804e2cfce075f9df12fecaff1252290ae8da7da1bd95d482bc2e64aed04bfdce9c77db4d60cf6db6f24502732dd77a35a05a7c82498b5f49204b038b4da0b3f7b9e092b7729e8c647bfa044c3be448adcb367440204523ac512c306a82006150747827803dca7dc8d21bad20fda5d9fcbcf9d6adc3664bc07a3600c718441c6bc174a98a26484ad80796eabf1398916600f454cd109b8080efaadffb868cc307fe95468281924975e9d0ad182a3a8ad5a4fafcb515ff15e43c424329e0799f34f8811583948c31858d1b1170943adcf84eb3c6fd663745ec9c1d6f3df96ff07d13fc5311b4a910f388617df7903d8fc163adc08b38e6cd914687a8987db0216474b16932d584d79472289b6e1d492a5dbf0a52547d53260a4ddd162ba824387da6f326e3b39d7538f31251b2ff91535c440feac3336dd98476542e112411c84c82b6a886bdb599bf0fdf6c98ff8f015994d31192cb5e972d4b840d3ec449124d5f561f6d455aa9141c3d0895fb22927cbc00f59bf9fde6426f75b95c0c72d4d8b74a776c4c174e4f6c4aef77ad91f734eafc3965a409ff3328bf3f248a88ced1d8f4969dd46ec6722dbd1d49152f4300bc27a9871966a55e3109599ee05cc164c431dc7e4359f1e831ca1f98d5d136e08f94e68b26e3d917f3682a47ac58416ee49ee68cc57e99b53a242c9bd91214425f411527f3a211551c3e6924b3379342ca0a7a1e755380018325ae7136f698fa64eca85f4b113eab4debf73dac3801e96ac50cb2f5da33f978ad7554f0381c2efce7a91c9f27a2178a51f150dd27dbcce612fff4581fb4245689df3aa0db3e3f0b1eaa059b48ffc00c68b9a65f3d72d39d6bf8e4ae50d17a573515817e66e9d4ade03401f3d563e0f3c2efbb4a61b7ef8247c6f2cde15cf20d8ad9392cb784a80764ea8d5f94b21bde361d714be3687280419d341149410f599864425eda9b9ff336ff046328b821d87dc00dd94ba14bed686f7f9d3da53c059349f774d67069a049026940d41b1585f6bbf2a1e7928d298b2b3fbc40bc261eecc9a936bf9a512ee013235045d2a96e582347510ab6531759ba5b44023d9e2f485834efa0e3869a31c10f24f004cbf0ea4128dbfd1d8a7e07bfa2a969e762133d9562e7975f9e1a034f179fe2aef7fda67e9d0c77e64c729d496c7be35f9a42a3e796ce3d7421054a8e6716f2c46612c67fc24b52b44e40d77c70c727d542d6d1b419f48f663bf622400d60120634ad85b581b1292fd81d07310b5193cd5718486f5d5344e05174394db9776e00bcc0c43411c1197de2b9086fab00c144aa13900c6e7c31806ea77359e2663caa77b143d1dec9ad0c2aab179f268b003a4e502397e6d8fb42c885d9ae426294fbb4338bc393c11d758c5bc3f07d48303832870fb76d1cc5de116e27c1102bce910bdf6a113fab8538550f4a8120fd02ef483fae281fd9f738f5a8b89ee56caded31029b213ab44d35865b9cadb8da92ddaaffc898054afb79afd36bdc44b12fc79c27699f74a0e2a0b4221b01a1e5f5d7e7ea79d320bcdb9c4fabab93e4cd59af0142ff72391808f860b5602260bb970639d846727724af9c58123d9034a5f42e6ee0d6708ed253e22338c1227f766a536a8350aa8284917345c1926a744876a5537edeef0d6b8d6cd38c0d9a8e8b9dd8759dd5996b9359c2b048f0d8ae9b7068d3bbe513e71ce1ec5783173ecac99392c7d4ad1855d29919be1a50967dd12cab0f0edc43f618b395043c42a6172b8cadc5c5dc8074ca82084c7f9d412773f907c649d09b4d63ba058ca3a02d69bd94c6db705c9a0459d03ba27ea96fbf2aaad65e32b05ddd20456f9ef915f284a4899e40d9ed6b367170af47d29bca399c676fbb0429292efc3bbb2265b4b9de27f851afdae5290d8ec5a230aab0fc82a992b8ad0f326ebdfb5b6f76b142eda2c931be82c7848c088c7b3e15bed4313330d8c8983dc260fabd8d6fbb6ca3f0a470ef4ebb746d493b33db6124557afdc89c18edcae1a1058f6527450e4d77a0e4c3ca89d112eb9d74bd0e5ed615b1e7e3d925840ef33477e38ae5484c4551205acb2f3aa964eb21bdbd1d4ab5fd3eb3c28e8ee0d08f87574ae5032d5a8eb3fbf851941dd7b54e9da86da96ec9f58882b8d7abce51eefb2590521c9ab36aadac4b1b3fc4a474";

    protected static ObjectMapper objectMapper = new ObjectMapper();
    public FullBlockStore store;

    public void testCreateDomainToken() throws Exception {
        this.walletKeys();
        this.initWalletKeysMapper();

        String tokenid = walletKeys.get(1).getPublicKeyAsHex();
        int amount = 678900000;
        final String domainname = "de";
        this.createDomainToken(tokenid, "中央银行token - 000", "de", amount, this.walletKeys);
        this.checkTokenAssertTrue(tokenid, domainname);
    }

    protected Block addFixedBlocks(int num, Block startBlock, List<Block> blocksAddedAll) throws BlockStoreException {
        // add more blocks follow this startBlock
        Block rollingBlock1 = startBlock;
        for (int i = 0; i < num; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true, store);
            blocksAddedAll.add(rollingBlock1);
        }
        return rollingBlock1;
    }

    public void checkTokenAssertTrue(String tokenid, String domainname) throws Exception {
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", tokenid);
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        Token token_ = getTokensResponse.getTokens().get(0);
        assertTrue(token_.getDomainName().equals(domainname));
    }

    public void initWalletKeysMapper() throws Exception {
        wallet1();
        wallet2();
        List<ECKey> tmpList = new ArrayList<ECKey>();
        tmpList.addAll(this.walletKeys);
        tmpList.addAll(this.wallet1Keys);
        tmpList.addAll(this.wallet2Keys);
        for (Iterator<ECKey> iterator = tmpList.iterator(); iterator.hasNext();) {
            ECKey outKey = iterator.next();
            walletKeyData.put(outKey.getPublicKeyAsHex(), outKey);
        }
    }

    @Before
    public void setUp() throws Exception {
        Utils.unsetMockClock();
        store = storeService.getStore();
        store.resetStore();

        this.walletKeys();
        this.initWalletKeysMapper();

    }

    @After
    public void close() throws Exception {
        store.close();
    }

    protected void payBigTo(ECKey beneficiary, long amount) throws Exception {
    	payBigTo(beneficiary, amount, new ArrayList<>());
    }

    protected void payTestTokenTo(ECKey beneficiary, ECKey testKey, long amount)
            throws Exception {
    	payTestTokenTo(beneficiary, testKey, amount, new ArrayList<>());
    }

    protected void payBig(long amount) throws Exception {
    	payBig(amount, new ArrayList<>());
    }

    protected void payTestToken(ECKey testKey, long amount)
            throws Exception {
    	payTestToken(testKey, amount, new ArrayList<>());
    }

    protected void payBigTo(ECKey beneficiary, long amount, List<Block> addedBlocks) throws Exception {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        giveMoneyResult.put(beneficiary.toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payBig");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
    }

    protected void payTestTokenTo(ECKey beneficiary, ECKey testKey, long amount, List<Block> addedBlocks)
            throws Exception {

        HashMap<String, Long> giveMoneyTestToken = new HashMap<String, Long>();

        giveMoneyTestToken.put(beneficiary.toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyTestToken, testKey.getPubKey(), "");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
        // Open sell order for test tokens
    }

    protected void payBig(long amount, List<Block> addedBlocks) throws Exception {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payBig");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
    }

    protected void payTestToken(ECKey testKey, long amount, List<Block> addedBlocks)
            throws Exception {

        HashMap<String, Long> giveMoneyTestToken = new HashMap<String, Long>();

        giveMoneyTestToken.put(wallet2Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyTestToken, testKey.getPubKey(), "");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
        // Open sell order for test tokens
    }

    protected Block resetAndMakeTestToken(ECKey testKey, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        Block block = makeTestToken(testKey, BigInteger.valueOf(77777L), addedBlocks, 0);
		return block;
    }

    protected Block resetAndMakeTestTokenWithSpare(ECKey testKey, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        Block block = makeTestToken(testKey, BigInteger.valueOf(77777L), addedBlocks, 0);
        payTestTokenTo(testKey, testKey, 50000, addedBlocks);
        payTestTokenTo(testKey, testKey, 40000, addedBlocks);
        payTestTokenTo(testKey, testKey, 30000, addedBlocks);
        payTestTokenTo(testKey, testKey, 20000, addedBlocks);
        payTestTokenTo(testKey, testKey, 10000, addedBlocks);
		return block;
    }

    protected void generateSpareChange(ECKey beneficiary, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        payBigTo(beneficiary, 500000, addedBlocks);
        payBigTo(beneficiary, 400000, addedBlocks);
        payBigTo(beneficiary, 300000, addedBlocks);
        payBigTo(beneficiary, 200000, addedBlocks);
        payBigTo(beneficiary, 100000, addedBlocks);
    }

    protected Block resetAndMakeTestToken(ECKey testKey, BigInteger amount, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        return makeTestToken(testKey, amount, addedBlocks, 0);
    }

    protected Block makeTestToken(ECKey testKey, BigInteger amount, List<Block> addedBlocks, int decimal)
            throws JsonProcessingException, Exception, BlockStoreException {
        // Make the "test" token
        Block block = null;
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = new Coin(amount, testKey.getPubKey());
        // BigInteger amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, null, testKey.getPublicKeyAsHex(), testKey.getPublicKeyAsHex(),
                "", 1, 0, amount, true, decimal, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

        block = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmTransaction(ECKey fromKey, ECKey beneficiary, String tokenId, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        return makeAndConfirmTransaction(fromKey, beneficiary, tokenId, sellAmount, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmTransaction(ECKey fromKey, ECKey beneficiary, String tokenId, long sellAmount,
            List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;

        // Make transaction
        Transaction tx = new Transaction(networkParameters);
        Coin amount = Coin.valueOf(sellAmount, tokenId);
        List<UTXO> outputs = getBalance(false, fromKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(tokenId))
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, beneficiary));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), fromKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);

        // Sign
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        TransactionSignature sig = new TransactionSignature(fromKey.sign(sighash).sig);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with tx
        block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        // Confirm and return
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmBlock(List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;

        // Create and add block
        block = predecessor.createNextBlock(predecessor);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        // Confirm and return
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndAddBlock(Block predecessor) throws Exception {
        Block block = null;

        // Create and add block
        block = predecessor.createNextBlock(predecessor);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        return block;
    }

    protected Block makeSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        return makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.sellOrder(null, tokenId, sellPrice, sellAmount, null, null, basetoken, true);
        addedBlocks.add(block);
        return block;

    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
    		String basetoken, List<Block> addedBlocks) throws Exception {

        Block block = makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
        		basetoken, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmPayContract(ECKey beneficiary, String tokenId, BigInteger buyAmount,
            String contractTokenid, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.payContract(null, tokenId, buyAmount, null, null, contractTokenid);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);
        return block;

    }

    protected Block makeBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        return block;
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.buyOrder(null, tokenId, buyPrice, buyAmount, null, null, basetoken,true);
        addedBlocks.add(block);
        return block;
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
        		basetoken, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks)
            throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        return makeCancelOp(order, legitimatingKey, addedBlocks, predecessor);
    }

    protected Block makeCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks,
            Block predecessor) throws Exception {
        // Make an order op
        Transaction tx = new Transaction(networkParameters);
        OrderCancelInfo info = new OrderCancelInfo(order.getHash());
        tx.setData(info.toByteArray());

        // Legitimate it by signing
        Sha256Hash sighash1 = tx.getHash();
        ECKey.ECDSASignature party1Signature = legitimatingKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.sig;
        tx.setDataSignature(buf1);

        // Create block with order
        Block block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_CANCEL);
        block = adjustSolve(block);

        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        return block;
    }

    protected Block makeRewardBlock() throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        return makeRewardBlock(predecessor);
    }

    protected Block makeRewardBlock(List<Block> addedBlocks) throws Exception {
    	Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        Block block = makeRewardBlock(predecessor);
        addedBlocks.add(block);
		return block;
    }

    protected Block makeRewardBlock(Block predecessor) throws Exception {
        return makeRewardBlock(predecessor.getHash());
    }

    protected Block makeRewardBlock(Sha256Hash predecessor) throws Exception {
        Block block = makeRewardBlock(store.getMaxConfirmedReward().getBlockHash(),
                predecessor, predecessor);
        return block;
    }

    protected Block makeAndConfirmContractExecution(List<Block> addedBlocks) throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());

        Block block = makeRewardBlock(store.getMaxConfirmedReward().getBlockHash(),
                predecessor.getHash(), predecessor.getHash());
        addedBlocks.add(block);

        // Confirm
        makeRewardBlock();

        return block;
    }

    protected void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts) throws BlockStoreException {
        assertCurrentTokenAmountEquals(origTokenAmounts, true);
    }

    protected void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts, boolean skipBig)
            throws BlockStoreException {
        // Asserts that the current token amounts are equal to the given token
        // amounts
        HashMap<String, Long> currTokenAmounts = getCurrentTokenAmounts();
        for (Entry<String, Long> origTokenAmount : origTokenAmounts.entrySet()) {
            if (skipBig && origTokenAmount.getKey().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING))
                continue;

            assertTrue(currTokenAmounts.containsKey(origTokenAmount.getKey()));
            assertEquals(origTokenAmount.getValue(), currTokenAmounts.get(origTokenAmount.getKey()));
        }
        for (Entry<String, Long> currTokenAmount : currTokenAmounts.entrySet()) {
            if (skipBig && currTokenAmount.getKey().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING))
                continue;

            assertTrue(origTokenAmounts.containsKey(currTokenAmount.getKey()));
            assertEquals(origTokenAmounts.get(currTokenAmount.getKey()), currTokenAmount.getValue());
        }
    }

    protected void assertHasAvailableToken(ECKey testKey, String tokenId_, Long amount) throws Exception {
        // Asserts that the given ECKey possesses the given amount of tokens
        List<UTXO> balance = getBalance(false, testKey);
        HashMap<String, Long> hashMap = new HashMap<>();
        for (UTXO o : balance) {
            String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue().longValue());
        }

        assertEquals(amount == 0 ? null : amount, hashMap.get(tokenId_));
    }

    protected HashMap<String, Long> getCurrentTokenAmounts() throws BlockStoreException {
        // Adds the token values of open orders and UTXOs to a hashMap
        HashMap<String, Long> hashMap = new HashMap<>();
        addCurrentUTXOTokens(hashMap);
        addCurrentOrderTokens(hashMap);
        return hashMap;
    }

    protected void addCurrentOrderTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
        // Adds the token values of open orders to the hashMap
        List<OrderRecord> orders = store.getAllOpenOrdersSorted(null, null);
        for (OrderRecord o : orders) {
            String tokenId = o.getOfferTokenid();
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getOfferValue());
        }
    }

    protected void addCurrentUTXOTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
        // Adds the token values of open UTXOs to the hashMap
        List<UTXO> utxos = store.getAllAvailableUTXOsSorted();
        for (UTXO o : utxos) {
            String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue().longValue());
        }
    }

    protected void showOrders() throws BlockStoreException {
        // Snapshot current state
        List<OrderRecord> allOrdersSorted = store.getAllOpenOrdersSorted(null, null);
        for (OrderRecord o : allOrdersSorted) {
            log.debug(o.toString());
        }
    }

    protected void checkOrders(int ordersize) throws BlockStoreException {
        // Snapshot current state
        assertTrue(store.getAllOpenOrdersSorted(null, null).size() == ordersize);

    }

    protected void readdConfirmedBlocksAndAssertDeterministicExecution(List<Block> addedBlocks)
            throws BlockStoreException, JsonParseException, JsonMappingException, IOException, InterruptedException,
            ExecutionException {
        // Snapshot current state

        List<OrderRecord> allOrdersSorted = store.getAllOpenOrdersSorted(null, null);
        List<UTXO> allUTXOsSorted = store.getAllAvailableUTXOsSorted();
        Map<Block, Boolean> blockConfirmed = new HashMap<>();
        for (Block b : addedBlocks) {
            blockConfirmed.put(b, blockService.getBlockEvaluation(b.getHash(), store).isConfirmed());
        }

        // Redo and assert snapshot equal to new state
        store.resetStore();
        for (Block b : addedBlocks) {
            blockGraph.add(b, true, true, store);
        }

        List<OrderRecord> allOrdersSorted2 = store.getAllOpenOrdersSorted(null, null);
        List<UTXO> allUTXOsSorted2 = store.getAllAvailableUTXOsSorted();
        assertEquals(allOrdersSorted.toString(), allOrdersSorted2.toString());
        assertEquals(allUTXOsSorted.toString(), allUTXOsSorted2.toString());
    }

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    protected Block createAndAddNextBlock(Block b1, Block b2) throws VerificationException, BlockStoreException {
        Block block = b1.createNextBlock(b2);
        this.blockGraph.add(block, true, store);
        return block;
    }

    protected Block createAndAddNextBlockWithTransaction(Block b1, Block b2, Transaction prevOut)
            throws VerificationException, BlockStoreException, JsonParseException, JsonMappingException, IOException {
        Block block1 = b1.createNextBlock(b2);
        block1.addTransaction(prevOut);
        block1 = adjustSolve(block1);
        this.blockGraph.add(block1, true, store);
        return block1;
    }

    public Block adjustSolve(Block block) throws IOException, JsonParseException, JsonMappingException {
        // save block
       byte[] resp = OkHttp3Util.post(contextRoot + ReqCmd.adjustHeight.name(), block.bitcoinSerialize());
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
        String dataHex = (String) result.get("dataHex");

        Block adjust = networkParameters.getDefaultSerializer().makeBlock(Utils.HEX.decode(dataHex));
        adjust.solve();
        return adjust;
    }

    protected Transaction createTestTransaction() throws Exception {

        ECKey genesiskey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
        if (spendableOutput.getValue().subtract(amount).getValue().signum() != 0)
            tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount),
                    genesiskey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash) .sig);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        return tx;
    }

    protected void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        File f = new File("./logs/", "bigtangle.wallet");
        if (f.exists())
            f.delete();
        walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle");
        walletAppKit.wallet().importKey(
                ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
        // add ge
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
    }

    protected void wallet1() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle1.wallet");
        if (f.exists())
            f.delete();
        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle1");
        walletAppKit1.wallet().setServerURL(contextRoot);

        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
    }

    protected void wallet2() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle2.wallet");
        if (f.exists())
            f.delete();
        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle2");
        walletAppKit2.wallet().setServerURL(contextRoot);

        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);
    }

    protected List<UTXO> getBalance() throws Exception {
        return getBalance(false);
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero) throws Exception {
        return getBalance(withZero, walletKeys);
    }

    protected UTXO getBalance(String tokenid, boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> ulist = getBalance(withZero, keys);

        for (UTXO u : ulist) {
            if (tokenid.equals(u.getTokenId())) {
                return u;
            }
        }

        throw new RuntimeException();
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        //byte[] response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(String address) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            listUTXO.add(utxo);
        }

        return listUTXO;
    }

    protected List<UTXO> getBalance(boolean withZero, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return getBalance(withZero, keys);
    }

    protected void testInitWallet() throws Exception {

        // testCreateMultiSig();
        // testCreateMarket();
        testInitTransferWallet();
        makeRewardBlock();

        // testInitTransferWalletPayToTestPub();
        List<UTXO> ux = getBalance();
        // assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }

    }

    // transfer the coin from protected testPub to address in wallet

    protected void testInitTransferWallet() throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(walletKeys.get(1).toAddress(networkParameters).toString(),
                MonetaryFormat.FIAT.noCode().parse("33333").getValue().longValue());
        walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "testInitTransferWallet");
    }

    protected Block testCreateToken(ECKey outKey, String tokennameName) throws JsonProcessingException, Exception {
        return testCreateToken(outKey, tokennameName, networkParameters.getGenesisBlock().getHashAsString());
    }

    protected Block testCreateToken(ECKey outKey, String tokennameName, String domainpre)
            throws JsonProcessingException, Exception {
        // ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);

        Coin basecoin = Coin.valueOf(77777L, pubKey);
        BigInteger amount = basecoin.getValue();

        Token token = Token.buildSimpleTokenInfo(true, null, tokenid, tokennameName, "", 1, 0, amount, true, 0,
                domainpre);

        tokenInfo.setToken(token);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(token.getTokenid(), "", outKey.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex, 0));
            }
        }

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        ECKey ecKey = this.walletKeyData.get(pubKeyHex);
        return this.pullBlockDoMultiSign(tokenid, ecKey, aesKey);

    }

    protected void testCreateMarket() throws JsonProcessingException, Exception {
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);
        Token tokens = Token.buildMarketTokenInfo(true, null, tokenid, "p2p", "", null);
        tokenInfo.setToken(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0, pubKey);
        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

    protected void checkResponse(byte[]  resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    protected void checkResponse(byte[]  resp, int code) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        assertTrue(error == code);
    }

    protected void checkBalance(Coin coin, ECKey ecKey) throws Exception {
        ArrayList<ECKey> a = new ArrayList<ECKey>();
        a.add(ecKey);
        checkBalance(coin, a);
    }

    protected void checkBalance(Coin coin, List<ECKey> a) throws Exception {
        List<UTXO> ulist = getBalance(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue().equals(u.getValue().getValue())) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    protected void checkBalanceSum(Coin coin, List<ECKey> a) throws Exception {
        List<UTXO> ulist = getBalance(false, a);

        Coin sum = new Coin(0, coin.getTokenid());
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId())) {
                sum = sum.add(u.getValue());

            }
        }
        if (coin.getValue().compareTo(sum.getValue()) != 0) {
            log.error(" expected: " + coin + " got: " + sum);
        }
        assertTrue(coin.getValue().compareTo(sum.getValue()) == 0);

    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws JsonProcessingException, Exception {
        // First issuance cannot be multisign but instead needs the signature of
        // the token id
        // Hence we first create a normal token with multiple permissioned, then
        // we can issue via multisign

        String tokenid = createFirstMultisignToken(keys, tokenInfo);

        makeRewardBlock();

        BigInteger amount = new BigInteger("200000");
        Coin basecoin = new Coin(amount, tokenid);

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
       byte[] resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        ;

        Token tokens = Token.buildSimpleTokenInfo(true, tokenIndexResponse.getBlockhash(), tokenid, "test", "test", 3,
                tokenindex_, amount, false, 0, networkParameters.getGenesisBlock().getHashAsString());
        KeyValue kv = new KeyValue();
        kv.setKey("testkey");
        kv.setKey("testvalue");
        tokens.addKeyvalue(kv);

        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(keys.get(2).getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));
        block = adjustSolve(block);

        log.debug("block hash : " + block.getHashAsString());

        // save block, but no signature and is not saved as block, but in a
        // table for signs
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());

        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(key1);
        ecKeys.add(key2);

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        final String pubKeyHex = multiSignAddress.getPubKeyHex();
        ecKeys.add(this.walletKeyData.get(pubKeyHex));

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
           byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByAddress.name(),
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);

            if (multiSignResponse.getMultiSigns().isEmpty())
                continue;

            String blockhashHex = multiSignResponse.getMultiSigns().get((int) tokenindex_).getBlockhashHex();
            byte[] payloadBytes = Utils.HEX.decode(blockhashHex);

            Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            Transaction transaction = block0.getTransactions().get(0);

            List<MultiSignBy> multiSignBies = null;
            if (transaction.getDataSignature() == null) {
                multiSignBies = new ArrayList<MultiSignBy>();
            } else {
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                        MultiSignByRequest.class);
                multiSignBies = multiSignByRequest.getMultiSignBies();
            }
            Sha256Hash sighash = transaction.getHash();
            ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
            byte[] buf1 = party1Signature.sig;

            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setTokenid(tokenid);
            multiSignBy0.setTokenindex(tokenindex_);
            multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);
            MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
            transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
            checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block0.bitcoinSerialize()));

        }

        checkBalance(basecoin, key1);
    }

    private String createFirstMultisignToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        String tokenid = keys.get(1).getPublicKeyAsHex();

        Coin basecoin = MonetaryFormat.FIAT.noCode().parse("678900000");

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
       byte[] resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        Sha256Hash prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, "test", "test", 3, tokenindex_,
                basecoin.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, keys.get(1), null);
        return tokenid;
    }

    // for unit tests
    public Block saveTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        tokenInfo.getToken().setTokenname(UUIDUtil.randomUUID());
        return saveTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block saveTokenUnitTestWithTokenname(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        return saveTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block saveTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey,
            Block overrideHash1, Block overrideHash2) throws IOException, Exception {

        Block block = makeTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, overrideHash1, overrideHash2);
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());

        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        pullBlockDoMultiSign(tokenInfo.getToken().getTokenid(), this.walletKeyData.get(pubKeyHex), aesKey);
        ECKey genesiskey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        pullBlockDoMultiSign(tokenInfo.getToken().getTokenid(), genesiskey, null);

        return block;
    }

    public Block makeTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws JsonProcessingException, IOException, Exception {
        return makeTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block makeTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey,
            Block overrideHash1, Block overrideHash2) throws JsonProcessingException, IOException, Exception {

        final String tokenid = tokenInfo.getToken().getTokenid();
        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            if (StringUtils.isBlank(tokenInfo.getToken().getDomainName())) {
                tokenInfo.getToken().setDomainName(permissionedAddressesResponse.getDomainName());
            }
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex, 0));
            }
        }

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

        if (overrideHash1 != null && overrideHash2 != null) {
            block.setPrevBlockHash(overrideHash1.getHash());

            block.setPrevBranchBlockHash(overrideHash2.getHash());

            block.setHeight(Math.max(overrideHash2.getHeight(), overrideHash1.getHeight()) + 1);
        }

        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();

        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.sig;
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);

        ECKey genesiskey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey.ECDSASignature party2Signature = genesiskey.sign(sighash, aesKey);
        byte[] buf2 = party2Signature.sig;
        multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(genesiskey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(genesiskey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf2));
        multiSignBies.add(multiSignBy0);

        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        // save block
        block = adjustSolve(block);
        return block;
    }

    public void createDomainToken(String tokenid, String tokenname, String domainname, final int amount,
            List<ECKey> walletKeys) throws Exception {

        Coin basecoin = Coin.valueOf(amount, tokenid);
        TokenIndexResponse tokenIndexResponse = this.getServerCalTokenIndex(tokenid);

        long tokenindex_ = tokenIndexResponse.getTokenindex();
        Sha256Hash prevblockhash = tokenIndexResponse.getBlockhash();

        int signnumber = 3;

        // TODO domainname create token
        Token tokens = Token.buildDomainnameTokenInfo(true, prevblockhash, tokenid, tokenname, "de domain name",
                signnumber, tokenindex_, false, domainname, networkParameters.getGenesisBlock().getHashAsString());
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setToken(tokens);

        List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
        tokenInfo.setMultiSignAddresses(multiSignAddresses);

        for (int i = 1; i <= 3; i++) {
            ECKey ecKey = walletKeys.get(i);
            multiSignAddresses.add(new MultiSignAddress(tokenid, "", ecKey.getPublicKeyAsHex()));
        }

        PermissionedAddressesResponse permissionedAddressesResponse = this.getPrevTokenMultiSignAddressList(tokens);
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        signnumber++;
        tokens.setSignnumber(signnumber);

        upstreamToken2LocalServer(tokenInfo, basecoin, walletKeys.get(1), aesKey);

        for (int i = 2; i <= 3; i++) {
            ECKey outKey = walletKeys.get(i);
            pullBlockDoMultiSign(tokenid, outKey, aesKey);
        }

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        ECKey outKey = this.walletKeyData.get(pubKeyHex);
        this.pullBlockDoMultiSign(tokenid, outKey, aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        this.pullBlockDoMultiSign(tokenid, genesiskey, null);
    }

    public TokenIndexResponse getServerCalTokenIndex(String tokenid) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("tokenid", tokenid);
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp, TokenIndexResponse.class);
        return tokenIndexResponse;
    }

    public void upstreamToken2LocalServer(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.sig;
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        block = adjustSolve(block);
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());
    }

    public Block pullBlockDoMultiSign(final String tokenid, ECKey outKey, KeyParameter aesKey) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        String address = outKey.toAddress(networkParameters).toBase58();
        requestParam.put("address", address);
        requestParam.put("tokenid", tokenid);

       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        if (multiSignResponse.getMultiSigns().isEmpty())
            return null;
        MultiSign multiSign = multiSignResponse.getMultiSigns().get(0);

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign.getBlockhashHex());
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.sig;

        MultiSignBy multiSignBy0 = new MultiSignBy();

        multiSignBy0.setTokenid(multiSign.getTokenid());
        multiSignBy0.setTokenindex(multiSign.getTokenindex());
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block0.bitcoinSerialize());
        return block0;
    }

    public PermissionedAddressesResponse getPrevTokenMultiSignAddressList(Token token) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("domainNameBlockHash", token.getDomainNameBlockHash());
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenPermissionedAddresses.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        PermissionedAddressesResponse permissionedAddressesResponse = Json.jsonmapper().readValue(resp,
                PermissionedAddressesResponse.class);
        return permissionedAddressesResponse;
    }

    public Block makeRewardBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws Exception {
        Block block = rewardService.createMiningRewardBlock(prevHash, prevTrunk, prevBranch, store);
        if (block != null) {
            blockService.saveBlock(block, store);
            blockGraph.updateChain();
        }
        return block;
    }

    public void sendEmpty() throws JsonProcessingException, Exception {
        int c = needEmptyBlocks();
        if (c > 0) {
            sendEmpty(c);
        }
    }

    public void sendEmpty(int c) throws JsonProcessingException {

        for (int i = 0; i < c; i++) {
            try {
                send();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    public void send() throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

    }

    private int needEmptyBlocks() throws Exception {
        try {
            List<BlockEvaluationDisplay> a = getBlockInfos();
            // only parallel blocks with rating < 70 need empty to resolve
            // conflicts
            int res = 0;
            for (BlockEvaluationDisplay b : a) {
                if (b.getMcmc().getRating() < 70) {
                    res += 1;
                }
            }

            return res;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<BlockEvaluationDisplay> getBlockInfos() throws Exception {

        String lastestAmount = "200";
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
       byte[] response = OkHttp3Util.postString(contextRoot + "/" + ReqCmd.findBlockEvaluation.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses);

    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w, byte[] pubkeyTo, MemoInfo memoInfo) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses, pubkeyTo, memoInfo);

    }

    public void mcmcServiceUpdate() throws InterruptedException, ExecutionException, BlockStoreException {
        mcmcService.update(store);
//        blockGraph.updateConfirmed();
    }

    public void mcmc() throws JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
        sendEmpty(5);
        mcmcServiceUpdate();

    }
}
