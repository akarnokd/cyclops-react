#Simple React : Simple Fluent Api for Functional Reactive Programming with Java 8

Simple React is the new Java 8 based concurrency mechanism for LANA. It is a fluent API built on top of Java 8 CompletableFutures and the Stream API. It provides a focused, simple and limited Reactive API aimed at solving the 90% use case - but without adding complexity.

##Why Simple React

Simple React is built on top of JDK standard libraries and unlike other Reactive implementations for Java does not re-invent Streams, Functional interfaces etc, rather the goal is to reuse them. SimpleReact augments the parallel Streams functionality in JDK by providing a facade over both the Streams and CompletableFuture apis. Under-the-hood, SimpleReact *is* a Stream of CompletableFutures, and presents that externally as an api somewhat inspired by the Javascript Promises / A+ Spec (https://promisesaplus.com/).

##When to use ParallelStreams and when to use SimpleReact

It is a core goal of SimpleReact to integrate tightly with JDK ParallelStreams, and is itself built on top of the Streams Api.

Because of this, it is often possible, but more complex to use parallelStreams for the same task as SimpleReact. But where this turns into an exercise in hacking parallelStreams to make it work they way needed - SimpleReact is a better choice. In practice we recommend using  whichever is simpler for the portion of the problem being solved. 

There a few core differences

*	With parallelStreams evaluation is lazy, it won’t be triggered until something like collect is called, which will then block the current thread
*	With SimpleReact, the async processing starts as soon as react is called, and won’t block the current thread unless the user asks for it (via block())
*	A Stream will be closed and can’t be resused at a given stage (you can’t take a copy of the stream and rerun the same task over and over from that point)
*	A SimpleReact stage can be forked and merged at will
*	Selection of ExecutorService - with parallelStreams you are limited to using a ForkJoinPool, and changing away from the common ForkJoinPool is awkward
*	With a SimpleReact data flow you can also access the underlying concurrency primitives (Streams of CompletableFutures) at any point and build something tailored to your own use case for the next stage. SimpleReact also makes it straightforward to return back into the reactive dataflow.
*	It’s very obvious that your code is concurrent  with SimpleReact - it can be easy to miss the parallelStream method call, and may not always be apparent which steps are executed concurrently and which are blocking.	


**Example** : Making a call to ~ 50 servers, on a 16 core box, which will handle errors, process the responses, and then publish each processed result .

Pure ParallelStreams :-

	new ForkJoinPool(50)
			.submit( () -> Arrays.asList( () -> restCall(url1), () -> restCall(url2), {..})
				.parallelStream()
				.map(  sp -> try { 
									return sp.supply(); 
								 } catch(Exception e) { 
									return new ErrorCase(e);    
								 })
				.map( case -> publish(case))
				.collect(Collectors.toList()));

With SimpleReact (& the current thread able to continue processing) :-

	new SimpleReact(new ForkJoinPool(50))
		.react( () -> restCall(url1), () -> restCall(url2), {..})
		.onFail( e -> new ErrorCase(e))
		.then( case -> publish(case));

##Data flow

Simple react starts with an array of Suppliers which generate data other functions will react to. Each supplier will be passed to an Executor to be executed, potentially on a separate thread. Each additional step defined when calling Simple React will also be added as a linked task, also to be executed, potentially on a separate thread.
##Example 1 : reacting with completablefutures

React **with**

			List<CompletableFuture<Integer>> futures = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.with((it) -> it * 100);

In this instance, 3 suppliers generate 3 numbers. These may be executed in parallel, when they complete each number will be multiplied by 100 - as a separate parrellel task (handled by a ForkJoinPool or configurable task executor). A List of Future objects will be returned immediately from Simple React and the tasks will be executed asynchronously.
React with does not block.
##Example 2 : chaining

React **then**

	 	new SimpleReact()
	 			.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then((it) -> it * 100)
				.then((it) -> "*" + it)


React then allows event reactors to be chained. Unlike React with, which returns a collection of Future references, React then is a fluent interface that returns the React builder - allowing further reactors to be added to the chain.
React then does not block.
React with can be called after React then which gives access to the full CompleteableFuture api. CompleteableFutures can be passed back into SimpleReact via SimpleReact.react(streamOfCompleteableFutures);
See this blog post for examples of what can be achieved via CompleteableFuture :- http://www.nurkiewicz.com/2013/12/promises-and-completablefuture.html

##Example 3: blocking

React and **block**

			List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then((it) -> it * 100)
				.then((it) -> "*" + it)
				.block();
						
In this example, once the current thread of execution meets the React block method, it will block until all tasks have been completed. The result will be returned as a List. The Reactive tasks triggered by the Suppliers are non-blocking, and are not impacted by the block method until they are complete. Block, only blocks the current thread.

##Example 4: breakout

Sometimes you may not need to block until all the work is complete, one result or a subset may be enough. To faciliate this, block can accept a Predicate functional interface that will allow SimpleReact to stop blocking the current thread when the Predicate has been fulfilled. E.g.

			List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.block(status -> status.getCompleted()>1);

In this example the current thread will unblock once more than one result has been returned. The available fields on the status object are :-
completed
errors
total
elapsedMillis

##Example 5 : onFail

React **onFail**
onFail allows disaster recovery for each task (a separate onFail should be configured for each react phase that can fail). E.g. if reading data from an external service fails, but default value is acceptable - onFail is a suitable mechanism to set the default value.

			List<String> strings = new SimpleReact()
				.<Integer> react(() -> 100, () -> 2, () -> 3)
				.then(it -> {
					if (it == 100)
						throw new RuntimeException("boo!");
		
					return it;
				})
				.onFail(e -> 1)
				.then(it -> "*" + it)
				.block();

In this example, should the first "then" phase fail, the default value of 1 will be used instead.

##Example 6: non-blocking

React and **allOf**

allOf is a non-blocking equivalent of block. The current thread is not impacted by the calculations, but the reactive chain does not continue until all currently alloted tasks complete. The allOf task is then provided with a list of the results from the previous tasks in the chain. Any parallelStreams used inside allOf will reuse the SimpleReact ExecutorService - if it is a ForkJoinPool (which it is by default), rather than the Common ForkJoinPool parallelStreams use by default. 

        	boolean blocked[] = {false};
			new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)	
				.then(it -> {
					try {
						Thread.sleep(50000);
					} catch (Exception e) {
						
					}
					blocked[0] =true;
					return 10;
				})
				.allOf( it -> it.size());

		
			assertThat(blocked[0],is(false));

