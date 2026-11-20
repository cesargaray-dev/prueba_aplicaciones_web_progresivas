#!/usr/bin/env python3
"""
Master CLI Test Runner for Gesto Pago E2E Test Suite (API Tiers 1-4).
Strictly decoupled from browser/WebSocket dependencies (uses Python stdlib + `requests` ONLY).

Usage:
    python e2e-tests/run_e2e_tests.py [--base-url URL] [--tier {1,2,3,4,all}]
        [--feature {E1,E2,E3,E4,E5,E6,E7}] [--mock] [--mock-port PORT] [--dry-run]
        [--live-safe] [--allow-live-payments] [--otp CODE] [--verbose] [--junit-xml PATH]
"""

import argparse
import inspect
import os
import socket
import sys
import time
from typing import Any, List, Optional, Tuple
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

# Ensure e2e-tests directory is in Python module search path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import E2EConfig
from mock_server import MockServerManager
from test_client import E2EClient, SkipTest
from tier1_feature_coverage import Tier1FeatureCoverage
from tier2_boundary_corner import Tier2BoundaryCorner
from tier3_cross_feature import Tier3CrossFeature
from tier4_real_world_scenarios import Tier4RealWorldScenarios


class TestResult:
    def __init__(self, name: str, tier: int, category: str, doc: str, feature_tags: Tuple[str, ...]):
        self.name = name
        self.tier = tier
        self.category = category
        self.doc = doc.strip() if doc else ""
        self.feature_tags = feature_tags
        self.passed = False
        self.skipped = False
        self.duration_ms = 0.0
        self.error_message: Optional[str] = None
        self.skip_reason: Optional[str] = None


