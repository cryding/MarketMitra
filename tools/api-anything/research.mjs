#!/usr/bin/env node
/**
 * MarketMitra optional API Anything research runner.
 *
 * API Anything is a local CLI, not a hosted API. This runner calls only already
 * installed/read-only operations and submits normalized findings to MarketMitra.
 * It never logs in, writes to websites, solves CAPTCHAs, or bypasses blocks.
 *
 * Usage:
 *   node tools/api-anything/research.mjs "cotton kurti"
 *   API_URL=http://127.0.0.1:8000 node tools/api-anything/research.mjs "cotton kurti"
 *   node tools/api-anything/research.mjs "cotton kurti" --out report.json
 */
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { writeFile } from "node:fs/promises";
import path from "node:path";

const execFileAsync = promisify(execFile);
const API_URL = (process.env.API_URL || "https://marketmitra-api.vercel.app").replace(/\/$/, "");
const TIMEOUT_MS = 25_000;

function parseArgs(argv) {
  const args = [...argv];
  const query = args.shift();
  let out = "marketmitra-api-anything-report.json";
  while (args.length) {
    const token = args.shift();
    if (token === "--out") {
      out = args.shift();
      if (!out) throw new Error("--out requires a file path");
    } else {
      throw new Error(`Unknown argument: ${token}`);
    }
  }
  if (!query || query.trim().length < 2) {
    throw new Error('Usage: node tools/api-anything/research.mjs "product query" [--out report.json]');
  }
  return { query: query.trim().slice(0, 100), out };
}

async function callOperation(site, operation, parameter) {
  const [name, value] = parameter;
  try {
    const { stdout, stderr } = await execFileAsync(
      process.env.API_ANYTHING_BIN || "api-anything",
      ["call", site, operation, `${name}=${value}`, "--max-tier", "1"],
      { timeout: TIMEOUT_MS, maxBuffer: 2_000_000, windowsHide: true }
    );
    const line = stdout.split(/\r?\n/).find((entry) => entry.trim().startsWith("{"));
    if (!line) throw new Error(stderr.trim() || "CLI returned no JSON output");
    const result = JSON.parse(line);
    if (!result.ok) {
      return { site, operation, ok: false, status: result.class || "operation_failed", count: 0, tier: result.tier ?? null, latency_ms: result.ms ?? null, reason: String(result.reason || stderr || "Operation returned ok=false").slice(0, 500), data: [] };
    }
    const raw = result.data;
    let data = Array.isArray(raw) ? raw : [];
    if (raw && !Array.isArray(raw) && Array.isArray(raw.rows) && Array.isArray(raw.columns)) {
      data = raw.rows.map((row) => Object.fromEntries(raw.columns.map((column, index) => [column, row[index]])));
    }
    return { site, operation, ok: true, status: result.truncated ? "partial" : "ok", count: data.length, tier: result.tier ?? null, latency_ms: result.ms ?? null, reason: result.truncated ? "Result was truncated by API Anything." : null, data };
  } catch (error) {
    const reason = error?.code === "ENOENT"
      ? "api-anything CLI is not installed. Follow tools/api-anything/README.md."
      : String(error?.stderr || error?.message || error).slice(0, 500);
    return { site, operation, ok: false, status: error?.code === "ENOENT" ? "cli_not_installed" : error?.killed ? "timeout" : "call_failed", count: 0, tier: null, latency_ms: null, reason, data: [] };
  }
}

function normalizeAmazon(record) {
  const asin = String(record?.asin || "").trim();
  const title = String(record?.title || "").trim();
  if (!title) return null;
  return {
    title: title.slice(0, 300),
    source: "API Anything / Amazon.com",
    kind: "product",
    url: asin ? `https://www.amazon.com/dp/${encodeURIComponent(asin)}` : null,
    price: record?.price == null ? null : String(record.price).slice(0, 80),
    currency: "USD",
    metadata: { asin: asin || null, market: "US", source_operation: "amazon.search" }
  };
}

function normalizeYouTube(record) {
  const title = String(record?.title || "").trim();
  if (!title) return null;
  const id = String(record?.id || "").trim();
  return {
    title: title.slice(0, 300),
    source: "API Anything / YouTube",
    kind: "video",
    url: id ? `https://www.youtube.com/watch?v=${encodeURIComponent(id)}` : null,
    price: null,
    currency: "UNKNOWN",
    metadata: { channel: String(record?.channel || "").slice(0, 200), views: record?.views ?? null, source_operation: "youtube.search" }
  };
}

function normalizeHackerNews(record) {
  const title = String(record?.title || "").trim();
  if (!title) return null;
  return {
    title: title.slice(0, 300),
    source: "API Anything / Hacker News",
    kind: "article",
    url: typeof record?.url === "string" ? record.url.slice(0, 2000) : null,
    price: null,
    currency: "UNKNOWN",
    metadata: { points: record?.points ?? null, comments: record?.num_comments ?? null, created_at: record?.created_at ?? null, source_operation: "hacker-news.search" }
  };
}

async function main() {
  const { query, out } = parseArgs(process.argv.slice(2));
  const calls = await Promise.all([
    callOperation("amazon", "search", ["k", query]),
    callOperation("youtube", "search", ["q", query]),
    callOperation("hacker-news", "search", ["query", query])
  ]);

  const normalized = [];
  for (const call of calls) {
    const mapper = call.site === "amazon" ? normalizeAmazon : call.site === "youtube" ? normalizeYouTube : normalizeHackerNews;
    for (const row of call.data) {
      const item = mapper(row);
      if (item) normalized.push(item);
    }
  }
  const sourceRows = calls.map((call) => ({
    name: `API Anything / ${call.site}`,
    ok: call.ok && call.count > 0,
    status: call.status,
    count: call.count,
    tier: call.tier,
    latency_ms: call.latency_ms,
    reason: call.reason
  }));
  const payload = {
    query,
    collected_at_utc: new Date().toISOString(),
    sources: sourceRows,
    items: normalized
  };
  let aggregate = null;
  let submitError = null;
  try {
    const response = await fetch(`${API_URL}/api/v1/research/aggregate`, {
      method: "POST",
      headers: { "content-type": "application/json", "accept": "application/json" },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15_000)
    });
    if (!response.ok) throw new Error(`MarketMitra API returned HTTP ${response.status}: ${(await response.text()).slice(0, 500)}`);
    aggregate = await response.json();
  } catch (error) {
    submitError = String(error?.message || error).slice(0, 500);
  }

  const report = {
    ...payload,
    aggregate,
    submit_error: submitError,
    provenance: "Read-only API Anything operations; result availability depends on learned operations, public access and site policy.",
    note: "Amazon.com results are USD/US-market signals, not Indian INR prices. YouTube views indicate attention, not sales. Hacker News is only a weak conversation signal and may be irrelevant to consumer products."
  };
  const outputPath = path.resolve(out);
  await writeFile(outputPath, JSON.stringify(report, null, 2) + "\n", "utf8");
  const successCount = sourceRows.filter((source) => source.ok).length;
  console.log(JSON.stringify({
    ok: successCount > 0,
    query,
    sources_attempted: sourceRows.length,
    sources_with_results: successCount,
    items: normalized.length,
    report: outputPath,
    submitted_to_marketmitra: Boolean(aggregate),
    submit_error: submitError
  }, null, 2));
  if (successCount === 0) process.exitCode = 2;
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exitCode = 1;
});
