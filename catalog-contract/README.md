# Catalog Search Contract

`catalog-contract` is the platform-neutral behavioral contract for catalog
search, retrieval, caching, and recovery. Android and iOS deliberately keep
native implementations; they consume the same versioned JSON documents and
fixtures in this directory.

The contract contains data and test fixtures. Each app implements it in its
native code.

## Layout

- `contract/v1/catalog-search-contract.json` defines capabilities, planning,
  outcome, provenance, cache, connectivity, facet, merge, and pagination rules.
- `contract/v1/catalog-source-health-contract.json` independently defines
  stable-root probes, health states, classification, and bounded cache rules.
- `schemas/v1/` contains JSON Schema documents for the contract, scenarios, and
  fixture manifest.
- `scenarios/v1/catalog-search-scenarios.json` contains deterministic vectors
  shared by Kotlin and Swift tests.
- `fixtures/v1/` contains compact structural excerpts of source responses plus
  deterministic failure bodies.
- `fixtures/v1/manifest.json` records the request represented by every fixture,
  capture metadata, media type, and SHA-256 digest.

All paths and identifiers are relative to this directory. Consumers should
reject a `contractVersion` they do not implement instead of silently applying
new semantics.

## Validation

Node 18 or newer is sufficient; there are no third-party dependencies.

```bash
cd catalog-contract
npm test
npm run validate
```

The validator checks JSON syntax, schema/document versions, enum values,
fixture references and hashes, canonical operation keys, required coverage,
normalization examples, and cache-policy invariants. JSON Schema files are also
included so Gradle, SwiftPM, editors, or CI can add their preferred schema
validator without changing this package.

## Fixture policy

Successful source fixtures are compact structural excerpts recorded from the
public endpoints on 2026-07-10. They preserve the response envelope and the
fields used by the native parsers, while omitting unrelated results and long
descriptions. They are deterministic test data, not live availability probes.

Live catalog tests must be opt-in (`RUN_LIVE_CATALOG_TESTS=1`) and assert durable
invariants rather than exact counts. A source outage must not require updating
these recorded fixtures.

Gutendex is no longer an active catalog. Its recorded fixtures remain for legacy
data compatibility; they are excluded from search scenarios and health probes.
Saved source identifiers and direct media links must remain readable by both apps.
