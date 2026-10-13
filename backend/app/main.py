from __future__ import annotations

import ipaddress
import json
from concurrent.futures import ThreadPoolExecutor
import math
import re
import socket
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
import xml.etree.ElementTree as ET
from typing import Any, Literal

from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field, HttpUrl

app = FastAPI(
    title="MarketMitra API",
    description="Open-source market research MVP. Demo ideas are sample data, not live marketplace rankings.",
    version="0.1.0",
)


@app.middleware("http")
async def normalize_vercel_rewrite_path(request, call_next):
    """Strip the serverless-function prefix if Vercel forwards it to ASGI."""
    path = request.scope.get("path", "/")
    prefix = "/api/index"
    if path == prefix:
        request.scope["path"] = "/"
        request.scope["raw_path"] = b"/"
    elif path.startswith(prefix + "/"):
        normalized = path[len(prefix):]
        request.scope["path"] = normalized
        request.scope["raw_path"] = normalized.encode("utf-8")
    return await call_next(request)

SAMPLE_PRODUCTS: list[dict[str, Any]] = [
    {"name": "Everyday cotton kurti", "category": "Women fashion", "market": "India", "price_band_inr": "₹299–₹699", "signals": ["lightweight shipping", "many style variants"], "source_type": "sample", "opportunity_score": 72},
    {"name": "Reusable kitchen storage set", "category": "Home & kitchen", "market": "India", "price_band_inr": "₹199–₹499", "signals": ["bundle potential", "repeat-use household item"], "source_type": "sample", "opportunity_score": 68},
    {"name": "Jute gift and packaging bags", "category": "Sustainable goods", "market": "India + export research", "price_band_inr": "₹80–₹350", "signals": ["custom branding potential", "B2B gifting"], "source_type": "sample", "opportunity_score": 75},
    {"name": "Fashion jewellery combo", "category": "Accessories", "market": "India", "price_band_inr": "₹149–₹399", "signals": ["compact parcel", "bundle pricing"], "source_type": "sample", "opportunity_score": 64},
    {"name": "Incense gift pack", "category": "Home fragrance", "market": "India + export research", "price_band_inr": "₹99–₹299", "signals": ["giftable packaging", "brand differentiation"], "source_type": "sample", "opportunity_score": 61},
]


class ScrapeRequest(BaseModel):
    url: HttpUrl = Field(description="Public product or company page URL; no login-only pages.")
    max_bytes: int = Field(default=1_000_000, ge=10_000, le=2_000_000)


def _validate_public_target(url: str) -> urllib.parse.ParseResult:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise HTTPException(status_code=400, detail="Only http/https URLs with a hostname are supported.")
    if parsed.username or parsed.password:
        raise HTTPException(status_code=400, detail="URLs containing credentials are not allowed.")
    host = parsed.hostname.rstrip(".").lower()
    if host in {"localhost", "localhost.localdomain"} or host.endswith(".localhost") or host.endswith(".local"):
        raise HTTPException(status_code=400, detail="Local/private hosts are not allowed.")
    try:
        addresses = {entry[4][0] for entry in socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM)}
    except socket.gaierror:
        raise HTTPException(status_code=400, detail="The host could not be resolved.") from None
    if not addresses:
        raise HTTPException(status_code=400, detail="The host did not resolve to a public IP address.")
    for address in addresses:
        ip = ipaddress.ip_address(address.split("%", 1)[0])
        if not ip.is_global:
            raise HTTPException(status_code=400, detail="Private, loopback, link-local, and reserved IP targets are blocked.")
    return parsed


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


def _robots_allows(url: str, user_agent: str) -> bool:
    parsed = urllib.parse.urlparse(url)
    robots_url = urllib.parse.urlunparse((parsed.scheme, parsed.netloc, "/robots.txt", "", "", ""))
    parser = urllib.robotparser.RobotFileParser()
    parser.set_url(robots_url)
    try:
        request = urllib.request.Request(robots_url, headers={"User-Agent": user_agent})
        with urllib.request.urlopen(request, timeout=3) as response:
            raw = response.read(256_000).decode("utf-8", errors="replace")
        parser.parse(raw.splitlines())
    except (OSError, urllib.error.URLError, ValueError, TimeoutError):
        # Fail closed if robots policy cannot be checked; never bypass access controls.
        return False
    return parser.can_fetch(user_agent, url)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "marketmitra-api", "version": "0.1.0"}


@app.get("/api/v1/research/products")
def research_products(q: str = "", category: str = "") -> dict[str, Any]:
    """Return clearly labelled starter sample ideas; this is not live marketplace data."""
    query = q.strip().casefold()
    cat = category.strip().casefold()
    items = [
        item for item in SAMPLE_PRODUCTS
        if (not query or query in item["name"].casefold() or query in item["category"].casefold() or any(query in signal.casefold() for signal in item["signals"]))
        and (not cat or cat in item["category"].casefold())
    ]
    return {"mode": "sample", "live_data": False, "count": len(items), "items": items}


