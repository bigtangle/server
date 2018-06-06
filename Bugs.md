



 
## add new key generation in key window
 

## problem that schedule is down.
  

## add fromadresse in UTXO



## save user data in database
  a) user data like  user postal data,  positive list and contacts (json data ) will be encrypted with public keys
    table userdata  hash
    
     private static final String CREATE_USERDATA_TABLE = "CREATE TABLE userdata (\n" + "    blockhash varbinary(32) 						NOT NULL,\n"
            + "    datatypeclassname varchar(255) NOT NULL,\n" + "    data mediumblob NOT NULL,\n"
             + "publickey varchar(255),\n" 
             + "    CONSTRAINT userdata_pk PRIMARY KEY (datatypeclassname, publickey) USING BTREE \n" + ")";
    
    like single token creation with check of data signature and add graph (UTXO)  use overwrite
    Transaction datatype to string 
  b) UI save the data as block API 

#  positive list and contact list as UserData with structure and Json to save
 


## address change to public keys

## create first block with a list of coinbase transaction with format
amount_0, publikey01, publickey02,  publickey03
amount_1, publikey11, publickey12


## remove coinbase from BlockType  Transfer, 
    add check allow only BLOCKTYPE_TOKEN_CREATION, BLOCKTYPE_REWARD, BLOCKTYPE_INITIAL

## mark of multi signs in UTXO    ( display   minimal sign number and signs address )
    