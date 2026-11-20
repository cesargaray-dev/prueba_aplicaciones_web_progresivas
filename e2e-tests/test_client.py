"""
HTTP Test Client and Assertion Core for Gesto Pago E2E Test Suite.
Uses Python stdlib + `requests` ONLY (zero browser/WebSocket dependencies).
"""

import json
import re
import time
from typing import Any, Dict, Iterable, Optional, Tuple, Union

try:
    import requests
except ImportError:
    requests = None


class SkipTest(Exception):
    """Raised when a test is intentionally skipped under safe live constraints (e.g., --live-safe)."""


class TestResponse:
    """Encapsulates HTTP response details (including raw bytes for PDF checks) and timing."""

    def __init__(
        self,
        status_code: int,
        headers: Dict[str, str],
        text: str,
        latency_ms: float,
        content: bytes = b"",
    ):
        self.status_code = status_code
        self.headers = headers
        self.text = text
        self.latency_ms = latency_ms
        self.content = content if content else text.encode("utf-8", errors="ignore")
        self._json: Optional[Any] = None
        self._json_parsed = False

    def header(self, name: str, default: Optional[str] = None) -> Optional[str]:
        target = name.lower()
        for k, v in self.headers.items():
            if k.lower() == target:
                return v
        return default

    @property
    def json(self) -> Any:
        if not self._json_parsed:
            try:
                self._json = json.loads(self.text)
            except Exception:
                self._json = None
            self._json_parsed = True
        return self._json

    def __repr__(self) -> str:
        return f"<TestResponse [{self.status_code}] in {self.latency_ms:.1f}ms>"


class E2EClient:
    """HTTP client specialized for REST API E2E testing."""

    def __init__(self, base_url: str = "http://127.0.0.1:10000", timeout: float = 15.0):
        # Normalize http://localhost to http://127.0.0.1 to prevent Windows IPv6 (::1) lookup delays
        if "://localhost" in base_url:
            base_url = base_url.replace("://localhost", "://127.0.0.1")
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.token: Optional[str] = None
        self.session = requests.Session() if requests else None

    def set_token(self, token: Optional[str]) -> None:
        """Sets the Bearer token for authenticated requests."""
        self.token = token

    def clear_token(self) -> None:
        """Clears current Bearer authentication."""
        self.token = None

    def with_token(self, token: Optional[str]) -> "E2EClient":
        """Creates an isolated sibling client bound to a specific Bearer token."""
        clone = E2EClient(base_url=self.base_url, timeout=self.timeout)
        clone.set_token(token)
        return clone

    def _build_headers(self, headers: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        req_headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Origin": "http://localhost:5173",
        }
        if self.token:
            req_headers["Authorization"] = f"Bearer {self.token}"
        if headers:
            for k, v in headers.items():
                if v is None:
                    req_headers.pop(k, None)
                else:
                    req_headers[k] = v
        return req_headers

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Any] = None,
        raw_body: Optional[str] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        if self.session is None:
            raise RuntimeError("The 'requests' library is required to execute HTTP requests.")
        url = f"{self.base_url}{path}"
        req_headers = self._build_headers(headers)
        body = raw_body if raw_body is not None else (json.dumps(json_data) if json_data is not None else None)
        start = time.perf_counter()
        resp = self.session.request(
            method=method.upper(),
            url=url,
            params=params,
            data=body,
            headers=req_headers,
            timeout=self.timeout,
        )
        duration_ms = (time.perf_counter() - start) * 1000.0
        return TestResponse(
            status_code=resp.status_code,
            headers=dict(resp.headers),
            text=resp.text,
            latency_ms=duration_ms,
            content=resp.content,
        )

    def get(
        self,
        path: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        return self._request("GET", path, params=params, headers=headers)

    def post(
        self,
        path: str,
        json_data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None,
        raw_body: Optional[str] = None,
    ) -> TestResponse:
        return self._request("POST", path, json_data=json_data, raw_body=raw_body, headers=headers)

    def put(
        self,
        path: str,
        json_data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        return self._request("PUT", path, json_data=json_data, headers=headers)

    def patch(
        self,
        path: str,
        json_data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        return self._request("PATCH", path, json_data=json_data, headers=headers)

    def delete(
        self,
        path: str,
        json_data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        return self._request("DELETE", path, json_data=json_data, headers=headers)

    def options(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
    ) -> TestResponse:
        req_headers = {
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Content-Type, Authorization",
        }
        if headers:
            req_headers.update(headers)
        url = f"{self.base_url}{path}"
        start = time.perf_counter()
        resp = self.session.options(url, headers=req_headers, timeout=self.timeout)
        duration_ms = (time.perf_counter() - start) * 1000.0
        return TestResponse(resp.status_code, dict(resp.headers), resp.text, duration_ms, resp.content)


# -----------------------------------------------------------------------------
# Assertion Utilities
# -----------------------------------------------------------------------------

def assert_status(
    response: TestResponse,
    expected_status: Union[int, Tuple[int, ...], Iterable[int]],
    message: str = "",
) -> None:
    allowed = (expected_status,) if isinstance(expected_status, int) else tuple(expected_status)
    if response.status_code not in allowed:
        err = f"Expected status {allowed}, got {response.status_code}. Response: {response.text[:400]}"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_field_present(data: Any, field_name: str, message: str = "") -> None:
    if not isinstance(data, dict) or field_name not in data:
        err = f"Expected field '{field_name}' in response object: {data}"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_any_field_present(data: Any, field_names: Tuple[str, ...], message: str = "") -> Any:
    if not isinstance(data, dict):
        raise AssertionError(f"{message} -> Expected dict, got {type(data).__name__}")
    for fname in field_names:
        if fname in data and data[fname] is not None:
            return data[fname]
    raise AssertionError(
        f"{message} -> Expected at least one non-null field in {field_names}, got {list(data.keys())}"
    )


def assert_field_equals(data: Any, field_name: str, expected_value: Any, message: str = "") -> None:
    assert_field_present(data, field_name, message)
    actual_value = data[field_name]
    if actual_value != expected_value:
        err = f"Field '{field_name}' mismatch: expected {expected_value!r}, got {actual_value!r}"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_is_none_or_absent(data: Any, field_name: str, message: str = "") -> None:
    if not isinstance(data, dict):
        raise AssertionError(f"{message} -> Expected dict, got {type(data).__name__}")
    if data.get(field_name) is not None:
        err = f"Field '{field_name}' must be null/omitted, got {data.get(field_name)!r}"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_regex_match(text: Any, pattern: str, message: str = "") -> None:
    if not re.search(pattern, str(text)):
        err = f"Text '{text}' does not match pattern '{pattern}'"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_numeric_range(
    value: Union[int, float], min_val: float, max_val: float, message: str = ""
) -> None:
    if not (min_val <= float(value) <= max_val):
        err = f"Value {value} out of range [{min_val}, {max_val}]"
        if message:
            err = f"{message} -> {err}"
        raise AssertionError(err)


def assert_pdf_bytes(response: TestResponse, min_bytes: int = 400, message: str = "") -> None:
    content_type = (response.header("Content-Type") or "").lower()
    if not content_type.startswith("application/pdf"):
        raise AssertionError(
            f"{message} -> Expected Content-Type application/pdf, got '{content_type}'"
        )
    if not response.content.startswith(b"%PDF-"):
        raise AssertionError(
            f"{message} -> Response bytes do not start with %PDF- magic header"
        )
    if len(response.content) < min_bytes:
        raise AssertionError(
            f"{message} -> PDF length {len(response.content)} bytes < {min_bytes}"
        )
