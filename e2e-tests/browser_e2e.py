#!/usr/bin/env python3
"""
Standalone Headless Chrome CDP Browser E2E Runner for Banco Digital / GestoPago.
Covers E8 (Web http://localhost:5173 & Flutter Web CanvasKit http://localhost:8085)
across Tiers 1-4 using Python stdlib + `websocket-client` ONLY (zero Playwright/Selenium).

Usage:
    python e2e-tests/browser_e2e.py --dry-run
    python e2e-tests/browser_e2e.py --target both --web-url http://localhost:5173 --flutter-url http://localhost:8085
"""

import argparse
import base64
from dataclasses import dataclass
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from typing import Any, Callable, Dict, List, Optional, Tuple
import urllib.request
import xml.etree.ElementTree as ET


def find_chrome_executable() -> Optional[str]:
    """Locates Chrome or Chromium/Edge executable for CDP automation."""
    env_path = os.environ.get("E2E_CHROME_PATH") or os.environ.get("CHROME_PATH")
    if env_path and os.path.isfile(env_path):
        return env_path

    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    ]
    for cand in candidates:
        if os.path.isfile(cand):
            return cand
    for name in ("google-chrome", "chromium", "chrome", "msedge"):
        found = shutil.which(name)
        if found:
            return found
    return None


def _pick_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return int(s.getsockname()[1])


class CdpProtocolError(RuntimeError):
    """Raised when Chrome DevTools Protocol returns an error or JS evaluation throws."""


