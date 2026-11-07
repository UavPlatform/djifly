# Stage1 actual-source codec / HTTP regression harness

Run from repository root: `mvn -B -f tools/stage1-client-tests/pom.xml clean test`.

Compiles the actual app codec/uploader, the actual SQLite-independent journal core, existing storage tests and OpenAPI contract gates. Uses real OkHttp + loopback MockWebServer and JDBC SQLite; it does not call a real backend, Android runtime or DJI device. JDK17+ / Kotlin2.0.21 / target11.

Backend main is pinned in `src/test/resources/backend-d10fc1b/SOURCE.json`. Four backend shared vectors and non-ASCII canonical fixture are test inputs with independent expected bytes/digests, not a second authority. HTTP responses are locally constructed simulations, not golden server behavior. See `docs/engineering/stage1-codec-http.md` for opt-in/production restrictions and the still-unfrozen source contract.

Use **clean test**, particularly when changing local branches: Maven otherwise retains obsolete test classes/resources from another branch. The independent `tools/jcs-probe` suite remains a separate regression gate.
