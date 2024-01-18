select * from blocks  where milestone <0 order by height desc ;
select * from orders   ;
select * from contractevent   ;
select * from contractresult   ;
select * from orderresult   ;
select * from outputs  where confirmed =true and spent=false ;
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

select *  FROM info.blocks where  hash=0x000c1f398adde6b2c31722340987dea675eadff2841cdb7825afc90f1d4c5097;

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
 txreward.difficulty, txreward.chainlength FROM txreward order by  txreward.chainlength desc
JOIN blocks on blocks.hash=txreward.blockhash WHERE milestone=339

delete from txreward where chainlength=197088

select * from blocks where hash = 0x0000000c1c45469ab3bcea91afbf582027800e7280c2dd90e05b5249296ed7f28;

 SELECT blockhash, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, 
 txreward.difficulty, txreward.chainlength FROM txreward 
JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1  and
   chainlength= (SELECT MAX(chainlength) FROM txreward JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1)
   
select  missingdependency, height from unsolidblocks where directlymissing=1
select * from blocks where hash =373;

select * from blocks where hash=0x000000b8374049724da3bfede9dee27a7b5f310942981028a27e779631fb8657;

select * from blocks join outputs on blocks.hash=outputs.blockhash where blocks.hash= 0x000039b6b149700642826b603800cbbbbe73a8b9af24980b3fb9154c2a0119e8;
select * from blocks where hash=0x00000075491105d21a1654d8f4566dd819c111b100818c07b66a3ae8a8b4de76 ;
select * from blocks where blocktype=12 ;
select * from blocks where confirmed=1 ;
select * from blocks where  milestone=36
select * from blocks join outputs on blocks.hash=outputs.blockhash where blocks.blocktype=12 and tokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";

select count(*) from outputs  where spent=1 and confirmed=1 ;
select * from outputs  where spent=0 and confirmed=1 ;
select * from orders  where spent=0 and confirmed=1 ;
select * from ordercancel
update blocks set milestone=0    where height=0
select count(*) from outputs where confirmed=1 and spent=0 and tokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a" ;
select * from orders where confirmed=1 and spent=0 and offertokenid = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a" ;
select * from outputs where blockhash = 0x006131c37244272ebba62f797a815f3f1f86703a054d015a3e50423e64876f12;


select * from txreward join orders on txreward.blockhash=orders.collectinghash order by toheight desc  ;


select * from blocks where blocktype> 9 and milestone =  37 ;
OUTPUTS
select * from outputs where tokenid !='bc' and coinbase=true;
select * from tokens   ;
select * from orders  where orderbasetoken !='bc' limit 1 ;
select *  FROM orders WHERE collectinghash = 0x000000b95317048c9a90e779769e5c15bcb8757a7379917ab6c9c09b9e5337a2 
select *  FROM orders WHERE  blockhash=0x000005964AF7DB191AEB73BEDD6FA739324D338F31D21A7F5FA03F2130C392BC
select * FROM ordercancel WHERE confirmed = true and spent=false
select * from contractresult;


select max(milestone) FROM orders, blocks WHERE  blockhash=0x000005964AF7DB191AEB73BEDD6FA739324D338F31D21A7F5FA03F2130C392BC and collectinghash=hash;

select * from multisign
select count(*) from orders where collectinghash= 0x0000000000000000000000000000000000000000000000000000000000000000   

 
select * from txreward  order by chainlength desc ;
select count(distinct(difficulty)) from txreward   ;
delete    from txreward where  difficulty <= 2490057664
delete    from txreward where   chainlength > 91100
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

select * from account   ;

HELPER
select * from tokenserial   ;

select count(*) from outputs where 
 fromaddress!=''

 select  * from outputs where toaddress='14Kt2zgLFL3DSi4eHofBjZisQWogBRnZhN'
 fromaddress='' and coinbase=false
 
select * from blockevaluation   ;
select * from multisign;
select * from multisignaddress;

select * from  exchange;

select * from ordermatch;
select * from orderpublish;
select * from orders;
SELECT * FROM blocks

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
 
 

 ALTER USER 'root' IDENTIFIED WITH mysql_native_password BY 'test1234';
flush privileges;
use info;