# MarketMitra

**Universal Market Research & Data Intelligence** — an open-source Android + Python project for early-stage business research, sourcing, unit economics and launch planning.

> **Current development direction:** MarketMitra is evolving into a ground-level business operating toolkit. Sample product ideas remain labelled sample data. Six marketplace adapters attempt best-effort extraction only when public access and `robots.txt` permit it; individual sites may block requests or change markup. Research-hub URLs are manual discovery links, not scraped evidence or verified supplier recommendations. No login bypass, CAPTCHA bypass, paywall bypass, or private-data collection.

## What is in this repository

- `android/`: Kotlin + Jetpack Compose Android client.
- `backend/`: FastAPI service with public-page extraction, six best-effort marketplace adapters, transparent price/unit-economics analysis, supplier discovery, budget planning and a free research/official-setup link hub.
- `.github/workflows/ci.yml`: Python tests and Android debug APK build/artifact.
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

## Roadmap

1. **v0.4.0:** modernized Android hero, grouped Business Toolkit, six best-effort marketplace adapters, free demand/price research links, supplier discovery and official business setup portals.
2. Add stronger evidence-backed scoring, structured source coverage and actionable validation experiments without confusing search interest/listings with actual sales.
3. Add saved projects, offline reports, CSV export and product/supplier comparison history.
4. Add more open-data or official data connectors only where access terms, reliability and free-tier limits are clear.

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
