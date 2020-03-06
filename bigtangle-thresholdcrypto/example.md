peers: peer1, peer2, peer3
Threshold=2

BLS

Commit to Secret Key Shares
This step involves each party using Shamir Secret Sharing to generate a set of threshold shares to
distribute to the other parties.

Let ss[] be SharmirSecretShares(x,peerCount, threshold)