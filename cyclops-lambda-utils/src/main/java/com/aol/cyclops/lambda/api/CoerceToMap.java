package com.aol.cyclops.lambda.api;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aol.cyclops.lambda.utils.ExceptionSoftener;

public class CoerceToMap {

	public static Map<String,?> toMap(Object o){
		try {
			
			return (Map)Stream.of(o.getClass().getDeclaredFields())
					.collect(Collectors.toMap((Field f)->f.getName(),(Field f) ->{
						try {
							f.setAccessible(true);
							return f.get(o);
						} catch (Exception e) {
							ExceptionSoftener.singleton.factory.getInstance().throwSoftenedException(e);
							return null;
						}
					}));
		} catch (Exception e) {
			ExceptionSoftener.singleton.factory.getInstance()
					.throwSoftenedException(e);
			return null;
		}
	}
}
