select * from blocks   ;
select * from unsolidblocks order by height;
select * from openorders   ;
select count( *) from blocks   ;
select count( *) from unsolidblocks   where inserttime < 1515432033 ;
select   *  from unsolidblocks order by inserttime asc   ;
select * from txreward ;
select * from blocks order by height desc limit 200 ;
select * from blocks order by height asc limit 200 ;
select * from blocks join unsolidblocks on blocks.hash = unsolidblocks.hash order by blocks.height asc limit 100 ;
select * from blocks order by inserttime desc limit 1000  ;
select * from openorders where spent=0 and confirmed=1  ;

select  missingdependency, height from unsolidblocks where directlymissing=1


select * from outputs  where spent=0 and confirmed=1 ;
select * from openorders  where spent=0 and confirmed=1 ;

OUTPUTS
select * from outputs  ;
select * from tokens   ;
select * from txreward   ;
select * from ordermatching   ;
select * from matching   ;

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
select * from openorders;


SELECT blockhash FROM blocks INNER JOIN openorders 
ON openorders.blockhash=blocks.hash 
WHERE blocks.height <= 99999999 AND blocks.milestone = 1 AND openorders.spent = 0;

select * from vm_deposit ;

select * from wechatinvite ;
delete from vm_deposit where amount <= 0
select userid ,useraccount, amount,  d.status, pubkey from vm_deposit d
             join Account a on d.userid=a.id
             join wechatinvite w on a.email=w.wechatId and w.pubkey is not null;

