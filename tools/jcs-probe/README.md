# Test-only RFC8785 feasibility probe

Not part of the Android app or its production dependencies. No packet client/encoder is shipped.

```sh
mvn -B -f tools/jcs-probe/pom.xml test
node tools/jcs-probe/fixture-vectors.cjs
```

17 JVM tests: 12 canonicalization/strict-ingress probes plus 5 synthetic formula tests.
The tests deliberately demonstrate published library ingress defects (duplicate null keys,
leading-zero numbers, lone surrogates, malformed UTF8); green tests do NOT approve the library
as a standalone production parser. The scanner and its toy root allowlist/limits are test-only.

The Node oracle and Kotlin constants use a deliberately fake identity and unitless test payload.
They validate the draft contentDigest of the full canonical packet including chainHash; they
are NOT frozen backend golden vectors or an approved chain identity/schema.

Details and remaining contract questions: ../../docs/engineering/event-contract-draft-feedback.md
