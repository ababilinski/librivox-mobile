import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
export const packageRoot = resolve(scriptDirectory, "..");

const operationPattern = /^(librivox|lit2go|gutendex|wolne_lektury):(title|author|reader|chapter|genre):(any|english|polish)$/;

export function normalizeSearchText(value) {
  return value
    .normalize("NFKD")
    .replace(/\p{M}+/gu, "")
    .replace(/[łŁđĐðÐþÞæÆœŒøØß]/gu, (character) => ({
      ł: "l", Ł: "L", đ: "d", Đ: "D", ð: "d", Ð: "D", þ: "th", Þ: "Th",
      æ: "ae", Æ: "Ae", œ: "oe", Œ: "Oe", ø: "o", Ø: "O", ß: "ss"
    })[character])
    .toLocaleLowerCase("en-US")
    .trim()
    .replace(/\s+/gu, " ");
}

async function parseJson(path, errors, label = path) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch (error) {
    errors.push(`${label}: invalid JSON (${error.message})`);
    return null;
  }
}

async function walkFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const paths = [];
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) paths.push(...await walkFiles(path));
    else paths.push(path);
  }
  return paths;
}

function check(condition, message, errors) {
  if (!condition) errors.push(message);
}

function checkUnique(values, label, errors) {
  check(new Set(values).size === values.length, `${label}: values must be unique`, errors);
}

