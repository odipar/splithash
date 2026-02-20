package org.jsplithash.core.splithash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jsplithash.core.splithash.Hashing.*;

/**
 * SplitHash is an immutable, uniquely represented Sequence ADS (Authenticated Data Structure).
 * It is based on a novel hashing scheme that was first introduced with SeqHash.
 * (See http://www.bu.edu/hic/files/2015/01/versum-ccs14.pdf).
 * 
 * Like SeqHashes, SplitHashes can be concatenated in O(log(n)^2).
 * But SplitHash extends SeqHash by allowing Hashes to also be split in O(log(n)^2).
 * It also solves SeqHash's issue with repeating nodes by applying RLE (Run Length Encoding) compression.
 * To improve cache coherence and memory bandwidth, SplitHashes can be optionally chunked into n-ary trees.
 * 
 * SplitHash is the first known History-Independent(HI) ADS that holds all these properties.
 * 
 * Copyright 2016: Robbert van Dalen.
 * Java port Copyright 2026.
 */
public final class SplitHash {
    
    // Node classification during hash-based merge rounds
    private static final byte UNKNOWN = 0;
    private static final byte MERGE = 1;
    private static final byte FRINGE = 2;
    
    /** Average width of the frontier scan window when discovering fringe boundaries. */
    private static final int FRINGE_SCAN_WIDTH = 5;
    
    @SuppressWarnings("unchecked")
    private static final RightFringe<?> EMPTY_RIGHT = new RightFringe<>(-1, new SHNode[0], Collections.emptyList());
    @SuppressWarnings("unchecked")
    private static final LeftFringe<?> EMPTY_LEFT = new LeftFringe<>(-1, new SHNode[0], Collections.emptyList());
    
    private SplitHash() {}
    
    @SuppressWarnings("unchecked")
    public static <X> RightFringe<X> emptyRight() {
        return (RightFringe<X>) EMPTY_RIGHT;
    }
    
    @SuppressWarnings("unchecked")
    public static <X> LeftFringe<X> emptyLeft() {
        return (LeftFringe<X>) EMPTY_LEFT;
    }
    
    // ========================
    // Public factory methods
    // ========================
    
    public static IntNode intNode(int i) {
        return new IntNode(i);
    }
    
    // ========================
    // Concat operations
    // ========================
    
    public static <X> SHNode<X> concat(SHNode<X> left, SHNode<X> right) {
        if (left == null) return right;
        if (right == null) return left;
        
        RightFringe<X> rfringe = transformRight(left);
        LeftFringe<X> lfringe = transformLeft(right);
        
        return concatFringes(rfringe, lfringe);
    }
    
