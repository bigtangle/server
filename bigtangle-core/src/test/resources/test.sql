select * from blocks   ;
select * from openorders   ;
select count( *) from blocks   ;
select count( *) from unsolidblocks   where inserttime < 1515432033 ;
select   *  from unsolidblocks order by inserttime asc   ;

select * from blocks order by inserttime desc limit 1000  ;
select * from openorders where spent=0 and confirmed=1  ;

OUTPUTS
select * from outputs   ;
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
