# Dependencies are wired by hand in one AppGraph

`:app` builds its object graph in a single `AppGraph` object created by the listener, with no Hilt or Koin. The graph is small (about ten adapters). A DI framework adds annotation processing, build time and setup cost that a 3-hour build can't spare, and `AppGraph` keeps every dependency visible in one file.
