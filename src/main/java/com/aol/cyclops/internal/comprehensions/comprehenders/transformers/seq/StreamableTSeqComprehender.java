package com.aol.cyclops.internal.comprehensions.comprehenders.transformers.seq;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.aol.cyclops.control.Streamable;
import com.aol.cyclops.control.monads.transformers.seq.StreamableTSeq;
import com.aol.cyclops.internal.comprehensions.comprehenders.MaterializedList;
import com.aol.cyclops.types.extensability.Comprehender;
import com.aol.cyclops.types.mixins.Printable;

public class StreamableTSeqComprehender implements Comprehender<StreamableTSeq>, Printable {

    @Override
    public Object resolveForCrossTypeFlatMap(final Comprehender comp, final StreamableTSeq apply) {
        final List list = (List) apply.stream()
                                      .collect(Collectors.toCollection(MaterializedList::new));
        return list.size() > 0 ? comp.of(list) : comp.empty();
    }

    @Override
    public Object filter(final StreamableTSeq t, final Predicate p) {
        return t.filter(p);
    }

    @Override
    public Object map(final StreamableTSeq t, final Function fn) {
        return t.map(r -> fn.apply(r));
    }

    @Override
    public Object flatMap(final StreamableTSeq t, final Function fn) {
        return t.flatMapT(r -> fn.apply(r));
    }

    @Override
    public StreamableTSeq of(final Object o) {
        return StreamableTSeq.of(Streamable.of(o));
    }

    @Override
    public StreamableTSeq empty() {
        return StreamableTSeq.emptyStreamable();
    }

    @Override
    public Class getTargetClass() {
        return StreamableTSeq.class;
    }

    @Override
    public StreamableTSeq fromIterator(final Iterator o) {
        return StreamableTSeq.of(Streamable.fromIterable(() -> o));
    }

}
