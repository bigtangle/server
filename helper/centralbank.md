# Fiat Money from Central Bank
The Central bank can issue Fiat money via BigTangle as a Token.

## The requirements:
 - max security: multiple control instance for seperate functions,
    - as issuer, issue process must be controlled by multiple signatures
    - control of the issue: 
    - transfer to other institition
    - revoke of a token in a seria
 - KYC 
 - AML
 
 ## Design 
  - use bigtangle to create a serial of token 
  - create meta token for control of validity with linkage to internal system
  - create a permissioned subtangle for transfer to other institution
  
## Process
  - Create  onetime token with multiple signatures in BigTangle, for example EURO2018
  - define a subtangle in Bigtangle with interface user list with multiple signatures for transfer
  - start the transfer of EURO2018 to subtangle interface
  
  - central bank install a permissioned subtangle in his full control
  - the token will be recreate in central bank Subtangle
  - all other institutions are permissioned user in central bank subtangle
  
### all institutions do the same as the central bank

### transfer token from central bank subtangle to subtangle of each institution
 - institution user has the token in central bank subtangle and do the transfer with format address@subtangle
 - the central interface address will transfer the token amout on BigTangle to institution interface address


## revoke of the token
  - central bank defines a meta token (for example EURO) with URL to his own system, this URL interface can anwser a list of valid token and true/false with a given token
  - this is maintained in central internal system and is full controlled by central bank
  - this enable the revoke of a token

  






  