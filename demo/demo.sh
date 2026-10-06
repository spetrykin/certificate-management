#!/usr/bin/env bash
#
# Live terminal walkthrough of certificate-management, run against a real,
# already-running instance of the app (Docker Compose or the Testcontainers
# dev-run path — see README.md). Every request below is a real HTTP call;
# nothing here is mocked or pre-recorded.
#
# Usage:
#   ./demo/demo.sh                 interactive: press Enter between steps
#   AUTO=1 ./demo/demo.sh          auto-paced: sleeps PACE seconds instead
#   PACE=3 AUTO=1 ./demo/demo.sh   auto-paced with a custom pause length
#   BASE_URL=http://host:port ./demo/demo.sh   point at a non-default app
#
# Requires: curl, python3 (used for JSON pretty-printing and field
# extraction — no jq dependency).

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
AUTO="${AUTO:-0}"
PACE="${PACE:-2}"

BOLD=$'\033[1m'
DIM=$'\033[2m'
CYAN=$'\033[36m'
GREEN=$'\033[32m'
YELLOW=$'\033[33m'
RED=$'\033[31m'
RESET=$'\033[0m'

pause() {
    if [ "$AUTO" = "1" ]; then
        sleep "$PACE"
    else
        read -r -p "${DIM}-- press Enter to continue --${RESET}" _
    fi
}

section() {
    echo
    echo "${BOLD}${CYAN}=== $1 ===${RESET}"
    if [ -n "${2:-}" ]; then
        echo "${DIM}$2${RESET}"
    fi
    echo
}

pretty() {
    python3 -m json.tool 2>/dev/null || cat
}

field() {
    # field '<json>' 'dotted.path' -> value, or empty string if absent
    python3 -c '
import json, sys
try:
    d = json.loads(sys.argv[1])
    for p in sys.argv[2].split("."):
        d = d[p]
    print(d)
except Exception:
    print("", end="")
' "$1" "$2"
}

# req METHOD PATH [AUTH_TOKEN] [JSON_BODY]
# Prints the curl command as it would be typed, executes it, prints the
# status and pretty-printed body, and leaves the raw body in $LAST_BODY
# and the status code in $LAST_STATUS for the caller to inspect.
req() {
    local method="$1" path="$2" token="${3:-}" body="${4:-}"
    local -a curl_args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
    local shown="curl -s -X $method $BASE_URL$path"
    if [ -n "$token" ]; then
        curl_args+=(-H "Authorization: Bearer $token")
        shown="$shown -H 'Authorization: Bearer <token>'"
    fi
    if [ -n "$body" ]; then
        curl_args+=(-d "$body")
        shown="$shown -d '$body'"
    fi

    echo "${YELLOW}\$ ${shown}${RESET}"
    local response
    response=$(curl "${curl_args[@]}")
    LAST_STATUS="${response##*$'\n'}"
    LAST_BODY="${response%$'\n'*}"
    echo "${DIM}< HTTP ${LAST_STATUS}${RESET}"
    if [ -n "$LAST_BODY" ]; then
        echo "$LAST_BODY" | pretty
    fi
    echo
}

status_tag() {
    # status_tag <expected> <actual> -> colored OK/UNEXPECTED marker
    if [ "$1" = "$2" ]; then
        echo "${GREEN}(expected: $2)${RESET}"
    else
        echo "${RED}(expected $1, got $2 — see output above)${RESET}"
    fi
}

echo "${BOLD}certificate-management — live demo${RESET}"
echo "${DIM}Target: ${BASE_URL}${RESET}"
echo

# --- Preflight -------------------------------------------------------------
if ! curl -s -o /dev/null -w '' --max-time 3 "$BASE_URL/swagger-ui.html" 2>/dev/null; then
    echo "${RED}Cannot reach ${BASE_URL}.${RESET}"
    echo "Start the app first — either:"
    echo "  docker compose up --build"
    echo "or:"
    echo "  ./mvnw spring-boot:test-run   (Testcontainers dev-run, no Docker Compose needed)"
    exit 1
fi
echo "${GREEN}App is reachable at ${BASE_URL}.${RESET}"
pause

# --- 1. Login as ADMIN ------------------------------------------------------
section "1. Authenticate as ADMIN" \
    "JWT-based login. The demo user store (DemoUserDetailsConfig) has exactly two hardcoded users, ADMIN and VIEWER, BCrypt-hashed — see the class Javadoc for why that's a deliberate one-week cut, not an oversight."