In this example, the current thread will continue and assert that it is not blocked, allOf could continue and be executed in a separate thread.

first() is a useful method to extract a single value from a dataflow that ends in allOf. E.g. 


        	boolean blocked[] = {false};
			int size = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)	
				.then(it -> {
					try {
						Thread.sleep(50000);
					} catch (Exception e) {
						
					}
					blocked[0] =true;
					return 10;
				})
				.allOf( it -> it.size()).first();

		
			assertThat(blocked[0],is(false));

##Example 7: non-blocking with the Stream api

             List<Integer> result =new SimpleReact()
             	.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> {
					return it*200;
				})
				.<Integer,Integer>allOf( (it )->{
					return it.parallelStream()
					.filter( f -> f>300)
					.map(m -> m-5)
					.reduce(0, (acc,next) -> acc+next); 
				}).block();

		
			assertThat(result.size(),is(1));
			assertThat(result.get(0),is(990));


In this example we block the current thread to get the final result, the allOf task uses the Streams api to setup another FRP chain that takes the inputs from our initial parellel jobs ([1,2,3] -> [200,400,600]), and does a filter / map/ reduce on them in parallel.

##Example 6 : capturing exceptions

React *capture*

onFail is used for disaster recovery (when it is possible to recover) - capture is used to capture those occasions where the full pipeline has failed and is unrecoverable.

			List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if (it == 100)
						throw new RuntimeException("boo!");
		
					return it;
				})
				.onFail(e -> 1)
				.then(it -> "*" + it)
				.then(it -> {
					
					if ("*200".equals(it))
						throw new RuntimeException("boo!");
		
					return it;
				})
				.capture(e -> logger.error(e.getMessage(),e))
				.block();


