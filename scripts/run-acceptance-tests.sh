#!/usr/bin/env zsh
# =============================================================================
# ACME Inc. Acceptance Test Runner
# =============================================================================
# Runs acceptance tests and generates HTML reports.
#
# Usage: ./scripts/run-acceptance-tests.sh [options] [cucumber-args]
#
# Options:
#   --smoke         Run only smoke tests (@smoke tag)
#   --regression    Run full regression suite (@regression tag)
#   --customer      Run customer app tests only
#   --admin         Run admin app tests only
#   --api           Run API tests only
#   --headed        Run with visible browser
#   --skip-install  Skip dependency install step
#   --no-open       Don't open browser with results
#   --quiet, -q     Minimal output (progress bar only, no scenario names)
#   --help          Show this help message
#
# Examples:
#   ./scripts/run-acceptance-tests.sh                    # Run all tests
#   ./scripts/run-acceptance-tests.sh --smoke            # Run smoke tests
#   ./scripts/run-acceptance-tests.sh --headed           # Run with visible browser
#   ./scripts/run-acceptance-tests.sh --api --no-open    # Run API tests, don't open browser
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ACCEPTANCE_TESTS_DIR="${PROJECT_ROOT}/acceptance-tests"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default options
SKIP_INSTALL=false
SKIP_SERVICE_CHECK=false
OPEN_BROWSER=true
HEADED=false
QUIET=false
TEST_PROFILE="default"
EXTRA_ARGS=()
TAGS=""

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

