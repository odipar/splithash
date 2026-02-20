package org.jsplithash.core.splithash;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Iterates sub-nodes at a given target height, descending in a specified direction.
 * <p>
 * When direction is LEFT, descends leftward (post-order) — replaces the former LeftNodeIterator.
 * When direction is RIGHT, descends rightward (pre-order) — replaces the former RightNodeIterator.
 *
 * @param <X> the type of elements stored in the tree
 */
public final class NodeIterator<X> implements Iterator<SHNode<X>> {
    
    /** Direction of descent: LEFT descends via left(), RIGHT descends via right(). */
    public enum Direction { LEFT, RIGHT }
    
    private SHNode<X> tree;
    private final int targetHeight;
    private final Deque<SHNode<X>> nstack = new ArrayDeque<>();
    private final Function<SHNode<X>, SHNode<X>> descend;   // primary descent (left or right)
    private final Function<SHNode<X>, SHNode<X>> opposite;  // opposite child for backtracking
    
    public NodeIterator(SHNode<X> tree, int targetHeight, Direction direction) {
        this.tree = tree;
        this.targetHeight = targetHeight;
        if (direction == Direction.LEFT) {
            this.descend = SHNode::left;
            this.opposite = SHNode::right;
        } else {
            this.descend = SHNode::right;
            this.opposite = SHNode::left;
        }
    }
    
    @Override
    public boolean hasNext() {
        return tree != null || !nstack.isEmpty();
    }
    
    @Override
    public SHNode<X> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("NodeIterator exhausted");
        }
        if (tree != null) {
            SHNode<X> t = tree;
            tree = null;
            return descendToLeaf(t);
        } else {
            SHNode<X> head = nstack.removeFirst();
            return descendToLeaf(opposite.apply(head));
        }
    }
    
    /**
     * Descends along the primary direction until reaching the target height,
     * pushing intermediate nodes onto the stack for later backtracking.
     */
    private SHNode<X> descendToLeaf(SHNode<X> node) {
        if (node == null) {
            throw new RuntimeException("descendToLeaf called with null node");
        }
        SHNode<X> current = node;
        while (current.height() > targetHeight) {
            nstack.addFirst(current);
            SHNode<X> child = descend.apply(current);
            if (child == null) {
                throw new RuntimeException(current.getClass().getSimpleName()
                    + ".child() returned null with height " + current.height());
            }
            current = child;
        }
        return current;
    }
}