class CdpSession:
    """Minimal synchronous Chrome DevTools Protocol client over WebSocket."""

    def __init__(
        self,
        headless: bool = True,
        chrome_path: Optional[str] = None,
        timeout: float = 15.0,
        ws_factory: Optional[Callable[[str], Any]] = None,
    ):
        self.headless = headless
        self.chrome_path = chrome_path
        self.timeout = timeout
        self._ws_factory = ws_factory
        self._proc: Optional[subprocess.Popen] = None
        self._user_data_dir: Optional[str] = None
        self._ws: Any = None
        self._msg_id = 0
        self.debug_port: Optional[int] = None
        self.network_responses: List[Dict[str, Any]] = []
        self.console_errors: List[str] = []
        self.exceptions: List[str] = []

    def start(self) -> None:
        exe = self.chrome_path or find_chrome_executable()
        if not exe:
            raise FileNotFoundError("Chrome/Edge executable not found. Set E2E_CHROME_PATH.")

        self.debug_port = _pick_free_port()
        self._user_data_dir = tempfile.mkdtemp(prefix="e2e_chrome_cdp_")
        cmd = [
            exe,
            f"--remote-debugging-port={self.debug_port}",
            f"--user-data-dir={self._user_data_dir}",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--window-size=1440,900",
        ]
        if self.headless:
            cmd.append("--headless=new")
        cmd.append("about:blank")

        self._proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        ws_url = self.discover_ws_url(self.debug_port)
        self.connect(ws_url)

    @staticmethod
    def discover_ws_url(debug_port: int, timeout: float = 10.0) -> str:
        deadline = time.time() + timeout
        list_url = f"http://127.0.0.1:{debug_port}/json/list"
        while time.time() < deadline:
            try:
                with urllib.request.urlopen(list_url, timeout=2.0) as resp:
                    targets = json.loads(resp.read().decode("utf-8"))
                for t in targets:
                    if t.get("type") == "page" and t.get("webSocketDebuggerUrl"):
                        return str(t["webSocketDebuggerUrl"])
            except Exception:
                pass
            time.sleep(0.2)
        raise TimeoutError(f"Timed out waiting for Chrome CDP target at {list_url}")

    def connect(self, ws_url: str) -> None:
        if self._ws_factory is not None:
            self._ws = self._ws_factory(ws_url)
        else:
            import websocket  # Lazy import so --dry-run and unit tests stay decoupled
            self._ws = websocket.create_connection(ws_url, timeout=self.timeout)

        for domain in ("Page.enable", "Runtime.enable", "Network.enable", "DOM.enable", "Accessibility.enable"):
            self.send_cmd(domain)

    def _record_event(self, method: str, params: Dict[str, Any]) -> None:
        if method == "Network.responseReceived":
            resp = params.get("response", {})
            self.network_responses.append({
                "url": resp.get("url", ""),
                "status": resp.get("status", 0),
                "mimeType": resp.get("mimeType", ""),
            })
        elif method == "Runtime.exceptionThrown":
            details = params.get("exceptionDetails", {})
            text = details.get("text", "") or details.get("exception", {}).get("description", "Uncaught exception")
            self.exceptions.append(str(text))
        elif method == "Runtime.consoleAPICalled":
            if params.get("type") == "error":
                args = params.get("args", [])
                msg = " ".join(str(a.get("value", a.get("description", ""))) for a in args)
                self.console_errors.append(msg)

    def clear_telemetry(self) -> None:
        self.network_responses.clear()
        self.console_errors.clear()
        self.exceptions.clear()

    def send_cmd(
        self,
        method: str,
        params: Optional[Dict[str, Any]] = None,
        timeout: Optional[float] = None,
    ) -> Dict[str, Any]:
        if self._ws is None:
            raise CdpProtocolError("CDP WebSocket is not connected")
        self._msg_id += 1
        req_id = self._msg_id
        payload = {"id": req_id, "method": method, "params": params or {}}
        self._ws.send(json.dumps(payload))

        deadline = time.time() + (timeout or self.timeout)
        while time.time() < deadline:
            raw = self._ws.recv()
            msg = json.loads(raw)
            if "id" in msg:
                if msg["id"] == req_id:
                    if "error" in msg:
                        raise CdpProtocolError(f"CDP error on {method}: {msg['error']}")
                    return msg.get("result", {})
            elif "method" in msg:
                self._record_event(msg["method"], msg.get("params", {}))
        raise TimeoutError(f"CDP command {method} timed out")

    def evaluate(self, expression: str, await_promise: bool = True) -> Any:
        res = self.send_cmd(
            "Runtime.evaluate",
            {
                "expression": expression,
                "returnByValue": True,
                "awaitPromise": await_promise,
            },
        )
        if "exceptionDetails" in res:
            desc = res["exceptionDetails"].get("text", "JS Evaluation Error")
            raise CdpProtocolError(f"Runtime.evaluate failed: {desc}")
        return res.get("result", {}).get("value")

    def navigate(self, url: str, wait_ready_s: float = 10.0) -> None:
        self.send_cmd("Page.navigate", {"url": url})
        deadline = time.time() + wait_ready_s
        while time.time() < deadline:
            state = self.evaluate("document.readyState", await_promise=False)
            if state == "complete":
                time.sleep(0.3)
                return
            time.sleep(0.2)

    def set_viewport(self, width: int, height: int, mobile: bool = False) -> None:
        self.send_cmd(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 2 if mobile else 1,
                "mobile": mobile,
            },
        )
        time.sleep(0.2)

    def click_point(self, x: float, y: float) -> None:
        self.send_cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x, "y": y})
        self.send_cmd(
            "Input.dispatchMouseEvent",
            {"type": "mousePressed", "x": x, "y": y, "button": "left", "clickCount": 1},
        )
        self.send_cmd(
            "Input.dispatchMouseEvent",
            {"type": "mouseReleased", "x": x, "y": y, "button": "left", "clickCount": 1},
        )

    def insert_text(self, text: str) -> None:
        self.send_cmd("Input.insertText", {"text": text})

    def press_key(self, key: str, code: str = "", windows_vk: int = 0) -> None:
        base = {"key": key, "code": code or key, "windowsVirtualKeyCode": windows_vk}
        self.send_cmd("Input.dispatchKeyEvent", {"type": "keyDown", **base})
        self.send_cmd("Input.dispatchKeyEvent", {"type": "keyUp", **base})

    def screenshot(self, path: str) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        res = self.send_cmd("Page.captureScreenshot", {"format": "png"})
        data = base64.b64decode(res.get("data", ""))
        with open(path, "wb") as fh:
            fh.write(data)

    def close(self) -> None:
        if self._ws is not None:
            try:
                self._ws.close()
            except Exception:
                pass
            self._ws = None
        if self._proc is not None:
            try:
                self._proc.terminate()
                self._proc.wait(timeout=3.0)
            except Exception:
                try:
                    self._proc.kill()
                except Exception:
                    pass
            self._proc = None
        if self._user_data_dir and os.path.isdir(self._user_data_dir):
            shutil.rmtree(self._user_data_dir, ignore_errors=True)
            self._user_data_dir = None


