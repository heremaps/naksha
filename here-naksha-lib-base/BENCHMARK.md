# Benchmark
We test including GC, to see the impact of GC to the parser performance. The test does:

- Load all files into memory, considering the requested memory-quota between JSON bytes and HEAP.
- Then parse the first file 10 times to warmup.
- Force a garbage collection, measure how much memory is now used (information only).
- Start timer
- Parse all files, loop 10 times, keep the result of the last round.
  - Note: We parse file 1, 2, 3, ... 10, then restart the loop, so not 10 times the first, 10 times the second, ...!
- End timer
- Clear all cached JSON bytes (set references to `null`).
- Force a garbage collection, measure how much memory is now used.
  - This should measure how much memory is kept on heap by each parser.

This shows us what impact garbage collection has to parser performance, and it measures roughly how much memory the files eventually consume at HEAP, after GC done its job, which is not unimportant either.

## Prepare the test
You need around 51 GiB of free disk space. Go to [json_data/README.md](../json_data/README.md) and follow the instructions to download the JSON test data.

## -XX:+UseG1GC -Xmx12g -Xms12g ; json_data/ {parser} 10 18.0
- `naksha: 5.90 seconds, json-size: 825.97 MiB, heap-usage: 1925.90 MiB, 140.09 MiB/second, source-to-heap ratio: 2.33`
- `jackson: 7.07 seconds, json-size: 825.97 MiB, heap-usage: 4806.29 MiB, 116.83 MiB/second, source-to-heap ratio: 5.82`
- `naksha_jackson: 8.05 seconds, json-size: 825.97 MiB, heap-usage: 5224.46 MiB, 102.59 MiB/second, source-to-heap ratio: 6.33`
- `fastjson: 10.09 seconds, json-size: 825.97 MiB, heap-usage: 4932.15 MiB, 81.84 MiB/second, source-to-heap ratio: 5.97`
- `gson: 17.57 seconds, json-size: 825.97 MiB, heap-usage: 6791.26 MiB, 47.00 MiB/second, source-to-heap ratio: 8.22`
- `jsonio: 22.06 seconds, json-size: 825.97 MiB, heap-usage: 5143.81 MiB, 37.43 MiB/second, source-to-heap ratio: 6.23`

Overview:

| Parser         | Time   | Json-Size     | Heap-Usage   | Parser rate  | JSON : HEAP |
|----------------|--------|---------------|--------------|--------------|-------------|
| naksha         | 5.90   | 825.97 MiB    | 1925.90 MiB  | 140.09 MiB/s | 2.33        |
| jackson        | 7.07   | 825.97 MiB    | 4806.29 MiB  | 116.83 MiB/s | 5.82        |
| naksha_jackson | 8.05   | 825.97 MiB    | 5224.46 MiB  | 102.59 MiB/s | 6.33        |
| fastjson       | 10.09  | 825.97 MiB    | 4932.15 MiB  | 4932.15 MiB  | 5.97        |
| gson           | 17.57  | 825.97 MiB    | 6791.26 MiB  | 47.00 MiB/s  | 8.22        |
| jsonio         | 22.06  | 825.97 MiB    | 5143.81 MiB  | 37.43 MiB/s  | 6.23        |

