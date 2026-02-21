# SplitHash

SplitHash is an immutable, uniquely represented **Sequence Authenticated Data Structure (ADS)**. It extends the SeqHash scheme (introduced in the [Versum paper](#references)) by adding efficient split operations while preserving history-independence.

## Table of Contents

- [Overview](#overview)
- [Unique Properties](#unique-properties)
- [Comparison with Other Data Structures](#comparison-with-other-data-structures)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Implementation Details](#implementation-details)
- [References](#references)

## Overview

SplitHash builds on the core idea of SeqHash — using hash-bit patterns to deterministically structure a binary tree over a sequence — and adds two key capabilities:

1. **Splitting** a hash in O(log(n)²) time (SeqHash only supported concatenation).
2. **RLE (Run-Length Encoding) compression** to correctly handle repeating elements (fixing a known issue in SeqHash).

The result is the first known **History-Independent (HI) ADS** that supports both concatenation and splitting over sequences, with optional n-ary chunking for improved memory efficiency.

## Unique Properties

| Property | Description |
|---|---|
| **History-Independence** | The structure converges to the same canonical form regardless of the order in which elements were inserted or operations were applied. Two sequences with the same elements will always produce identical SplitHash trees. |
| **Concatenation in O(log(n)²)** | Two SplitHash trees can be joined into a single canonical tree in O(log(n)²) time. |
| **Splitting in O(log(n)²)** | A SplitHash tree can be split at any position into two canonical sub-trees in O(log(n)²) time. This extends SeqHash, which only supported concatenation. |
| **RLE compression** | Consecutive equal sub-trees are automatically compressed using Run-Length Encoding, solving the repeating-node problem present in the original SeqHash. |
| **Authenticated** | Every node carries a cryptographic hash (SipHash-2-4) derived from its children, enabling O(log(n)) verification of sequence integrity. |
| **Immutability** | All operations produce new trees; existing trees are never mutated. |
| **Optional n-ary chunking** | Binary trees can be converted to n-ary (`ChunkedNode`) trees for better cache coherence and lower memory bandwidth. |

## Comparison with Other Data Structures

### Merkle Trees

| | Merkle Tree | SplitHash |
|---|---|---|
| **Authenticated** | ✓ | ✓ |
| **History-Independent** | ✗ (structure depends on insertion order) | ✓ |
| **Concatenation** | O(n) rebuild | O(log(n)²) |
| **Splitting** | O(n) rebuild | O(log(n)²) |
| **Repeating elements** | No special handling | RLE compression |

Standard Merkle trees authenticate data by chaining hashes up a tree, but their shape is fixed by the insertion order. SplitHash produces the same canonical tree regardless of how the sequence was assembled.

### Finger Trees

| | Finger Tree | SplitHash |
|---|---|---|
| **Authenticated** | ✗ | ✓ |
| **History-Independent** | ✗ | ✓ |
| **Concatenation** | O(log(n)) | O(log(n)²) |
| **Splitting** | O(log(n)) | O(log(n)²) |
| **Verified proofs** | ✗ | ✓ (O(log(n))) |

Finger Trees are an efficient purely-functional sequence structure supporting O(log(n)) concatenation and split, but they carry no authentication information and their shape depends on the sequence of operations performed.

### Skip Lists

| | Skip List | SplitHash |
|---|---|---|
| **Authenticated** | Possible (with hashing) | ✓ |
| **History-Independent** | ✗ (probabilistic) | ✓ (deterministic) |
| **Concatenation** | O(log(n)) | O(log(n)²) |
| **Splitting** | O(log(n)) | O(log(n)²) |

Authenticated skip lists can provide probabilistic history-independence, but the structure is not deterministically canonical — the same logical sequence may produce different skip list shapes on different runs.

### SeqHash (from the Versum paper)

| | SeqHash | SplitHash |
|---|---|---|
| **Authenticated** | ✓ | ✓ |
| **History-Independent** | ✓ | ✓ |
| **Concatenation** | O(log(n)²) | O(log(n)²) |
| **Splitting** | ✗ | ✓ O(log(n)²) |
| **Repeating elements** | ✗ (problematic) | ✓ (RLE compression) |
| **N-ary chunking** | ✗ | ✓ |

SplitHash is a direct extension of SeqHash. It retains SeqHash's concatenation algorithm and history-independence guarantee, while adding split support and fixing the repeating-node problem.

## Getting Started

### Prerequisites

- Java 23 or later
- Maven 3.6 or later

### Build

```bash
mvn package
```

### Run the built-in test

```bash
mvn exec:java -Dexec.mainClass=org.jsplithash.core.splithash.SplitHashMain
```

This builds sequences of 50,000 elements forward, backward, and with repetitions; verifies that forward and backward insertion produce identical hashes; then splits and re-concatenates at every position to confirm round-trip correctness.

## Usage

```java
import org.jsplithash.core.splithash.*;

// Create leaf nodes
SHNode<Integer> a = SplitHash.intNode(1);
SHNode<Integer> b = SplitHash.intNode(2);
SHNode<Integer> c = SplitHash.intNode(3);

// Concatenate into a sequence [1, 2, 3]
SHNode<Integer> seq = SplitHash.concat(SplitHash.concat(a, b), c);

// Split at position 1 -> left=[1], right=[2, 3]
SHNode.SplitResult<Integer> parts = seq.split(1);
SHNode<Integer> left  = parts.left();   // [1]
SHNode<Integer> right = parts.right();  // [2, 3]

// Re-concatenate: the result is identical (same hash) to the original
SHNode<Integer> rejoined = SplitHash.concat(left, right);

// Optionally chunk the tree for better memory efficiency
SHNode<Integer> chunked = seq.chunk();
```

### Key API

| Method | Description |
|---|---|
| `SplitHash.intNode(int)` | Create a leaf node for an integer value |
| `SplitHash.concat(left, right)` | Concatenate two trees in O(log(n)²) |
| `node.split(int at)` | Split into `(left, right)` at the given position in O(log(n)²) |
| `node.chunk()` | Convert the binary tree into a chunked n-ary tree |
| `node.hashCode()` | Cryptographic hash of the entire sequence |
| `node.size()` | Number of elements |
| `node.first()` / `node.last()` | First and last elements |
| `node.equalTo(other)` | Structural equality check |

## Implementation Details

The core of SplitHash lives in `SplitHash.java` and is built from the following node types:

- **`BinNode`** — A canonical binary tree node. Its hash is lazily computed via SipHash-2-4 from its children and is the main building block of all sequences.
- **`RLENode`** — A Run-Length Encoded node representing *n* repetitions of a single sub-tree. Enables efficient handling of repeated elements.
- **`ChunkedNode`** — An n-ary tree node that packs a binary subtree into a compact array representation for improved cache coherence.
- **`IntNode`** — A leaf node storing a single integer.
- **`TempBinNode`** — A non-canonical temporary node used only during split/concat intermediate steps.

The merge algorithm used during concatenation is directly derived from SeqHash: it scans adjacent nodes and combines pairs whose hash bits form a `(1, 0)` pattern. Fringe boundaries are determined by iteratively widening a scan window until the result stabilises.

## References

- **Versum / SeqHash**: van den Hooff, M. F. Kaashoek, and N. Zeldovich, “VerSum: Verifiable computations over large public logs,” in Proc. 2014 ACM SIGSAC Conf. Comput. Commun. Secur., 2014, pp. 1304–1316.. 
  Paper: <http://www.bu.edu/hic/files/2015/01/versum-ccs14.pdf>  
  SeqHash introduces the hash-based merge algorithm that SplitHash builds upon.

- **SplitHash original implementation** (Scala): Copyright 2016 Robbert van Dalen.
- **SplitHash Java port**: Copyright 2026.