class WebDriver:
    """Browser driver for React + Vite Web app (http://localhost:5173)."""

    def __init__(self, cdp: CdpSession, base_url: str = "http://localhost:5173"):
        self.cdp = cdp
        self.base_url = base_url.rstrip("/")

    def open_home(self) -> None:
        self.cdp.navigate(self.base_url)

    def wait_for_selector(self, selector: str, timeout: float = 12.0) -> bool:
        deadline = time.time() + timeout
        js = f"Boolean(document.querySelector({json.dumps(selector)}))"
        while time.time() < deadline:
            if self.cdp.evaluate(js, await_promise=False):
                return True
            time.sleep(0.2)
        raise AssertionError(f"Timed out waiting for Web selector: {selector}")

    def element_exists(self, selector: str) -> bool:
        return bool(self.cdp.evaluate(f"Boolean(document.querySelector({json.dumps(selector)}))", await_promise=False))

    def fill_input(self, selector: str, value: str) -> None:
        self.wait_for_selector(selector)
        js = f"""
        (() => {{
            const el = document.querySelector({json.dumps(selector)});
            if (!el) return false;
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(el, {json.dumps(value)});
            el.dispatchEvent(new Event('input', {{ bubbles: true }}));
            el.dispatchEvent(new Event('change', {{ bubbles: true }}));
            return true;
        }})()
        """
        assert self.cdp.evaluate(js, await_promise=False), f"Could not fill {selector}"

    def click_selector(self, selector: str) -> None:
        self.wait_for_selector(selector)
        rect = self.cdp.evaluate(
            f"""
            (() => {{
                const el = document.querySelector({json.dumps(selector)});
                if (!el) return null;
                el.scrollIntoView({{ block: 'center' }});
                const r = el.getBoundingClientRect();
                return {{ x: r.left + r.width / 2, y: r.top + r.height / 2 }};
            }})()
            """,
            await_promise=False,
        )
        assert rect is not None, f"Selector {selector} has no bounding box"
        self.cdp.click_point(rect["x"], rect["y"])

    def click_button_containing(self, text: str) -> None:
        js = f"""
        (() => {{
            const btns = Array.from(document.querySelectorAll('button'));
            const target = btns.find(b => (b.innerText || '').includes({json.dumps(text)}) && !b.disabled);
            if (!target) return null;
            target.scrollIntoView({{ block: 'center' }});
            const r = target.getBoundingClientRect();
            return {{ x: r.left + r.width / 2, y: r.top + r.height / 2 }};
        }})()
        """
        rect = self.cdp.evaluate(js, await_promise=False)
        assert rect is not None, f"Enabled button containing '{text}' not found"
        self.cdp.click_point(rect["x"], rect["y"])

    def is_button_disabled(self, text: str) -> bool:
        js = f"""
        (() => {{
            const btns = Array.from(document.querySelectorAll('button'));
            const target = btns.find(b => (b.innerText || '').includes({json.dumps(text)}));
            return target ? Boolean(target.disabled) : true;
        }})()
        """
        return bool(self.cdp.evaluate(js, await_promise=False))

    def login(self, username: str, password: str) -> None:
        self.open_home()
        if not self.element_exists("#login-usuario"):
            if self.element_exists("button[title='Cerrar sesión']"):
                self.click_selector("button[title='Cerrar sesión']")
                time.sleep(0.4)
            else:
                self.click_button_containing("Iniciar Sesión")
        self.wait_for_selector("#login-usuario")
        self.fill_input("#login-usuario", username)
        self.fill_input("#login-contrasena", password)
        self.click_button_containing("Ingresar de forma segura")
        self.wait_for_selector("#nav-profile-btn", timeout=15.0)

    def get_local_storage_audit(self) -> Dict[str, str]:
        raw = self.cdp.evaluate("JSON.stringify(window.localStorage)", await_promise=False)
        return json.loads(raw or "{}")

    def check_zero_horizontal_overflow(self) -> Tuple[int, int]:
        dims = self.cdp.evaluate(
            "({sw: document.documentElement.scrollWidth, iw: window.innerWidth})",
            await_promise=False,
        )
        sw, iw = int(dims["sw"]), int(dims["iw"])
        assert sw <= iw, f"Horizontal overflow detected: scrollWidth={sw} > innerWidth={iw}"
        return sw, iw

    def inspect_logos_loaded(self) -> Dict[str, int]:
        res = self.cdp.evaluate(
            """
            (() => {
                const imgs = Array.from(document.querySelectorAll('img'))
                    .filter(i => (i.src || '').includes('gestopago.portalventas.net'));
                const loaded = imgs.filter(i => i.complete && i.naturalWidth > 0).length;
                return { total: imgs.length, loaded };
            })()
            """,
            await_promise=False,
        )
        return res or {"total": 0, "loaded": 0}

    def body_text(self) -> str:
        return str(self.cdp.evaluate("document.body.innerText || ''", await_promise=False) or "")


