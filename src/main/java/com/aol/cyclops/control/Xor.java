package com.aol.cyclops.control;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.Semigroups;
import com.aol.cyclops.control.Matchable.CheckValue1;
import com.aol.cyclops.data.collections.extensions.CollectionX;
import com.aol.cyclops.data.collections.extensions.persistent.PStackX;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.Combiner;
import com.aol.cyclops.types.BiFunctor;
import com.aol.cyclops.types.Filterable;
import com.aol.cyclops.types.Functor;
import com.aol.cyclops.types.MonadicValue;
import com.aol.cyclops.types.MonadicValue2;
import com.aol.cyclops.types.To;
import com.aol.cyclops.types.Value;
import com.aol.cyclops.types.anyM.AnyMValue;
import com.aol.cyclops.types.applicative.ApplicativeFunctor;
import com.aol.cyclops.types.stream.reactive.ValueSubscriber;
import com.aol.cyclops.util.function.Curry;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * eXclusive Or (Xor)
 * 
 * 'Right' (or primary type) biased disjunct union. Often called Either, but in a generics heavy Java world Xor is half the length of Either.
 * 
 *  No 'projections' are provided, swap() and secondaryXXXX alternative methods can be used instead.
 *  
 *  Xor is used to represent values that can be one of two states (for example a validation result, either everything is ok - or we have an error).
 *  It can be used to avoid a common design anti-pattern where an Object has two fields one of which is always null (or worse, both are defined as Optionals).
 *  
 *  <pre>
 *  {@code 
 *     
 *     public class Member{
 *           Xor<SeniorTeam,JuniorTeam> team;      
 *     }
 *     
 *     Rather than
 *     
 *     public class Member{
 *           @Setter
 *           SeniorTeam seniorTeam = null;
 *           @Setter
 *           JuniorTeam juniorTeam = null;      
 *     }
 *  }
 *  </pre>
 *  
 *  Xor's have two states
 *  Primary : Most methods operate naturally on the primary type, if it is present. If it is not, nothing happens.
 *  Secondary : Most methods do nothing to the secondary type if it is present. 
 *              To operate on the Secondary type first call swap() or use secondary analogs of the main operators.
 *  
 *  Instantiating an Xor - Primary
 *  <pre>
 *  {@code 
 *      Xor.primary("hello").map(v->v+" world") 
 *    //Xor.primary["hello world"]
 *  }
 *  </pre>
 *  
 *  Instantiating an Xor - Secondary
 *  <pre>
 *  {@code 
 *      Xor.secondary("hello").map(v->v+" world") 
 *    //Xor.seconary["hello"]
 *  }
 *  </pre>
 *  
 *  Xor can operate (via map/flatMap) as a Functor / Monad and via combine as an ApplicativeFunctor
 *  
 *   Values can be accumulated via 
 *  <pre>
 *  {@code 
 *  Xor.accumulateSecondary(ListX.of(Xor.secondary("failed1"),
                                                    Xor.secondary("failed2"),
                                                    Xor.primary("success")),
                                                    Semigroups.stringConcat)
 *  
 *  //failed1failed2
 *  
 *   Xor<String,String> fail1 = Xor.secondary("failed1");
     fail1.swap().combine((a,b)->a+b)
                 .combine(Xor.secondary("failed2").swap())
                 .combine(Xor.<String,String>primary("success").swap())
 *  
 *  //failed1failed2
 *  }
 *  </pre>
 * 
 * 
 * For Inclusive Ors @see Ior
 * 
 * @author johnmcclean
 *
 * @param <ST> Secondary type
 * @param <PT> Primary type
 */
public interface Xor<ST, PT> extends To<Xor<ST,PT>>,Supplier<PT>, MonadicValue2<ST, PT>, Functor<PT>, BiFunctor<ST,PT>,Filterable<PT>, ApplicativeFunctor<PT> {

    /**
     * Construct a Primary Xor from the supplied publisher
     * <pre>
     * {@code 
     *   ReactiveSeq<Integer> stream =  ReactiveSeq.of(1,2,3);
        
         Xor<Throwable,Integer> future = Xor.fromPublisher(stream);
        
         //Xor[1]
     * 
     * }
     * </pre>
     * @param pub Publisher to construct an Xor from
     * @return Xor constructed from the supplied Publisher
     */
    public static <T> Xor<Throwable, T> fromPublisher(final Publisher<T> pub) {
        final ValueSubscriber<T> sub = ValueSubscriber.subscriber();
        pub.subscribe(sub);
        return sub.toXor();
    }

    /**
     * Construct a Primary Xor from the supplied Iterable
     * <pre>
     * {@code 
     *   List<Integer> list =  Arrays.asList(1,2,3);
        
         Xor<Throwable,Integer> future = Xor.fromPublisher(stream);
        
         //Xor[1]
     * 
     * }
     * </pre> 
     * @param iterable Iterable to construct an Xor from
     * @return Xor constructed from the supplied Iterable
     */
    public static <ST, T> Xor<ST, T> fromIterable(final Iterable<T> iterable) {

        final Iterator<T> it = iterable.iterator();
        return Xor.primary(it.hasNext() ? it.next() : null);
    }

