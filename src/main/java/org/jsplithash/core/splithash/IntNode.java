package org.jsplithash.core.splithash;

import static org.jsplithash.core.splithash.Hashing.*;

/**
 * A Leaf node that holds a single integer value.
 */
public final class IntNode extends SHNode<Integer> {
    
    public final int value;
    private final int cachedHashCode;
    
    public IntNode(int value) {
        this.value = value;
        this.cachedHashCode = siphash24(value + MAGIC_P1, value - MAGIC_P2);
    }
    
    @Override
    public int hashCode() {
        return cachedHashCode;
    }
    
    @Override
    public int hashAt(int index) {
        if (index == 0) return cachedHashCode;
        if (index == 1) return siphash24(value + MAGIC_P2, cachedHashCode * MAGIC_P1);
        return siphash24(cachedHashCode * MAGIC_P3, hashAt(index - 1) - MAGIC_P2);
    }
    
    @Override
    public int size() {
        return 1;
    }
    
    @Override
    public Integer first() {
        return value;
    }
    
    @Override
    public Integer last() {
        return value;
    }
    
    @Override
    public SHNode<Integer> left() {
        return null;
    }
    
    @Override
    public SHNode<Integer> right() {
        return null;
    }
    
    @Override
    public int height() {
        return 0;
    }
    
    @Override
    public SHNode<Integer> chunk() {
        return this;
    }
    
    @Override
    public boolean isChunked() {
        return false;
    }
    
    @Override
    public int chunkHeight() {
        return 0;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public SHNode<Integer>[] splitParts() {
        return new SHNode[0];
    }
    
    @Override
    public boolean equalTo(SHNode<Integer> other) {
        return this == other || (other instanceof IntNode n && value == n.value);
    }
    
    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof IntNode n && value == n.value);
    }
    
    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