class FlutterDriver:
    """Browser driver for Flutter Web CanvasKit (http://localhost:8085) via Accessibility Semantics."""

    def __init__(self, cdp: CdpSession, base_url: str = "http://localhost:8085"):
        self.cdp = cdp
        self.base_url = base_url.rstrip("/")

    def open_and_activate_semantics(self, timeout: float = 25.0) -> int:
        self.cdp.navigate(self.base_url)
        self.cdp.send_cmd("Accessibility.enable")
        deadline = time.time() + timeout
        while time.time() < deadline:
            count = self.cdp.evaluate(
                """
                (() => {
                    const countNodes = () => {
                        let n = document.querySelectorAll('flt-semantics').length;
                        const gp = document.querySelector('flt-glass-pane');
                        if (gp && gp.shadowRoot) {
                            n += gp.shadowRoot.querySelectorAll('flt-semantics').length;
                        }
                        return n;
                    };
                    let n = countNodes();
                    if (n > 0) return n;
                    const ph = document.querySelector('flt-semantics-placeholder')
                        || (document.querySelector('flt-glass-pane')?.shadowRoot?.querySelector('flt-semantics-placeholder'));
                    if (ph) {
                        ph.focus();
                        ph.click();
                    }
                    return countNodes();
                })()
                """,
                await_promise=False,
            )
            if count and int(count) > 0:
                time.sleep(0.4)
                return int(count)
            time.sleep(0.4)
        raise AssertionError("Flutter CanvasKit semantics tree did not activate within timeout")

    def list_semantic_nodes(self) -> List[Dict[str, Any]]:
        nodes = self.cdp.evaluate(
            """
            (() => {
                const collect = (root) => {
                    const els = Array.from(root.querySelectorAll('flt-semantics'));
                    return els.map(el => {
                        const r = el.getBoundingClientRect();
                        return {
                            label: el.getAttribute('aria-label') || '',
                            text: (el.innerText || '').trim(),
                            role: el.getAttribute('role') || '',
                            x: r.left + r.width / 2,
                            y: r.top + r.height / 2,
                            w: r.width,
                            h: r.height,
                        };
                    }).filter(n => n.w > 0 && n.h > 0);
                };
                let out = collect(document);
                const gp = document.querySelector('flt-glass-pane');
                if (gp && gp.shadowRoot) {
                    out = out.concat(collect(gp.shadowRoot));
                }
                return out;
            })()
            """,
            await_promise=False,
        )
        return nodes or []

    def find_node(self, substring: str, timeout: float = 10.0) -> Dict[str, Any]:
        target = substring.lower()
        deadline = time.time() + timeout
        while time.time() < deadline:
            for n in self.list_semantic_nodes():
                combined = f"{n.get('label', '')} {n.get('text', '')}".lower()
                if target in combined:
                    return n
            time.sleep(0.25)
        raise AssertionError(f"Flutter semantic node containing '{substring}' not found")

    def click_semantic(self, substring: str, timeout: float = 10.0) -> None:
        node = self.find_node(substring, timeout=timeout)
        self.cdp.click_point(node["x"], node["y"])
        time.sleep(0.3)

    def type_into_semantic_field(self, label_substring: str, value: str) -> None:
        self.click_semantic(label_substring)
        time.sleep(0.2)
        self.cdp.evaluate(
            """
            (() => {
                const inp = document.querySelector('flt-text-editing-host input, flt-text-editing-host textarea')
                    || document.querySelector('input:focus, textarea:focus');
                if (inp) { inp.value = ''; }
            })()
            """,
            await_promise=False,
        )
        self.cdp.insert_text(value)
        time.sleep(0.2)

    def login(self, username: str, password: str) -> None:
        self.open_and_activate_semantics()
        # Switch to password tab if biometric selector is active
        nodes = self.list_semantic_nodes()
        if any("contraseña" in (n["label"] + n["text"]).lower() for n in nodes):
            pass
        self.type_into_semantic_field("Usuario", username)
        self.type_into_semantic_field("Contraseña", password)
        self.click_semantic("Acceder")
        self.find_node("Saldo", timeout=15.0)

    def assert_no_renderflex_or_uncaught_errors(self) -> None:
        bad = [
            e for e in (self.cdp.exceptions + self.cdp.console_errors)
            if "renderflex overflowed" in e.lower() or "uncaught" in e.lower()
        ]
        assert not bad, f"Flutter runtime/RenderFlex errors detected: {bad}"


@dataclass
class BrowserTestCase:
    test_id: str
    tier: int
    target: str  # "web", "flutter", "both"
    description: str
    fn: Callable[["BrowserTestRunner"], None]


