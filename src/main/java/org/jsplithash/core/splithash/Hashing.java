package org.jsplithash.core.splithash;

/**
 * Hashing utilities including SipHash-2-4 implementation.
 */
public final class Hashing {
    
    // Magic relative primes
    public static final int MAGIC_P1 = 1664525;
    public static final int MAGIC_P2 = 22695477;
    public static final int MAGIC_P3 = 1103515245;
    
    private Hashing() {}
    
    public static long rotl(long x, int b) {
        return (x << b) | (x >>> -b);
    }
    
    public static int siphash24(int x1, int x2) {
        long v0 = 0x736f6d6570736575L;
        long v1 = 0x646f72616e646f6dL;
        long v2 = 0x6c7967656e657261L;
        long v3 = 0x7465646279746573L;
        
        long m = rotl(x1, 32) + x2; // combine the input ints into one long
        
        // Compression phase: 2 rounds
        v3 ^= m;
        for (int i = 0; i < 2; i++) {
            v0 += v1; v1 = rotl(v1, 13) ^ v0; v0 = rotl(v0, 32);
            v2 += v3; v3 = rotl(v3, 16) ^ v2;
            v0 += v3; v3 = rotl(v3, 21) ^ v0;
            v2 += v1; v1 = rotl(v1, 17) ^ v2; v2 = rotl(v2, 32);
        }
        v0 ^= m;
        
        // Finalization phase: 4 rounds
        v2 ^= 0xff;
        for (int i = 0; i < 4; i++) {
            v0 += v1; v1 = rotl(v1, 13) ^ v0; v0 = rotl(v0, 32);
            v2 += v3; v3 = rotl(v3, 16) ^ v2;
            v0 += v3; v3 = rotl(v3, 21) ^ v0;
            v2 += v1; v1 = rotl(v1, 17) ^ v2; v2 = rotl(v2, 32);
        }
        
        long r = v0 ^ v1 ^ v2 ^ v3;
        return (int)(rotl(r, 32) ^ r); // munch the long into an int
    }
    
    public static int bitAt(int value, int index) {
        return ((value >>> (31 - index)) & 1);
    }
}
