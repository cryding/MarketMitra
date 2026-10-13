# MarketMitra

**Universal Market Research & Data Intelligence** — an open-source Android + Python project for early-stage business research, sourcing, unit economics and launch planning.

> **Current development direction:** MarketMitra is evolving into a ground-level business operating toolkit. Sample product ideas remain labelled sample data. Six marketplace adapters attempt best-effort extraction only when public access and `robots.txt` permit it; individual sites may block requests or change markup. Research-hub URLs are manual discovery links, not scraped evidence or verified supplier recommendations. No login bypass, CAPTCHA bypass, paywall bypass, or private-data collection.

## What is in this repository

- `android/`: Kotlin + Jetpack Compose Android client with modern research UI, API Anything JSON-report import, and offline product/stock, order, expense and task records.
- `backend/`: FastAPI service with public-page extraction, six best-effort marketplace adapters, transparent price/unit-economics analysis, supplier discovery, budget planning, official setup links and API Anything report aggregation.
- `tools/api-anything/research.mjs`: optional local read-only research runner for API Anything's bundled Amazon.com, YouTube and Hacker News operations.
- `.github/workflows/ci.yml`: Python tests, API Anything runner syntax check and Android debug APK build/artifact.
- No paid AI APIs or paid dependencies are required for the first milestone.

## Run the backend locally

Python 3.11+ recommended.

```bash
cd backend
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Open `http://127.0.0.1:8000/docs` for the API docs and `http://127.0.0.1:8000/health` for a health check.

### API

- `GET /health` — service health.
- `GET /api/v1/research/products?q=kurti` — deterministic sample product ideas (not live market data).
- `POST /api/v1/scrape` with JSON `{"url":"https://example.com/product-page"}` — extracts a limited set of metadata from a public HTML page, subject to safety and robots checks.

Example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/scrape \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/"}'
```

## Android build

Open `android/` in Android Studio, or build in a Java 17 + Android SDK environment:

```bash
cd android
gradle assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions is configured to build it and upload an artifact. A green workflow run is required before calling the APK build verified.

The app supports local demo product filtering. To use live backend scraping, paste a reachable backend base URL into the app (for example `http://10.0.2.2:8000` when the Android emulator can reach a backend running on the same computer). A real phone needs a reachable LAN or HTTPS deployment URL; this repository does not claim a backend has already been deployed.

## Free deployment notes

The backend can be deployed to any compatible Python host with a free tier, but free-tier limits and availability can change. Configure the deployment from `backend/` using `pip install -r requirements.txt` and start command `uvicorn app.main:app --host 0.0.0.0 --port $PORT`. Verify the resulting `/health` endpoint before configuring the Android app.

## Responsible data collection

- Use only public pages and data sources you are permitted to access.
- Respect `robots.txt`, site terms, rate limits, copyright, and applicable privacy law.
- The scraper blocks localhost/private/reserved IP targets and does not follow page redirects.
- Never enter passwords, session cookies, API keys, or private account URLs into the scraper.
- Product opportunity scores are heuristic signals, not guarantees of demand, profit, or export success.

## Current operational scope and next milestones

- **v0.5.0 implementation:** API Anything local research runner + currency-safe aggregation endpoint + Android JSON report import; offline records for products/stock, orders, expenses and tasks; six best-effort India marketplace adapters; unit economics and business launch suggestions.
- Offline operations records are stored only on the Android device and are not cloud-synced, backed by a server database, or automatically connected to marketplace orders.
- Next: export/import backups, link order items to stock movements, supplier/product comparison history, saved research projects and CSV reports.
- Later: optional sync with a configured persistent database, permission-aware marketplace connectors and stronger evidence-backed recommendations. No sales, demand or profit guarantees.

## API Anything research connector

