package org.jsplithash.core.splithash;

import static org.jsplithash.core.splithash.Hashing.*;

/**
 * A temporary binary node that shouldn't be part of a canonical tree.
 * Only the height is important for this node type.
 * 
 * @param <X> the type of elements stored in the tree
 */
public final class TempBinNode<X> extends SHNode<X> {
    
    private final SHNode<X> leftNode;
    private final SHNode<X> rightNode;
    
    public TempBinNode(SHNode<X> left, SHNode<X> right) {
        this.leftNode = left;
        this.rightNode = right;
    }
    
    private RuntimeException error() {
        return new IllegalStateException("Internal inconsistency. Should not be called on TempBinNode");
    }
    
    @Override
    public int height() {
        return 1 + Math.max(leftNode.height(), rightNode.height());
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
    public int size() {
        throw error();
    }
    
    @Override
    public X first() {
        throw error();
    }
    
    @Override
    public X last() {
        throw error();
    }
    
    @Override
    public SHNode<X> chunk() {
        throw error();
    }
    
    @Override
    public boolean isChunked() {
        throw error();
    }
    
    @Override
    public int chunkHeight() {
        throw error();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public SHNode<X>[] splitParts() {
        throw error();
    }
    
    @Override
    public int hashAt(int i) {
        throw error();
    }
}
