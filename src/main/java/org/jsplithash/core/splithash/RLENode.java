package org.jsplithash.core.splithash;

import static org.jsplithash.core.splithash.Hashing.*;

/**
 * A RLE (Run Length Encoded) node denotes the repetition of another node.
 * 
 * @param <X> the type of elements stored in the tree
 */
public final class RLENode<X> extends SHNode<X> {
    
    private final SHNode<X> node;
    private final int multiplicity;
    private final int cachedHashCode;

    public RLENode(SHNode<X> node, int multiplicity) {
        this.node = node;
        this.multiplicity = multiplicity;
        this.cachedHashCode = siphash24(node.hashCode() + MAGIC_P1, multiplicity - MAGIC_P3);
    }

    public SHNode<X> getNode() {
        return node;
    }

    public int getMultiplicity() {
        return multiplicity;
    }
    
    @Override
    public int hashCode() {
        return cachedHashCode;
    }
    
    @Override
    public int hashAt(int index) {
        if (index > 0) {
            return siphash24(hashAt(index - 1) + MAGIC_P2, multiplicity - (MAGIC_P3 * index));
        }
        return cachedHashCode;
    }
    
    @Override
    public SHNode<X> left() {
        if (multiplicity < 4) return node;
        return new RLENode<>(node, multiplicity / 2);
    }
    
    @Override
    public SHNode<X> right() {
        if (multiplicity < 3) return node;
        return new RLENode<>(node, multiplicity - (multiplicity / 2));
    }
    
    @Override
    public int size() {
        return node.size() * multiplicity;
    }
    
    @Override
    public int height() {
        return node.height();
    }
    
    @Override
    public int chunkHeight() {
        return node.chunkHeight();
    }
    
    @Override
    public SHNode<X> chunk() {
        if (!node.isChunked()) {
            return new RLENode<>(node.chunk(), multiplicity);
        }
        return this;
    }
    
    @Override
    public boolean isChunked() {
        return node.isChunked();
    }
    
    @Override
    public X first() {
        return node.first();
    }
    
    @Override
    public X last() {
        return node.last();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public SHNode<X>[] splitParts() {
        return new SHNode[] { left(), right() };
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public boolean equalTo(SHNode<X> other) {
        if (hashCode() != other.hashCode()) return false;
        if (this == other) return true;
        if (!(other instanceof RLENode<X> rle)) return false;
        return multiplicity == rle.multiplicity && node.equalTo(rle.node);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RLENode<?> other)) return false;
        @SuppressWarnings("unchecked")
        RLENode<X> typedOther = (RLENode<X>) other;
        return multiplicity == typedOther.multiplicity && node.equals(typedOther.node);
    }
    
    @Override
    public String toString() {
        return multiplicity + ":" + node;
    }
}