req POST /auth/login "" '{"username":"admin","password":"admin-demo-pw"}'
ADMIN_TOKEN=$(field "$LAST_BODY" token)
if [ -z "$ADMIN_TOKEN" ]; then
    echo "${RED}Login failed — aborting demo.${RESET}"
    exit 1
fi
echo "${GREEN}Got an ADMIN JWT.${RESET}"
pause

# --- 2. Create a device -----------------------------------------------------
section "2. Create a device" \
    "POST /api/devices requires ADMIN. Device is deliberately minimal — identifier only, no metadata, per CLAUDE.md's cut list."

req POST /api/devices "$ADMIN_TOKEN" '{"identifier":"edge-gateway-demo-001"}'
DEVICE_ID=$(field "$LAST_BODY" id)
echo "Device id: ${BOLD}${DEVICE_ID}${RESET}"
pause

# --- 3. Create a certificate ------------------------------------------------
section "3. Create a certificate for that device" \
    "Every new certificate starts in PENDING_CSR (CertificateService.createCertificate) — the only way out of PENDING_CSR is through CertificateStateMachine, never a direct setState()."

req POST /api/certificates "$ADMIN_TOKEN" "{\"deviceId\":${DEVICE_ID},\"commonName\":\"edge-gateway-demo-001.internal\"}"
CERT_ID=$(field "$LAST_BODY" id)
echo "Certificate id: ${BOLD}${CERT_ID}${RESET}, state: ${BOLD}$(field "$LAST_BODY" state)${RESET}"
pause

# --- 4. Legal transition ----------------------------------------------------
section "4. Legal transition: PENDING_CSR -> ISSUED" \
    "Allowed by CertificateStateMachine.ALLOWED_TRANSITIONS. The state change and its CertificateAuditLog row are written in the same @Transactional method — never separately."

req POST "/api/certificates/${CERT_ID}/transitions" "$ADMIN_TOKEN" \
    '{"target":"ISSUED","actor":"demo-admin","reason":"CA issued the certificate"}'
echo "New state: ${BOLD}$(field "$LAST_BODY" state)${RESET}"
pause

# --- 5. Illegal transition --------------------------------------------------
section "5. Illegal transition: ISSUED -> REVOKED" \
    "ISSUED's only legal next state is ACTIVE. This target isn't in the allowed set, so the state machine rejects it before anything is written — mapped to 409 via IllegalStateTransitionException / ApiExceptionHandler, RFC 7807 ProblemDetail body."

req POST "/api/certificates/${CERT_ID}/transitions" "$ADMIN_TOKEN" \
    '{"target":"REVOKED","actor":"demo-admin","reason":"deliberately illegal, for the demo"}'
echo "Result: ${LAST_STATUS} $(status_tag 409 "$LAST_STATUS")"
pause

# --- 6. Another legal transition, to set up the race ------------------------
section "6. Legal transition: ISSUED -> ACTIVE" \
    "Puts the certificate somewhere with more than one interesting next transition, ahead of the concurrency demo below."

req POST "/api/certificates/${CERT_ID}/transitions" "$ADMIN_TOKEN" \
    '{"target":"ACTIVE","actor":"demo-admin","reason":"activating for demo"}'
echo "New state: ${BOLD}$(field "$LAST_BODY" state)${RESET}"
pause

# --- 7. Concurrency: optimistic lock ----------------------------------------
section "7. Concurrent transition attempts on the same certificate" \
    "Two requests fire at (as close to) the same instant, both trying ACTIVE -> EXPIRING_SOON on the same certificate — modeling two overlapping scheduler scans, or two admins racing each other. Certificate.version (@Version) is the primary concurrency guard (see architecture-plan.md, Concurrency section): only one write can win. Depending on exact timing, the loser gets its 409 either from a genuine ObjectOptimisticLockingFailureException (a true version conflict) or from IllegalStateTransitionException (it reads the already-updated state and finds its own target no longer legal) — both are 409s, and either way the guarantee holds: this certificate can never be double-transitioned."

RACE_BODY='{"target":"EXPIRING_SOON","actor":"demo-admin","reason":"expiry scan (simulated race)"}'
TMP_A=$(mktemp)
TMP_B=$(mktemp)

echo "${YELLOW}\$ curl -s -X POST ${BASE_URL}/api/certificates/${CERT_ID}/transitions ... &   (request A)${RESET}"
echo "${YELLOW}\$ curl -s -X POST ${BASE_URL}/api/certificates/${CERT_ID}/transitions ... &   (request B, fired immediately after)${RESET}"

curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/api/certificates/${CERT_ID}/transitions" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ADMIN_TOKEN}" -d "$RACE_BODY" >"$TMP_A" &
PID_A=$!
curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/api/certificates/${CERT_ID}/transitions" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ADMIN_TOKEN}" -d "$RACE_BODY" >"$TMP_B" &
PID_B=$!
wait "$PID_A" "$PID_B"

RESP_A=$(<"$TMP_A"); STATUS_A="${RESP_A##*$'\n'}"; BODY_A="${RESP_A%$'\n'*}"
RESP_B=$(<"$TMP_B"); STATUS_B="${RESP_B##*$'\n'}"; BODY_B="${RESP_B%$'\n'*}"
rm -f "$TMP_A" "$TMP_B"

echo
echo "${DIM}< request A: HTTP ${STATUS_A}${RESET}"
echo "$BODY_A" | pretty
echo
echo "${DIM}< request B: HTTP ${STATUS_B}${RESET}"
echo "$BODY_B" | pretty
echo

if { [ "$STATUS_A" = "200" ] && [ "$STATUS_B" = "409" ]; } || { [ "$STATUS_A" = "409" ] && [ "$STATUS_B" = "200" ]; }; then
    echo "${GREEN}Exactly one request won, the other got 409 — the guard held.${RESET}"
elif [ "$STATUS_A" = "200" ] && [ "$STATUS_B" = "200" ]; then
    echo "${RED}Both reported 200 — the requests weren't actually concurrent this run (one fully completed, including its transaction commit, before the other's read). Re-run this step to see the race.${RESET}"
else
    echo "${YELLOW}Unexpected combination (${STATUS_A} / ${STATUS_B}) — worth a look, but note two 409s can legitimately happen if this step is re-run after the certificate already left ACTIVE.${RESET}"
fi
pause

# --- 8. Validation failure ---------------------------------------------------
section "8. Bean Validation failure" \
    "DeviceCreateRequest.identifier is @NotBlank. ApiExceptionHandler maps MethodArgumentNotValidException to 400 with field-level detail in the ProblemDetail body's 'errors' property."

req POST /api/devices "$ADMIN_TOKEN" '{"identifier":""}'
echo "Result: ${LAST_STATUS} $(status_tag 400 "$LAST_STATUS")"
pause

# --- 9. Login as VIEWER ------------------------------------------------------
section "9. Authenticate as VIEWER" \
    "Same login endpoint, a role with read-only intent. SecurityConfig's convention: GET under /api/** requires ADMIN or VIEWER; every other method requires ADMIN."

req POST /auth/login "" '{"username":"viewer","password":"viewer-demo-pw"}'
VIEWER_TOKEN=$(field "$LAST_BODY" token)
pause

# --- 10. VIEWER read access ---------------------------------------------------
section "10. VIEWER reads the certificate" \
    "Allowed — GET only requires ADMIN or VIEWER."

req GET "/api/certificates/${CERT_ID}" "$VIEWER_TOKEN"
echo "Result: ${LAST_STATUS} $(status_tag 200 "$LAST_STATUS")"
pause

# --- 11. VIEWER forbidden write ----------------------------------------------
section "11. VIEWER attempts a transition" \
    "This is the endpoint behind Day 6's real bug: an authenticated-but-wrong-role request should get 403 from AccessDeniedHandlerImpl. It briefly, silently came back as 401 instead — Spring Boot's default error handling forwards the already-decided 403 response to /error, which re-enters the security filter chain as a fresh, anonymous request; without an explicit permitAll on /error, that second unrelated pass failed authentication and its 401 overwrote the correct 403. No @WebMvcTest/MockMvc slice could have caught this — MockMvc never executes the real container-level error-page forward. Found via exactly this kind of live curl check, fixed with permitAll(\"/error\") in SecurityConfig. What you'll see below is the now-correct behavior."

req POST "/api/certificates/${CERT_ID}/transitions" "$VIEWER_TOKEN" \
    '{"target":"REVOKED","actor":"viewer-demo","reason":"should be forbidden"}'
echo "Result: ${LAST_STATUS} $(status_tag 403 "$LAST_STATUS")"
pause

# --- 12. No token at all -----------------------------------------------------
section "12. No token at all" \
    "Anonymous request to a protected endpoint — 401 from the stateless JWT filter chain, distinct from the 403 above (401 = who are you, 403 = I know who you are and it's not enough)."

req GET "/api/certificates/${CERT_ID}" ""
echo "Result: ${LAST_STATUS} $(status_tag 401 "$LAST_STATUS")"

echo
echo "${BOLD}${CYAN}=== Done ===${RESET}"
echo "Interactive API docs: ${BASE_URL}/swagger-ui.html"
echo "Full design rationale: architecture-plan.md"