function fixtureInvariant(path, contents, errors) {
  if (path.endsWith("librivox/feed-title-alice.json")) {
    const payload = JSON.parse(contents);
    check(payload.books?.some((book) => book.id === "200"), `${path}: must contain durable LibriVox ID 200`, errors);
    check(payload.books?.some((book) => book.sections?.some((section) => section.id === "123443" && section.readers?.length > 0)), `${path}: must retain extended chapter and reader metadata`, errors);
  } else if (path.endsWith("librivox/advanced-author-alice.json")) {
    const payload = JSON.parse(contents);
    check(payload.status === "SUCCESS" && /Alice Gerstenberg/.test(payload.results), `${path}: must preserve a partial author match`, errors);
  } else if (path.endsWith("librivox/advanced-reader-alice.json")) {
    const payload = JSON.parse(contents);
    check(payload.status === "SUCCESS" && /catalog-result/.test(payload.results), `${path}: must preserve the reader-search result envelope`, errors);
  } else if (path.endsWith("librivox/advanced-chapter-advice.json")) {
    const payload = JSON.parse(contents);
    check(/Advice From A Caterpillar/i.test(payload.results), `${path}: must preserve the chapter result`, errors);
  } else if (path.endsWith("lit2go/index-a.html")) {
    check(/\/lit2go\/1\/alices-adventures-in-wonderland\//.test(contents), `${path}: must preserve the Alice index stub`, errors);
  } else if (path.endsWith("lit2go/detail-alice.html")) {
    check(/Advice from a Caterpillar/.test(contents), `${path}: must preserve the Alice chapter`, errors);
  } else if (path.endsWith("gutendex/search-alice.json")) {
    const payload = JSON.parse(contents);
    check(payload.results?.some((book) => book.id === 19573 && book.formats?.["audio/mpeg"]), `${path}: must contain playable Alice audiobook ID 19573`, errors);
  } else if (path.endsWith("wolne/index-antek.json")) {
    const payload = JSON.parse(contents);
    check(payload.some((book) => book.slug === "antek" && book.author === "Bolesław Prus" && book.has_audio === true), `${path}: must preserve playable Antek and its diacritic`, errors);
  } else if (path.endsWith("wolne/detail-antek.json")) {
    const payload = JSON.parse(contents);
    check(payload.media?.some((item) => item.type === "mp3"), `${path}: must contain playable MP3 media`, errors);
  } else if (path.endsWith("failures/blank.html")) {
    check(contents.trim() === "", `${path}: blank fixture must remain blank`, errors);
  }
}

function validateContract(contract, errors) {
  if (!contract) return;
  check(contract.contractId === "org.librivox-mobile.catalog-search", "contract: unexpected contractId", errors);
  check(contract.contractVersion === 1 && contract.fixtureVersion === 1, "contract: version must be 1", errors);

  const enumNames = ["sources", "fields", "languages", "outcomes", "skipReasons", "failureKinds", "facetStates", "provenanceKinds"];
  for (const name of enumNames) {
    check(Array.isArray(contract.enums?.[name]) && contract.enums[name].length > 0, `contract: enums.${name} must be non-empty`, errors);
    if (Array.isArray(contract.enums?.[name])) checkUnique(contract.enums[name], `contract: enums.${name}`, errors);
  }

  for (const source of contract.enums?.sources ?? []) {
    const capability = contract.sourceCapabilities?.[source];
    check(Boolean(capability), `contract: missing capabilities for ${source}`, errors);
    if (!capability) continue;
    checkUnique(capability.fields, `contract: ${source} fields`, errors);
    checkUnique(capability.languages, `contract: ${source} languages`, errors);
    for (const field of capability.fields) {
      check(typeof capability.adapters?.[field] === "string", `contract: ${source}.${field} is missing an adapter`, errors);
    }
  }

  for (const example of contract.normalization?.examples ?? []) {
    check(normalizeSearchText(example.input) === example.output, `contract: normalization mismatch for ${JSON.stringify(example.input)}`, errors);
  }

  const cache = contract.cachePolicy ?? {};
  check(cache.positiveFreshSeconds === 30 * 60, "contract: positive TTL must be 30 minutes", errors);
  check(cache.authoritativeEmptyFreshSeconds === 5 * 60, "contract: empty TTL must be 5 minutes", errors);
  check(cache.staleDisplaySeconds === 7 * 24 * 60 * 60, "contract: stale window must be 7 days", errors);
  check(cache.maximumSnapshots === 100, "contract: maximumSnapshots must be 100", errors);
  check(cache.partialIsFresh === false, "contract: partial snapshots must never be fresh", errors);
  check(cache.failedOverwritesComplete === false, "contract: failures must not overwrite complete snapshots", errors);
  check(cache.priorityInCacheKey === false, "contract: source priority must not be in cache keys", errors);
  check(JSON.stringify(cache.cacheKeyParts) === JSON.stringify(["contractVersion", "normalizedQuery", "source", "field", "language"]), "contract: cache key parts changed", errors);
  check(cache.legacyNamespacesToInvalidate?.includes("discover-v2"), "contract: discover-v2 invalidation is required", errors);
  check(cache.legacyNamespacesToInvalidate?.includes("searchCache.v2"), "contract: searchCache.v2 invalidation is required", errors);
  check(contract.connectivityPolicy?.maximumAutomaticReconnectRetriesPerOperation === 1, "contract: reconnect retry count must be exactly one", errors);
}

function validateSourceHealthContract(contract, searchContract, errors) {
  if (!contract) return;
  check(contract.contractId === "org.librivox-mobile.catalog-source-health", "source health contract: unexpected contractId", errors);
  check(contract.contractVersion === 1, "source health contract: version must be 1", errors);
  check(contract.contractVersion === searchContract?.contractVersion, "source health contract: version must match catalog search", errors);
  check(
    JSON.stringify(contract.states) === JSON.stringify(["unknown", "checking", "available", "broken", "offline"]),
    "source health contract: states changed",
    errors
  );

  const searchSources = searchContract?.enums?.sources ?? [];
  check(
    JSON.stringify(Object.keys(contract.providers ?? {}).sort()) === JSON.stringify([...searchSources].sort()),
    "source health contract: providers must match public catalog sources",
    errors
  );
  for (const [source, provider] of Object.entries(contract.providers ?? {})) {
    check(/^https:\/\//.test(provider.probeURL ?? ""), `source health contract: ${source} probe URL must use HTTPS`, errors);
    check(Array.isArray(provider.recognizableTitleMarkers) && provider.recognizableTitleMarkers.length > 0, `source health contract: ${source} must define recognizable title markers`, errors);
    checkUnique(provider.recognizableTitleMarkers ?? [], `source health contract: ${source} recognizable title markers`, errors);
  }

  const probe = contract.probePolicy ?? {};
  check(probe.method === "GET", "source health contract: stable roots must use GET", errors);
  check(probe.timeoutSeconds >= 45, "source health contract: timeout must accommodate slow healthy roots", errors);
  check(probe.availableStatusRange?.minimum === 200, "source health contract: available status minimum must be 200", errors);
  check(probe.availableStatusRange?.maximumExclusive === 300, "source health contract: available status maximum must be exclusive 300", errors);
  check(probe.bodyValidation === "provider_specific_title_marker_and_outage_rejection", "source health contract: root health must require a provider title marker", errors);
  check(probe.unrecognizedSuccessfulResponse === "broken", "source health contract: generic successful pages must be broken", errors);
  check(Array.isArray(probe.outageContentMarkers) && probe.outageContentMarkers.length > 0, "source health contract: outage markers must be non-empty", errors);
  checkUnique(probe.outageContentMarkers ?? [], "source health contract: outage markers", errors);
  check(probe.emitCheckingBeforeUncachedProbe === true, "source health contract: uncached probes must expose checking", errors);
  check(probe.probeProvidersIndependently === true, "source health contract: provider probes must be independent", errors);

  const cache = contract.cachePolicy ?? {};
  check(cache.availableFreshSeconds === 5 * 60, "source health contract: available TTL must be 5 minutes", errors);
  check(cache.brokenFreshSeconds === 60, "source health contract: broken TTL must be 1 minute", errors);
  check(cache.offlineFreshSeconds === 0, "source health contract: offline must not be cached", errors);
  check(cache.checkingFreshSeconds === 0, "source health contract: checking must not be cached", errors);
  check(cache.unknownFreshSeconds === 60, "source health contract: checked unknown timeout must be cached for 1 minute", errors);
  check(cache.maximumEntries === 4, "source health contract: maximumEntries must be 4", errors);
  check(cache.eviction === "deterministic_lru", "source health contract: eviction must be deterministic LRU", errors);
  check(JSON.stringify(contract.failureKinds) === JSON.stringify(["offline", "http_status", "invalid_content", "timeout", "network"]), "source health contract: failure kinds changed", errors);

  const independence = contract.independence ?? {};
  check(independence.rootHealthDoesNotRewriteOperationOutcomes === true, "source health contract: root health must not rewrite operation outcomes", errors);
  check(independence.operationOutcomesDoNotRewriteRootHealth === true, "source health contract: operation outcomes must not rewrite root health", errors);
  check(independence.fieldOperationsContinueToUseCatalogSearchContract === true, "source health contract: field operations must remain in catalog search", errors);
}

function validateScenario(scenario, contract, manifestPaths, errors) {
  const prefix = `scenario ${scenario.id ?? "<missing>"}`;
  check(typeof scenario.id === "string" && /^[a-z0-9][a-z0-9-]*$/.test(scenario.id), `${prefix}: invalid id`, errors);
  check(["normalization", "planning", "retrieval", "ranking", "cache", "connectivity"].includes(scenario.kind), `${prefix}: invalid kind`, errors);
  check(Array.isArray(scenario.tags) && scenario.tags.length > 0, `${prefix}: tags must be non-empty`, errors);
  checkUnique(scenario.tags ?? [], `${prefix}: tags`, errors);

  const request = scenario.request ?? {};
  for (const [key, enumName] of [["sources", "sources"], ["fields", "fields"], ["languages", "languages"], ["sourcePriority", "sources"]]) {
    check(Array.isArray(request[key]), `${prefix}: request.${key} must be an array`, errors);
    checkUnique(request[key] ?? [], `${prefix}: request.${key}`, errors);
    for (const value of request[key] ?? []) {
      check(contract.enums[enumName].includes(value), `${prefix}: invalid request.${key} value ${value}`, errors);
    }
  }
  for (const source of request.sourcePriority ?? []) {
    check(request.sources.includes(source), `${prefix}: sourcePriority contains disabled source ${source}`, errors);
  }

  const normalized = normalizeSearchText(request.query ?? "");
  check(scenario.expected?.normalizedQuery === normalized, `${prefix}: expected normalizedQuery ${JSON.stringify(scenario.expected?.normalizedQuery)} but got ${JSON.stringify(normalized)}`, errors);

  const operationKeys = [
    ...(scenario.expected?.plannedOperationKeys ?? []),
    ...Object.keys(scenario.expected?.outcomes ?? {}),
    ...(scenario.transport ?? []).map((step) => step.operationKey)
  ];
  for (const key of operationKeys) check(operationPattern.test(key), `${prefix}: invalid operation key ${key}`, errors);
  checkUnique(scenario.expected?.plannedOperationKeys ?? [], `${prefix}: plannedOperationKeys`, errors);

  for (const [key, outcome] of Object.entries(scenario.expected?.outcomes ?? {})) {
    check(contract.enums.outcomes.includes(outcome), `${prefix}: invalid outcome ${outcome} for ${key}`, errors);
    if (outcome === "skipped") {
      const reason = scenario.expected?.skipReasons?.[key];
      check(contract.enums.skipReasons.includes(reason), `${prefix}: skipped ${key} must have a valid reason`, errors);
    }
  }

  for (const step of scenario.transport ?? []) {
    check(["response", "timeout", "transport_error", "cancelled"].includes(step.result), `${prefix}: invalid transport result ${step.result}`, errors);
    if (step.result === "response") {
      check(Number.isInteger(step.status), `${prefix}: response ${step.operationKey} must have a status`, errors);
      check(typeof step.fixture === "string" && manifestPaths.has(step.fixture), `${prefix}: unknown fixture ${step.fixture}`, errors);
    } else {
      check(typeof step.errorCode === "string", `${prefix}: ${step.result} must have errorCode`, errors);
      check(step.fixture === undefined, `${prefix}: ${step.result} must not have a fixture`, errors);
    }
  }

  if (normalized === "") {
    check((scenario.expected?.plannedOperationKeys ?? []).length === 0, `${prefix}: empty normalized query must not plan operations`, errors);
    check(scenario.expected?.networkRequestCount === 0, `${prefix}: empty normalized query must not use the network`, errors);
  }
}

function validateScenarioCoverage(scenarios, errors) {
  const tags = new Set(scenarios.flatMap((scenario) => scenario.tags));
  const requiredTags = [
    "alice", "advice-from-a-caterpillar", "common-sense", "antek", "ante", "boleslaw-prus", "no-match", "whitespace-only",
    "source-alone", "no-sources", "any-language", "english", "polish", "reversed-source-priority",
    "valid-empty", "malformed-response", "blank-response", "http-404", "http-500", "timeout", "delayed-response", "cancellation", "partial-failure", "all-source-failure",
    "fresh-cache", "negative-cache", "partial-cache", "stale-cache", "cache-corruption", "schema-migration", "lru",
    "offline", "reconnect", "source-change"
  ];
  for (const tag of requiredTags) check(tags.has(tag), `scenarios: missing required coverage tag ${tag}`, errors);

  const sourceAlone = new Set(scenarios.filter((scenario) => scenario.tags.includes("source-alone") && scenario.request.sources.length === 1).map((scenario) => scenario.request.sources[0]));
  for (const source of ["librivox", "lit2go", "gutendex", "wolne_lektury"]) {
    check(sourceAlone.has(source), `scenarios: missing source-alone scenario for ${source}`, errors);
  }

  const languages = new Set(scenarios.flatMap((scenario) => scenario.request.languages));
  for (const language of ["any", "english", "polish"]) check(languages.has(language), `scenarios: missing ${language} language configuration`, errors);

  check(scenarios.some((scenario) => scenario.request.sources.length === 4 && scenario.request.fields.length === 5), "scenarios: missing all-source/all-field configuration", errors);
  check(scenarios.some((scenario) => scenario.request.sources.length === 0), "scenarios: missing no-source configuration", errors);
}

export async function validatePackage(root = packageRoot) {
  const errors = [];
  const contract = await parseJson(join(root, "contract/v1/catalog-search-contract.json"), errors, "contract");
  const sourceHealthContract = await parseJson(
    join(root, "contract/v1/catalog-source-health-contract.json"),
    errors,
    "source health contract"
  );
  const scenariosDocument = await parseJson(join(root, "scenarios/v1/catalog-search-scenarios.json"), errors, "scenarios");
  const manifest = await parseJson(join(root, "fixtures/v1/manifest.json"), errors, "manifest");

  for (const schemaPath of [
    "schemas/v1/catalog-search-contract.schema.json",
    "schemas/v1/catalog-source-health-contract.schema.json",
    "schemas/v1/catalog-search-scenarios.schema.json",
    "schemas/v1/fixture-manifest.schema.json"
  ]) {
    const schema = await parseJson(join(root, schemaPath), errors, schemaPath);
    check(schema?.$schema === "https://json-schema.org/draft/2020-12/schema", `${schemaPath}: must use JSON Schema 2020-12`, errors);
  }

  validateContract(contract, errors);
  validateSourceHealthContract(sourceHealthContract, contract, errors);
  if (!manifest || !scenariosDocument || !contract) return errors;

  check(manifest.contractVersion === contract.contractVersion, "manifest: contract version mismatch", errors);
  check(manifest.fixtureVersion === contract.fixtureVersion, "manifest: fixture version mismatch", errors);
  check(scenariosDocument.contractVersion === contract.contractVersion, "scenarios: contract version mismatch", errors);
  check(scenariosDocument.fixtureVersion === contract.fixtureVersion, "scenarios: fixture version mismatch", errors);

  const entryIds = manifest.entries.map((entry) => entry.id);
  const entryPaths = manifest.entries.map((entry) => entry.path);
  checkUnique(entryIds, "manifest entry ids", errors);
  checkUnique(entryPaths, "manifest entry paths", errors);
  const manifestPaths = new Set(entryPaths);
  const manifestByPath = new Map(manifest.entries.map((entry) => [entry.path, entry]));

  for (const entry of manifest.entries) {
    const absolutePath = join(root, entry.path);
    let contents;
    try {
      contents = await readFile(absolutePath);
    } catch (error) {
      errors.push(`manifest ${entry.id}: cannot read ${entry.path} (${error.message})`);
      continue;
    }
    const digest = createHash("sha256").update(contents).digest("hex");
    check(digest === entry.sha256, `manifest ${entry.id}: SHA-256 mismatch`, errors);
    const text = contents.toString("utf8");
    fixtureInvariant(entry.path, text, errors);

    if (entry.mediaType.startsWith("application/json") && entry.captureMode !== "synthetic_failure") {
      try {
        JSON.parse(text);
      } catch (error) {
        errors.push(`manifest ${entry.id}: successful JSON fixture is malformed (${error.message})`);
      }
    }
    check(entry.request?.method === "GET" || entry.request?.method === "POST", `manifest ${entry.id}: invalid request method`, errors);
    check(/^https:\/\//.test(entry.request?.url ?? ""), `manifest ${entry.id}: request URL must be HTTPS`, errors);
  }

  const fixtureFiles = (await walkFiles(join(root, "fixtures/v1")))
    .map((path) => relative(root, path))
    .filter((path) => path !== "fixtures/v1/manifest.json")
    .sort();
  for (const path of fixtureFiles) check(manifestPaths.has(path), `manifest: unlisted fixture ${path}`, errors);
  for (const path of manifestPaths) check(fixtureFiles.includes(path), `manifest: entry does not point to a fixture file ${path}`, errors);

  const scenarios = scenariosDocument.scenarios ?? [];
  checkUnique(scenarios.map((scenario) => scenario.id), "scenario ids", errors);
  for (const scenario of scenarios) validateScenario(scenario, contract, manifestPaths, errors);
  validateScenarioCoverage(scenarios, errors);

  for (const scenario of scenarios) {
    for (const step of scenario.transport ?? []) {
      if (step.result !== "response") continue;
      const manifestEntry = manifestByPath.get(step.fixture);
      check(manifestEntry?.status === step.status, `scenario ${scenario.id}: status ${step.status} disagrees with manifest for ${step.fixture}`, errors);
    }
  }

  return errors;
}

async function main() {
  const errors = await validatePackage();
  if (errors.length > 0) {
    console.error(`Catalog contract validation failed with ${errors.length} issue(s):`);
    for (const error of errors) console.error(`- ${error}`);
    process.exitCode = 1;
    return;
  }
  const scenarios = JSON.parse(await readFile(join(packageRoot, "scenarios/v1/catalog-search-scenarios.json"), "utf8")).scenarios;
  const fixtures = JSON.parse(await readFile(join(packageRoot, "fixtures/v1/manifest.json"), "utf8")).entries;
  console.log(`Catalog contract v1 is valid (${scenarios.length} scenarios, ${fixtures.length} fixtures).`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