    /**
     * Merges a right fringe and left fringe into a single canonical tree by
     * interleaving their fringe layers bottom-up and applying merge rounds at each level.
     */
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X> concatFringes(RightFringe<X> left, LeftFringe<X> right) {
        SHNode<X>[] elems = new SHNode[0];
        int height = 0;
        boolean done = false;
        
        List<SHNode<X>[]> lf = new ArrayList<>(left.fringes());
        List<SHNode<X>[]> rf = new ArrayList<>(right.fringes());
        
        int lfIdx = 0;
        int rfIdx = 0;
        
        int lh = left.height();
        int rh = right.height();
        
        while (!done) {
            // Append left fringe layer (or top) at the current height
            if (height < lh) {
                elems = concatArrays(lf.get(lfIdx++), elems);
            } else if (height == lh) {
                elems = concatArrays(left.top(), elems);
            }
            
            // Append right fringe layer (or top) at the current height
            if (height < rh) {
                elems = concatArrays(elems, rf.get(rfIdx++));
            } else if (height == rh) {
                elems = concatArrays(elems, right.top());
            }
            
            if (height >= lh && height >= rh && elems.length == 1) {
                done = true;
            } else {
                elems = doRound(elems);
                height++;
            }
        }
        
        return elems[0];
    }
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] concatArrays(SHNode<X>[] a, SHNode<X>[] b) {
        SHNode<X>[] result = (SHNode<X>[]) new SHNode[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
    
    // ========================
    // Split operations
    // ========================
    
    public static <X> SHNode<X> leftSplit(SHNode<X> h, int size) {
        if (size <= 0) return null;
        if (size >= h.size()) return h;
        
        List<SHNode<X>> parts = collectLeftParts(h, size, new ArrayList<>());
        return rebuildAsCanonical(toArray(parts), /* asRightFringe= */ true);
    }
    
    /** Collects subtrees from left to right that together cover {@code pos} elements. */
    private static <X> List<SHNode<X>> collectLeftParts(SHNode<X> h, int pos, List<SHNode<X>> result) {
        if (pos == 0) return result;
        
        SHNode<X> left = h.left();
        if (pos >= left.size()) {
            result.add(left);
            return collectLeftParts(h.right(), pos - left.size(), result);
        }
        return collectLeftParts(left, pos, result);
    }
    
    public static <X> SHNode<X> rightSplit(SHNode<X> h, int size) {
        if (size <= 0) return h;
        if (size >= h.size()) return null;
        
        List<SHNode<X>> parts = collectRightParts(h, h.size() - size, new ArrayList<>());
        // Java append produces same order as Scala prepend (before reverse), so reverse here
        return rebuildAsCanonical(toArrayReversed(parts), /* asRightFringe= */ false);
    }
    
    /** Collects subtrees from right to left that together cover {@code pos} elements. */
    private static <X> List<SHNode<X>> collectRightParts(SHNode<X> h, int pos, List<SHNode<X>> result) {
        if (pos == 0) return result;
        
        SHNode<X> right = h.right();
        if (pos >= right.size()) {
            result.add(right);
            return collectRightParts(h.left(), pos - right.size(), result);
        }
        return collectRightParts(right, pos, result);
    }
    
    /**
     * Shared final step for both leftSplit and rightSplit:
     * compresses parts, builds a temporary tree, transforms to a fringe, and
     * concatenates with an empty opposite fringe to produce a canonical tree.
     *
     * @param parts         the collected subtree parts (already in correct order)
     * @param asRightFringe if true, transform into a RightFringe (for leftSplit);
     *                      if false, transform into a LeftFringe (for rightSplit)
     */
    private static <X> SHNode<X> rebuildAsCanonical(SHNode<X>[] parts, boolean asRightFringe) {
        SHNode<X>[] compressed = compress(parts);
        SHNode<X> tmpTree = toTmpTree(compressed);
        if (tmpTree == null) return null;
        
        if (asRightFringe) {
            return concatFringes(transformRight(tmpTree), emptyLeft());
        } else {
            return concatFringes(emptyRight(), transformLeft(tmpTree));
        }
    }
    
    // ========================
    // Fringe operations
    // ========================
    
    /**
     * Computes the fringe (boundary nodes) of a tree at the given height and direction.
     * Left fringe is reversed because the left-descent iterator yields nodes right-to-left.
     */
    private static <X> SHNode<X>[] computeFringe(SHNode<X> tree, int height, NodeIterator.Direction direction) {
        int fringeDir = (direction == NodeIterator.Direction.LEFT) ? 0 : 1;
        SHNode<X>[] fringe = findFringeWidth(
            new LazyIndexableIterator<>(new NodeIterator<>(tree, height, direction)),
            fringeDir
        );
        return (direction == NodeIterator.Direction.LEFT) ? reverse(fringe) : fringe;
    }
    
    /**
     * Iteratively widens the scan frontier until the fringe boundary stabilizes.
     * Stability means the fringe index is the same whether the frontier is N or N+1.
     */
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] findFringeWidth(LazyIndexableIterator<X> elems, int direction) {
        int frontier = FRINGE_SCAN_WIDTH;
        
        while (true) {
            int frontier1 = frontier + 1;
            
            byte[] kinds = new byte[frontier1];
            int[] hashes = new int[frontier1];
            
            int fringeIdx1 = scanFringeBoundary(elems, direction, frontier, kinds, hashes);
            Arrays.fill(kinds, UNKNOWN);
            
            // Verify stability: same result with a wider frontier means we found the true boundary
            int fringeIdx2 = scanFringeBoundary(elems, direction, frontier1, kinds, hashes);
            if (fringeIdx1 == fringeIdx2) {
                return elems.firstReversed(fringeIdx1);
            }
            frontier += FRINGE_SCAN_WIDTH;
        }
    }
    
    /**
     * Scans elements from index 1 up to the frontier, classifying each as FRINGE or MERGE
     * based on hash bits. Returns the index of the first non-fringe element (= fringe width).
     * <p>
     * Fringe nodes have their hash bit matching the given direction.
     * Adjacent nodes with opposing hash bits form a MERGE pair, which terminates the fringe.
     */
    private static <X> int scanFringeBoundary(LazyIndexableIterator<X> elems, int direction,
                                               int frontier, byte[] kind, int[] hashes) {
        int minFrontier = frontier;
        int otherDirection = 1 - direction;
        boolean done = false;
        int index = 1;
        int bitIndex = 0;
        int intIndex = 0;
        kind[0] = FRINGE;
        
        while (!done) {
            done = true;
            
            // At each new 32-bit word boundary, cache hashes for all UNKNOWN nodes
            if (bitIndex == 0) {
                cacheHashes(elems, kind, hashes, index, minFrontier, intIndex);
                intIndex++;
            }
            
            // Extend fringe: consecutive nodes whose hash bit matches direction
            if (index < minFrontier) {
                SHNode<X> e1 = elems.get(index);
                if (e1 != null) {
                    if (kind[index] == UNKNOWN && bitAt(hashes[index], bitIndex) == direction) {
                        kind[index] = FRINGE;
                        index++;
                    }
                    if (index < minFrontier && kind[index] == UNKNOWN) done = false;
                }
            }
            
            // Detect merge pairs: adjacent UNKNOWN nodes with opposite hash bits
            if (!done) {
                int mf1 = minFrontier - 1;
                for (int j = index; j < mf1; j++) {
                    if (kind[j] == UNKNOWN && kind[j + 1] == UNKNOWN) {
                        SHNode<X> e1 = elems.get(j);
                        SHNode<X> e2 = elems.get(j + 1);
                        
                        if (e1 != null && e2 != null) {
                            if (bitAt(hashes[j], bitIndex) == otherDirection && bitAt(hashes[j + 1], bitIndex) == direction) {
                                kind[j] = MERGE;
                                kind[j + 1] = MERGE;
                                minFrontier = j;
                            } else {
                                done = false;
                            }
                        }
                    }
                }
            }
            bitIndex = (bitIndex + 1) & 31;
        }
        return index;
    }
    
    // Transform a canonical tree into right-catenable LeftFringe tree
    private static <X> LeftFringe<X> transformLeft(SHNode<X> t) {
        SHNode<X> current = t;
        int height = 0;
        List<SHNode<X>[]> fringes = new ArrayList<>();
        
        while (true) {
            SHNode<X>[] fringe = computeFringe(current, height, NodeIterator.Direction.LEFT);
            SHNode<X> remaining = collectRemaining(height, fringe.length, current, /* leftward= */ true);
            
            if (remaining != null) {
                fringes.add(fringe);
                current = remaining;
                height++;
            } else {
                return new LeftFringe<>(height, fringe, fringes);
            }
        }
    }
    
    // Transform a canonical tree into left-catenable RightFringe tree
    private static <X> RightFringe<X> transformRight(SHNode<X> t) {
        SHNode<X> current = t;
        int height = 0;
        List<SHNode<X>[]> fringes = new ArrayList<>();
        
        while (true) {
            SHNode<X>[] fringe = computeFringe(current, height, NodeIterator.Direction.RIGHT);
            SHNode<X> remaining = collectRemaining(height, fringe.length, current, /* leftward= */ false);
            
            if (remaining != null) {
                fringes.add(fringe);
                current = remaining;
                height++;
            } else {
                return new RightFringe<>(height, fringe, fringes);
            }
        }
    }
    
    /**
     * After removing n fringe nodes from one side of the tree at a given height,
     * collects the remaining subtrees from the opposite side and rebuilds them
     * into a temporary tree.
     * <p>
     * When {@code leftward=true} (used by transformLeft): descends left, collects right subtrees.
     * When {@code leftward=false} (used by transformRight): descends right, collects left subtrees.
     */
    private static <X> SHNode<X> collectRemaining(int targetHeight, int fringeSize, SHNode<X> tree,
                                                    boolean leftward) {
        List<SHNode<X>> collected = new ArrayList<>();
        collectRemaining2(targetHeight, fringeSize, tree, collected, leftward);
        SHNode<X>[] compressed = compress(leftward ? toArray(collected) : toArrayReversed(collected));
        return toTmpTree(compressed);
    }
    
    /**
     * Recursive helper: descends along the primary direction (left or right), counting
     * fringe-height nodes. Once {@code count} nodes have been passed, the opposite subtree
     * is added to the collection.
     *
     * @return the number of fringe-height nodes encountered in this subtree
     */
    private static <X> int collectRemaining2(int targetHeight, int count, SHNode<X> tree,
                                              List<SHNode<X>> collected, boolean leftward) {
        if (tree.height() <= targetHeight) return 1;
        
        SHNode<X> primary = leftward ? tree.left() : tree.right();
        SHNode<X> secondary = leftward ? tree.right() : tree.left();
        
        int primaryCount = collectRemaining2(targetHeight, count, primary, collected, leftward);
        if (primaryCount < count) {
            int secondaryCount = collectRemaining2(targetHeight, count - primaryCount, secondary, collected, leftward);
            return primaryCount + secondaryCount;
        }
        collected.add(secondary);
        return primaryCount;
    }
    
    // ========================
    // Round operations (SeqHash-inspired)
    // ========================
    
    /**
     * Performs one merge round: compresses the input, then pairs adjacent nodes
     * whose hash bits indicate a (1,0) merge pattern. Unpaired nodes pass through.
     */
    public static <X> SHNode<X>[] doRound(SHNode<X>[] elems) {
        return mergeRound(compress(elems));
    }
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] mergeRound(SHNode<X>[] elems) {
        int N = elems.length;
        byte[] kind = new byte[N];
        int[] hashes = new int[N];
        boolean done = false;
        int merges = 0;
        
        int bitIndex = 0;
        int intIndex = 0;
        int lastIdx = N - 1;
        
        while (!done) {
            done = true;
            
            // Cache hashes at each new 32-bit word boundary for UNKNOWN nodes
            if (bitIndex == 0) {
                cacheHashes(elems, kind, hashes, 0, N, intIndex);
                intIndex++;
            }
            
            // Scan for merge pairs: left node hash bit=1, right node hash bit=0
            for (int j = 0; j < lastIdx; j++) {
                if (kind[j] == UNKNOWN && kind[j + 1] == UNKNOWN) {
                    if (bitAt(hashes[j], bitIndex) == 1 && bitAt(hashes[j + 1], bitIndex) == 0) {
                        kind[j] = MERGE;
                        kind[j + 1] = MERGE;
                        j++;
                        merges++;
                    } else {
                        done = false;
                    }
                }
            }
            bitIndex = (bitIndex + 1) & 31;
        }
        
        // Build result: merge marked pairs, pass through unmarked nodes
        SHNode<X>[] result = (SHNode<X>[]) new SHNode[N - merges];
        int i = 0;
        int ri = 0;
        
        while (i < N) {
            if (kind[i] == UNKNOWN) {
                result[ri] = elems[i];
            } else {
                result[ri] = elems[i].combine(elems[i + 1]);
                i++;
            }
            i++;
            ri++;
        }
        
        return result;
    }
    
    /**
     * Caches hash values for UNKNOWN nodes in the given range.
     * Used by both {@link #mergeRound} and {@link #scanFringeBoundary} to avoid
     * re-computing hashes at the same index across multiple bit iterations.
     */
    private static <X> void cacheHashes(SHNode<X>[] elems, byte[] kind, int[] hashes,
                                         int from, int to, int intIndex) {
        for (int ei = from; ei < to; ei++) {
            if (kind[ei] == UNKNOWN) {
                hashes[ei] = elems[ei].hash().hashAt(intIndex);
            }
        }
    }
    
    /**
     * Overload for {@link LazyIndexableIterator} (used in fringe scanning where
     * elements are lazily fetched).
     */
    private static <X> void cacheHashes(LazyIndexableIterator<X> elems, byte[] kind, int[] hashes,
                                         int from, int to, int intIndex) {
        for (int ei = from; ei < to && elems.get(ei) != null; ei++) {
            if (kind[ei] == UNKNOWN) {
                hashes[ei] = elems.get(ei).hash().hashAt(intIndex);
            }
        }
    }
    
    // ========================
    // Compression operations
    // ========================
    
    /**
     * Compresses consecutive equal (RLE-compatible) nodes into RLE nodes.
     * Returns the input unchanged if no compression is needed.
     */
    @SuppressWarnings("unchecked")
    public static <X> SHNode<X>[] compress(SHNode<X>[] elems) {
        if (elems.length == 0) return elems;
        
        // Quick check: skip allocation if no adjacent duplicates exist
        boolean needsCompress = false;
        for (int i = 1; i < elems.length; i++) {
            if (elems[i - 1].isMultipleOf(elems[i])) {
                needsCompress = true;
                break;
            }
        }
        
        if (!needsCompress) return elems;
        return compressRLE(elems);
    }
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] compressRLE(SHNode<X>[] elems) {
        List<SHNode<X>> stack = new ArrayList<>();
        stack.add(elems[0]);
        
        for (int i = 1; i < elems.length; i++) {
            SHNode<X> head = stack.get(stack.size() - 1);
            SHNode<X> elem = elems[i];
            if (head.isMultipleOf(elem)) {
                stack.set(stack.size() - 1, head.combine(elem));
            } else {
                stack.add(elem);
            }
        }
        
        return toArray(stack);
    }
    
    // ========================
    // Chunking operations
    // ========================
    
    @SuppressWarnings("unchecked")
    public static <X> SHNode<X> chunkTree(SHNode<X> tree) {
        ChunkResult<X> result = encodeChunkTree(tree, new ArrayList<>());
        
        SHNode<X>[] nodes = toArray(result.nodes);
        boolean[] treeBits = toBooleanArray(result.treeEnc);
        
        return new ChunkedNode<>(nodes, treeBits, tree.hashCode(), tree.size(), tree.height());
    }
    
    private static <X> ChunkResult<X> encodeChunkTree(SHNode<X> node, List<Boolean> treeEnc) {
        if (node.chunkHeight() == 0) {
            List<SHNode<X>> nodes = new ArrayList<>();
            nodes.add(node);
            treeEnc.add(false);
            return new ChunkResult<>(nodes, treeEnc);
        }
        
        treeEnc.add(true);
        ChunkResult<X> leftResult = encodeChunkTree(node.left(), treeEnc);
        leftResult.treeEnc.add(true);
        ChunkResult<X> rightResult = encodeChunkTree(node.right(), leftResult.treeEnc);
        leftResult.nodes.addAll(rightResult.nodes);
        return new ChunkResult<>(leftResult.nodes, rightResult.treeEnc);
    }
    
    public static <X> SHNode<X> unchunk(ChunkedNode<X> cn) {
        UnchunkResult<X> result = decodeChunkTree(cn.getNodes(), cn.getTree(), 0, 0);
        return result.node();
    }
    
    private static <X> UnchunkResult<X> decodeChunkTree(SHNode<X>[] nodes, boolean[] tree, int nodeIdx, int treeIdx) {
        if (!tree[treeIdx]) {
            return new UnchunkResult<>(nodeIdx + 1, treeIdx + 1, nodes[nodeIdx]);
        }
        
        UnchunkResult<X> left = decodeChunkTree(nodes, tree, nodeIdx, treeIdx + 1);
        UnchunkResult<X> right = decodeChunkTree(nodes, tree, left.nodeIdx(), left.treeIdx() + 1);
        
        return new UnchunkResult<>(right.nodeIdx(), right.treeIdx(), left.node().combine(right.node()));
    }
    
    // ========================
    // Helper classes
    // ========================
    
    private static class ChunkResult<X> {
        final List<SHNode<X>> nodes;
        final List<Boolean> treeEnc;
        
        ChunkResult(List<SHNode<X>> nodes, List<Boolean> treeEnc) {
            this.nodes = nodes;
            this.treeEnc = treeEnc;
        }
    }
    
    private record UnchunkResult<X>(int nodeIdx, int treeIdx, SHNode<X> node) {}
    
    // ========================
    // Array utilities
    // ========================
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] toArray(List<SHNode<X>> list) {
        return list.toArray((SHNode<X>[]) new SHNode[0]);
    }
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] toArrayReversed(List<SHNode<X>> list) {
        SHNode<X>[] arr = (SHNode<X>[]) new SHNode[list.size()];
        int j = list.size() - 1;
        for (SHNode<X> node : list) {
            arr[j--] = node;
        }
        return arr;
    }
    
    @SuppressWarnings("unchecked")
    private static <X> SHNode<X>[] reverse(SHNode<X>[] arr) {
        SHNode<X>[] result = (SHNode<X>[]) new SHNode[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[arr.length - 1 - i];
        }
        return result;
    }
    
    private static boolean[] toBooleanArray(List<Boolean> list) {
        boolean[] arr = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
    
    /** Builds a temporary (non-canonical) tree for fringe consumption only. */
    private static <X> SHNode<X> toTmpTree(SHNode<X>[] subtrees) {
        if (subtrees.length == 0) return null;
        
        SHNode<X> tree = subtrees[0];
        for (int i = 1; i < subtrees.length; i++) {
            tree = tree.combine2(subtrees[i]);
        }
        return tree;
    }
}
