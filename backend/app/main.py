from __future__ import annotations

import ipaddress
import json
import re
import socket
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from typing import Any

from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field, HttpUrl

app = FastAPI(
    title="MarketMitra API",
    description="Open-source market research MVP. Demo ideas are sample data, not live marketplace rankings.",
    version="0.1.0",
)

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
        parser.read()
    except (OSError, urllib.error.URLError, ValueError):
        # Fail closed if robots policy cannot be checked.
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
    for source_name, build_url in MARKETPLACE_SEARCHES.items():
        status = _fetch_marketplace_search(source_name, build_url(query))
        source_status.append({"source": status["source"], "status": status["status"]})
        results.extend(status["items"])
    # Prefer results that contain query terms and have explicit prices.
    query_terms = [term.casefold() for term in query.split() if len(term) > 1]
    for item in results:
        name = item.get("name", "").casefold()
        item["relevance"] = sum(1 for term in query_terms if term in name)
        item["has_price"] = bool(item.get("price"))
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