print_header() {
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

show_help() {
    cat << EOF
${BLUE}ACME Inc. Acceptance Test Runner${NC}

${YELLOW}Usage:${NC} ./scripts/run-acceptance-tests.sh [options] [cucumber-args]

${YELLOW}Test Selection:${NC}
  --smoke           Run only smoke tests (@smoke tag)
  --regression      Run full regression suite (@regression tag)
  --customer        Run customer app tests only
  --admin           Run admin app tests only
  --api             Run API tests only (@api tag)

${YELLOW}Execution Options:${NC}
  --headed             Run with visible browser (not headless)
  --skip-install       Skip dependency install step
  --skip-service-check Skip the application-services readiness check
  --no-open            Don't automatically open browser with results
  --quiet, -q       Minimal output (progress bar only, no scenario names)

${YELLOW}Other:${NC}
  --help            Show this help message

${YELLOW}Output:${NC}
  By default, shows scenario names with progress counts (e.g., [1/10] Running: Login test).
  Use --quiet for minimal output (progress bar only).

${YELLOW}Examples:${NC}
  ./scripts/run-acceptance-tests.sh                    # Run all tests
  ./scripts/run-acceptance-tests.sh --smoke            # Run smoke tests only
  ./scripts/run-acceptance-tests.sh --headed           # Run with visible browser
  ./scripts/run-acceptance-tests.sh --api --no-open    # Run API tests, don't open browser

${YELLOW}Reports:${NC}
  HTML Report:    acceptance-tests/reports/cucumber-report.html
  JSON Report:    acceptance-tests/reports/cucumber-report.json

${YELLOW}Prerequisites:${NC}
  - Bun 1.2+ (https://bun.sh) - runs the TypeScript suite directly
  - Application services must be running (./scripts/docker-manage.sh start)
    The runner verifies this before tests; bypass with --skip-service-check

EOF
}

# -----------------------------------------------------------------------------
# Bun Setup
# -----------------------------------------------------------------------------

setup_bun() {
    print_info "Setting up Bun environment..."

    if ! command -v bun &> /dev/null; then
        print_error "Bun is not installed or not in PATH"
        print_info "Install it with: curl -fsSL https://bun.sh/install | bash"
        exit 1
    fi

    local bun_version
    bun_version=$(bun --version)

    # Require Bun 1.2+, which is the baseline this suite is tested against.
    local major minor
    major=$(echo "$bun_version" | cut -d. -f1)
    minor=$(echo "$bun_version" | cut -d. -f2)
    if [[ "$major" -lt 1 ]] || { [[ "$major" -eq 1 ]] && [[ "$minor" -lt 2 ]]; }; then
        print_error "Bun 1.2+ is required, but found $bun_version"
        print_info "Upgrade with: bun upgrade"
        exit 1
    fi

    print_success "Using Bun $bun_version"
}

# -----------------------------------------------------------------------------
# Dependencies
# -----------------------------------------------------------------------------

install_dependencies() {
    if [[ "$SKIP_INSTALL" == true ]]; then
        print_info "Skipping dependency install (--skip-install)"
        return
    fi

    print_info "Installing dependencies..."

    cd "$ACCEPTANCE_TESTS_DIR"

    # bun install is fast and idempotent, so it is safe to run on every
    # invocation; --frozen-lockfile keeps it honest against bun.lock.
    bun install --frozen-lockfile --silent
    print_success "Dependencies installed"
}

# -----------------------------------------------------------------------------
# Service Readiness
# -----------------------------------------------------------------------------

# Verify the application services the acceptance tests depend on are reachable.
# Fails fast with guidance rather than running a suite that is doomed to fail.
# Ports/URLs default to the same values used by acceptance-tests/playwright.config.ts
# and can be overridden via the matching environment variables.
check_services() {
    if [[ "$SKIP_SERVICE_CHECK" == true ]]; then
        print_info "Skipping application service check (--skip-service-check)"
        return
    fi

    print_info "Checking application services..."

    # Required services as "name|url" entries (zsh-friendly parallel-style array).
    # The admin frontend is intentionally excluded: it is not started by
    # ./scripts/docker-manage.sh start (commented out in docker-compose.apps.yml).
    local services=(
        "Identity Service|${IDENTITY_API_URL:-http://localhost:10300}/actuator/health"
        "Customer Service|${CUSTOMER_API_URL:-http://localhost:10301}/actuator/health"
        "Notification Service|${NOTIFICATION_API_URL:-http://localhost:10302}/actuator/health"
        "Product Service|${PRODUCT_API_URL:-http://localhost:10303}/actuator/health"
        "Customer Frontend|${CUSTOMER_APP_URL:-http://localhost:7600}/"
    )

    local down=()
    local entry name url
    for entry in "${services[@]}"; do
        name="${entry%%|*}"
        url="${entry#*|}"
        if curl -sf -o /dev/null --max-time 5 "$url" &>/dev/null; then
            print_success "$name: up"
        else
            print_error "$name: not reachable ($url)"
            down+=("$name")
        fi
    done

    if [[ ${#down[@]} -gt 0 ]]; then
        echo ""
        print_error "Application services are not running: ${down[*]}"
        print_info "Start them with: ./scripts/docker-manage.sh start"
        print_info "Or bypass this check with: --skip-service-check"
        exit 1
    fi

    print_success "All required application services are up"
}

# -----------------------------------------------------------------------------
# Test Execution
# -----------------------------------------------------------------------------

run_tests() {
    print_header "Running Acceptance Tests"

    cd "$ACCEPTANCE_TESTS_DIR"

    # Build the command as an array
    local cmd_array=("bun" "./node_modules/@cucumber/cucumber/bin/cucumber.js")

    # Import support and step definition files
    cmd_array+=("--import" "support/world.ts")
    cmd_array+=("--import" "support/hooks.ts")
    cmd_array+=("--import" "steps/**/*.ts")

    # Add report formats
    cmd_array+=("--format" "json:reports/cucumber-report.json")
    cmd_array+=("--format" "html:reports/cucumber-report.html")

    # Add progress output (must be last)
    if [[ "$QUIET" == true ]]; then
        cmd_array+=("--format" "progress-bar")
    else
        # Custom progress formatter for detailed scenario-level output
        cmd_array+=("--format" "./support/progress-formatter.ts")
    fi

    # Add profile-specific tags or paths
    local FEATURE_PATHS=""
    case "$TEST_PROFILE" in
        smoke)
            TAGS="@smoke"
            ;;
        regression)
            TAGS="@regression"
            ;;
        customer)
            FEATURE_PATHS="features/customer/**/*.feature"
            ;;
        api)
            TAGS="@api"
            ;;
    esac

    # Add tags if specified
    # Note: @rate-limiting tests require RATE_LIMITING_ENABLED=true on Identity Service
    # They are excluded by default since acceptance tests typically run with rate limiting disabled
    # Note: @wip tests require additional infrastructure (database access) and are excluded by default
    if [[ -n "$TAGS" ]]; then
        cmd_array+=("--tags" "$TAGS and not @rate-limiting and not @wip")
    else
        cmd_array+=("--tags" "not @rate-limiting and not @wip")
    fi

    # Add extra arguments
    if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
        cmd_array+=("${EXTRA_ARGS[@]}")
    fi

    # Add feature paths (must be last)
    if [[ -n "$FEATURE_PATHS" ]]; then
        cmd_array+=("$FEATURE_PATHS")
    fi

    # Set environment variables
    export NODE_ENV=test
    if [[ "$HEADED" == true ]]; then
        export HEADED=true
        print_info "Running in headed mode (visible browser)"
    fi

    # Clean previous reports
    rm -rf reports/*.html reports/*.json 2>/dev/null || true
    mkdir -p reports

    print_info "Test profile: $TEST_PROFILE"
    if [[ -n "$TAGS" ]]; then
        print_info "Tags: $TAGS"
    fi
    echo ""

    # Run tests
    local exit_code=0
    "${cmd_array[@]}" || exit_code=$?

    return $exit_code
}

# -----------------------------------------------------------------------------
# Reports
# -----------------------------------------------------------------------------

open_reports() {
    if [[ "$OPEN_BROWSER" != true ]]; then
        print_info "Skipping browser open (--no-open)"
        return
    fi

    cd "$ACCEPTANCE_TESTS_DIR"

    print_header "Opening HTML Report"

    local report_file="${ACCEPTANCE_TESTS_DIR}/reports/cucumber-report.html"

    if [[ ! -f "$report_file" ]]; then
        print_warning "HTML report not found at: $report_file"
        return
    fi

    print_success "Report generated: $report_file"

    # Open in browser based on OS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "$report_file"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        if command -v xdg-open &> /dev/null; then
            xdg-open "$report_file"
        elif command -v gnome-open &> /dev/null; then
            gnome-open "$report_file"
        else
            print_info "Open manually: $report_file"
        fi
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        start "$report_file"
    else
        print_info "Open manually: $report_file"
    fi
}

# -----------------------------------------------------------------------------
# Parse Arguments
# -----------------------------------------------------------------------------

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --smoke)
                TEST_PROFILE="smoke"
                shift
                ;;
            --regression)
                TEST_PROFILE="regression"
                shift
                ;;
            --customer)
                TEST_PROFILE="customer"
                shift
                ;;
            --admin)
                TEST_PROFILE="admin"
                shift
                ;;
            --api)
                TEST_PROFILE="api"
                shift
                ;;
            --headed)
                HEADED=true
                shift
                ;;
            --skip-install)
                SKIP_INSTALL=true
                shift
                ;;
            --skip-service-check)
                SKIP_SERVICE_CHECK=true
                shift
                ;;
            --no-open)
                OPEN_BROWSER=false
                shift
                ;;
            --quiet|-q)
                QUIET=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                EXTRA_ARGS+=("$1")
                shift
                ;;
        esac
    done
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    parse_args "$@"

    print_header "ACME Inc. Acceptance Test Runner"

    # Check if acceptance tests directory exists
    if [[ ! -d "$ACCEPTANCE_TESTS_DIR" ]]; then
        print_error "Acceptance tests directory not found: $ACCEPTANCE_TESTS_DIR"
        exit 1
    fi

    # Setup
    setup_bun
    install_dependencies

    # Verify application services are up before running the suite
    check_services

    # Run tests
    local test_exit_code=0
    run_tests || test_exit_code=$?

    # Show results
    echo ""
    if [[ $test_exit_code -eq 0 ]]; then
        print_success "All tests passed!"
    else
        print_error "Some tests failed (exit code: $test_exit_code)"
    fi

    # Open reports
    open_reports

    exit $test_exit_code
}

main "$@"