MarketMitra can optionally enrich its own India-focused public listing research with read-only operations from [goodnight000/api-anything](https://github.com/goodnight000/api-anything). API Anything is a **local CLI/MCP tool**, not a hosted scraping API; this connector runs on your computer and sends only normalized research results to MarketMitra's aggregation endpoint.

### What it can collect

- **Amazon.com**: product title, ASIN and USD price from its bundled `amazon.search` operation.
- **YouTube**: video title, channel and visible view-count text from `youtube.search`.
- **Hacker News**: technology/business discussion results from `hacker-news.search`; relevance depends on the query.
- The existing MarketMitra backend separately attempts India-focused Meesho, Flipkart, Amazon India, Myntra, Snapdeal and JioMart public listing extraction. These sources can be blocked or unavailable.

The runner deliberately uses API Anything's `--max-tier 1` (plain HTTP only). It does not log into websites, use browser fallback, bypass CAPTCHA, or perform write operations. API Anything operations are limited to the features that have been learned and verified. **Amazon.com USD prices are never mixed with Indian INR prices**, and video views are treated as attention signals—not sales.

### Install and run

Requirements: Node.js 22.13+ and Git. A Chromium browser is only needed if you later teach additional site operations. API Anything is MIT-licensed and does not require a paid AI/API key for the included read operations. Its package is not yet published to npm.

From Git Bash, WSL or another POSIX shell:

```bash
git clone https://github.com/goodnight000/api-anything.git
cd api-anything
npm ci --no-audit --no-fund
npm install -g "$(npm pack --silent)" --no-audit --no-fund
api-anything sites
api-anything call amazon search "k=cotton kurti" --max-tier 1
cd ../MarketMitra
node tools/api-anything/research.mjs "cotton kurti"
```

The runner writes `marketmitra-api-anything-report.json` locally and submits the normalized results to `POST /api/v1/research/aggregate`. Override the API with `API_URL=http://127.0.0.1:8000` for local development. To view the extra findings inside the Android app, transfer the JSON report to your phone and tap **Import API Anything research report (JSON)**. The API response is stateless; the report file is the local saved copy. Do not upload cookies, tokens, private account data or secrets.

### Teach more operations safely

API Anything only knows its bundled or explicitly taught operations. To add a new site, first read its terms and access rules, inspect the public page with the API Anything skill, propose a read-only operation, and verify it with an input that was not used during learning. Do not teach operations that bypass access controls, paywalls, CAPTCHAs, or a site's restrictions. A site being listed in the MarketMitra research hub does not mean it has an API Anything adapter.

```bash
# Optional local checks
node --check tools/api-anything/research.mjs
cd backend && PYTHONPATH=. pytest -q
```

## License

MIT. See [LICENSE](LICENSE).


## CI troubleshooting

The repository workflow runs backend tests with `PYTHONPATH=.` and builds the Android debug APK using the Android SDK on GitHub-hosted runners. Check the [Actions tab](https://github.com/cryding/MarketMitra/actions) for the latest run and artifact; an earlier failed run is not evidence of a successful current build.


## Advanced research and business analysis

- \`POST /api/v1/research/analyze\` combines the live public research results with source coverage, observed price range/median, an evidence-coverage score, next-step recommendations, and optional per-order unit economics.
- Example request:

\`\`\`json
{
  "q": "cotton kurti",
  "unit_cost_inr": 180,
  "selling_price_inr": 499,
  "packaging_cost_inr": 15,
  "shipping_cost_inr": 45,
  "marketplace_fee_pct": 12,
  "return_loss_pct": 5,
  "ads_cost_per_order_inr": 20
}
\`\`\`

- The endpoint is deterministic and uses no paid AI API. The evidence score measures how complete the collected research is; it is not a demand forecast or a guarantee of profit.
- Leave \`unit_cost_inr\` out to omit profit calculations. If you provide unit cost but omit selling price, the observed marketplace median can be used as a clearly labelled scenario.


## Empire business workspace

- \`POST /api/v1/empire/workspace\` combines the live research endpoint, evidence score, observed listing-price comparison, competition activity signal, supplier-directory search links, supplier due-diligence checklist, budget allocation and an optional monthly unit-economics scenario.
- The response also includes a grouped `research_hub.links` list for demand signals (Google Trends/Search/YouTube), marketplace price checks, supplier discovery and official setup portals (Udyam, GST, FSSAI where applicable, IP India and ONDC).
- Marketplace adapters include Meesho, Flipkart, Amazon India, Myntra, Snapdeal and JioMart. They run concurrently with bounded robots.txt and page-fetch timeouts; sources that cannot be checked are skipped rather than bypassed.
- It does not invent supplier records: supplier links open third-party search pages and require independent verification. External research-hub links are not scraped evidence.
- Example request:

\`\`\`json
{
  "q": "cotton kurti",
  "unit_cost_inr": 200,
  "selling_price_inr": 499,
  "packaging_cost_inr": 15,
  "shipping_cost_inr": 45,
  "marketplace_fee_pct": 12,
  "return_loss_pct": 5,
  "ads_cost_per_order_inr": 20,
  "startup_budget_inr": 5000,
  "monthly_fixed_costs_inr": 1000,
  "target_monthly_orders": 30,
  "location": "Itahar, West Bengal",
  "sourcing_type": "wholesale"
}
\`\`\`

- Competition is a listing-activity signal, not verified seller counts or proof of demand. Financial outputs are scenarios, not guarantees.
- The app and backend are intended to run with free/open-source code; hosting free-tier limits and marketplace access can change.
