# Contract Driven Testing
We only test contracts. All tests must be **provable correct**, that means, when the test fails _(alerts)_, it **must** show a code error. We do not create tests that only show us that something changed.

For example, given a function `mul` that accepts two arguments `a` and `b`, and by definition multiplies the values, returning the result, does have a contract . We expected that `4` mul `5` always result in `20`. It could even have contractual details about edge cases, so when two numbers are too big _(overflow)_, but it could be as well an _undefined_ behavior. If the bahavior is _undefined_, we do **not** test for it!

## What not to do
For example, assume we have a query-generator, we input some arguments, and expect it to build an SQL query for PostgresQL database, that, when executed against a specific table layout, returns rows with certain properties, and maybe not more than 100. In this case, we **must not** test the returned SQL query. We **must** execute the query against the PostgresQL engine, for which it is designed, and check the results returned by the database, if they fulfil the contract.

**The contract in this case is **not** the generated SQL string, it is the result being promised!**

Would we just compare the generated SQL string against a specific test string, we would actually always have to adjust the test, when the code of the query builder is modified. This is not **_contract driven testing_**, it may be some other form of testing, but not what we want.

Therefore, the rule to write **contract driven tests** is, that we only test the contracts, not the implications. In the above given example, it is expected that the SQL query can change, its part of the _contract_. We do not expect a specific SQL query to be generated, we expect that the generated query, when being executed against a PostgresQL database, returns a very specific result-set! So we need to test only this.

## Epilogue
This document does not claim that this is the only way to write tests, nor that it is the best way, but for the author of this document it is the most efficient way to do it, when comparing use vs cost, the cost of creating and maintaining the test. This is, because it results in tests that are generally always true, as long as the contracts do not change, and ensures that the consumer of the contracts can rely upon the contracts. It avoids any effort, when modifying the code in a way that does not impact the contract, which allows us to do any kind of optimizations, without adjusted tons of tests.

However, if the _contract_ changes, then clearly the test must be adjusted. So, assume we decide that the limit is no longer a hard limit, but can be a soft-limit, so by _contract_ now the SQL query is allowed to return more than the limit, then we have to update the test.

In a nutshell:

**We expect that we only need to modify test code, when the _contracts_ they test do actually change!**

It's discussable if this is the correct way to test, but for this project it provides actually the right amount of test code, that ensures the functionality, while not causing unnecessary test coding.

## Performance testing
Next to the **contract driven testing** we should add performance testing. However, performance testing is not easy, because it is heavily relying on the local environment. We get totally different numbers on laptops compared against servers, actually other background processes can have impacts, disk speed, network congestion aso..

Therefore, performance testing need to be done in a dedicated environment. Still, what we want to have, are basic performance tests that can be executed locally to at least detect major issues being produced. Therefore, we want to have performances tests, but they should first run some local base rate detection, so for example do a query that is always the same, to get a base value for the current environment. Then we compare the results of the tests against this base value, and if code gets slower, we will alert.
