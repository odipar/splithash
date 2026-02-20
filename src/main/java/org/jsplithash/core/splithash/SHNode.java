package org.jsplithash.core.splithash;

/**
 * A canonical tree SH(Split Hash)Node.
 * This is the main node type for the SplitHash data structure.
 * 
 * @param <X> the type of elements stored in the tree
 */
public abstract sealed class SHNode<X> implements Hashable, Hash
        permits BinNode, ChunkedNode, IntNode, RLENode, TempBinNode {
    
    public abstract int size();
    public abstract X first();
    public abstract X last();
    
    // tree traversal
    public abstract SHNode<X> left();
    public abstract SHNode<X> right();
    public abstract int height();
    
    // chunking
    public abstract SHNode<X> chunk();
    public abstract boolean isChunked();
    public abstract int chunkHeight();
    
    // Split parts
    public abstract SHNode<X>[] splitParts();
    
    @Override
    public Hash hash() {
        return this;
    }
    
    @Override
    public Hashable[] parts() {
        return splitParts();
    }
    
    /**
     * Equality check based on content equality, rather than structure equality.
     * Used to detect node repetitions.
     */
    public abstract boolean equalTo(SHNode<X> other);
    
    /**
     * Helper to get the base node (unwrapping RLE if needed).
     */
    @SuppressWarnings("unchecked")
    public static <X> SHNode<X> mget(SHNode<X> e1) {
        if (e1 instanceof RLENode<X> rle) {
            return rle.getNode();
        }
        return e1;
    }
    
    /**
     * Helper to get the multiplicity (1 for non-RLE nodes).
     */
    @SuppressWarnings("unchecked")
    public static <X> int msize(SHNode<X> n) {
        if (n instanceof RLENode<X> rle) {
            return rle.getMultiplicity();
        }
        return 1;
    }
    
    public boolean isMultipleOf(SHNode<X> n) {
        return mget(this).equalTo(mget(n));
    }
    
    public SHNode<X> combine(SHNode<X> n) {
        if (isMultipleOf(n)) {
            return new RLENode<>(mget(this), msize(this) + msize(n));
        }
        return new BinNode<>(this, n, size() + n.size());
    }
    
    public SHNode<X> combine2(SHNode<X> n) {
        if (isMultipleOf(n)) {
            return new RLENode<>(mget(this), msize(this) + msize(n));
        }
        return new TempBinNode<>(this, n);
    }
    
    public SHNode<X> concat(SHNode<X> other) {
        return SplitHash.concat(this, other);
    }
    
    public SplitResult<X> split(int at) {
        SHNode<X> l = SplitHash.leftSplit(this, at);
        SHNode<X> r = SplitHash.rightSplit(this, at);
        return new SplitResult<>(l, r);
    }
    
    /**
     * Result of splitting a node.
     *
     * @param left  the left part after the split
     * @param right the right part after the split
     */
    public record SplitResult<X>(SHNode<X> left, SHNode<X> right) {}
}