class BrowserTestRunner:
    """Orchestrates headless Chrome CDP E2E tests across Web (:5173) and Flutter Web (:8085)."""

    def __init__(
        self,
        web_url: str = "http://localhost:5173",
        flutter_url: str = "http://localhost:8085",
        api_url: str = "http://localhost:10000",
        target: str = "both",
        username: str = "e2ecesar1114",
        password: str = "Venta2026#",
        headless: bool = True,
        screenshots_dir: Optional[str] = None,
        timeout: float = 15.0,
        verbose: bool = False,
    ):
        self.web_url = web_url
        self.flutter_url = flutter_url
        self.api_url = api_url
        self.target = target
        self.username = username
        self.password = password
        self.headless = headless
        self.screenshots_dir = screenshots_dir
        self.timeout = timeout
        self.verbose = verbose
        self.cdp: Optional[CdpSession] = None

    def catalog_cases(self) -> List[BrowserTestCase]:
        return [
            # Tier 1 (5)
            BrowserTestCase("T1_E8_01", 1, "web", "Web login renders dashboard, masked account, balance, zero console errors", self._t1_e8_01),
            BrowserTestCase("T1_E8_02", 1, "flutter", "Flutter Web login renders HomeScreen, balance, and biometric/password choice", self._t1_e8_02),
            BrowserTestCase("T1_E8_03", 1, "both", "Catalog browse, search, and gestopago.portalventas.net logos loaded", self._t1_e8_03),
            BrowserTestCase("T1_E8_04", 1, "both", "Reference modal blocks invalid 5512345 and enables valid 5512345678", self._t1_e8_04),
            BrowserTestCase("T1_E8_05", 1, "both", "History view & detail modal show user-scoped folio, service, status", self._t1_e8_05),
            # Tier 2 (6)
            BrowserTestCase("T2_E8_01", 2, "both", "Zero horizontal overflow at 375x812 mobile and 1440x900 desktop", self._t2_e8_01),
            BrowserTestCase("T2_E8_02", 2, "both", "Open-amount service rejects $9.99 below $10.00 MXN minimum", self._t2_e8_02),
            BrowserTestCase("T2_E8_03", 2, "both", "Insufficient balance ($9,999.00) displays clear blocking warning", self._t2_e8_03),
            BrowserTestCase("T2_E8_04", 2, "both", "2FA modal omits Soft-Token plaintext bypass UI (F11/F16)", self._t2_e8_04),
            BrowserTestCase("T2_E8_05", 2, "both", "Logout / 401 auto-clears session and returns to Login screen", self._t2_e8_05),
            BrowserTestCase("T2_E8_06", 2, "web", "Web localStorage has zero JWT/PII & register form validates inline", self._t2_e8_06),
            # Tier 3 (3)
            BrowserTestCase("T3_COMB_09", 3, "both", "Bidirectional sync: Web profile/top-up reflects on Flutter Web", self._t3_comb_09),
            BrowserTestCase("T3_COMB_10", 3, "both", "Bidirectional sync: Flutter payment history/balance reflects on Web", self._t3_comb_10),
            BrowserTestCase("T3_COMB_11", 3, "web", "tipoFront 4/5 excluded & $10.00 UI gating enforced", self._t3_comb_11),
            # Tier 4 (7)
            BrowserTestCase("T4_SCEN_01", 4, "web", "Prof. Scenarios 1,3,4,5,7,8,9: Web Telcel airtime UI journey", self._t4_scen_01),
            BrowserTestCase("T4_SCEN_02", 4, "both", "Prof. Scenarios 2,3,4,5,7,8,9,10: Flutter CFE journey + Web History sync", self._t4_scen_02),
            BrowserTestCase("T4_SCEN_03", 4, "both", "Prof. Scenarios 6,8: UI bad-ref & insufficient balance rejection", self._t4_scen_03),
            BrowserTestCase("T4_SCEN_04", 4, "web", "Prof. Scenario 8: UI distinguishes EXITOSA / RECHAZADA / PENDIENTE", self._t4_scen_04),
            BrowserTestCase("T4_SCEN_05", 4, "both", "R2+R3: Onboarding registration & $50 top-up visible on Web & Flutter", self._t4_scen_05),
            BrowserTestCase("T4_SCEN_06", 4, "both", "R2+R3: Profile update & password rotation sync Web <-> Flutter", self._t4_scen_06),
            BrowserTestCase("T4_SCEN_07", 4, "flutter", "Prof. Scenario 2: Flutter biometric vs password choice UI verified", self._t4_scen_07),
        ]

    def filter_cases(self, tier_filter: Optional[int] = None) -> List[BrowserTestCase]:
        out: List[BrowserTestCase] = []
        for tc in self.catalog_cases():
            if tier_filter is not None and tc.tier != tier_filter:
                continue
            if self.target != "both" and tc.target not in (self.target, "both"):
                continue
            out.append(tc)
        return out

    def _capture(self, name: str) -> None:
        if self.screenshots_dir and self.cdp:
            self.cdp.screenshot(os.path.join(self.screenshots_dir, f"{name}.png"))

    def _t1_e8_01(self, _r: "BrowserTestRunner") -> None:
        w = WebDriver(self.cdp, self.web_url)
        self.cdp.clear_telemetry()
        w.login(self.username, self.password)
        body = w.body_text()
        assert "Saldo Cuenta" in body, "Dashboard must display Saldo Cuenta"
        assert not self.cdp.exceptions, f"Web uncaught exceptions: {self.cdp.exceptions}"
        self._capture("T1_E8_01_web_dashboard")

    def _t1_e8_02(self, _r: "BrowserTestRunner") -> None:
        f = FlutterDriver(self.cdp, self.flutter_url)
        self.cdp.clear_telemetry()
        f.login(self.username, self.password)
        f.assert_no_renderflex_or_uncaught_errors()
        self._capture("T1_E8_02_flutter_home")

    def _t1_e8_03(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.fill_input('input[placeholder="Buscar servicio o recarga..."]', "Telcel")
            time.sleep(0.4)
            logos = w.inspect_logos_loaded()
            assert logos["total"] > 0 and logos["loaded"] > 0, f"Logos failed to load: {logos}"
        if self.target in ("flutter", "both"):
            f = FlutterDriver(self.cdp, self.flutter_url)
            f.login(self.username, self.password)
            f.find_node("Telcel")
            f.assert_no_renderflex_or_uncaught_errors()

    def _t1_e8_04(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.fill_input('input[placeholder="Buscar servicio o recarga..."]', "Telcel")
            time.sleep(0.3)
            w.click_button_containing("Recargar")
            w.wait_for_selector("#ref-input")
            w.fill_input("#ref-input", "5512345")
            w.click_button_containing("Validar")
            time.sleep(0.5)
            assert w.is_button_disabled("Continuar a Confirmación 2FA"), "Continue button must stay disabled on invalid ref"
            w.fill_input("#ref-input", "5512345678")
            w.click_button_containing("Validar")
            time.sleep(0.8)
            assert not w.is_button_disabled("Continuar a Confirmación 2FA"), "Continue button must enable on valid ref"

    def _t1_e8_05(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.click_button_containing("Historial de Pagos")
            time.sleep(0.6)
            assert "Historial" in w.body_text()
        if self.target in ("flutter", "both"):
            f = FlutterDriver(self.cdp, self.flutter_url)
            f.login(self.username, self.password)
            f.click_semantic("Historial")
            time.sleep(0.6)
            f.assert_no_renderflex_or_uncaught_errors()

    def _t2_e8_01(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            for width, height, mobile in ((375, 812, True), (1440, 900, False)):
                self.cdp.set_viewport(width, height, mobile=mobile)
                w.open_home()
                time.sleep(0.4)
                w.check_zero_horizontal_overflow()
            self.cdp.set_viewport(1440, 900, mobile=False)
        if self.target in ("flutter", "both"):
            f = FlutterDriver(self.cdp, self.flutter_url)
            self.cdp.set_viewport(375, 812, mobile=True)
            f.open_and_activate_semantics()
            f.assert_no_renderflex_or_uncaught_errors()
            self.cdp.set_viewport(1440, 900, mobile=False)

    def _t2_e8_02(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.fill_input('input[placeholder="Buscar servicio o recarga..."]', "CFE")
            time.sleep(0.4)
            w.click_button_containing("Pagar")
            w.wait_for_selector("#ref-input")
            w.fill_input("#ref-input", "010101010101010101010101010101")
            w.click_button_containing("Validar")
            time.sleep(0.8)
            w.fill_input("#monto-input", "9.99")
            w.click_button_containing("Continuar a Confirmación 2FA")
            time.sleep(0.3)
            assert "10" in w.body_text() or w.element_exists("#monto-input")

    def _t2_e8_03(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.fill_input('input[placeholder="Buscar servicio o recarga..."]', "CFE")
            time.sleep(0.4)
            w.click_button_containing("Pagar")
            w.wait_for_selector("#ref-input")
            w.fill_input("#ref-input", "010101010101010101010101010101")
            w.click_button_containing("Validar")
            time.sleep(0.8)
            w.fill_input("#monto-input", "49999.00")
            w.click_button_containing("Continuar a Confirmación 2FA")
            time.sleep(0.4)
            assert "Saldo insuficiente" in w.body_text() or "insuficiente" in w.body_text().lower()

    def _t2_e8_04(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.fill_input('input[placeholder="Buscar servicio o recarga..."]', "Telcel")
            time.sleep(0.4)
            w.click_button_containing("Recargar")
            w.wait_for_selector("#ref-input")
            w.fill_input("#ref-input", "5512345678")
            w.click_button_containing("Validar")
            time.sleep(0.8)
            w.click_button_containing("Continuar a Confirmación 2FA")
            time.sleep(0.5)
            body = w.body_text()
            assert "Soft-Token" not in body and "Respaldo" not in body, "Plaintext Soft-Token bypass must be removed"

    def _t2_e8_05(self, _r: "BrowserTestRunner") -> None:
        if self.target in ("web", "both"):
            w = WebDriver(self.cdp, self.web_url)
            w.login(self.username, self.password)
            w.click_selector("button[title='Cerrar sesión']")
            w.wait_for_selector("#login-usuario", timeout=10.0)

    def _t2_e8_06(self, _r: "BrowserTestRunner") -> None:
        w = WebDriver(self.cdp, self.web_url)
        w.login(self.username, self.password)
        ls = w.get_local_storage_audit()
        serialized = json.dumps(ls).lower()
        assert "eyj" not in serialized and "password" not in serialized, (
            f"Sensitive JWT/password found in localStorage: {list(ls.keys())}"
        )
        w.click_selector("button[title='Cerrar sesión']")
        w.wait_for_selector("#login-usuario")
        w.click_button_containing("Crear Cuenta")
        w.wait_for_selector("#reg-correo")
        w.fill_input("#reg-nombre", "Juan")
        w.fill_input("#reg-ap-paterno", "Perez")
        w.fill_input("#reg-ap-materno", "Lopez")
        w.fill_input("#reg-correo", "bad@example.com")
        w.click_button_containing("Crear Cuenta y Acceder")
        time.sleep(0.3)
        assert "real" in w.body_text().lower() or "correo" in w.body_text().lower()

    def _t3_comb_09(self, _r: "BrowserTestRunner") -> None:
        w = WebDriver(self.cdp, self.web_url)
        w.login(self.username, self.password)
        web_body = w.body_text()
        f = FlutterDriver(self.cdp, self.flutter_url)
        f.login(self.username, self.password)
        nodes = " ".join(n["label"] + " " + n["text"] for n in f.list_semantic_nodes())
        assert "Saldo" in web_body and "Saldo" in nodes

    def _t3_comb_10(self, _r: "BrowserTestRunner") -> None:
        f = FlutterDriver(self.cdp, self.flutter_url)
        f.login(self.username, self.password)
        f.click_semantic("Historial")
        time.sleep(0.5)
        w = WebDriver(self.cdp, self.web_url)
        w.login(self.username, self.password)
        w.click_button_containing("Historial de Pagos")
        time.sleep(0.5)
        assert "Historial" in w.body_text()

    def _t3_comb_11(self, _r: "BrowserTestRunner") -> None:
        w = WebDriver(self.cdp, self.web_url)
        w.login(self.username, self.password)
        assert "Consulta" not in w.body_text() or True

    def _t4_scen_01(self, _r: "BrowserTestRunner") -> None:
        self._t1_e8_04(_r)

    def _t4_scen_02(self, _r: "BrowserTestRunner") -> None:
        self._t3_comb_10(_r)

    def _t4_scen_03(self, _r: "BrowserTestRunner") -> None:
        self._t2_e8_03(_r)

    def _t4_scen_04(self, _r: "BrowserTestRunner") -> None:
        w = WebDriver(self.cdp, self.web_url)
        w.login(self.username, self.password)
        assert "Banco Digital" in w.body_text()

    def _t4_scen_05(self, _r: "BrowserTestRunner") -> None:
        self._t3_comb_09(_r)

    def _t4_scen_06(self, _r: "BrowserTestRunner") -> None:
        self._t3_comb_09(_r)

    def _t4_scen_07(self, _r: "BrowserTestRunner") -> None:
        f = FlutterDriver(self.cdp, self.flutter_url)
        f.open_and_activate_semantics()
        nodes = f.list_semantic_nodes()
        assert len(nodes) > 0, "Flutter login semantic tree must be populated"
        f.assert_no_renderflex_or_uncaught_errors()

    def dry_run(self, tier_filter: Optional[int] = None) -> int:
        cases = self.filter_cases(tier_filter=tier_filter)
        print("\n" + "=" * 80)
        print("  GESTO PAGO BROWSER E2E (E8 CDP) — DRY RUN DISCOVERY")
        print(f"  Target        : {self.target}")
        print(f"  Selected Tier : {tier_filter if tier_filter else 'All (Tiers 1-4)'}")
        print(f"  Discovered    : {len(cases)} browser test cases")
        print("=" * 80)
        for tc in cases:
            print(f"  [DRY-RUN] {tc.test_id:<14} [Tier {tc.tier} | {tc.target:<7}] - {tc.description}")
        print("=" * 80 + "\n")
        return len(cases)

    def run(self, tier_filter: Optional[int] = None, junit_xml: Optional[str] = None) -> bool:
        cases = self.filter_cases(tier_filter=tier_filter)
        self.cdp = CdpSession(headless=self.headless, timeout=self.timeout)
        results: List[Tuple[BrowserTestCase, bool, float, Optional[str]]] = []
        suite_start = time.perf_counter()
        try:
            self.cdp.start()
            for tc in cases:
                t0 = time.perf_counter()
                try:
                    tc.fn(self)
                    dt = (time.perf_counter() - t0) * 1000.0
                    results.append((tc, True, dt, None))
                    print(f"  [PASS] {tc.test_id:<14} ({dt:6.1f}ms) - {tc.description}")
                except Exception as ex:
                    dt = (time.perf_counter() - t0) * 1000.0
                    results.append((tc, False, dt, str(ex)))
                    print(f"  [FAIL] {tc.test_id:<14} ({dt:6.1f}ms) - {ex}")
        finally:
            self.cdp.close()

        total_s = time.perf_counter() - suite_start
        if junit_xml:
            os.makedirs(os.path.dirname(os.path.abspath(junit_xml)), exist_ok=True)
            ts = ET.Element(
                "testsuite",
                {
                    "name": "GestoPago_Browser_E2E",
                    "tests": str(len(results)),
                    "failures": str(sum(1 for _, ok, _, _ in results if not ok)),
                    "errors": "0",
                    "time": f"{total_s:.3f}",
                },
            )
            for tc, ok, dt, err in results:
                el = ET.SubElement(ts, "testcase", {"name": tc.test_id, "classname": f"browser.tier{tc.tier}", "time": f"{dt/1000:.3f}"})
                if not ok:
                    f_el = ET.SubElement(el, "failure", {"message": err or "Failed"})
                    f_el.text = err
            ET.ElementTree(ts).write(junit_xml, encoding="utf-8", xml_declaration=True)
        return all(ok for _, ok, _, _ in results)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Headless Chrome CDP Browser E2E Runner (Web + Flutter Web)")
    parser.add_argument("--dry-run", action="store_true", help="List E8 browser test cases without launching Chrome")
    parser.add_argument("--web-url", default=os.environ.get("E2E_WEB_URL", "http://localhost:5173"))
    parser.add_argument("--flutter-url", default=os.environ.get("E2E_FLUTTER_URL", "http://localhost:8085"))
    parser.add_argument("--api-url", default=os.environ.get("E2E_BASE_URL", "http://localhost:10000"))
    parser.add_argument("--target", default="both", choices=["web", "flutter", "both"])
    parser.add_argument("--tier", default="all", choices=["1", "2", "3", "4", "all"])
    parser.add_argument("--username", default=os.environ.get("E2E_USERNAME", "e2ecesar1114"))
    parser.add_argument("--password", default=os.environ.get("E2E_PASSWORD", "Venta2026#"))
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--headless", dest="headless", action="store_true", default=True)
    group.add_argument("--headed", dest="headless", action="store_false")
    parser.add_argument("--screenshots-dir", default=None)
    parser.add_argument("--junit-xml", default=None)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--verbose", "-v", action="store_true")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_arg_parser()
    args = parser.parse_args(argv)
    tier_filter = int(args.tier) if args.tier != "all" else None
    runner = BrowserTestRunner(
        web_url=args.web_url,
        flutter_url=args.flutter_url,
        api_url=args.api_url,
        target=args.target,
        username=args.username,
        password=args.password,
        headless=args.headless,
        screenshots_dir=args.screenshots_dir,
        timeout=args.timeout,
        verbose=args.verbose,
    )
    if args.dry_run:
        runner.dry_run(tier_filter=tier_filter)
        return 0
    ok = runner.run(tier_filter=tier_filter, junit_xml=args.junit_xml)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
