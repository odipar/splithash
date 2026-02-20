package org.jsplithash.core.splithash;

import static org.jsplithash.core.splithash.Hashing.*;

/**
 * A full binary node holds a hash (Int), size (Int), (chunk)height (Int) 
 * and its left and right sub-trees.
 * There is some trickery to reduce the memory footprint and the lazy computation of hashes.
 * 
 * @param <X> the type of elements stored in the tree
 */
public final class BinNode<X> extends SHNode<X> {
    
    /** Chunk height threshold: nodes with chunkHeight above this are further chunked. */
    private static final int MAX_CHUNK_HEIGHT = 5;

    private final SHNode<X> leftNode;
    private final SHNode<X> rightNode;
    private final int csize;
    private final int heightEE; // encodes both height and chunkHeight

    private int lHash = 0; // lazy hash (trick borrowed from Avail Programming Language)

    // Counter for unlikely > 64 bit hash consumption
    private static volatile int unlikely = 0;

    /** Returns the count of times the hash computation consumed more than 64 bits. */
    public static int getUnlikelyCount() {
        return unlikely;
    }
    
    public BinNode(SHNode<X> left, SHNode<X> right, int csize) {
        this.leftNode = left;
        this.rightNode = right;
        this.csize = csize;
        int heightE = 1 + Math.max(left.height(), right.height());
        int chunkHeightE = 1 + Math.max(left.chunkHeight(), right.chunkHeight());
        this.heightEE = (heightE << 8) | chunkHeightE;
    }
    
    @Override
    public int hashCode() {
        if (lHash == 0) {
            lHash = siphash24(leftNode.hashCode() - MAGIC_P2, rightNode.hashCode() + MAGIC_P3);
        }
        return lHash; // could be 0 again, but then we just recompute 0
    }
    
    @Override
    public int hashAt(int index) {
        if (index == 0) return hashCode();
        if (index == 1) return (leftNode.hashCode() - rightNode.hashCode()) ^ hashCode();
        
        // 64 bits or more are requested. This should normally not happen.
        unlikely++;
        int nindex = index / 2;
        
        if (hashCode() > 0) {
            return siphash24(
                leftNode.hash().hashAt(nindex) - MAGIC_P3,
                rightNode.hash().hashAt(index - nindex) + (MAGIC_P1 * hashCode())
            );
        } else {
            return siphash24(
                rightNode.hash().hashAt(nindex) - (MAGIC_P3 * hashCode()),
                leftNode.hash().hashAt(index - nindex) + MAGIC_P1
            );
        }
    }
    
    @Override
    public int size() {
        return Math.abs(csize);
    }
    
    @Override
    public X first() {
        return leftNode.first();
    }
    
    @Override
    public X last() {
        return rightNode.last();
    }
    
    @Override
    public SHNode<X> left() {
        return leftNode;
    }
    
    @Override
    public SHNode<X> right() {
        return rightNode;
    }
    
    @Override
    public int height() {
        return heightEE >> 8;
    }
    
    @Override
    public int chunkHeight() {
        return heightEE & 0xff;
    }
    
    @Override
    public boolean isChunked() {
        return csize < 0; // negative csize indicates that the node is chunked
    }
    
    @Override
    public SHNode<X> chunk() {
        if (isChunked()) return this;
        
        SHNode<X> l = leftNode.chunk();
        SHNode<X> r = rightNode.chunk();
        BinNode<X> nt = new BinNode<>(l, r, -(l.size() + r.size()));
        
        if (nt.chunkHeight() > MAX_CHUNK_HEIGHT) {
            return SplitHash.chunkTree(nt);
        }
        return nt;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public SHNode<X>[] splitParts() {
        return new SHNode[] { leftNode, rightNode };
    }
    
    @Override
    public boolean equalTo(SHNode<X> other) {
        // BY DESIGN: fast hashcode in-equality check
        if (hashCode() != other.hashCode()) return false;
        // fast reference equality check (=true when references are equal)
        if (this == other) return true;
        return leftNode.equalTo(other.left()) && rightNode.equalTo(other.right());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BinNode<?> other)) return false;
        @SuppressWarnings("unchecked")
        BinNode<X> typedOther = (BinNode<X>) other;
        return equalTo(typedOther);
    }
    
    @Override
    public String toString() {
        return leftNode + " ! " + rightNode;
    }
}
