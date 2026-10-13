import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import test from "node:test";
import { normalizeSearchText, packageRoot, validatePackage } from "../scripts/validate.mjs";

test("the versioned contract, scenarios, and fixtures validate together", async () => {
  assert.deepEqual(await validatePackage(), []);
});

test("normalization is case, whitespace, and diacritic insensitive", () => {
  assert.equal(normalizeSearchText("alice "), "alice");
  assert.equal(normalizeSearchText("  Advice   from a Caterpillar  "), "advice from a caterpillar");
  assert.equal(normalizeSearchText("Bolesław Prus"), "boleslaw prus");
  assert.equal(normalizeSearchText("\t \n"), "");
});

test("shared acceptance vectors retain the durable source invariants", async () => {
  const scenarios = JSON.parse(await readFile(join(packageRoot, "scenarios/v1/catalog-search-scenarios.json"), "utf8")).scenarios;
  const alice = scenarios.find((scenario) => scenario.id === "retrieve-alice-all-fields-all-sources-any");
  assert.ok(alice);
  assert.ok(alice.expected.requiredVariantIds.includes("librivox:200"));
  assert.ok(alice.expected.sourceFacetMinimums.librivox > 0);
  assert.equal(alice.expected.outcomes["lit2go:reader:any"], "skipped");

  const polishSkip = scenarios.find((scenario) => scenario.id === "plan-librivox-polish-skipped");
  assert.equal(polishSkip.expected.skipReasons["librivox:title:polish"], "incompatible_language");
  assert.equal(polishSkip.expected.networkRequestCount, 0);

  const reconnect = scenarios.find((scenario) => scenario.id === "reconnect-retries-once");
  assert.equal(reconnect.expected.automaticRetryCount, 1);
});

test("source health is versioned, bounded, and independent from field outcomes", async () => {
  const health = JSON.parse(await readFile(
    join(packageRoot, "contract/v1/catalog-source-health-contract.json"),
    "utf8"
  ));

  assert.equal(health.contractVersion, 1);
  assert.deepEqual(health.states, ["unknown", "checking", "available", "broken", "offline"]);
  assert.deepEqual(Object.keys(health.providers).sort(), ["librivox", "lit2go", "wolne_lektury"]);
  assert.ok(Object.values(health.providers).every((provider) => provider.probeURL.startsWith("https://")));
  assert.equal(health.probePolicy.timeoutSeconds, 45);
  assert.equal(health.probePolicy.bodyValidation, "provider_specific_title_marker_and_outage_rejection");
  assert.equal(health.probePolicy.unrecognizedSuccessfulResponse, "broken");
  assert.ok(Object.values(health.providers).every((provider) => provider.recognizableTitleMarkers.length > 0));
  assert.equal(health.cachePolicy.maximumEntries, 4);
  assert.equal(health.cachePolicy.offlineFreshSeconds, 0);
  assert.equal(health.independence.rootHealthDoesNotRewriteOperationOutcomes, true);
  assert.equal(health.independence.operationOutcomesDoNotRewriteRootHealth, true);

  const genericErrorPage = (await readFile(
    join(packageRoot, "fixtures/v1/failures/http-200-error-page.html"),
    "utf8"
  )).toLocaleLowerCase("en-US");
  assert.ok(health.probePolicy.outageContentMarkers.some((marker) =>
    genericErrorPage.includes(marker.toLocaleLowerCase("en-US"))
  ));
  assert.equal(health.cachePolicy.unknownFreshSeconds, 60);
  assert.ok(health.failureKinds.includes("timeout"));
});
