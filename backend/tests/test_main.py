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
