# Test-only RFC8785 feasibility probe

Not part of the Android app or its production dependencies. No packet client/encoder is shipped.

```sh
mvn -B -f tools/jcs-probe/pom.xml test
node tools/jcs-probe/fixture-vectors.cjs
```

Original 33-test corpus: 12 canonicalization/strict-ingress probes, 5 synthetic
formula tests, and 16 old pinned-backend golden/strict-profile checks. The current
harness runs 59 tests in total; see the actual-main section below.
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

## Actual main Stage1 snapshot

The same independent corpus also runs against `../../spec/event-contract/`, pinned
to backend main `d924e34522119ae220b48dee03669752f3d8e331`. SOURCE.json records
the full source commit and Git blob/SHA256 fingerprints. Four current snapshot
files (including OpenAPI) are checked against remote Git blob identities.
No current golden values are substituted by the old synthetic oracle.

The harness now compiles the actual generated app model/path files and runs the
actual app OpenApiContractTest through a target-only source copy. Total: 59 tests
(17 original probes + 16 old-snapshot checks + 19 current-main checks + 7 app
contract gates). No app runtime dependencies or Android tests are added.

Known draft gaps are tested, not silently fixed: missing required/nullable OpenAPI
constraints and default Gson dropping explicit null keys. These DTOs are structural
snapshots, NOT production packet or reconciliation codecs. See
[Stage1 synchronization report](../../docs/engineering/stage1-contract-sync-d924e345.md).
