package org.jsplithash.core.splithash;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * A ChunkedNode that holds all the LeafNodes of the canonical tree it represents.
 * When lazily requested, it also caches the unchunked canonical tree in a WeakReference.
 * The unchunked version can be GC'ed anytime, as it can always be rebuilt from the originating chunk.
 * ChunkedNode turns a binary tree into a n-ary tree, thus saving a lot of memory (bandwidth)
 * 
 * @param <X> the type of elements stored in the tree
 */
public final class ChunkedNode<X> extends SHNode<X> {
    
    private final SHNode<X>[] nodes;
    private final boolean[] tree;
    private final int h;
    private final int sizeValue;
    private final int heightValue;

    private volatile WeakReference<SHNode<X>> unchunked = null;

    public ChunkedNode(SHNode<X>[] nodes, boolean[] tree, int h, int size, int height) {
        this.nodes = nodes;
        this.tree = tree;
        this.h = h;
        this.sizeValue = size;
        this.heightValue = height;
    }

    /** Returns a copy of the leaf nodes array. */
    public SHNode<X>[] getNodes() {
        return Arrays.copyOf(nodes, nodes.length);
    }

    /** Returns a copy of the tree-structure bit array. */
    public boolean[] getTree() {
        return Arrays.copyOf(tree, tree.length);
    }
    
    @Override
    public int hashCode() {
        return h;
    }
    
    @Override
    public int hashAt(int i) {
        if (i == 0) return h;
        return getUnchunked().hashAt(i);
    }
    
    @Override
    public int size() {
        return sizeValue;
    }
    
    @Override
    public int height() {
        return heightValue;
    }
    
    @Override
    public int chunkHeight() {
        return 0;
    }
    
    @Override
    public boolean isChunked() {
        return true;
    }
    
    @Override
    public SHNode<X> chunk() {
        return this;
    }
    
    @Override
    public SHNode<X> left() {
        return getUnchunked().left();
    }
    
    @Override
    public SHNode<X> right() {
        return getUnchunked().right();
    }
    
    @Override
    public X first() {
        return nodes[0].first();
    }
    
    @Override
    public X last() {
        return nodes[nodes.length - 1].last();
    }
    
    @Override
    public SHNode<X>[] splitParts() {
        return Arrays.copyOf(nodes, nodes.length);
    }
    
    @SuppressWarnings("unchecked")
    private SHNode<X> getUnchunked() {
        WeakReference<SHNode<X>> ref = unchunked;
        if (ref != null) {
            SHNode<X> u = ref.get();
            if (u != null) return u;
        }
        return getUnchunked2();
    }
    
    private synchronized SHNode<X> getUnchunked2() {
        // Double-check in case another thread already computed it
        WeakReference<SHNode<X>> ref = unchunked;
        if (ref != null) {
            SHNode<X> u = ref.get();
            if (u != null) return u;
        }
        
        SHNode<X> uchunk = SplitHash.unchunk(this);
        unchunked = new WeakReference<>(uchunk);
        return uchunk;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public boolean equalTo(SHNode<X> other) {
        if (hashCode() != other.hashCode()) return false;
        if (this == other) return true;
        // For ChunkedNode, compare the chunked representation (like Scala case class equality)
        if (other instanceof ChunkedNode<X> cn) {
            if (sizeValue != cn.sizeValue || heightValue != cn.heightValue) return false;
            if (nodes.length != cn.nodes.length) return false;
            for (int i = 0; i < nodes.length; i++) {
                if (!nodes[i].equalTo(cn.nodes[i])) return false;
            }
            return Arrays.equals(tree, cn.tree);
        }
        // For non-ChunkedNode, compare via unchunked tree
        return left().equalTo(other.left()) && right().equalTo(other.right());
    }
}
