# weight of a block and related concepts
Here, we define the (own) weight of a block and related concepts. The weight
of a block is proportional to the amount of work that the issuing node invested
into it; in practice, the weight may assume only values 3 n , where n is positive integer
and belongs to some nonempty interval of acceptable values.
## cumulative weight
One of the notions we need is the cumulative weight of a block: it is defined
as the own weight of this block plus the sum of own weights of all blocks
that approve our block directly or indirectly. This algorithm of cumulative
weights calculation is illustrated on Figure 1. The boxes represent blocks; the
small numbers in the SE corner stand for the own weights of the blocks, while
the (bigger) bold numbers are the cumulative weights. For example, the block F
is approved, directly or indirectly, by the blocks A, B, C, E. The cumulative
weight of F is 9 = 3 + 1 + 3 + 1 + 1, the sum of the weight of F and the weights of
A, B, C, E.
On the top picture, the only unapproved blocks (the “tips”) are A and C.
When the new block X comes and approves A and C, it becomes the only tip;
the cumulative weight of all other blocks increases by 3 (which is the weight
of X).

<img src="weightchange.png" alt> 

Figure 1: On the weights (re)calculation

<img src="weigths.png" alt> 
 
Figure 2: On the calculation of scores (circled)

For the discussion of approval algorithms, we need also to introduce some other
variables. First, for a site (i.e., a block) of the tangle, 
we introduce its
## height, 
as the length of the longest oriented path to the genesis;
## depth, 
as the length of the longest reverse-oriented path to some tip.
For example, on Figure 2, G has height 1 and depth 3 (because of the reverse path
F, B, A), while D has height 2 and depth 2. Also, let us introduce the notion of the
score. 
##score,  
By definition, the score of a block is sum of own weights of all blocks
approved by this block plus the own weight of the block See Figure 2.
Again, the only tips are A and C. Block A approves (directly or indirectly)
blocks B, D, F, G, so the score of A is 1 + 3 + 1 + 3 + 1 = 9. Analogously, the
score of C is 1 + 1 + 1 + 3 + 1 = 7.
Also, let us observe that, among the above metrics, the cumulative weight is the
most important for us (although heights, depths, and scores will briefly enter to some
discussions as well).


## tip selection and confidence
It is important to observe that, in general, we have an asynchronous network, so that
nodes do not necessarily see the same set of Blocks. It should be noted also that
the tangle may contain conflicting Blocks. The nodes do not have to achieve
consensus on which valid 4 Blocks have the right to be in the ledger (all of them
can be there); but, in case there are conflicting Blocks, they need to decide
which Blocks will become orphaned (that is, eventually not indirectly approved
by incoming Blocks anymore). The main rule that the nodes use for deciding
between two conflicting Blocks is the following: a node runs the tip selection
algorithm 5 (cf. Section 4.1) many times, and see which Block of the two is more
likely to be (indirectly) approved by the selected tip. For example, if, after 100 runs
of the tip selection algorithm, a Block was selected 97 times, we say that it is
confirmed with 97% confidence.

## MCMC (Markov Chain Monte Carlo) algorithm 
MCMC (Markov Chain Monte Carlo) algorithm to select the two tips to reference.
Let H x be the current cumulative weight of a site (i.e., a block represented
on the tangle graph). Recall that we assumed that all own weights are equal to 1; so,
the cumulative weight of a tip is always 1, and the cumulative weight of other sites
is at least 2.
The idea is to place some particles (a.k.a. random walkers) on sites of the tangle,
and let them walk towards the tips in a random way. The tips “chosen” by the walks
are then the candidates for approval. The algorithm is described in the following way:
1. consider all transactions with cumulative weight between W and (say) 2W
(where W is reasonably large, to be chosen 13 );
2. place N particles independently there (N is not so big, say, 10 or so 14 );
3. these particles will perform independent discrete-time random walks “towards
the tips” (i.e., transition from x to y is possible if and only if y approves x);
4. the two random walks that reach the tip set first will indicate our two tips to
approve;
