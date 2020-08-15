package net.bigtangle.utils;

/*
 * The race between the honest chain and an attacker chain can be characterized as a Binomial
Random Walk. The success event is the honest chain being extended by one block, increasing its
lead by +1, and the failure event is the attacker's chain being extended by one block, reducing the
gap by -1.
The probability of an attacker catching up from a given deficit is analogous to a Gambler's
Ruin problem. Suppose a gambler with unlimited credit starts at a deficit and plays potentially an
infinite number of trials to try to reach breakeven. We can calculate the probability he ever
reaches breakeven, or that an attacker ever catches up with the honest chain, as follows [8]:
p = probability an honest node finds the next block
q = probability the attacker finds the next block
qz = probability the attacker will ever catch up from z blocks behind

Solving for P less than 0.1%...
P < 0.001
q=0.10 z=5
q=0.15 z=8
q=0.20 z=11
q=0.25 z=15
q=0.30 z=24
q=0.35 z=41
q=0.40 z=89
q=0.45 z=340
https://bitcoin.org/bitcoin.pdf
 */
public class ProbabilityBlock {

    public static double attackerSuccessProbability(double q, long z) {
        double p = 1.0 - q;
        double pq= (q / p);
        double lambda = z * pq;
        double sum = 1.0;
        int i, k;
        for (k = 0; k <= z; k++) {
            double poisson = Math.exp(-lambda);
            for (i = 1; i <= k; i++) {
                poisson *= lambda / i;
            }
            sum -= poisson * (1 - Math.pow(pq, z - k));

        }
        return sum;
    }

}
