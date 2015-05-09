package com.aol.cyclops.comprehensions.converters;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;

public class CollectionToStreamConverter implements MonadicConverter<Seq> {

	private static final Map<Class,Boolean> shouldConvertCache=  new ConcurrentHashMap<>();
	public boolean accept(Object o){
		return (o instanceof Collection) || (o instanceof Map) || (o instanceof Iterable && shouldConvertCache.computeIfAbsent(o.getClass(),c->shouldConvert(c)));
	}
	@SuppressWarnings("rawtypes")
	public Seq convertToMonadicForm(Object f) {
			
			if(f instanceof Collection)
				return Seq.seq(((Collection)f).stream());
			if(f instanceof Map)
				return Seq.seq(((Map)f).entrySet().stream());
			
			if(f instanceof Iterable){
				return Seq.seq((Iterable)f);
			}
			
			return null; //should never happen
		}
	private Boolean shouldConvert(Class c) {
		return !Stream.of(c.getMethods())
		.filter(method -> "map".equals(method.getName()))
		.filter(method -> method.getParameterCount()==1).findFirst().isPresent();
	}

}
