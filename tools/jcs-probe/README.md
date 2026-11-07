# Test-only RFC8785 feasibility probe

Not part of the Android app or its production dependencies. No packet client/encoder is shipped.

```sh
mvn -B -f tools/jcs-probe/pom.xml test
node tools/jcs-probe/fixture-vectors.cjs
```

33 JVM tests: 12 canonicalization/strict-ingress probes, 5 synthetic formula tests,
and 16 pinned-backend golden/strict-profile checks.
The tests deliberately demonstrate published library ingress defects (duplicate null keys,
leading-zero numbers, lone surrogates, malformed UTF8); green tests do NOT approve the library
as a standalone production parser. The scanner and its toy root allowlist/limits are test-only.

The original synthetic Node oracle and Kotlin constants use a deliberately fake identity and unitless test payload.
They validate the draft contentDigest of the full canonical packet including chainHash; they
are NOT frozen backend golden vectors or an approved chain identity/schema.

Details and remaining contract questions: ../../docs/engineering/event-contract-draft-feedback.md

## Pinned backend draft (still not production)

`BackendA11d931GoldenTest` independently validates backend commit
`a11d9316e97d582e7460a13000314c873f065bb5`: all four packets' canonical bytes,
without-chainHash bytes, nine-field identity anchors, chain hashes, contentDigest,
and raw digests; plus the raw UTF8 non-ASCII/line-separator fixture.
Fixed inputs/expected values and source snapshots are under
`src/test/resources/backend-a11d931/`; no backend verifier is run as the oracle.
`PinnedDraftProbe` is strictly test-only, with local rejection-layer labels, not
an API implementation or a complete payload/authorization/receipt validator.
The original synthetic probe remains deliberately separate.

Results, snapshot fingerprints, rejection boundaries, PR19's already-merged state,
and remaining contract questions:
[backend-a11d931-independent-validation.md](../../docs/engineering/backend-a11d931-independent-validation.md).
