package com.aol.cyclops.types;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aol.cyclops.control.StreamUtils;

/**
 * An interface that represents a non-scalar Filterable
 * 
 * @author johnmcclean
 *
 * @param <T> Data type of elements stored in this IterableFilterable
 */
public interface IterableFilterable<T> extends Filterable<T>, Iterable<T> {

    /**
     * Remove all elements in the supplied Stream from this filterable
     * 
     * @param stream of elements to remove
     * @return Filterable with all supplied elements removed
     */
    default Filterable<T> removeAll(final Stream<? extends T> stream) {
        final Set<T> set = stream.collect(Collectors.toSet());
        return filterNot(i -> set.contains(i));
    }

    /**
     * Remove all elements in the supplied Iterable from this filterable 
     * 
     * @param it  an Iterable of elements to remove
     * @return Filterable with all supplied elements removed
     */
    default Filterable<T> removeAll(final Iterable<? extends T> it) {
        return removeAll(StreamUtils.stream(it));
    }

    /**
     * Remove all supplied elements from this filterable   
     * 
     * @param values to remove
     * @return Filterable with all supplied values removed
     */
    default Filterable<T> removeAll(final T... values) {
        return removeAll(Stream.of(values));

    }

    /**
     * Retain only the supplied elements in the returned Filterable
     * 
     * @param it Iterable of elements to retain
     * @return Filterable with supplied values retained, and others removed
     */
    default Filterable<T> retainAll(final Iterable<? extends T> it) {
        return retainAll(StreamUtils.stream(it));
    }

    /**
     * Retain only the supplied elements in the returned Filterable
     * 
     * @param stream of elements to retain
     * @return Filterable with supplied values retained, and others removed
     */
    default Filterable<T> retainAll(final Stream<? extends T> stream) {
        final Set<T> set = stream.collect(Collectors.toSet());
        return filter(i -> set.contains(i));
    }

    /**
     * Retain only the supplied elements in the returned Filterable
     * 
     * @param values elements to retain
     * @return Filterable with supplied values retained, and others removed
     */
    default Filterable<T> retainAll(final T... values) {
        return retainAll(Stream.of(values));
    }

}