@app.post("/api/v1/scrape")
def scrape_public_page(request: ScrapeRequest) -> dict[str, Any]:
    """Extract basic metadata from a permitted public HTML page without following redirects."""
    url = str(request.url)
    parsed = _validate_public_target(url)
    user_agent = "MarketMitraResearchBot/0.1 (+https://github.com/cryding/MarketMitra)"
    if not _robots_allows(url, user_agent):
        raise HTTPException(status_code=403, detail="robots.txt disallows this page or its policy could not be checked.")

    req = urllib.request.Request(url, headers={"User-Agent": user_agent, "Accept": "text/html,application/xhtml+xml"})
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        with opener.open(req, timeout=10) as response:
            content_type = response.headers.get("Content-Type", "").lower()
            if "text/html" not in content_type and "application/xhtml+xml" not in content_type:
                raise HTTPException(status_code=415, detail="Only HTML/XHTML pages are supported in this milestone.")
            payload = response.read(request.max_bytes + 1)
            if len(payload) > request.max_bytes:
                raise HTTPException(status_code=413, detail="Page is larger than the configured extraction limit.")
            charset = response.headers.get_content_charset() or "utf-8"
            html = payload.decode(charset, errors="replace")
    except urllib.error.HTTPError as exc:
        if 300 <= exc.code < 400:
            raise HTTPException(status_code=400, detail="Redirects are not followed; submit the final public page URL.") from None
        raise HTTPException(status_code=502, detail=f"Source website returned HTTP {exc.code}.") from None
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise HTTPException(status_code=502, detail="Could not fetch the public page within the timeout.") from exc

    soup = BeautifulSoup(html, "html.parser")
    title = soup.title.get_text(" ", strip=True) if soup.title else ""
    description_tag = soup.find("meta", attrs={"name": "description"})
    description = str(description_tag.get("content", "")).strip() if description_tag else ""
    canonical_tag = soup.find("link", rel="canonical")
    canonical = str(canonical_tag.get("href", "")).strip() if canonical_tag else url
    names = [tag.get_text(" ", strip=True) for tag in soup.select('[itemprop="name"]')[:20]]
    prices = [tag.get("content") or tag.get_text(" ", strip=True) for tag in soup.select('[itemprop="price"]')[:20]]
    links = []
    for tag in soup.find_all("a", href=True)[:50]:
        href = urllib.parse.urljoin(url, str(tag["href"]))
        target = urllib.parse.urlparse(href)
        if target.scheme in {"http", "https"} and target.hostname == parsed.hostname:
            links.append({"text": tag.get_text(" ", strip=True)[:120], "url": href})
    return {
        "source_url": url,
        "canonical_url": canonical,
        "title": title[:300],
        "description": description[:1000],
        "product_name_candidates": names,
        "price_candidates": prices,
        "same_host_links": links,
        "extraction_note": "Page metadata only; verify all extracted fields against the source. This is not a guaranteed product parser.",
    }


# Marketplace adapters are best-effort. Each live source is fetched only when its
# robots.txt permits this bot; blocked/unreachable sources are reported, not faked.
MARKETPLACE_SEARCHES = {
    "Meesho": lambda q: "https://www.meesho.com/search?q=" + urllib.parse.quote_plus(q),
    "Flipkart": lambda q: "https://www.flipkart.com/search?q=" + urllib.parse.quote_plus(q),
    "Amazon India": lambda q: "https://www.amazon.in/s?k=" + urllib.parse.quote_plus(q),
    "Myntra": lambda q: "https://www.myntra.com/search?q=" + urllib.parse.quote_plus(q),
    "Snapdeal": lambda q: "https://www.snapdeal.com/search?keyword=" + urllib.parse.quote_plus(q),
    "JioMart": lambda q: "https://www.jiomart.com/search/" + urllib.parse.quote_plus(q),
}

_PRICE_RE = re.compile(r"(?:₹|Rs\\.?\\s*)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", re.IGNORECASE)


def _jsonld_products(soup: BeautifulSoup, source_name: str, source_url: str) -> list[dict[str, Any]]:
    found: list[dict[str, Any]] = []

    def visit(node: Any) -> None:
        if isinstance(node, list):
            for child in node:
                visit(child)
            return
        if not isinstance(node, dict):
            return
        node_type = node.get("@type", "")
        types = node_type if isinstance(node_type, list) else [node_type]
        if any(str(t).casefold() == "product" for t in types):
            name = str(node.get("name", "")).strip()
            offers = node.get("offers", {})
            if isinstance(offers, list):
                offers = offers[0] if offers else {}
            price = ""
            if isinstance(offers, dict) and offers.get("price") is not None:
                currency = str(offers.get("priceCurrency", "INR"))
                symbol = "₹" if currency == "INR" else currency + " "
                price = symbol + str(offers.get("price"))
            product_url = urllib.parse.urljoin(source_url, str(node.get("url", source_url)))
            if name:
                found.append({"name": name[:180], "price": price, "url": product_url, "source": source_name})
        for value in node.values():
            if isinstance(value, (dict, list)):
                visit(value)

    for tag in soup.find_all("script", attrs={"type": "application/ld+json"})[:30]:
        raw = tag.string or tag.get_text()
        if not raw:
            continue
        try:
            visit(json.loads(raw))
        except (json.JSONDecodeError, TypeError):
            continue
    return found


