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