    /**
     * Create an instance of the secondary type. Most methods are biased to the primary type,
     * so you will need to use swap() or secondaryXXXX to manipulate the wrapped value
     * 
     * <pre>
     * {@code 
     *   Xor.<Integer,Integer>secondary(10).map(i->i+1);
     *   //Xor.secondary[10]
     *    
     *    Xor.<Integer,Integer>secondary(10).swap().map(i->i+1);
     *    //Xor.primary[11]
     * }
     * </pre>
     * 
     * 
     * @param value to wrap
     * @return Secondary instance of Xor
     */
    public static <ST, PT> Xor<ST, PT> secondary(final ST value) {
        return new Secondary<>(
                               value);
    }

    /**
     * Create an instance of the primary type. Most methods are biased to the primary type,
     * which means, for example, that the map method operates on the primary type but does nothing on secondary Xors
     * 
     * <pre>
     * {@code 
     *   Xor.<Integer,Integer>primary(10).map(i->i+1);
     *   //Xor.primary[11]
     *    
     *   
     * }
     * </pre>
     * 
     * 
     * @param value To construct an Xor from
     * @return Primary type instanceof Xor
     */
    public static <ST, PT> Xor<ST, PT> primary(final PT value) {
        return new Primary<>(
                             value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue#anyM()
     */
    @Override
    default AnyMValue<PT> anyM() {
        return AnyM.ofValue(this);
    }
    
    

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Applicative#combine(java.util.function.BinaryOperator, com.aol.cyclops.types.Applicative)
     */
    @Override
    default Xor<ST,PT> combine(BinaryOperator<Combiner<PT>> combiner, Combiner<PT> app) {
       
        return (Xor<ST,PT>)ApplicativeFunctor.super.combine(combiner, app);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#flatMapIterable(java.util.function.Function)
     */
    @Override
    default <R> Xor<ST, R> flatMapIterable(Function<? super PT, ? extends Iterable<? extends R>> mapper) {
        return (Xor<ST, R>)MonadicValue2.super.flatMapIterable(mapper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#flatMapPublisher(java.util.function.Function)
     */
    @Override
    default <R> Xor<ST, R> flatMapPublisher(Function<? super PT, ? extends Publisher<? extends R>> mapper) {
        return (Xor<ST, R>)MonadicValue2.super.flatMapPublisher(mapper);
    }

    /* (non-Javadoc)
    * @see com.aol.cyclops.types.MonadicValue#coflatMap(java.util.function.Function)
    */
    @Override
    default <R> Xor<ST, R> coflatMap(final Function<? super MonadicValue<PT>, R> mapper) {
        return (Xor<ST, R>) MonadicValue2.super.coflatMap(mapper);
    }

    //cojoin
    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue#nest()
     */
    @Override
    default Xor<ST, MonadicValue<PT>> nest() {
        return this.map(t -> unit(t));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#combine(com.aol.cyclops.Monoid, com.aol.cyclops.types.MonadicValue2)
     */
    @Override
    default Xor<ST, PT> combineEager(final Monoid<PT> monoid, final MonadicValue2<? extends ST, ? extends PT> v2) {
        return (Xor<ST, PT>) MonadicValue2.super.combineEager(monoid, v2);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#unit(java.lang.Object)
     */
    @Override
    default <T> Xor<ST, T> unit(final T unit) {
        return Xor.primary(unit);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Convertable#toOptional()
     */
    @Override
    default Optional<PT> toOptional() {
        return isPrimary() ? Optional.of(get()) : Optional.empty();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Filterable#filter(java.util.function.Predicate)
     */
    @Override
    Xor<ST, PT> filter(Predicate<? super PT> test);

    /**
     * If this Xor contains the Secondary type, map it's value so that it contains the Primary type 
     * 
     * 
     * @param fn Function to map secondary type to primary
     * @return Xor with secondary type mapped to primary
     */
    Xor<ST, PT> secondaryToPrimayMap(Function<? super ST, ? extends PT> fn);

    /**
     * Always map the Secondary type of this Xor if it is present using the provided transformation function
     * 
     * @param fn Transformation function for Secondary types
     * @return Xor with Secondary type transformed
     */
    <R> Xor<R, PT> secondaryMap(Function<? super ST, ? extends R> fn);

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#map(java.util.function.Function)
     */
    @Override
    <R> Xor<ST, R> map(Function<? super PT, ? extends R> fn);

    /**
     * Peek at the Secondary type value if present
     * 
     * @param action Consumer to peek at the Secondary type value
     * @return Xor with the same values as before
     */
    Xor<ST, PT> secondaryPeek(Consumer<? super ST> action);

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Functor#peek(java.util.function.Consumer)
     */
    @Override
    Xor<ST, PT> peek(Consumer<? super PT> action);

    /**
     * Swap types so operations directly affect the current (pre-swap) Secondary type
     *<pre>
     *  {@code 
     *    
     *    Xor.secondary("hello")
     *       .map(v->v+" world") 
     *    //Xor.seconary["hello"]
     *    
     *    Xor.secondary("hello")
     *       .swap()
     *       .map(v->v+" world") 
     *       .swap()
     *    //Xor.seconary["hello world"]
     *  }
     *  </pre>
     * 
     * 
     * @return Swap the primary and secondary types, allowing operations directly on what was the Secondary type
     */
    Xor<PT, ST> swap();

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Value#toIor()
     */
    @Override
    Ior<ST, PT> toIor();

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Convertable#isPresent()
     */
    @Override
    default boolean isPresent() {
        return isPrimary();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Value#toXor()
     */
    @Override
    default Xor<ST, PT> toXor() {
        return this;
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Value#toXor(java.lang.Object)
     */
    @Override
    default <ST2> Xor<ST2, PT> toXor(final ST2 secondary) {
        return visit(s -> secondary(secondary), p -> primary(p));
    }
    /**
     *  Turn a collection of Xors into a single Xor with Lists of values.
     *  Primary and secondary types are swapped during this operation.
     * 
     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
     *  Xor<ListX<Integer>,ListX<String>> xors =Xor.sequenceSecondary(ListX.of(just,none,Xor.primary(1)));
        //Xor.primary(ListX.of("none")))
     * 
     * }
     * </pre>
     * 
     * 
     * @param xors Xors to sequence
     * @return Xor sequenced and swapped
     */
    public static <ST, PT> Xor<ListX<PT>, ListX<ST>> sequenceSecondary(final CollectionX<Xor<ST, PT>> xors) {
        return AnyM.sequence(AnyM.listFromXor(xors.map(Xor::swap)))
                   .unwrap();
    }
    /**
     * Accumulate the result of the Secondary types in the Collection of Xors provided using the supplied Reducer  {@see com.aol.cyclops.Reducers}.
     * 
     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
        
     *  Xor<?,PSetX<String>> xors = Xor.accumulateSecondary(ListX.of(just,none,Xor.primary(1)),Reducers.<String>toPSetX());
      //Xor.primary(PSetX.of("none"))));
      * }
     * </pre>
     * @param xors Collection of Iors to accumulate secondary values
     * @param reducer Reducer to accumulate results
     * @return Xor populated with the accumulate secondary operation
     */
    public static <ST, PT, R> Xor<?, R> accumulateSecondary(final CollectionX<Xor<ST, PT>> xors, final Reducer<R> reducer) {
        return sequenceSecondary(xors).map(s -> s.mapReduce(reducer));
    }
    /**
     * Accumulate the results only from those Xors which have a Secondary type present, using the supplied mapping function to
     * convert the data from each Xor before reducing them using the supplied Monoid (a combining BiFunction/BinaryOperator and identity element that takes two
     * input values of the same type and returns the combined result) {@see com.aol.cyclops.Monoids }..
     * 
     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
        
     *  Xor<?,String> xors = Xor.accumulateSecondary(ListX.of(just,none,Xor.secondary("1")),i->""+i,Monoids.stringConcat);
        //Xor.primary("none1")
     * 
     * }
     * </pre>
     * 
     * 
     *  
     * @param xors Collection of Iors to accumulate secondary values
     * @param mapper Mapping function to be applied to the result of each Ior
     * @param reducer Semigroup to combine values from each Ior
     * @return Xor populated with the accumulate Secondary operation
     */
    public static <ST, PT, R> Xor<?, R> accumulateSecondary(final CollectionX<Xor<ST, PT>> xors, final Function<? super ST, R> mapper,
            final Monoid<R> reducer) {
        return sequenceSecondary(xors).map(s -> s.map(mapper)
                                                 .reduce(reducer));
    }
    
  
    /**
     *  Turn a collection of Xors into a single Ior with Lists of values.
     *  
     * <pre>
     * {@code 
     * 
     * Xor<String,Integer> just  = Xor.primary(10);
       Xor<String,Integer> none = Xor.secondary("none");
        
        
     * Xor<ListX<String>,ListX<Integer>> xors =Xor.sequencePrimary(ListX.of(just,none,Xor.primary(1)));
       //Xor.primary(ListX.of(10,1)));
     * 
     * }</pre>
     *
     * 
     * 
     * @param iors Xors to sequence
     * @return Xor Sequenced
     */
    public static <ST, PT> Xor<ListX<ST>, ListX<PT>> sequencePrimary(final CollectionX<Xor<ST, PT>> xors) {
        return AnyM.sequence(AnyM.<ST, PT> listFromXor(xors))
                   .unwrap();
    }
    /**
     * Accumulate the result of the Primary types in the Collection of Xors provided using the supplied Reducer  {@see com.aol.cyclops.Reducers}.

     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
     * 
     *  Xor<?,PSetX<Integer>> xors =Xor.accumulatePrimary(ListX.of(just,none,Xor.primary(1)),Reducers.toPSetX());
        //Xor.primary(PSetX.of(10,1))));
     * }
     * </pre>
     * @param Xors Collection of Iors to accumulate primary values
     * @param reducer Reducer to accumulate results
     * @return Xor populated with the accumulate primary operation
     */
    public static <ST, PT, R> Xor<?, R> accumulatePrimary(final CollectionX<Xor<ST, PT>> xors, final Reducer<R> reducer) {
        return sequencePrimary(xors).map(s -> s.mapReduce(reducer));
    }
    
    /**
     * Accumulate the results only from those Iors which have a Primary type present, using the supplied mapping function to
     * convert the data from each Xor before reducing them using the supplied Monoid (a combining BiFunction/BinaryOperator and identity element that takes two
     * input values of the same type and returns the combined result) {@see com.aol.cyclops.Monoids }.. 
     * 
     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
        
     * Xor<?,String> iors = Xor.accumulatePrimary(ListX.of(just,none,Xor.primary(1)),i->""+i,Monoids.stringConcat);
       //Xor.primary("101"));
     * }
     * </pre>
     * 
     * 
     * @param xors Collection of Iors to accumulate primary values
     * @param mapper Mapping function to be applied to the result of each Ior
     * @param reducer Reducer to accumulate results
     * @return Xor populated with the accumulate primary operation
     */
    public static <ST, PT, R> Xor<?, R> accumulatePrimary(final CollectionX<Xor<ST, PT>> xors, final Function<? super PT, R> mapper,
            final Monoid<R> reducer) {
        return sequencePrimary(xors).map(s -> s.map(mapper)
                                               .reduce(reducer));
    }
    /**
     *  Accumulate the results only from those Xors which have a Primary type present, using the supplied Monoid (a combining BiFunction/BinaryOperator and identity element that takes two
     * input values of the same type and returns the combined result) {@see com.aol.cyclops.Monoids }.
     * 
     * <pre>
     * {@code 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
     *  
     *  Xor<?,Integer> xors XIor.accumulatePrimary(Monoids.intSum,ListX.of(just,none,Ior.primary(1)));
        //Ior.primary(11);
     * 
     * }
     * </pre>
     * 
     * 
     * 
     * @param xors Collection of Xors to accumulate primary values
     * @param reducer  Reducer to accumulate results
     * @return  Xor populated with the accumulate primary operation
     */
    public static <ST, PT> Xor<?, PT> accumulatePrimary(final Monoid<PT> reducer,final CollectionX<Xor<ST, PT>> xors) {
        return sequencePrimary(xors).map(s -> s.reduce(reducer));
    }
 
    /**
     * 
     * Accumulate the results only from those Xors which have a Secondary type present, using the supplied Monoid (a combining BiFunction/BinaryOperator and identity element that takes two
     * input values of the same type and returns the combined result) {@see com.aol.cyclops.Monoids }.
     * <pre>
     * {@code 
     * Xor.accumulateSecondary(ListX.of(Xor.secondary("failed1"),
    												Xor.secondary("failed2"),
    												Xor.primary("success")),
    												Semigroups.stringConcat)
    												
    												
     * //Xors.Primary[failed1failed2]
     * }
     * </pre>
     * <pre>
     * {@code 
     * 
     *  Xor<String,Integer> just  = Xor.primary(10);
        Xor<String,Integer> none = Xor.secondary("none");
        
     * Xor<?,Integer> iors = Xor.accumulateSecondary(Monoids.intSum,ListX.of(Xor.both(2, "boo!"),Xor.secondary(1)));
       //Xor.primary(3);  2+1
     * 
     * 
     * }
     * </pre>
     * 
     * @param xors Collection of Xors to accumulate secondary values
     * @param reducer  Semigroup to combine values from each Xor
     * @return Xor populated with the accumulate Secondary operation
     */
    public static <ST, PT> Xor<?, ST> accumulateSecondary(final Monoid<ST> reducer,final CollectionX<Xor<ST, PT>> xors) {
        return sequenceSecondary(xors).map(s -> s.reduce(reducer));
    }

    /**
     * Visitor pattern for this Ior.
     * Execute the secondary function if this Xor contains an element of the secondary type
     * Execute the primary function if this Xor contains an element of the primary type
     * 
     * 
     * <pre>
     * {@code 
     *  Xor.primary(10)
     *     .visit(secondary->"no", primary->"yes")
     *  //Xor["yes"]
        
        Xor.secondary(90)
           .visit(secondary->"no", primary->"yes")
        //Xor["no"]
         
 
     * 
     * }
     * </pre>
     * 
     * @param secondary Function to execute if this is a Secondary Xor
     * @param primary Function to execute if this is a Primary Ior
     * @param both Function to execute if this Ior contains both types
     * @return Result of executing the appropriate function
     */
    <R> R visit(Function<? super ST, ? extends R> secondary, Function<? super PT, ? extends R> primary);

    @Deprecated //use bimap instead
    default <R1, R2> Xor<R1, R2> mapBoth(final Function<? super ST, ? extends R1> secondary, final Function<? super PT, ? extends R2> primary) {
        if (isSecondary())
            return (Xor<R1, R2>) swap().map(secondary)
                                       .swap();
        return (Xor<R1, R2>) map(primary);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.BiFunctor#bimap(java.util.function.Function, java.util.function.Function)
     */
    @Override
    default <R1, R2> Xor<R1, R2> bimap(Function<? super ST, ? extends R1> secondary, Function<? super PT, ? extends R2> primary) {
        if (isSecondary())
            return (Xor<R1, R2>) swap().map(secondary)
                                       .swap();
        return (Xor<R1, R2>) map(primary);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.BiFunctor#bipeek(java.util.function.Consumer, java.util.function.Consumer)
     */
    @Override
    default Xor<ST, PT> bipeek(Consumer<? super ST> c1, Consumer<? super PT> c2) {
        
        return (Xor<ST, PT>)BiFunctor.super.bipeek(c1, c2);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.BiFunctor#bicast(java.lang.Class, java.lang.Class)
     */
    @Override
    default <U1, U2> Xor<U1, U2> bicast(Class<U1> type1, Class<U2> type2) {
        
        return (Xor<U1, U2>)BiFunctor.super.bicast(type1, type2);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.BiFunctor#bitrampoline(java.util.function.Function, java.util.function.Function)
     */
    @Override
    default <R1, R2> Xor<R1, R2> bitrampoline(Function<? super ST, ? extends Trampoline<? extends R1>> mapper1,
            Function<? super PT, ? extends Trampoline<? extends R2>> mapper2) {
        
        return  (Xor<R1, R2>)BiFunctor.super.bitrampoline(mapper1, mapper2);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Functor#patternMatch(java.util.function.Function, java.util.function.Supplier)
     */
    @Override
    default <R> Xor<ST, R> patternMatch(final Function<CheckValue1<PT, R>, CheckValue1<PT, R>> case1, final Supplier<? extends R> otherwise) {

        return (Xor<ST, R>) ApplicativeFunctor.super.patternMatch(case1, otherwise);
    }

    /**
     * Pattern match on the value/s inside this Xor.
     * 
     * <pre>
     * {@code 
     * 
     * import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
       import static com.aol.cyclops.util.function.Predicates.instanceOf;
     * 
     * Xor.primary(10)
     *    .matches(c->c.is(when("10"),then("hello")),
                   c->c.is(when(instanceOf(Integer.class)), then("error")),
                   otherwise("miss"))
           .get()
       //"error" Note the second case, 'primary' case is the one that matches
     * 
     * 
     * }
     * </pre>
     * 
     * 
     * @param fn1 Pattern matching function executed if this Xor has the secondary type
     * @param fn2 Pattern matching function executed if this Xor has the primary type
     * @param otherwise Supplier used to provide a value if the selecting pattern matching function fails to find a match
     * @return Lazy result of the pattern matching
     */
    <R> Eval<R> matches(Function<CheckValue1<ST, R>, CheckValue1<ST, R>> fn1, Function<CheckValue1<PT, R>, CheckValue1<PT, R>> fn2,
            Supplier<? extends R> otherwise);

    /* (non-Javadoc)
     * @see java.util.function.Supplier#get()
     */
    @Override
    PT get();
    /**
     * @return A Value containing the secondary Value if present
     */
    Value<ST> secondaryValue();
    /**
     * @return The Secondary Value if present, otherwise null
     */
    ST secondaryGet();
    /**
     * @return The Secondary value wrapped in an Optional if present, otherwise an empty Optional
     */
    Optional<ST> secondaryToOptional();
    /**
     * @return A Stream containing the secondary value if present, otherwise an empty Stream
     */
    ReactiveSeq<ST> secondaryToStream();

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.MonadicValue2#flatMap(java.util.function.Function)
     */
    @Override
    <LT1, RT1> Xor<LT1, RT1> flatMap(Function<? super PT, ? extends MonadicValue2<? extends LT1, ? extends RT1>> mapper);
    /**
     * Perform a flatMap operation on the Secondary type
     * 
     * @param mapper Flattening transformation function
     * @return Xor containing the value inside the result of the transformation function as the Secondary value, if the Secondary type was present
     */
    <LT1, RT1> Xor<LT1, RT1> secondaryFlatMap(Function<? super ST, ? extends Xor<LT1, RT1>> mapper);
    /**
     * A flatMap operation that keeps the Secondary and Primary types the same
     * 
     * @param fn Transformation function
     * @return Xor
     */
    Xor<ST, PT> secondaryToPrimayFlatMap(Function<? super ST, ? extends Xor<ST, PT>> fn);

    @Deprecated //use bipeek
    void peek(Consumer<? super ST> stAction, Consumer<? super PT> ptAction);
    /**
     * @return True if this is a primary Xor
     */
    public boolean isPrimary();
    /**
     * @return True if this is a secondary Xor
     */
    public boolean isSecondary();

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.applicative.ApplicativeFunctor#ap(com.aol.cyclops.types.Value, java.util.function.BiFunction)
     */
    @Override
    <T2, R> Xor<ST, R> combine(Value<? extends T2> app, BiFunction<? super PT, ? super T2, ? extends R> fn);

    /**
     * @return An Xor with the secondary type converted to a persistent list, for use with accumulating app function  {@link Xor#combine(Xor,BiFunction)}
     */
    default Xor<PStackX<ST>, PT> list() {
        return secondaryMap(PStackX::of);
    }

    /**
     * Accumulate secondarys into a PStackX (extended Persistent List) and Primary with the supplied combiner function
     * Primary accumulation only occurs if all phases are primary
     * 
     * @param app Value to combine with
     * @param fn Combiner function for primary values
     * @return Combined Xor
     */
    default <T2, R> Xor<PStackX<ST>, R> combineToList(final Xor<ST, ? extends T2> app, final BiFunction<? super PT, ? super T2, ? extends R> fn) {
        return list().combine(app.list(), Semigroups.collectionXConcat(), fn);
    }

    /**
     * Accumulate secondary values with the provided BinaryOperator / Semigroup {@link Semigroups}
     * Primary accumulation only occurs if all phases are primary
     * 
     * <pre>
     * {@code 
     *  Xor<String,String> fail1 =  Xor.secondary("failed1");
        Xor<PStackX<String>,String> result = fail1.list().combine(Xor.secondary("failed2").list(), Semigroups.collectionConcat(),(a,b)->a+b);
        
        //Secondary of [PStackX.of("failed1","failed2")))]
     * }
     * </pre>
     * 
     * @param app Value to combine with
     * @param semigroup to combine secondary types
     * @param fn To combine primary types
     * @return Combined Xor
     */

    default <T2, R> Xor<ST, R> combine(final Xor<? extends ST, ? extends T2> app, final BinaryOperator<ST> semigroup,
            final BiFunction<? super PT, ? super T2, ? extends R> fn) {
        return this.visit(secondary -> app.visit(s2 -> Xor.secondary(semigroup.apply(s2, secondary)), p2 -> Xor.secondary(secondary)),
                          primary -> app.visit(s2 -> Xor.secondary(s2), p2 -> Xor.primary(fn.apply(primary, p2))));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.applicative.ApplicativeFunctor#zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    default <T2, R> Xor<ST, R> zip(final Iterable<? extends T2> app, final BiFunction<? super PT, ? super T2, ? extends R> fn) {
        return map(v -> Tuple.tuple(v, Curry.curry2(fn)
                                            .apply(v))).flatMap(tuple -> Xor.fromIterable(app)
                                                                            .visit(i -> Xor.primary(tuple.v2.apply(i)), () -> Xor.secondary(null)));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.applicative.ApplicativeFunctor#zip(java.util.function.BiFunction, org.reactivestreams.Publisher)
     */
    @Override
    default <T2, R> Xor<ST, R> zip(final BiFunction<? super PT, ? super T2, ? extends R> fn, final Publisher<? extends T2> app) {
        return map(v -> Tuple.tuple(v, Curry.curry2(fn)
                                            .apply(v))).flatMap(tuple -> Xor.fromPublisher(app)
                                                                            .visit(i -> Xor.primary(tuple.v2.apply(i)), () -> Xor.secondary(null)));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Zippable#zip(org.jooq.lambda.Seq, java.util.function.BiFunction)
     */
    @Override
    default <U, R> Xor<ST, R> zip(final Seq<? extends U> other, final BiFunction<? super PT, ? super U, ? extends R> zipper) {

        return (Xor<ST, R>) MonadicValue2.super.zip(other, zipper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Zippable#zip(java.util.stream.Stream, java.util.function.BiFunction)
     */
    @Override
    default <U, R> Xor<ST, R> zip(final Stream<? extends U> other, final BiFunction<? super PT, ? super U, ? extends R> zipper) {

        return (Xor<ST, R>) MonadicValue2.super.zip(other, zipper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Zippable#zip(java.util.stream.Stream)
     */
    @Override
    default <U> Xor<ST, Tuple2<PT, U>> zip(final Stream<? extends U> other) {

        return (Xor) MonadicValue2.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Zippable#zip(org.jooq.lambda.Seq)
     */
    @Override
    default <U> Xor<ST, Tuple2<PT, U>> zip(final Seq<? extends U> other) {

        return (Xor) MonadicValue2.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Zippable#zip(java.lang.Iterable)
     */
    @Override
    default <U> Xor<ST, Tuple2<PT, U>> zip(final Iterable<? extends U> other) {

        return (Xor) MonadicValue2.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.Filterable#ofType(java.lang.Class)
     */
    @Override
    default <U> Xor<ST, U> ofType(final Class<? extends U> type) {

        return (Xor<ST, U>) Filterable.super.ofType(type);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.Filterable#filterNot(java.util.function.Predicate)
     */
    @Override
    default Xor<ST, PT> filterNot(final Predicate<? super PT> fn) {

        return (Xor<ST, PT>) Filterable.super.filterNot(fn);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.Filterable#notNull()
     */
    @Override
    default Xor<ST, PT> notNull() {

        return (Xor<ST, PT>) Filterable.super.notNull();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.Functor#cast(java.lang.Class)
     */
    @Override
    default <U> Xor<ST, U> cast(final Class<? extends U> type) {

        return (Xor<ST, U>) ApplicativeFunctor.super.cast(type);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.Functor#trampoline(java.util.function.Function)
     */
    @Override
    default <R> Xor<ST, R> trampoline(final Function<? super PT, ? extends Trampoline<? extends R>> mapper) {

        return (Xor<ST, R>) ApplicativeFunctor.super.trampoline(mapper);
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode(of = { "value" })
    static class Primary<ST, PT> implements Xor<ST, PT> {
        private final PT value;

        @Override
        public Xor<ST, PT> secondaryToPrimayMap(final Function<? super ST, ? extends PT> fn) {
            return this;
        }

        @Override
        public <R> Xor<R, PT> secondaryMap(final Function<? super ST, ? extends R> fn) {
            return (Xor<R, PT>) this;
        }

        @Override
        public <R> Xor<ST, R> map(final Function<? super PT, ? extends R> fn) {
            return new Primary<ST, R>(
                                      fn.apply(value));
        }

        @Override
        public Xor<ST, PT> secondaryPeek(final Consumer<? super ST> action) {
            return this;
        }

        @Override
        public Xor<ST, PT> peek(final Consumer<? super PT> action) {
            action.accept(value);
            return this;
        }

        @Override
        public Xor<ST, PT> filter(final Predicate<? super PT> test) {
            if (test.test(value))
                return this;
            return Xor.secondary(null);
        }

        @Override
        public Xor<PT, ST> swap() {
            return new Secondary<PT, ST>(
                                         value);
        }

        @Override
        public PT get() {
            return value;
        }

        @Override
        public ST secondaryGet() {
            return null;
        }

        @Override
        public Optional<ST> secondaryToOptional() {
            return Optional.empty();
        }

        @Override
        public ReactiveSeq<ST> secondaryToStream() {
            return ReactiveSeq.empty();
        }

        @Override
        public <LT1, RT1> Xor<LT1, RT1> flatMap(final Function<? super PT, ? extends MonadicValue2<? extends LT1, ? extends RT1>> mapper) {
            return (Xor<LT1, RT1>) mapper.apply(value)
                                         .toXor();
        }

        @Override
        public <LT1, RT1> Xor<LT1, RT1> secondaryFlatMap(final Function<? super ST, ? extends Xor<LT1, RT1>> mapper) {
            return (Xor<LT1, RT1>) this;
        }

        @Override
        public Xor<ST, PT> secondaryToPrimayFlatMap(final Function<? super ST, ? extends Xor<ST, PT>> fn) {
            return this;
        }

        @Override
        public void peek(final Consumer<? super ST> stAction, final Consumer<? super PT> ptAction) {
            ptAction.accept(value);
        }

        @Override
        public boolean isPrimary() {
            return true;
        }

        @Override
        public boolean isSecondary() {
            return false;
        }

        @Override
        public Value<ST> secondaryValue() {
            return Value.of(() -> null);
        }

        @Override
        public String toString() {
            return mkString();
        }

        @Override
        public String mkString() {
            return "Xor.primary[" + value + "]";
        }

        @Override
        public Ior<ST, PT> toIor() {
            return Ior.primary(value);
        }

        @Override
        public <R> R visit(final Function<? super ST, ? extends R> secondary, final Function<? super PT, ? extends R> primary) {
            return primary.apply(value);
        }

        @Override
        public <R> Eval<R> matches(
                final Function<com.aol.cyclops.control.Matchable.CheckValue1<ST, R>, com.aol.cyclops.control.Matchable.CheckValue1<ST, R>> secondary,
                final Function<com.aol.cyclops.control.Matchable.CheckValue1<PT, R>, com.aol.cyclops.control.Matchable.CheckValue1<PT, R>> primary,
                final Supplier<? extends R> otherwise) {
            final Matchable.MTuple1<PT> mt1 = () -> Tuple.tuple(value);
            return mt1.matches(primary, otherwise);

        }

        /* (non-Javadoc)
         * @see com.aol.cyclops.types.applicative.ApplicativeFunctor#ap(com.aol.cyclops.types.Value, java.util.function.BiFunction)
         */
        @Override
        public <T2, R> Xor<ST, R> combine(final Value<? extends T2> app, final BiFunction<? super PT, ? super T2, ? extends R> fn) {
            return app.toXor()
                      .visit(s -> Xor.secondary(null), f -> Xor.primary(fn.apply(get(), app.get())));
        }

    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode(of = { "value" })
    static class Secondary<ST, PT> implements Xor<ST, PT> {
        private final ST value;

        @Override
        public boolean isSecondary() {
            return true;
        }

        @Override
        public boolean isPrimary() {
            return false;
        }

        @Override
        public <R> Eval<R> matches(
                final Function<com.aol.cyclops.control.Matchable.CheckValue1<ST, R>, com.aol.cyclops.control.Matchable.CheckValue1<ST, R>> secondary,
                final Function<com.aol.cyclops.control.Matchable.CheckValue1<PT, R>, com.aol.cyclops.control.Matchable.CheckValue1<PT, R>> primary,
                final Supplier<? extends R> otherwise) {
            final Matchable.MTuple1<ST> mt1 = () -> Tuple.tuple(value);
            return mt1.matches(secondary, otherwise);
        }

        @Override
        public Xor<ST, PT> secondaryToPrimayMap(final Function<? super ST, ? extends PT> fn) {
            return new Primary<ST, PT>(
                                       fn.apply(value));
        }

        @Override
        public <R> Xor<R, PT> secondaryMap(final Function<? super ST, ? extends R> fn) {
            return new Secondary<R, PT>(
                                        fn.apply(value));
        }

        @Override
        public <R> Xor<ST, R> map(final Function<? super PT, ? extends R> fn) {
            return (Xor<ST, R>) this;
        }

        @Override
        public Xor<ST, PT> secondaryPeek(final Consumer<? super ST> action) {
            return secondaryMap((Function) FluentFunctions.expression(action));
        }

        @Override
        public Xor<ST, PT> peek(final Consumer<? super PT> action) {
            return this;
        }

        @Override
        public Xor<ST, PT> filter(final Predicate<? super PT> test) {
            return this;
        }

        @Override
        public Xor<PT, ST> swap() {
            return new Primary<PT, ST>(
                                       value);
        }

        @Override
        public PT get() {
            throw new NoSuchElementException();
        }

        @Override
        public ST secondaryGet() {
            return value;
        }

        @Override
        public Optional<ST> secondaryToOptional() {
            return Optional.ofNullable(value);
        }

        @Override
        public ReactiveSeq<ST> secondaryToStream() {
            return ReactiveSeq.fromStream(StreamUtils.optionalToStream(secondaryToOptional()));
        }

        @Override
        public <LT1, RT1> Xor<LT1, RT1> flatMap(final Function<? super PT, ? extends MonadicValue2<? extends LT1, ? extends RT1>> mapper) {
            return (Xor<LT1, RT1>) this;
        }

        @Override
        public <LT1, RT1> Xor<LT1, RT1> secondaryFlatMap(final Function<? super ST, ? extends Xor<LT1, RT1>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public Xor<ST, PT> secondaryToPrimayFlatMap(final Function<? super ST, ? extends Xor<ST, PT>> fn) {
            return fn.apply(value);
        }

        @Override
        public void peek(final Consumer<? super ST> stAction, final Consumer<? super PT> ptAction) {
            stAction.accept(value);

        }

        @Override
        public <R> R visit(final Function<? super ST, ? extends R> secondary, final Function<? super PT, ? extends R> primary) {
            return secondary.apply(value);
        }

        @Override
        public Maybe<PT> toMaybe() {
            return Maybe.none();
        }

        @Override
        public Optional<PT> toOptional() {
            return Optional.empty();
        }

        @Override
        public Value<ST> secondaryValue() {
            return Value.of(() -> value);
        }

        @Override
        public String toString() {
            return mkString();
        }

        @Override
        public String mkString() {
            return "Xor.secondary[" + value + "]";
        }

        /* (non-Javadoc)
         * @see com.aol.cyclops.value.Value#unapply()
         */
        @Override
        public ListX<ST> unapply() {
            return ListX.of(value);
        }

        @Override
        public Ior<ST, PT> toIor() {
            return Ior.secondary(value);
        }

        /* (non-Javadoc)
         * @see com.aol.cyclops.types.applicative.ApplicativeFunctor#ap(com.aol.cyclops.types.Value, java.util.function.BiFunction)
         */
        @Override
        public <T2, R> Xor<ST, R> combine(final Value<? extends T2> app, final BiFunction<? super PT, ? super T2, ? extends R> fn) {
            return (Xor<ST, R>) this;
        }

    }

}