def _fetch_marketplace_search(source_name: str, url: str) -> dict[str, Any]:
    parsed = _validate_public_target(url)
    user_agent = "MarketMitraResearchBot/0.1 (+https://github.com/cryding/MarketMitra)"
    if not _robots_allows(url, user_agent):
        return {"source": source_name, "status": "blocked_by_robots_or_policy_unavailable", "items": []}

    request = urllib.request.Request(
        url,
        headers={"User-Agent": user_agent, "Accept": "text/html,application/xhtml+xml"},
    )
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        with opener.open(request, timeout=8) as response:
            content_type = response.headers.get("Content-Type", "").lower()
            if "text/html" not in content_type and "application/xhtml+xml" not in content_type:
                return {"source": source_name, "status": "not_html", "items": []}
            raw = response.read(1_000_001)
            if len(raw) > 1_000_000:
                return {"source": source_name, "status": "page_too_large", "items": []}
            html = raw.decode(response.headers.get_content_charset() or "utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        state = "redirect_not_followed" if 300 <= exc.code < 400 else f"http_{exc.code}"
        return {"source": source_name, "status": state, "items": []}
    except (urllib.error.URLError, TimeoutError, OSError):
        return {"source": source_name, "status": "fetch_failed", "items": []}

    soup = BeautifulSoup(html, "html.parser")
    items = _jsonld_products(soup, source_name, url)
    if not items:
        # Generic fallback for public result cards; source pages may change their markup.
        for anchor in soup.find_all("a", href=True):
            name = anchor.get_text(" ", strip=True)
            if len(name) < 12 or len(name) > 180:
                continue
            href = urllib.parse.urljoin(url, str(anchor["href"]))
            target = urllib.parse.urlparse(href)
            if target.scheme not in {"http", "https"} or target.hostname != parsed.hostname:
                continue
            parent_text = anchor.parent.get_text(" ", strip=True)[:700] if anchor.parent else name
            price_match = _PRICE_RE.search(parent_text)
            price = ("₹" + price_match.group(1)) if price_match else ""
            if any(existing["url"] == href for existing in items):
                continue
            items.append({"name": name, "price": price, "url": href, "source": source_name})
            if len(items) >= 20:
                break
    # De-duplicate repeated structured data, keep the first source occurrence.
    unique: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for item in items:
        key = (item["name"].casefold(), item["url"])
        if key not in seen:
            seen.add(key)
            unique.append(item)
    return {"source": source_name, "status": "ok", "items": unique[:20]}


def _suggest_opportunities(query: str, items: list[dict[str, Any]]) -> list[dict[str, str]]:
    if not items:
        return []
    with_price = sum(1 for item in items if item.get("price"))
    sources = sorted({item.get("source", "") for item in items if item.get("source")})
    signals = []
    if len(sources) > 1:
        signals.append("Products found across multiple marketplaces; compare seller, shipping and reviews.")
    if with_price:
        signals.append(f"{with_price} of {len(items)} extracted results included a price; verify prices on the source page.")
    if len(items) >= 5:
        signals.append("Several public results were discoverable for this query; validate demand with independent trend data.")
    if not signals:
        signals.append("Public results were found, but price fields were not reliably available.")
    return [{"title": f"Research opportunity: {query}", "reason": signal, "confidence": "early_signal"} for signal in signals]


@app.get("/api/v1/research/live")
def research_live(q: str = Query(default="", max_length=100)) -> dict[str, Any]:
    """Search public marketplace pages live, where robots.txt and access policy permit."""
    query = q.strip()
    if len(query) < 2:
        raise HTTPException(status_code=422, detail="Enter at least two characters to research a product.")
    results: list[dict[str, Any]] = []
    source_status: list[dict[str, str]] = []
    # Fetch permitted sources concurrently so adding adapters does not multiply latency.
    source_requests = [(name, build_url(query)) for name, build_url in MARKETPLACE_SEARCHES.items()]
    with ThreadPoolExecutor(max_workers=min(6, len(source_requests))) as pool:
        source_results = list(pool.map(lambda pair: _fetch_marketplace_search(pair[0], pair[1]), source_requests))
    for status in source_results:
        source_status.append({
            "source": status["source"],
            "status": status["status"],
            "items_found": len(status["items"]),
        })
        results.extend(status["items"])
    # Prefer results that contain query terms and have explicit prices. If relevant
    # results exist, drop generic navigation/category links that would inflate counts.
    query_terms = [term.casefold() for term in query.split() if len(term) > 1]
    for item in results:
        name = item.get("name", "").casefold()
        item["relevance"] = sum(1 for term in query_terms if term in name)
        item["has_price"] = bool(item.get("price"))
    if any(item["relevance"] > 0 for item in results):
        results = [item for item in results if item["relevance"] > 0]
    results.sort(key=lambda item: (item["relevance"], item["has_price"]), reverse=True)
    unique_results: list[dict[str, Any]] = []
    seen_names: set[str] = set()
    for item in results:
        key = re.sub(r"\\W+", " ", item["name"].casefold()).strip()
        if key and key not in seen_names:
            seen_names.add(key)
            unique_results.append(item)
    return {
        "mode": "live_public_scrape",
        "live_data": True,
        "query": query,
        "fetched_at_utc": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        "count": len(unique_results),
        "items": unique_results[:40],
        "suggestions": _suggest_opportunities(query, unique_results[:40]),
        "sources": source_status,
        "note": "Live results depend on each marketplace's robots.txt, access rules, page markup and network availability. Missing results do not mean no demand. Verify prices, stock and seller claims on the source page.",
    }


class MarketAnalysisRequest(BaseModel):
    """Inputs for transparent unit economics; no account or API key is required."""
    q: str = Field(min_length=2, max_length=100)
    unit_cost_inr: float | None = Field(default=None, ge=0, le=10_000_000)
    selling_price_inr: float | None = Field(default=None, gt=0, le=10_000_000)
    packaging_cost_inr: float = Field(default=0, ge=0, le=1_000_000)
    shipping_cost_inr: float = Field(default=0, ge=0, le=1_000_000)
    marketplace_fee_pct: float = Field(default=0, ge=0, le=100)
    return_loss_pct: float = Field(default=0, ge=0, le=100)
    ads_cost_per_order_inr: float = Field(default=0, ge=0, le=1_000_000)


def _price_value(raw: str) -> float | None:
    """Parse a conservative numeric price from a visible INR price string."""
    if not raw:
        return None
    match = re.search(r"(?:₹|Rs\.?\s*)([0-9][0-9,]*(?:\.[0-9]{1,2})?)", raw, re.IGNORECASE)
    if not match:
        return None
    try:
        value = float(match.group(1).replace(",", ""))
    except ValueError:
        return None
    return value if value > 0 else None


def _money(value: float) -> float:
    return round(value, 2)


@app.post("/api/v1/research/analyze")
def analyze_market(request: MarketAnalysisRequest) -> dict[str, Any]:
    """Combine live public research with explainable coverage scoring and optional unit economics."""
    query = request.q.strip()
    if len(query) < 2:
        raise HTTPException(status_code=422, detail="Enter at least two characters to analyze a market.")

    research = research_live(q=query)
    items = research.get("items", [])
    sources = research.get("sources", [])
    prices = sorted(value for item in items if (value := _price_value(str(item.get("price", "")))) is not None)
    source_count = sum(
        1 for source in sources
        if source.get("status") == "ok" and source.get("items_found", 1) > 0
    )
    item_count = len(items)
    priced_count = len(prices)
    price_coverage = priced_count / item_count if item_count else 0.0

    # This score measures evidence quality only. It is deliberately not a demand or profit prediction.
    listing_component = min(item_count / 20, 1.0) * 35
    source_component = min(source_count / 3, 1.0) * 30
    price_component = price_coverage * 25
    spread_component = 10 if len(prices) >= 3 else 0
    evidence_score = round(listing_component + source_component + price_component + spread_component)
    confidence = "low"
    if evidence_score >= 70 and source_count >= 2 and priced_count >= 3:
        confidence = "medium"
    if evidence_score >= 85 and source_count >= 3 and priced_count >= 8:
        confidence = "higher, but still not proof of demand"

    price_summary: dict[str, Any] | None = None
    if prices:
        middle = len(prices) // 2
        median_price = prices[middle] if len(prices) % 2 else (prices[middle - 1] + prices[middle]) / 2
        price_summary = {
            "currency": "INR",
            "min": _money(prices[0]),
            "median": _money(median_price),
            "max": _money(prices[-1]),
            "priced_listing_count": priced_count,
            "listing_count": item_count,
            "coverage_pct": round(price_coverage * 100, 1),
            "note": "Observed public listing prices only; shipping, variants, discounts, stock and authenticity may differ."
        }

    recommendations: list[dict[str, str]] = []
    if item_count == 0:
        recommendations.append({
            "priority": "high",
            "action": "Do not conclude that demand is absent. Retry with simpler product keywords and collect permitted results from other sources.",
            "why": "No usable listings were extracted; access restrictions or page changes can cause this."
        })
    else:
        if source_count < 2:
            recommendations.append({
                "priority": "high",
                "action": "Validate the same product on at least two independent sources before committing money.",
                "why": "Current source coverage is limited."
            })
        if priced_count < 3:
            recommendations.append({
                "priority": "high",
                "action": "Manually verify at least three comparable listings, including shipping and variant details.",
                "why": "There are too few machine-readable prices for a reliable comparison."
            })
        else:
            recommendations.append({
                "priority": "high",
                "action": "Compare like-for-like products by material, size, quality, shipping and return terms; do not choose by headline price alone.",
                "why": "Listing prices are not necessarily comparable landed costs."
            })
        recommendations.append({
            "priority": "medium",
            "action": "Validate demand using independent trend signals, customer questions, review patterns and a small test batch before scaling.",
            "why": "Marketplace listing counts and prices alone cannot prove sales volume or demand."
        })

    unit_economics: dict[str, Any] | None = None
    if request.unit_cost_inr is not None:
        scenario_price = request.selling_price_inr
        price_basis = "user_supplied"
        if scenario_price is None and price_summary is not None:
            scenario_price = float(price_summary["median"])
            price_basis = "observed_market_median_scenario"
        if scenario_price is not None:
            percentage_cost = (request.marketplace_fee_pct + request.return_loss_pct) / 100
            fixed_cost = (
                request.unit_cost_inr
                + request.packaging_cost_inr
                + request.shipping_cost_inr
                + request.ads_cost_per_order_inr
            )
            contribution = scenario_price * (1 - percentage_cost)
            net_profit = contribution - fixed_cost
            margin_pct = (net_profit / scenario_price * 100) if scenario_price else None
            break_even = fixed_cost / (1 - percentage_cost) if percentage_cost < 1 else None
            unit_economics = {
                "currency": "INR",
                "scenario_selling_price": _money(scenario_price),
                "price_basis": price_basis,
                "fixed_per_order_costs": _money(fixed_cost),
                "percentage_cost_pct": _money(percentage_cost * 100),
                "estimated_net_profit_per_order": _money(net_profit),
                "estimated_net_margin_pct": _money(margin_pct) if margin_pct is not None else None,
                "break_even_selling_price": _money(break_even) if break_even is not None else None,
                "status": "positive_estimate" if net_profit > 0 else "loss_or_no_margin",
                "note": "Scenario estimate only. Include all taxes, fees, returns, discounts and overhead before making a decision."
            }
            if net_profit <= 0:
                recommendations.insert(0, {
                    "priority": "high",
                    "action": "Do not scale this price/cost combination; revise sourcing, costs or pricing and recalculate.",
                    "why": "The current unit-economics scenario has no positive estimated contribution."
                })
        else:
            unit_economics = {
                "status": "needs_selling_price",
                "note": "Provide a selling_price_inr or collect comparable public prices to calculate a scenario."
            }

    return {
        "mode": "live_research_analysis",
        "live_data": bool(research.get("live_data")),
        "query": query,
        "evidence_score": evidence_score,
        "score_meaning": "Evidence coverage score, not demand, guaranteed profit or a promise of success.",
        "confidence": confidence,
        "market_snapshot": {
            "listing_count": item_count,
            "sources_attempted": len(sources),
            "sources_with_results": source_count,
            "source_status": sources,
            "price_summary": price_summary,
        },
        "unit_economics": unit_economics,
        "recommendations": recommendations,
        "items": items[:40],
        "limitations": [
            "A listing is not proof that a product sells.",
            "Some marketplaces may block automated access or expose incomplete prices.",
            "Validate demand and supplier quality independently before investing."
        ],
        "fetched_at_utc": research.get("fetched_at_utc"),
    }


class APIAnythingSource(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    ok: bool
    status: str = Field(max_length=80)
    count: int = Field(default=0, ge=0, le=10000)
    tier: int | None = Field(default=None, ge=1, le=3)
    latency_ms: int | None = Field(default=None, ge=0, le=600000)
    reason: str | None = Field(default=None, max_length=500)


class APIAnythingItem(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    source: str = Field(min_length=1, max_length=100)
    kind: Literal["product", "video", "article", "other"] = "other"
    url: str | None = Field(default=None, max_length=2000)
    price: str | None = Field(default=None, max_length=80)
    currency: str = Field(default="UNKNOWN", max_length=8)
    metadata: dict[str, Any] = Field(default_factory=dict)


class APIAnythingAggregateRequest(BaseModel):
    query: str = Field(min_length=2, max_length=100)
    sources: list[APIAnythingSource] = Field(default_factory=list, max_length=30)
    items: list[APIAnythingItem] = Field(default_factory=list, max_length=300)
    collected_at_utc: str | None = Field(default=None, max_length=80)


def _external_price_value(raw: str | None) -> float | None:
    if not raw:
        return None
    match = re.search(r"([0-9][0-9,]*(?:\.[0-9]{1,2})?)", raw)
    if not match:
        return None
    try:
        value = float(match.group(1).replace(",", ""))
    except ValueError:
        return None
    return value if value > 0 else None


@app.post("/api/v1/research/aggregate")
def aggregate_external_research(request: APIAnythingAggregateRequest) -> dict[str, Any]:
    """Aggregate local API Anything results without mixing currencies or overstating demand."""
    query = request.query.strip()
    items = [item.model_dump() for item in request.items]
    source_successes = [source for source in request.sources if source.ok and source.count > 0]
    currency_prices: dict[str, list[float]] = {}
    for item in items:
        value = _external_price_value(item.get("price"))
        currency = str(item.get("currency", "UNKNOWN")).upper()
        if value is not None and currency in {"INR", "USD", "GBP", "EUR"}:
            currency_prices.setdefault(currency, []).append(value)

    price_summaries: dict[str, dict[str, Any]] = {}
    for currency, values in currency_prices.items():
        values.sort()
        middle = len(values) // 2
        median = values[middle] if len(values) % 2 else (values[middle - 1] + values[middle]) / 2
        price_summaries[currency] = {
            "min": _money(values[0]),
            "median": _money(median),
            "max": _money(values[-1]),
            "count": len(values),
            "note": "Raw observed prices from the source. No currency conversion or landed-cost adjustment was applied."
        }

    kind_counts: dict[str, int] = {}
    for item in items:
        kind = str(item.get("kind", "other"))
        kind_counts[kind] = kind_counts.get(kind, 0) + 1

    recommendations: list[dict[str, str]] = []
    if not source_successes:
        recommendations.append({
            "priority": "high",
            "action": "No API Anything source returned usable records. Check the installed operations and retry a permitted source; do not interpret this as zero demand.",
            "why": "Blocked, stale, unauthenticated or unconfigured operations are not evidence of an empty market."
        })
    if len(source_successes) < 2:
        recommendations.append({
            "priority": "high",
            "action": "Cross-check the query on at least two independent sources before investing.",
            "why": "A single source can be incomplete, biased or temporarily unavailable."
        })
    if items and not any(item.get("kind") == "product" for item in items):
        recommendations.append({
            "priority": "medium",
            "action": "Collect comparable product listings with explicit currency and delivered price before estimating margins.",
            "why": "Content/video/article results can indicate customer conversation but are not product prices or sales."
        })
    if "USD" in price_summaries and "INR" not in price_summaries:
        recommendations.append({
            "priority": "high",
            "action": "Do not use USD listing prices as Indian selling-price evidence. Collect India-specific prices or calculate a fully landed import cost separately.",
            "why": "This report intentionally keeps currencies separate."
        })
    if kind_counts.get("video", 0):
        recommendations.append({
            "priority": "medium",
            "action": "Review video titles, channels and visible view counts to learn customer vocabulary and content angles; validate purchase intent with customer interviews or a small test.",
            "why": "Video views are attention signals, not verified product demand or sales."
        })
    if not recommendations:
        recommendations.append({
            "priority": "medium",
            "action": "Verify source pages, product variants, stock and shipping, then combine this report with customer feedback and a full unit-economics calculation.",
            "why": "Aggregated public results are exploratory evidence, not a profit guarantee."
        })

    return {
        "mode": "api_anything_research_aggregate",
        "query": query,
        "collected_at_utc": request.collected_at_utc,
        "source_summary": {
            "attempted": len(request.sources),
            "with_results": len(source_successes),
            "items_received": len(items),
            "kind_counts": kind_counts,
            "sources": [source.model_dump() for source in request.sources],
        },
        "price_summaries_by_currency": price_summaries,
        "items": items,
        "recommendations": recommendations,
        "limitations": [
            "API Anything is a local tool; this endpoint aggregates results explicitly submitted by the local research runner.",
            "An operation must be taught and verified before use; not every feature of a website is covered.",
            "A blocked or empty result is not proof of absent demand.",
            "USD, INR and other currencies are never combined in a single price summary.",
            "Respect each website's terms, rate limits, privacy rules and access controls."
        ]
    }


class EmpireWorkspaceRequest(MarketAnalysisRequest):
    """One-query workspace for market, competition, sourcing and budget planning."""
    startup_budget_inr: float = Field(default=5000, gt=0, le=100_000_000)
    monthly_fixed_costs_inr: float = Field(default=0, ge=0, le=100_000_000)
    target_monthly_orders: int = Field(default=30, ge=1, le=1_000_000)
    location: str = Field(default="India", min_length=2, max_length=100)
    sourcing_type: str = Field(default="wholesale", max_length=40)


def _research_hub_links(query: str, location: str, sourcing_type: str) -> list[dict[str, str]]:
    """Build free research and official business-setup entry points; these are links, not scraped evidence."""
    q = urllib.parse.quote_plus(query)
    local = urllib.parse.quote_plus(query + " supplier " + location)
    supplier = urllib.parse.quote_plus(query + " " + sourcing_type + " supplier")
    return [
        {"name": "Google Trends", "category": "Demand signals", "description": "Compare search-interest patterns; not sales data.", "url": "https://trends.google.com/trends/explore?geo=IN&q=" + q},
        {"name": "Google Search", "category": "Demand signals", "description": "Look for customer questions, price points and unmet needs.", "url": "https://www.google.com/search?q=" + q},
        {"name": "YouTube Research", "category": "Demand signals", "description": "Find product demos, reviews and customer language.", "url": "https://www.youtube.com/results?search_query=" + q},
        {"name": "Google Shopping", "category": "Price comparison", "description": "Manually compare similar products and listed prices.", "url": "https://www.google.com/search?tbm=shop&q=" + q},
        {"name": "Amazon India", "category": "Marketplaces", "description": "Manual listing and price verification.", "url": "https://www.amazon.in/s?k=" + q},
        {"name": "Flipkart", "category": "Marketplaces", "description": "Manual listing and price verification.", "url": "https://www.flipkart.com/search?q=" + q},
        {"name": "Meesho", "category": "Marketplaces", "description": "Manual listing and price verification.", "url": "https://www.meesho.com/search?q=" + q},
        {"name": "Myntra", "category": "Marketplaces", "description": "Fashion listing and positioning research.", "url": "https://www.myntra.com/search?q=" + q},
        {"name": "Snapdeal", "category": "Marketplaces", "description": "Manual assortment and price research.", "url": "https://www.snapdeal.com/search?keyword=" + q},
        {"name": "JioMart", "category": "Marketplaces", "description": "Manual assortment and price research.", "url": "https://www.jiomart.com/search/" + q},
        {"name": "IndiaMART", "category": "Supplier sourcing", "description": "Discover suppliers; verify identity, samples and terms.", "url": "https://dir.indiamart.com/search.mp?ss=" + supplier},
        {"name": "TradeIndia", "category": "Supplier sourcing", "description": "Discover manufacturers and wholesalers.", "url": "https://www.tradeindia.com/search.html?keyword=" + supplier},
        {"name": "ExportersIndia", "category": "Supplier sourcing", "description": "Discover suppliers and manufacturers.", "url": "https://www.exportersindia.com/search.php?srch_catg_ty=prod&term=" + supplier},
        {"name": "Nearby suppliers", "category": "Supplier sourcing", "description": "Search local suppliers around the chosen location.", "url": "https://www.google.com/maps/search/" + local},
        {"name": "Udyam Registration", "category": "Official setup", "description": "Official MSME registration portal; check current eligibility and requirements.", "url": "https://udyamregistration.gov.in/"},
        {"name": "GST Portal", "category": "Official setup", "description": "Check GST registration and compliance requirements for your business.", "url": "https://www.gst.gov.in/"},
        {"name": "FSSAI FoSCoS", "category": "Official setup", "description": "For food businesses only; check applicable licence requirements.", "url": "https://foscos.fssai.gov.in/"},
        {"name": "IP India", "category": "Official setup", "description": "Trademark and intellectual-property information.", "url": "https://ipindia.gov.in/"},
        {"name": "ONDC", "category": "Sales channels", "description": "Explore the open-network ecosystem and seller onboarding options.", "url": "https://ondc.org/"},
    ]


def _supplier_search_links(query: str, location: str, sourcing_type: str) -> list[dict[str, str]]:
    """Return search entry points, not fabricated or verified supplier listings."""
    search = urllib.parse.quote_plus(query + " " + sourcing_type + " supplier")
    local_search = urllib.parse.quote_plus(query + " supplier " + location)
    return [
        {"name": "IndiaMART", "url": "https://dir.indiamart.com/search.mp?ss=" + search, "type": "B2B supplier directory"},
        {"name": "TradeIndia", "url": "https://www.tradeindia.com/search.html?keyword=" + search, "type": "B2B supplier directory"},
        {"name": "ExportersIndia", "url": "https://www.exportersindia.com/search.php?srch_catg_ty=prod&term=" + search, "type": "Supplier and manufacturer directory"},
        {"name": "Google Maps", "url": "https://www.google.com/maps/search/" + local_search, "type": "Local sourcing discovery"},
    ]


@app.post("/api/v1/empire/workspace")
def empire_workspace(request: EmpireWorkspaceRequest) -> dict[str, Any]:
    """Provide a single transparent workspace response without a paid AI service."""
    analysis_input = MarketAnalysisRequest(
        q=request.q,
        unit_cost_inr=request.unit_cost_inr,
        selling_price_inr=request.selling_price_inr,
        packaging_cost_inr=request.packaging_cost_inr,
        shipping_cost_inr=request.shipping_cost_inr,
        marketplace_fee_pct=request.marketplace_fee_pct,
        return_loss_pct=request.return_loss_pct,
        ads_cost_per_order_inr=request.ads_cost_per_order_inr,
    )
    analysis = analyze_market(analysis_input)
    items = analysis["items"]
    snapshot = analysis["market_snapshot"]
    price_summary = snapshot.get("price_summary")
    count = snapshot["listing_count"]
    source_count = snapshot["sources_with_results"]

    # A listing-count signal is not verified seller count or sales volume.
    if count >= 20:
        competition_level = "high_listing_activity_signal"
        competition_note = "Many accessible listings were found. This is a listing-activity signal, not a verified seller count or proof of sales."
    elif count >= 5:
        competition_level = "moderate_listing_activity_signal"
        competition_note = "Some accessible listings were found. Compare product quality, delivery, reviews and differentiation before entering."
    else:
        competition_level = "insufficient_competition_evidence"
        competition_note = "Too few accessible listings to assess competition. Sources may block collection; absence of results does not prove low competition or demand."

    supplier_links = _supplier_search_links(request.q.strip(), request.location.strip(), request.sourcing_type.strip())
    economics = analysis.get("unit_economics")
    business_plan: dict[str, Any] = {
        "currency": "INR",
        "startup_budget_inr": _money(request.startup_budget_inr),
        "monthly_fixed_costs_inr": _money(request.monthly_fixed_costs_inr),
        "target_monthly_orders": request.target_monthly_orders,
        "recommended_test_budget_allocation": [
            {"category": "Samples and quality checks", "pct": 20, "amount_inr": _money(request.startup_budget_inr * .20)},
            {"category": "First small inventory batch", "pct": 35, "amount_inr": _money(request.startup_budget_inr * .35)},
            {"category": "Packaging and fulfilment setup", "pct": 15, "amount_inr": _money(request.startup_budget_inr * .15)},
            {"category": "Content and customer testing", "pct": 10, "amount_inr": _money(request.startup_budget_inr * .10)},
            {"category": "Cash reserve and unexpected costs", "pct": 20, "amount_inr": _money(request.startup_budget_inr * .20)},
        ],
        "note": "Illustrative starting allocation, not a supplier quote or guaranteed return. Adjust to actual minimum order quantities, fees and local rules.",
        "supplier_due_diligence": [
            "Ask for a sample and verify material, size, finish and packaging before bulk payment.",
            "Confirm GST invoice/business identity, address, payment beneficiary and return/replacement terms.",
            "Request written MOQ, lead time, shipping charge, defect policy and price validity.",
            "Compare at least three suppliers; never treat a directory listing as proof of legitimacy.",
            "Avoid advance payments you cannot afford to lose; verify the supplier independently."
        ],
    }
    if economics and economics.get("status") != "needs_selling_price":
        profit = economics.get("estimated_net_profit_per_order")
        if profit is not None:
            monthly_contribution = float(profit) * request.target_monthly_orders
            business_plan["scenario"] = {
                "price_basis": economics.get("price_basis"),
                "estimated_profit_per_order_inr": profit,
                "target_monthly_orders": request.target_monthly_orders,
                "estimated_monthly_contribution_before_fixed_costs_inr": _money(monthly_contribution),
                "estimated_monthly_result_after_fixed_costs_inr": _money(monthly_contribution - request.monthly_fixed_costs_inr),
                "break_even_orders_per_month": (
                    max(0, math.ceil(request.monthly_fixed_costs_inr / float(profit)))
                    if float(profit) > 0 and request.monthly_fixed_costs_inr > 0
                    else (0 if float(profit) > 0 else None)
                ),
                "estimated_units_affordable_at_product_cost": (
                    int(request.startup_budget_inr // request.unit_cost_inr)
                    if request.unit_cost_inr and request.unit_cost_inr > 0 else None
                ),
                "note": "Scenario math based on user-supplied assumptions; taxes, platform fees, returns and overhead may be incomplete."
            }
    return {
        "mode": "empire_business_workspace",
        "query": request.q.strip(),
        "generated_at_utc": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        "market_analysis": analysis,
        "competition": {
            "level": competition_level,
            "listing_count_signal": count,
            "sources_with_results": source_count,
            "observed_price_range": price_summary,
            "note": competition_note,
            "differentiation_checklist": [
                "Compare like-for-like quality, material, sizes, bundle contents and delivery time.",
                "Review negative customer feedback and return reasons where publicly available.",
                "Choose a specific customer segment or use case rather than copying the lowest price.",
                "Do not infer sales volume from listing counts or reviews alone."
            ]
        },
        "supplier_discovery": {
            "mode": "search_links_only",
            "query": request.q.strip(),
            "location": request.location.strip(),
            "links": supplier_links,
            "verification_required": True,
            "note": "These links open supplier-search pages. MarketMitra has not verified individual suppliers, prices, certifications or stock."
        },
        "research_hub": {
            "mode": "free_link_hub",
            "links": _research_hub_links(request.q.strip(), request.location.strip(), request.sourcing_type.strip()),
            "note": "External research links are manual discovery tools, not evidence collected by the scraper. Only sources shown as successfully fetched count toward the evidence score."
        },
        "business_plan": business_plan,
        "next_actions": [
            {"priority": "1", "action": "Demand gate: use the research hub to check search interest, customer questions and competing offers. Record evidence from at least three independent sources; listing counts alone are not demand."},
            {"priority": "2", "action": "Customer gate: speak to 10-20 likely buyers or test a simple catalogue/interest form before placing a bulk order. Record objections, preferred variants and acceptable prices."},
            {"priority": "3", "action": "Competition gate: compare at least 10 like-for-like offers where possible, including material/specification, delivery, reviews, returns and total delivered price."},
            {"priority": "4", "action": "Supplier gate: contact at least three suppliers; request samples, written MOQ, GST invoice/business identity, lead time, defect policy and replacement terms. Independently verify every supplier."},
            {"priority": "5", "action": "Profit gate: calculate landed cost, packaging, shipping, marketplace/payment fees, taxes, return loss, discounts and ads. Reject or reprice any scenario without positive contribution."},
            {"priority": "6", "action": "Compliance gate: check product-specific licences, labelling, tax and marketplace eligibility on official sources before listing. Food products may require FSSAI; requirements depend on the actual business."},
            {"priority": "7", "action": "Pilot gate: start with the smallest affordable test batch, track orders, conversion, returns and cash left weekly, and scale only when real unit economics support it."}
        ],
        "safety_note": "Research scores and financial projections are decision aids, not guaranteed demand, profits or business success.",
    }


# Independent demand-signal collection: public RSS feeds are queried separately
# from marketplace listings so news/trend mentions are never misrepresented as sales.
def _local_xml_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].casefold()


def _fetch_public_rss(name: str, url: str, query: str, *, max_items: int = 12) -> dict[str, Any]:
    """Read a small, bounded public RSS/Atom feed; never log in or bypass access controls."""
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "MarketMitraResearchBot/0.6 (+https://github.com/cryding/MarketMitra)",
            "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=4) as response:
            content_type = response.headers.get("Content-Type", "").lower()
            if not any(token in content_type for token in ("xml", "rss", "atom", "text/plain", "octet-stream")):
                return {"source": name, "status": "unsupported_content_type", "count": 0, "items": []}
            raw = response.read(400_001)
            if len(raw) > 400_000:
                return {"source": name, "status": "feed_too_large", "count": 0, "items": []}
        root = ET.fromstring(raw)
    except (urllib.error.URLError, TimeoutError, OSError, ET.ParseError, ValueError) as exc:
        return {"source": name, "status": "unavailable", "detail": type(exc).__name__, "count": 0, "items": []}

    query_terms = [re.sub(r"[^a-z0-9]+", "", term.casefold()) for term in query.split()]
    query_terms = [term for term in query_terms if len(term) > 1]
    output: list[dict[str, Any]] = []
    for node in root.iter():
        if _local_xml_name(node.tag) not in {"item", "entry"}:
            continue
        fields: dict[str, str] = {}
        for child in list(node):
            key = _local_xml_name(child.tag)
            value = (child.text or "").strip()
            if key == "link" and not value:
                value = str(child.attrib.get("href", "")).strip()
            if key not in fields and value:
                fields[key] = value
        title = fields.get("title", "").strip()
        link = fields.get("link", "").strip()
        summary = re.sub(r"<[^>]+>", " ", fields.get("description", fields.get("summary", "")))
        summary = re.sub(r"\s+", " ", summary).strip()[:500]
        if not title or not link or not link.startswith("https://"):
            continue
        normalized = re.sub(r"[^a-z0-9]+", " ", (title + " " + summary).casefold())
        matched = [term for term in query_terms if term in normalized]
        # News RSS is query-scoped; trends RSS is a general India trend feed, so
        # return only matching titles there instead of pretending unrelated trends apply.
        if query_terms and not matched:
            continue
        output.append({
            "title": title[:240],
            "url": link[:1200],
            "summary": summary,
            "published_at": fields.get("pubdate", fields.get("published", fields.get("updated", "")))[:100],
            "source": name,
            "signal_type": (
                "news_mention" if "news" in name.casefold()
                else "customer_discussion" if "reddit" in name.casefold()
                else "search_trend"
            ),
            "matched_terms": matched,
        })
        if len(output) >= max_items:
            break
    return {"source": name, "status": "ok", "count": len(output), "items": output}


def _collect_demand_signals(query: str) -> dict[str, Any]:
    """Cross-check a query against India trend RSS and query-scoped news RSS."""
    encoded = urllib.parse.quote_plus(query)
    feeds = [
        (
            "Google Trends India RSS",
            "https://trends.google.com/trending/rss?geo=IN",
        ),
        (
            "Google News India RSS",
            "https://news.google.com/rss/search?q=" + encoded + "&hl=en-IN&gl=IN&ceid=IN:en",
        ),
        (
            "Public Reddit Search RSS",
            "https://www.reddit.com/search.rss?q=" + encoded + "&sort=new&t=month",
        ),
    ]
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda row: _fetch_public_rss(row[0], row[1], query), feeds))
    items = [item for result in results for item in result.get("items", [])]
    unique: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    for item in items:
        url = str(item.get("url", ""))
        if url and url not in seen_urls:
            seen_urls.add(url)
            unique.append(item)

    successful = sum(1 for result in results if result.get("status") == "ok")
    source_hits = sum(1 for result in results if result.get("count", 0) > 0)
    news_hits = sum(1 for item in unique if item.get("signal_type") == "news_mention")
    trend_hits = sum(1 for item in unique if item.get("signal_type") == "search_trend")
    discussion_hits = sum(1 for item in unique if item.get("signal_type") == "customer_discussion")
    # Score corroboration breadth, not raw article volume. Ten articles from one feed
    # must not outweigh missing independent sources.
    coverage_score = min(100, source_hits * 30 + min(news_hits, 2) * 5 + min(trend_hits, 2) * 5 + min(discussion_hits, 2) * 5)
    recommendations: list[dict[str, str]] = []
    if source_hits >= 2:
        recommendations.append({
            "action": "Cross-source corroboration found",
            "why": "Related items appeared in more than one independent feed. This is an attention signal, not proof of orders or sales.",
            "confidence": "early_signal",
        })
    if news_hits:
        recommendations.append({
            "action": "Review recent news and customer context",
            "why": f"{news_hits} query-matched news item(s) were found. Open the source and judge whether each item is relevant to your exact customer and market.",
            "confidence": "early_signal",
        })
    if discussion_hits:
        recommendations.append({
            "action": "Read public customer discussions for objections and unmet needs",
            "why": f"{discussion_hits} query-matched discussion item(s) were found. Treat posts as individual opinions, not a representative customer survey.",
            "confidence": "early_signal",
        })
    if trend_hits == 0:
        recommendations.append({
            "action": "Trend feed did not corroborate this exact query",
            "why": "A missing trend match can reflect feed coverage, spelling or low trend prominence; it does not establish low demand.",
            "confidence": "low",
        })
    if not unique:
        recommendations.append({
            "action": "Collect a broader demand sample",
            "why": "No query-matched items were returned from these feeds. Try synonyms, regional-language terms, and direct customer interviews before making an investment decision.",
            "confidence": "low",
        })
    return {
        "method": "Cross-Source Evidence Lens",
        "query": query,
        "generated_at_utc": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        "coverage_score": coverage_score,
        "score_meaning": "Feed availability and matching-evidence coverage only; not demand, sales volume or profit.",
        "source_summary": {"attempted": len(results), "available": successful, "with_matching_items": source_hits},
        "sources": [{"source": r["source"], "status": r["status"], "count": r["count"]} for r in results],
        "items": unique[:24],
        "recommendations": recommendations,
        "limitations": [
            "RSS items are discovery leads; relevance and publication dates must be checked on the source page.",
            "Google Trends RSS shows trending searches, not absolute search volume or purchases. Public forum posts are anecdotal and not representative survey data.",
            "No source match is not evidence that demand is absent.",
        ],
    }


@app.get("/api/v1/research/signals")
def research_signals(q: str = Query(default="", min_length=2, max_length=100)) -> dict[str, Any]:
    """Collect independent public trend/news signals without a CLI, API key or paid service."""
    query = q.strip()
    if len(query) < 2:
        raise HTTPException(status_code=422, detail="Enter at least two characters.")
    return _collect_demand_signals(query)


class BusinessEconomicsRequest(BaseModel):
    """Monthly business P&L/cashflow calculator with explicit, user-provided inputs."""
    revenue_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    opening_inventory_cost_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    purchases_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    closing_inventory_cost_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    operating_expenses_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    marketplace_fees_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    shipping_and_packaging_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    returns_and_refunds_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    taxes_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    cash_received_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    cash_paid_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    monthly_fixed_costs_inr: float = Field(default=0, ge=0, le=1_000_000_000_000)
    average_order_value_inr: float | None = Field(default=None, gt=0, le=1_000_000_000)
    target_net_margin_pct: float = Field(default=15, ge=0, le=99.9)


@app.post("/api/v1/operations/economics")
def business_economics(request: BusinessEconomicsRequest) -> dict[str, Any]:
    """Calculate gross profit, net profit, cash movement, margin and break-even transparently."""
    cogs = max(0.0, request.opening_inventory_cost_inr + request.purchases_inr - request.closing_inventory_cost_inr)
    net_sales = max(0.0, request.revenue_inr - request.returns_and_refunds_inr)
    gross_profit = net_sales - cogs
    operating_costs = (
        request.operating_expenses_inr + request.marketplace_fees_inr
        + request.shipping_and_packaging_inr + request.taxes_inr
    )
    net_profit = gross_profit - operating_costs
    cash_movement = request.cash_received_inr - request.cash_paid_inr
    margin_pct = net_profit / net_sales * 100 if net_sales > 0 else None
    required_revenue = (
        (request.monthly_fixed_costs_inr / (request.target_net_margin_pct / 100))
        if request.target_net_margin_pct > 0 else None
    )
    break_even_orders = (
        math.ceil(request.monthly_fixed_costs_inr / request.average_order_value_inr)
        if request.average_order_value_inr and request.average_order_value_inr > 0 else None
    )
    return {
        "mode": "business_economics_v1",
        "currency": "INR",
        "inputs": request.model_dump(),
        "cost_of_goods_sold_inr": _money(cogs),
        "net_sales_after_refunds_inr": _money(net_sales),
        "gross_profit_inr": _money(gross_profit),
        "operating_costs_inr": _money(operating_costs),
        "net_profit_before_unentered_costs_inr": _money(net_profit),
        "net_margin_pct": _money(margin_pct) if margin_pct is not None else None,
        "cash_movement_inr": _money(cash_movement),
        "cash_warning": "Profit and cash movement are different: unpaid customer invoices, supplier credit and inventory purchases can change cash without changing profit in the same period.",
        "revenue_target_for_fixed_costs_and_margin_inr": _money(required_revenue) if required_revenue is not None else None,
        "break_even_orders_at_average_order_value": break_even_orders,
        "warnings": [
            "Enter complete period totals and inventory values; missing costs make profit look higher than reality.",
            "This calculator does not file taxes or replace an accountant.",
            "Break-even orders based on average order value alone are a rough sales-target indicator, not contribution-margin break-even.",
        ],
    }