class TestRunner:
    def __init__(self, config: E2EConfig, junit_xml: Optional[str] = None):
        self.config = config
        self.base_url = config.base_url
        self.verbose = config.verbose
        self.junit_xml = junit_xml
        self.client = E2EClient(base_url=config.base_url, timeout=config.timeout)
        self.results: List[TestResult] = []

    def _check_connectivity(self) -> bool:
        """Quick preflight TCP check to verify target server is listening."""
        parsed = urlparse(self.client.base_url)
        host = parsed.hostname or "127.0.0.1"
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        try:
            with socket.create_connection((host, port), timeout=2.0):
                return True
        except Exception:
            return False

    def _discover_tests(
        self,
        tier_filter: Optional[int] = None,
        feature_filter: Optional[str] = None,
    ) -> List[Tuple[Any, str, int, str, Tuple[str, ...]]]:
        """Returns list of (test_instance, method_name, tier_number, category, feature_tags)."""
        suite_map = [
            (Tier1FeatureCoverage(self.client, self.config), 1, "Tier 1: Feature Coverage"),
            (Tier2BoundaryCorner(self.client, self.config), 2, "Tier 2: Boundary & Corner"),
            (Tier3CrossFeature(self.client, self.config), 3, "Tier 3: Cross-Feature Combinations"),
            (Tier4RealWorldScenarios(self.client, self.config), 4, "Tier 4: Real-World Scenarios"),
        ]

        discovered: List[Tuple[Any, str, int, str, Tuple[str, ...]]] = []
        target_feature = feature_filter.upper() if feature_filter else None

        for instance, tier_num, cat_name in suite_map:
            if tier_filter is not None and tier_num != tier_filter:
                continue
            for attr_name in sorted(dir(instance)):
                if not attr_name.startswith("test_"):
                    continue
                method = getattr(instance, attr_name)
                if not callable(method):
                    continue
                tags = tuple(getattr(method, "_features", ()))
                if not tags:
                    # Fallback infer from method name e.g. test_t1_e1_01_...
                    parts = attr_name.upper().split("_")
                    inferred = [p for p in parts if len(p) == 2 and p.startswith("E") and p[1].isdigit()]
                    tags = tuple(inferred)
                if target_feature and target_feature not in tags:
                    continue
                discovered.append((instance, attr_name, tier_num, cat_name, tags))
        return discovered

    def dry_run(
        self,
        tier_filter: Optional[int] = None,
        feature_filter: Optional[str] = None,
    ) -> int:
        tests = self._discover_tests(tier_filter, feature_filter)
        print("\n" + "=" * 80)
        print("  GESTO PAGO E2E TEST SUITE — DRY RUN DISCOVERY")
        print(f"  Selected Tier   : {tier_filter if tier_filter else 'All (Tiers 1-4)'}")
        print(f"  Selected Feature: {feature_filter if feature_filter else 'All (E1-E7)'}")
        print(f"  Discovered Tests: {len(tests)}")
        print("=" * 80)
        by_tier: dict[int, int] = {}
        current_cat = ""
        for _, method_name, tier_num, cat_name, tags in tests:
            by_tier[tier_num] = by_tier.get(tier_num, 0) + 1
            if cat_name != current_cat:
                current_cat = cat_name
                print(f"\n--- {current_cat} ---")
            tag_str = ",".join(tags) if tags else "-"
            print(f"  [DRY-RUN] {method_name:<52} [{tag_str}]")

        print("\n" + "-" * 80)
        for t in sorted(by_tier):
            print(f"  Tier {t}: {by_tier[t]} tests")
        print(f"  Total : {len(tests)} API tests")
        print("-" * 80 + "\n")
        return len(tests)

    def run_tests(
        self,
        tier_filter: Optional[int] = None,
        feature_filter: Optional[str] = None,
    ) -> bool:
        if not self._check_connectivity():
            print(f"\n[ERROR] Target server at {self.base_url} is not reachable.")
            print("        - Ensure Spring Boot is running (e.g. ./gradlew bootRun).")
            print("        - Or run with '--mock' for standalone contract validation mode.\n")
            raise ConnectionError(f"Cannot connect to target server at {self.base_url}")

        tests_to_run = self._discover_tests(tier_filter, feature_filter)
        total_tests = len(tests_to_run)

        print("\n" + "=" * 80)
        print("  GESTO PAGO E2E TEST SUITE — 4-TIER OPAQUE-BOX VALIDATION (F1-F20)")
        print(f"  Target Base URL : {self.base_url}")
        print(f"  Mode            : {'MOCK' if self.config.is_mock else ('LIVE-SAFE' if self.config.live_safe else 'LIVE')}")
        print(f"  Selected Tier   : {tier_filter if tier_filter else 'All (Tiers 1-4)'}")
        print(f"  Selected Feature: {feature_filter if feature_filter else 'All (E1-E7)'}")
        print(f"  Total Tests     : {total_tests}")
        print("=" * 80 + "\n")

        passed_count = 0
        failed_count = 0
        skipped_count = 0
        suite_start = time.perf_counter()
        use_color = sys.platform != "win32" or "WT_SESSION" in os.environ

        current_category = ""
        for instance, method_name, tier_num, cat_name, tags in tests_to_run:
            if cat_name != current_category:
                current_category = cat_name
                print(f"\n--- {current_category} ---")

            method = getattr(instance, method_name)
            doc = inspect.getdoc(method) or method_name
            summary = doc.split("\n")[0]

            res = TestResult(method_name, tier_num, cat_name, summary, tags)
            test_start = time.perf_counter()

            try:
                method()
                res.passed = True
                res.duration_ms = (time.perf_counter() - test_start) * 1000.0
                passed_count += 1
                status_str = "\033[92m[PASS]\033[0m" if use_color else "[PASS]"
                print(f"  {status_str} {method_name:<52} ({res.duration_ms:6.1f}ms) - {summary[:48]}")
            except SkipTest as sk:
                res.skipped = True
                res.skip_reason = str(sk)
                res.duration_ms = (time.perf_counter() - test_start) * 1000.0
                skipped_count += 1
                status_str = "\033[93m[SKIP]\033[0m" if use_color else "[SKIP]"
                print(f"  {status_str} {method_name:<52} ({res.duration_ms:6.1f}ms) - {sk}")
            except Exception as ex:
                res.passed = False
                res.duration_ms = (time.perf_counter() - test_start) * 1000.0
                res.error_message = str(ex)
                failed_count += 1
                status_str = "\033[91m[FAIL]\033[0m" if use_color else "[FAIL]"
                print(f"  {status_str} {method_name:<52} ({res.duration_ms:6.1f}ms)")
                print(f"         Error: {ex}")
                if self.verbose:
                    import traceback
                    traceback.print_exc()

            self.results.append(res)

        total_time = time.perf_counter() - suite_start
        executed_count = passed_count + failed_count

        print("\n" + "=" * 80)
        print("  TEST EXECUTION SUMMARY")
        print("=" * 80)
        print(f"  Total Discovered : {total_tests}")
        print(f"  Executed         : {executed_count}")
        print(f"  Passed           : {passed_count}")
        print(f"  Failed           : {failed_count}")
        print(f"  Skipped          : {skipped_count}")
        print(f"  Pass Rate (exec) : {(passed_count / executed_count * 100.0) if executed_count else 0:.1f}%")
        print(f"  Total Duration   : {total_time:.2f}s")
        print("=" * 80)

        if self.junit_xml:
            self._write_junit_xml(self.junit_xml, total_time)

        return failed_count == 0

    def _write_junit_xml(self, file_path: str, total_time: float) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(file_path)), exist_ok=True)
        testsuite = ET.Element(
            "testsuite",
            {
                "name": "GestoPago_E2E_TestSuite",
                "tests": str(len(self.results)),
                "failures": str(sum(1 for r in self.results if not r.passed and not r.skipped)),
                "skipped": str(sum(1 for r in self.results if r.skipped)),
                "errors": "0",
                "time": f"{total_time:.3f}",
            },
        )
        for r in self.results:
            tc = ET.SubElement(
                testsuite,
                "testcase",
                {
                    "name": r.name,
                    "classname": f"tier{r.tier}.{r.category.replace(' ', '')}",
                    "time": f"{r.duration_ms / 1000.0:.3f}",
                },
            )
            if r.skipped:
                ET.SubElement(tc, "skipped", {"message": r.skip_reason or "Skipped"})
            elif not r.passed:
                fail = ET.SubElement(tc, "failure", {"message": r.error_message or "Test failed"})
                fail.text = r.error_message

        tree = ET.ElementTree(testsuite)
        tree.write(file_path, encoding="utf-8", xml_declaration=True)
        print(f"\n  Exported JUnit XML report to: {file_path}")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Gesto Pago E2E API Test Suite Runner")
    parser.add_argument(
        "--base-url",
        default=None,
        help="Target API base URL (default: E2E_BASE_URL or http://localhost:10000)",
    )
    parser.add_argument(
        "--tier",
        default="all",
        choices=["1", "2", "3", "4", "all"],
        help="Select test tier to execute (1, 2, 3, 4, or all)",
    )
    parser.add_argument(
        "--feature",
        default=None,
        type=str.upper,
        choices=["E1", "E2", "E3", "E4", "E5", "E6", "E7"],
        help="Filter tests by feature area (E1..E7)",
    )
    parser.add_argument(
        "--mock",
        action="store_true",
        help="Launch embedded F1-F9 contract mock server for standalone verification",
    )
    parser.add_argument(
        "--mock-port",
        type=int,
        default=10000,
        help="Port for mock server (default: 10000)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Discover and list all tests without executing network requests",
    )
    parser.add_argument(
        "--live-safe",
        action="store_true",
        help="Skip live payment mutations unless --allow-live-payments is set",
    )
    parser.add_argument(
        "--allow-live-payments",
        action="store_true",
        help="Explicitly authorize real sandbox payment execution in live mode",
    )
    parser.add_argument("--otp", default=None, help="Inject 6-digit 2FA OTP for live payment verification")
    parser.add_argument("--username", default=None, help="Primary account username")
    parser.add_argument("--password", default=None, help="Primary account password")
    parser.add_argument("--secondary-username", default=None, help="Secondary account username for IDOR tests")
    parser.add_argument("--secondary-password", default=None, help="Secondary account password for IDOR tests")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose tracebacks")
    parser.add_argument("--junit-xml", default=None, help="Output path for JUnit XML report")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_arg_parser()
    try:
        args = parser.parse_args(argv)
    except SystemExit as ex:
        return int(ex.code) if ex.code is not None else 3

    tier_filter = int(args.tier) if args.tier != "all" else None
    config = E2EConfig.from_env_and_args(args)

    if args.dry_run:
        runner = TestRunner(config=config, junit_xml=args.junit_xml)
        runner.dry_run(tier_filter=tier_filter, feature_filter=args.feature)
        return 0

    mock_manager: Optional[MockServerManager] = None
    if args.mock:
        mock_port = args.mock_port
        try:
            mock_manager = MockServerManager(port=mock_port, expose_otp=False)
            mock_manager.start()
            config.base_url = f"http://127.0.0.1:{mock_port}"
            time.sleep(0.1)
            print(f"[MOCK] Embedded F1-F9 mock server listening at {config.base_url}")
        except Exception as ex:
            print(f"[MOCK] Port {mock_port} unavailable ({ex}), falling back to ephemeral port...")
            sock = socket.socket()
            sock.bind(("127.0.0.1", 0))
            fallback_port = sock.getsockname()[1]
            sock.close()
            mock_manager = MockServerManager(port=fallback_port, expose_otp=False)
            mock_manager.start()
            config.base_url = f"http://127.0.0.1:{fallback_port}"
            time.sleep(0.1)
            print(f"[MOCK] Embedded F1-F9 mock server listening at {config.base_url}")

    runner = TestRunner(config=config, junit_xml=args.junit_xml)
    try:
        success = runner.run_tests(tier_filter=tier_filter, feature_filter=args.feature)
        exit_code = 0 if success else 1
    except ConnectionError:
        exit_code = 2
    except Exception as ex:
        print(f"\n[FATAL ERROR] Test runner aborted: {ex}")
        exit_code = 3
    finally:
        if mock_manager:
            print("[MOCK] Shutting down embedded mock server...")
            mock_manager.stop()

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
