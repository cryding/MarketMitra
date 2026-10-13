from fastapi.testclient import TestClient

from app.main import app, _validate_public_target
from fastapi import HTTPException

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_product_research_is_explicitly_sample_data():
    response = client.get("/api/v1/research/products", params={"q": "kurti"})
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "sample"
    assert body["live_data"] is False
    assert body["count"] >= 1
    assert all(item["source_type"] == "sample" for item in body["items"])


def test_product_research_query_can_return_no_matches():
    response = client.get("/api/v1/research/products", params={"q": "not-a-real-category"})
    assert response.status_code == 200
    assert response.json()["count"] == 0


def test_localhost_target_is_blocked_before_network_lookup():
    try:
        _validate_public_target("http://127.0.0.1/private")
    except HTTPException as exc:
        assert exc.status_code == 400
        assert "blocked" in exc.detail.lower() or "local" in exc.detail.lower()
    else:
        raise AssertionError("Loopback target must be blocked")


def test_credentialed_url_is_rejected():
    try:
        _validate_public_target("https://user:password@example.com/")
    except HTTPException as exc:
        assert exc.status_code == 400
        assert "credentials" in exc.detail.lower()
    else:
        raise AssertionError("Credentialed URL must be blocked")



def test_live_research_requires_query():
    response = client.get("/api/v1/research/live", params={"q": " "})
    assert response.status_code == 422


def test_live_research_reports_source_status_and_suggestions(monkeypatch):
    from app import main

    def fake_source(source_name, url):
        if source_name == "Meesho":
            return {
                "source": source_name,
                "status": "ok",
                "items": [
                    {"name": "Cotton Kurti", "price": "₹399", "url": "https://www.meesho.com/p/1", "source": source_name}
                ],
            }
        if source_name == "Flipkart":
            return {
                "source": source_name,
                "status": "ok",
                "items": [
                    {"name": "Explore Plus navigation", "price": "", "url": "https://www.flipkart.com/plus", "source": source_name}
                ],
            }
        return {"source": source_name, "status": "blocked_by_robots_or_policy_unavailable", "items": []}

    monkeypatch.setattr(main, "_fetch_marketplace_search", fake_source)
    response = client.get("/api/v1/research/live", params={"q": "cotton kurti"})
    assert response.status_code == 200
    body = response.json()
    assert body["live_data"] is True
    assert body["count"] == 1
    assert body["items"][0]["price"] == "₹399"
    assert body["sources"][0]["status"] == "ok"
    assert body["suggestions"]


def test_market_analysis_returns_transparent_evidence_and_unit_economics(monkeypatch):
    from app import main

    def fake_research_live(q):
        return {
            "mode": "live_public_scrape",
            "live_data": True,
            "query": q,
            "fetched_at_utc": "2026-10-12T00:00:00+00:00",
            "count": 3,
            "items": [
                {"name": "Cotton Kurti A", "price": "₹400", "url": "https://example.com/a", "source": "Meesho"},
                {"name": "Cotton Kurti B", "price": "₹500", "url": "https://example.com/b", "source": "Flipkart"},
                {"name": "Cotton Kurti C", "price": "₹600", "url": "https://example.com/c", "source": "Amazon India"},
            ],
            "sources": [
                {"source": "Meesho", "status": "ok"},
                {"source": "Flipkart", "status": "ok"},
                {"source": "Amazon India", "status": "ok"},
            ],
        }

    monkeypatch.setattr(main, "research_live", fake_research_live)
    response = client.post("/api/v1/research/analyze", json={
        "q": "cotton kurti",
        "unit_cost_inr": 200,
        "selling_price_inr": 500,
        "packaging_cost_inr": 20,
        "shipping_cost_inr": 40,
        "marketplace_fee_pct": 10,
        "return_loss_pct": 5,
        "ads_cost_per_order_inr": 25,
    })
    assert response.status_code == 200
    body = response.json()
    assert body["evidence_score"] >= 0
    assert body["market_snapshot"]["price_summary"]["median"] == 500
    assert body["unit_economics"]["estimated_net_profit_per_order"] == 140
    assert body["unit_economics"]["break_even_selling_price"] == 335.29
    assert body["score_meaning"].startswith("Evidence coverage score")
    assert body["recommendations"]


def test_market_analysis_rejects_too_short_query():
    response = client.post("/api/v1/research/analyze", json={"q": "x"})
    assert response.status_code == 422


def test_empire_workspace_combines_analysis_competition_supplier_and_budget(monkeypatch):
    from app import main

    def fake_research_live(q):
        return {
            "mode": "live_public_scrape",
            "live_data": True,
            "query": q,
            "fetched_at_utc": "2026-10-12T00:00:00+00:00",
            "count": 3,
            "items": [
                {"name": "Kurti A", "price": "₹400", "url": "https://example.com/a", "source": "Meesho"},
                {"name": "Kurti B", "price": "₹500", "url": "https://example.com/b", "source": "Flipkart"},
                {"name": "Kurti C", "price": "₹600", "url": "https://example.com/c", "source": "Amazon India"},
            ],
            "sources": [
                {"source": "Meesho", "status": "ok"},
                {"source": "Flipkart", "status": "ok"},
                {"source": "Amazon India", "status": "ok"},
            ],
            "suggestions": [],
        }

    monkeypatch.setattr(main, "research_live", fake_research_live)
    response = client.post("/api/v1/empire/workspace", json={
        "q": "cotton kurti",
        "unit_cost_inr": 200,
        "selling_price_inr": 500,
        "packaging_cost_inr": 20,
        "shipping_cost_inr": 40,
        "marketplace_fee_pct": 10,
        "return_loss_pct": 5,
        "ads_cost_per_order_inr": 25,
        "startup_budget_inr": 5000,
        "monthly_fixed_costs_inr": 1000,
        "target_monthly_orders": 30,
        "location": "Itahar, West Bengal",
        "sourcing_type": "wholesale",
    })
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "empire_business_workspace"
    assert body["market_analysis"]["market_snapshot"]["price_summary"]["median"] == 500
    assert body["competition"]["level"] == "insufficient_competition_evidence"
    assert len(body["supplier_discovery"]["links"]) >= 3
    assert sum(x["pct"] for x in body["business_plan"]["recommended_test_budget_allocation"]) == 100
    assert body["business_plan"]["scenario"]["estimated_monthly_result_after_fixed_costs_inr"] == 3200
    assert len(body["next_actions"]) >= 4
    hub = body["research_hub"]
    assert hub["mode"] == "free_link_hub"
    assert len(hub["links"]) >= 15
    assert {"Demand signals", "Marketplaces", "Supplier sourcing", "Official setup"}.issubset(
        {link["category"] for link in hub["links"]}
    )
    assert all(link["url"].startswith("https://") for link in hub["links"])


def test_marketplace_adapters_include_multiple_indian_sources():
    from app.main import MARKETPLACE_SEARCHES

    assert {"Meesho", "Flipkart", "Amazon India", "Myntra", "Snapdeal", "JioMart"}.issubset(
        set(MARKETPLACE_SEARCHES)
    )


def test_supplier_links_are_search_links_not_claimed_verified_vendors():
    from app.main import _supplier_search_links

    links = _supplier_search_links("cotton kurti", "Itahar, West Bengal", "wholesale")
    assert len(links) >= 3
    assert all(link["url"].startswith("https://") for link in links)
    assert all("supplier" in link["type"].lower() or "sourcing" in link["type"].lower() for link in links)
