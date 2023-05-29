# Materializing Streams
getting a meaningful value our of a running stream

Components are static until they run

```scala
val graph = source.via(flow).to(sink)
val result = graph.run() //<-- materialzed value
```

A graph is a blueprint for a stream
Running a graph allocates the right resources
* actors, thread pools
* sockets, connections
* etc - everything is transparent

Running a graph = Materializing

## Materialized Values

### Materializing a graph = materializing all components

* each component produces a materialized value when run
* the graph produces a single materialized value
* out job is to choose which one to pick

### A component can materialize multiple times
* you can reuse the same component in different graphs
* different runs = different materializations!

### A Materialized value can be anything

# Backpressure

One of the fundamental features of Reactive Streams
Elements flow as response to demand from consumers

Fast consumers: all is well
Slow consumer: Problem
* consumer will send a signal to producer to slow down

Backpressure protocol is transparent 

## Reactions to Backpressure
* try to slow down if possible
* buffer elements until there's more demand
* drop down elements from the buffer if it overflows
* tear down/kill the whole stream (failure)


## overflow strategies:
- drop head = oldest
- drop tail = newest
- drop new = exact element to be added = keeps the buffer
- drop the entire buffer
- back pressure signal
- fail

