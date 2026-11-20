# Test Infrastructure Specification: Gesto Pago Payment Platform

## 1. Test Architecture Overview

The End-to-End (E2E) testing framework provides an opaque-box validation suite for the Gesto Pago Service Payment Platform. It evaluates the complete application behavior strictly through its public HTTP API contracts, enforcing financial transaction integrity, security boundaries, and user journey parity across Web and Flutter clients.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        E2E Test Runner CLI                             │
│                      (e2e-tests/run_e2e_tests.py)                      │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         ▼                         ▼                         ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│     Tier 1      │       │     Tier 2      │       │  Tiers 3 & 4    │
│Feature Coverage │       │Boundary & Corner│       │ Cross-Feature & │
│ (>=5 tests/feat)│       │ (>=5 tests/feat)│       │ Real Workflows  │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         └─────────────────────────┼─────────────────────────┘
                                   │
                                   ▼
                  ┌─────────────────────────────────┐
                  │    HTTP Client & Assertion Core │
                  │       (e2e-tests/test_client.py)│
                  └────────────────┬────────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   Live Spring Boot Server                   Deterministic Mock Server
   (http://localhost:10000)                  (e2e-tests/mock_server.py)
   Neon Postgres / GestoPago                 Contract & Schema Validation
```

### 1.1 Key Architectural Principles
1. **Opaque-Box Testing**: Tests interact solely via HTTP requests, validating headers, status codes, JSON schema response bodies, and timing. No internal classes or mock databases are reached directly by test cases.
2. **Deterministic & Self-Contained**: Each test sets up its own test data (creating dynamic test users, account references, or isolated transactions) without cross-test state leakage.
3. **Dual Execution Modes**:
   - **Live Mode**: Executes directly against the running Spring Boot server on `http://localhost:10000` (configurable via `--base-url`).
   - **Standalone / Contract Validation Mode**: A built-in high-fidelity simulated server (`--mock`) allows CI verification, offline validation, and instant contract auditing without external network dependencies.
4. **Authoritative Output Derivation**: Expected outputs are derived directly from `ORIGINAL_REQUEST.md`, `PROJECT.md` Interface Contracts, Gesto Pago API specifications, and Mexican banking security requirements.

---

## 2. Test Runner & CLI Interface

The test suite is driven by `e2e-tests/run_e2e_tests.py`, an automated CLI runner requiring Python 3.11+.

### 2.1 CLI Usage
```bash
# Run all 4 tiers against live server (http://localhost:10000)
python e2e-tests/run_e2e_tests.py

# Run against custom base URL
python e2e-tests/run_e2e_tests.py --base-url http://localhost:10000

# Run specific tier
python e2e-tests/run_e2e_tests.py --tier 1
python e2e-tests/run_e2e_tests.py --tier 2
python e2e-tests/run_e2e_tests.py --tier 3
python e2e-tests/run_e2e_tests.py --tier 4

# Run with verbose output and test timing
python e2e-tests/run_e2e_tests.py --verbose

# Run with built-in mock server (contract verification mode)
python e2e-tests/run_e2e_tests.py --mock

# Export JUnit XML report for CI/SonarQube integration
python e2e-tests/run_e2e_tests.py --junit-xml build/e2e-reports/results.xml
```

### 2.2 CLI Exit Codes
- `0`: All executed tests passed successfully.
- `1`: One or more tests failed assertion or encountered an unexpected HTTP status.
- `2`: Connection error (target server unreachable and `--mock` not specified).
- `3`: Configuration or CLI argument error.

---

## 3. Four-Tier Test Taxonomy

| Tier | Name | Target Scope | Minimum Required | Actual Implemented |
|---|---|---|---|---|
| **Tier 1** | Feature Coverage | Happy path and primary contracts across R1-R5 | >= 5 tests per feature | **27 tests** |
| **Tier 2** | Boundary & Corner Cases | Extreme values, invalid formats, idempotency, concurrency | >= 5 tests per feature | **26 tests** |
| **Tier 3** | Cross-Feature Combinations | Pairwise multi-step feature interactions | End-to-end multi-step flows | **5 comprehensive flows** |
| **Tier 4** | Real-World Application Scenarios | Complete end-to-end user workflows (CFE, Telcel, Lockout, etc.) | High-impact user scenarios | **5 user journeys** |
| **Total** | Full E2E Suite | Complete R1-R7 coverage | >= 50 tests | **63 tests** |

---

## 4. Feature Inventory & Requirement Traceability Matrix

| Requirement | Feature Description | Tier 1 Tests | Tier 2 Tests | Tier 3 Tests | Tier 4 Tests | Total Tests |
|---|---|---|---|---|---|---|
| **R1** | Client Authentication & Session (`login_clientes`, SHA-256, Logout, Profile) | T1_AUTH_01 to T1_AUTH_06 (6) | T2_AUTH_01 to T2_AUTH_05 (5) | T3_COMB_01, T3_COMB_05 (2) | T4_SCEN_04 (1) | **14** |
| **R2** | Dynamic Catalog (`/productos`, `/productos/agrupados`, CDN Logos) | T1_CAT_01 to T1_CAT_05 (5) | T2_CAT_01 to T2_CAT_05 (5) | T3_COMB_01, T3_COMB_04 (2) | T4_SCEN_01, T4_SCEN_02 (2) | **14** |
| **R3** | Reference Validation (`/validar-referencia`, `tipoFront` 1 & 2, check digits) | T1_VAL_01 to T1_VAL_05 (5) | T2_VAL_01 to T2_VAL_05 (5) | T3_COMB_01, T3_COMB_02 (2) | T4_SCEN_01, T4_SCEN_02 (2) | **14** |
| **R4** | 2FA OTP & Payment Execution (300s TTL, single-use, atomic deduction, idempotency) | T1_PAY_01 to T1_PAY_06 (6) | T2_PAY_01 to T2_PAY_06 (6) | T3_COMB_01, T3_COMB_03 (2) | T4_SCEN_01, T4_SCEN_02, T4_SCEN_03, T4_SCEN_05 (4) | **18** |
| **R5** | Transaction History (User account isolation, chronological sort, logoUrl, estado) | T1_HIST_01 to T1_HIST_05 (5) | T2_HIST_01 to T2_HIST_05 (5) | T3_COMB_01, T3_COMB_04 (2) | T4_SCEN_01, T4_SCEN_03 (2) | **14** |
| **R6** | Web & Flutter Parity & CORS Configuration | T1_AUTH_01, T1_CAT_01, T1_VAL_01 | T2_CAT_02 (UTF-8), CORS verification | T3_COMB_01 | T4_SCEN_01, T4_SCEN_02 | **Covered in all** |
| **R7** | Quality Assurance & Pass Thresholds | Suite health & JUnit XML | Suite health & JUnit XML | Suite health & JUnit XML | Suite health & JUnit XML | **Suite-wide** |

---

## 5. Pass/Fail Thresholds & Verification Criteria

1. **Pass Rate**: 100% of non-skipped test cases must pass. Zero regressions allowed.
2. **Response Time Thresholds**:
   - Authentication & Me endpoints: `< 1,500 ms`
   - Dynamic Catalog retrieval: `< 2,500 ms` (cached `< 200 ms`)
   - Reference validation: `< 800 ms`
   - 2FA generation: `< 800 ms`
   - Payment transaction execution: `< 5,000 ms`
   - History query: `< 1,000 ms`
3. **Data Integrity Criteria**:
   - Balance deductions must match exact transaction amounts: `NewBalance == OldBalance - Monto`.
   - Ticket folios must match the exact regex pattern: `^FT-\d{8,14}-[A-Za-z0-9]+$`.
   - OTP codes must be strictly 6 numeric digits: `^\d{6}$`.
   - Single-use OTP: A reused code must be rejected with HTTP 401.
   - Account isolation: Transaction history must contain zero records from other account numbers.