In this case, strings will only contain the two successful results (for ()->1 and ()->3), an exception for the chain starting from Supplier ()->2 will be logged by capture. Capture will not capture the exception thrown when an Integer value of 100 is found, but will catch the exception when the String value "*200" is passed along the chain.

##Example 7 : using the Streams Api

React and the *Streams Api*

It is possible to reuse the internal SimpleReact ExecutorService for JDK 8 parallelStreams. SimpleReact uses a ForkJoinPool as the ExecutorService by default, and to reuse the ExecutorService with parallelStreams it must be a ForkJoinPool - so if you want to supply your own make sure it is also a ForkJoinPool. 

A mechamism to share ExecutorServices is provided via the *collectResults* method, this collects the results from the current active tasks, and clients are given the full range of SimpleReact blocking options.  The results will then be provided to a function provided by client code when the *submit* is called. A way to merge all these steps into a single method is also provided (submitAndBlock). The easiest way to do this is via the submitAndBlock method 

Example :

		 Integer result = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 200)
				.<List<Integer>,Integer>submitAndblock(
						it -> it.parallelStream()
								.filter(f -> f > 300)
								.map(m -> m - 5)
								.reduce(0, (acc, next) -> acc + next));
								
To use a different ExecutorService than SimpleReact's internal ExecutorService leverae parallelStream directly from block() 

			ImmutableMap<String,Integer> dataSizes = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 30,()->400)
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.<String>block()
				.parallelStream()
				.filter( it -> it.length()>3)
				.map(it -> ImmutableMap.of(it,it.length()))
				.reduce(ImmutableMap.of(),  (acc, next) -> ImmutableMap.<String, Integer>builder()
					      .putAll(acc)
					      .putAll(next)
					      .build());

In this example the converted Strings are filtered by length and an ImmutableMap created using the Java 8 Streams Api.


##Example 8 : peeking at the current stage

Particularly during debugging and troubleshooting it can be very useful to check the results at a given stage in the dataflow. Just like within the Streams Api, the peek method can allow you to do this.

Example :

	List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.<String>then(it -> "*" + it)
				.peek((String it) -> logger.info("Value is {}",it))
				.block();
				
##Example 9 : filtering results

The filter method allows users to filter out results they are not interested in.

Example :

	List<String> result = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> "*" + it)
				.filter(it -> it.startsWith("*1"))
				.block();

##Example 10 : Concurrent iteration

SimpleReact provides a mechanism for starting a dataflow an iterator.

	List<Integer> list = Arrays.asList(1,2,3,4);
	List<String> strings = new SimpleReact()
				.<Integer> react(list.iterator() ,list.size())
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.block(); 
				
##Example 11 : 'Infinite' generators & iterators

SimpleReact provides a mechanism over JDK Stream iterate and generate which will create 'infinite' Streams of data to react to. Because SimpleReact eagerly collects these Streams (when converting to *active* CompletableFutures), the SimpleReact api always requires a maximum size parameter to be set.

	List<String> strings = new SimpleReact()
				.<Integer> react(() -> count++ ,SimpleReact.times(4))
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.capture(e -> capture++)
				.block();

##Example 12 : Splitting and merging SimpleReact dataflows

A simple example below where a dataflow is split into 3, processed separately then merged back into a single flow.

	Stage<String> stage = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> "*" + it);
		Stage<String> stage1 = stage.filter(it -> it.startsWith("*1"));
		Stage<String> stage2 = stage.filter(it -> it.startsWith("*2"));
		Stage<String> stage3 = stage.filter(it -> it.startsWith("*3"));
		
		stage1 = stage1.then(it -> it+"!");
		stage2 = stage2.then(it -> it+"*");
		stage3 = stage3.then(it -> it+"%");
		
		List<String> result = stage1.merge(stage2).merge(stage3).block();
