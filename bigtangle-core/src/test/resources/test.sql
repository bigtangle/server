select * from blocks  where milestone <0 order by height desc ;
select * from orders   ;
select count( *) from blocks   ;
select count( *) from blocks  WHERE milestone>9;
select count( *) from unsolidblocks   where inserttime < 1515432033 ;
select   *  from unsolidblocks order by inserttime asc   ;
select * from txreward order by chainlength desc;
SELECT block, height, blocktype FROM blocks WHERE milestone>11670 ;
select * from blocks where height < 750 ;

select * from blocks order by height desc limit 500 ;
select * from blocks where height < 4000 order by height desc limit 500 ;
select * from blocks join unsolidblocks on blocks.hash = unsolidblocks.hash order by blocks.height asc limit 100 ;
select * from blocks order by inserttime desc limit 1000  ;
select * from blocks where confirmed=1 order by height desc limit 500 ;
select * from orders where spent=0 and confirmed=1  ;

select * from mcmc  ;

select *  FROM blocks, mcmc  WHERE blocks.hash=mcmc.hash and solid=2 AND milestone = -1 
AND confirmed = false   AND mcmc.rating >= 5;

select *  FROM blocks where 00fee4ba4aa56d21a989c7617fa20954f346f73a6de3a0dbb61f4421c5655661

select * from blocks where blocktype=2 order by height desc limit 500
select * from blocks where milestone=339 and blocktype=3
select * from blocks order by milestone;
select * from blocks where milestone <0 ;
select * from blocks where milestone > 0 ;
select max(height) FROM blocks
select count(*) FROM blocks, mcmc  WHERE 
blocks.hash = mcmc.hash and solid=2 AND milestone = -1 AND confirmed = false

select  *  FROM blocks, mcmc  WHERE 
blocks.hash = mcmc.hash and solid=2 AND milestone = -1 AND confirmed = false

select  * FROM blocks WHERE milestone = -1   AND solid = 2
 SELECT blockhash, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, 
 txreward.difficulty, txreward.chainlength FROM txreward 
JOIN blocks on blocks.hash=txreward.blockhash WHERE milestone=339

select * from blocks where hash = 0x00003ea668739eb196b27ddb5903180e6a84305e5b13f5dcd5ab78f9dc6b3f6b;

 SELECT blockhash, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, 
 txreward.difficulty, txreward.chainlength FROM txreward 
JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1  and
   chainlength= (SELECT MAX(chainlength) FROM txreward JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1)
   
select  missingdependency, height from unsolidblocks where directlymissing=1
select * from blocks where hash =373;

select * from blocks join outputs on blocks.hash=outputs.blockhash where blocks.hash=0x00000075491105d21a1654d8f4566dd819c111b100818c07b66a3ae8a8b4de76 ;
select * from blocks where hash=0x00000075491105d21a1654d8f4566dd819c111b100818c07b66a3ae8a8b4de76 ;
select * from blocks where blocktype=12 ;
select * from blocks where confirmed=1 ;
select * from blocks where  milestone=36
select * from blocks join outputs on blocks.hash=outputs.blockhash where blocks.blocktype=12 and tokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";

select * from outputs  where spent=1 and confirmed=1 ;
select * from outputs  where spent=0 and confirmed=1 ;
select * from orders  where spent=0 and confirmed=1 ;
select * from ordercancel
update blocks set milestone=0    where height=0
select count(*) from outputs where confirmed=1 and spent=0 and tokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a" ;
select * from orders where confirmed=1 and spent=0 and offertokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a" ;
select * from outputs where blockhash = 0x000051e704d8ca112b077308fc2873e0062cb0530bab4757ccc1bb03779c2209;

select * from txreward join orders on txreward.blockhash=orders.collectinghash order by toheight desc  ;


select * from blocks where blocktype> 9 and milestone =  37 ;
OUTPUTS
select * from outputs where tokenid !='bc' ;
select * from tokens   ;
select * from orders  where orderbasetoken !='bc' limit 1 ;


select * from multisign
select count(*) from orders where collectinghash= 0x0000000000000000000000000000000000000000000000000000000000000000   

 
select * from txreward  order by chainlength desc ;
select count(distinct(difficulty)) from txreward   ;
delete    from txreward where  difficulty <= 2490057664
select * from matching   ;
select * from  multisignaddress
select * from  multisign

select count(*) from chainblockqueue   ;
select  *  from chainblockqueue where orphan = true 
 
select * from tips   ;
select count(*) from tips   ;
SELECT blocks.hash, rating, depth, cumulativeweight,   height, milestone, milestonelastupdate, 
inserttime,  block, solid, confirmed FROM blocks 
             INNER JOIN tips ON tips.hash=blocks.hash where milestone <0
             
  SELECT blocks.hash, rating, depth, cumulativeweight,   height, milestone, milestonelastupdate, 
inserttime,  block, solid, confirmed  FROM blocks order by inserttime desc limit 50

HELPER
select * from tokenserial   ;

select count(*) from outputs where 
 fromaddress!=''

 select  * from outputs where toaddress='1DVMvugpdT2QuhhtUAiUU3cTBMxaDvCCud'
 fromaddress='' and coinbase=false
 
select * from blockevaluation   ;
select * from multisign;
select * from multisignaddress;

select * from  exchange;

select * from ordermatch;
select * from orderpublish;
select * from orders;


SELECT blockhash FROM blocks INNER JOIN orders 
ON orders.blockhash=blocks.hash 
WHERE blocks.height <= 99999999 AND blocks.milestone = 1 AND orders.spent = 0;


 SELECT blockhash, height
             FROM blocks INNER JOIN orders ON orders.blockhash=blocks.hash
             WHERE   orders.confirmed = 0  
            AND orders.spent = 0 AND 
            orders.collectinghash=
            '0x0000000000000000000000000000000000000000000000000000000000000000'
 
 SELECT blockhash, height
             FROM blocks INNER JOIN orders ON orders.blockhash=blocks.hash
             WHERE    
            orders.collectinghash=
            '0x0000000000000000000000000000000000000000000000000000000000000000'           
            
select * from vm_deposit ;
select * from tokens where tokenid = '0201ad11827c4ed13a079ecca5e0506757065278bfda325533379fdc29ddb905f0';
select * from wechatinvite ;
delete from vm_deposit where amount <= 0
select userid ,useraccount, amount,  d.status, pubkey from vm_deposit d
             join Account a on d.userid=a.id
             join wechatinvite w on a.email=w.wechatId and w.pubkey is not null;
select count(*) from outputs where confirmed=1 and spent=0 and  tokenid = "bc";
select count(*) from outputs where confirmed=1 and spent=0 and  tokenid = "bc" group by toaddress;
select count(*) from outputs where confirmed=1 and spent=0 and tokenid = "03bed6e75294e48556d8bb2a53caf6f940b70df95760ee4c9772681bbf90df85ba";


 SELECT blockhash, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, 
 txreward.difficulty, txreward.chainlength FROM txreward where chainlength=446310;
 
 