from __future__ import annotations

import ipaddress
import json
import socket
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from typing import Any

from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException
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
