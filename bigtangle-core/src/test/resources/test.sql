select * from blocks   ;
select * from unsolidblocks order by height;
select * from orders   ;
select count( *) from blocks   ;
select count( *) from blocks  WHERE milestone>9;
select count( *) from unsolidblocks   where inserttime < 1515432033 ;
select   *  from unsolidblocks order by inserttime asc   ;
select * from txreward order by chainlength desc;
SELECT block, height, blocktype FROM blocks WHERE milestone>9;
select * from blocks where height > 800 ;
select * from blocks where height > 6150 order by height asc limit 500 ;
select * from blocks join unsolidblocks on blocks.hash = unsolidblocks.hash order by blocks.height asc limit 100 ;
select * from blocks order by inserttime desc limit 1000  ;
select * from orders where spent=0 and confirmed=1  ;

 SELECT blockhash, txreward.toheight, txreward.confirmed, txreward.spent, txreward.spenderblockhash, txreward.prevblockhash, 
 txreward.difficulty, txreward.chainlength FROM txreward 
JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1  and
   chainlength= (SELECT MAX(chainlength) FROM txreward JOIN blocks on blocks.hash=txreward.blockhash WHERE blocks.solid>=1)
   
select  missingdependency, height from unsolidblocks where directlymissing=1
select * from blocks where hash =373;

select * from blocks where confirmed=1 ;

select * from outputs  where spent=0 and confirmed=1 ;
select * from orders  where spent=0 and confirmed=1 ;
select * from ordercancel
update blocks set milestone=0    where height=0

OUTPUTS
select * from outputs where tokenid !='bc' ;
select * from tokens   ;
select * from orders   ;
select * from txreward   ;
select * from matching   ;
select * from  multisignaddress
select * from  multisign
MCMC 
select * from tips   ;

HELPER
select * from tokenserial   ;


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

select * from wechatinvite ;
delete from vm_deposit where amount <= 0
select userid ,useraccount, amount,  d.status, pubkey from vm_deposit d
             join Account a on d.userid=a.id
             join wechatinvite w on a.email=w.wechatId and w.pubkey is not null;

