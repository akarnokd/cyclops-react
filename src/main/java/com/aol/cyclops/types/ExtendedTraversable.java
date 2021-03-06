package com.aol.cyclops.types;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.types.stream.ConvertableSequence;

/**
 * Represents a finite traversable type
 * 
 * @TODO - rename to FiniteTraversable in cyclops-react 2.0
 * 
 * @author johnmcclean
 *
 * @param <T> Data type of elements in this ExtendedTraversable
 */
public interface ExtendedTraversable<T> extends Traversable<T>, TransformerTraversable<T>, Foldable<T>, Iterable<T>, ConvertableSequence<T> {

    /**
     * Generate the permutations based on values in the ExtendedTraversable. 
     * 
     * 
     * @return Permutations from this ExtendedTraversable
     */
    default ExtendedTraversable<ReactiveSeq<T>> permutations() {
        return stream().permutations();
    }

    /**
     *  Generate the combinations based on values in the ExtendedTraversable.
     * 
     * <pre>
     * {@code
     *   ExtendedTraversable<Integer> stream = ReactiveSeq.of(1,2,3);
     *   stream.combinations(2)
     *   
     *   //ReactiveSeq[ReactiveSeq[1,2],ReactiveSeq[1,3],ReactiveSeq[2,3]]
     * }
     * </pre>
     * 
     * 
     * @param size
     *            of combinations
     * @return All combinations of the elements in this ExtendedTraversable of the specified
     *         size
     */
    default ExtendedTraversable<ReactiveSeq<T>> combinations(final int size) {
        return stream().combinations(size);
    }

    /**
     * Generate the combinations based on values in the ExtendedTraversable.
     * 
     * <pre>
     * {@code
     *   ReactiveSeq.of(1,2,3).combinations()
     *   
     *   //ReactiveSeq[ReactiveSeq[],ReactiveSeq[1],ReactiveSeq[2],ReactiveSeq[3].ReactiveSeq[1,2],ReactiveSeq[1,3],ReactiveSeq[2,3]
     *   			,ReactiveSeq[1,2,3]]
     * }
     * </pre>
     * 
     * 
     * @return All combinations of the elements in this ExtendedTraversable
     */
    default ExtendedTraversable<ReactiveSeq<T>> combinations() {
        return stream().combinations();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Traversable#stream()
     */
    @Override
    default ReactiveSeq<T> stream() {

        return ConvertableSequence.super.stream();
    }

}
