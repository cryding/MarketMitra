# MarketMitra

**Universal Market Research & Data Intelligence** — an open-source Android + Python project for early-stage e-commerce research, competitor-page extraction, and business opportunity analysis.

> **Milestone 1 status:** starter implementation is being built. Product ideas returned by the demo endpoint are clearly labelled sample data, not live marketplace rankings. A scraper only fetches public pages when the host is publicly routable and the page is allowed by `robots.txt`. No login bypass, CAPTCHA bypass, paywall bypass, or private-data collection.

## What is in this repository

- `android/`: Kotlin + Jetpack Compose Android client.
- `backend/`: FastAPI service with health, sample product research, and public HTML page extraction endpoints.
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

1. **Milestone 1:** Android dashboard, sample e-commerce ideas, API connection, guarded public-page metadata extraction, tests, CI APK artifact.
2. Marketplace-specific adapters only where terms and access methods permit them.
3. Competitor price history, CSV export, supplier research, export/HS-code datasets.
4. Offline saved reports and transparent opportunity scoring.

## License

MIT. See [LICENSE](LICENSE).
