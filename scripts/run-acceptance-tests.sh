#!/usr/bin/env bash
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
#   --skip-install  Skip npm install step
#   --allure        Use Allure reports instead of HTML
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
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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
USE_ALLURE=false
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
  --headed          Run with visible browser (not headless)
  --skip-install    Skip npm install step
  --allure          Use Allure reports (opens interactive server)
  --no-open         Don't automatically open browser with results
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
  ./scripts/run-acceptance-tests.sh --allure           # Run all, use Allure reports

${YELLOW}Reports:${NC}
  HTML Report:    acceptance-tests/reports/cucumber-report.html
  JSON Report:    acceptance-tests/reports/cucumber-report.json
  Allure Results: acceptance-tests/allure-results/

${YELLOW}Prerequisites:${NC}
  - Node.js 24+ (LTS/Krypton) - uses nvm if available
  - Application services should be running (./scripts/docker-manage.sh start)

EOF
}

# -----------------------------------------------------------------------------
# Node.js Setup
# -----------------------------------------------------------------------------

setup_node() {
    print_info "Setting up Node.js environment..."

    # First check if node is already available and meets requirements
    if command -v node &> /dev/null; then
        local node_version
        node_version=$(node --version)
        local major_version
        major_version=$(echo "$node_version" | sed 's/v//' | cut -d. -f1)

        if [[ "$major_version" -ge 24 ]]; then
            print_success "Using Node.js $node_version"
            return 0
        fi
    fi

    # Node not found or version too old - try version managers
    local nvmrc_file="${ACCEPTANCE_TESTS_DIR}/.nvmrc"
    local required_version=""

    if [[ -f "$nvmrc_file" ]]; then
        required_version=$(cat "$nvmrc_file" | tr -d '[:space:]')
        print_info "Required Node.js version: $required_version"
    fi

    # Try nvm if available
    if [[ -s "$HOME/.nvm/nvm.sh" ]]; then
        # shellcheck source=/dev/null
        source "$HOME/.nvm/nvm.sh" 2>/dev/null

        if [[ -n "$required_version" ]]; then
            print_info "Using nvm to set Node.js version..."
            if nvm use "$required_version" 2>/dev/null; then
                print_success "Node.js version set via nvm"
            else
                print_warning "Could not set Node.js version via nvm"
            fi
        fi
    elif command -v fnm &> /dev/null; then
        print_info "Using fnm to set Node.js version..."
        if [[ -n "$required_version" ]]; then
            fnm use "$required_version" 2>/dev/null || true
        fi
    fi

    # Final verification
    if ! command -v node &> /dev/null; then
        print_error "Node.js is not installed or not in PATH"
        print_info "Please install Node.js 24+ or use nvm/fnm"
        exit 1
    fi

    local node_version
    node_version=$(node --version)
    print_success "Using Node.js $node_version"

    local major_version
    major_version=$(echo "$node_version" | sed 's/v//' | cut -d. -f1)
    if [[ "$major_version" -lt 24 ]]; then
        print_error "Node.js 24+ is required, but found $node_version"
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Dependencies
# -----------------------------------------------------------------------------

install_dependencies() {
    if [[ "$SKIP_INSTALL" == true ]]; then
        print_info "Skipping npm install (--skip-install)"
        return
    fi

    print_info "Installing dependencies..."

    cd "$ACCEPTANCE_TESTS_DIR"

    # Check if node_modules exists and package-lock.json hasn't changed
    if [[ -d "node_modules" ]] && [[ -f "node_modules/.package-lock.json" ]]; then
        if diff -q package-lock.json node_modules/.package-lock.json &>/dev/null; then
            print_success "Dependencies up to date"
            return
        fi
    fi

    npm ci --silent
    print_success "Dependencies installed"
}

# -----------------------------------------------------------------------------
# Test Execution
# -----------------------------------------------------------------------------

run_tests() {
    print_header "Running Acceptance Tests"

    cd "$ACCEPTANCE_TESTS_DIR"

    # Build the command as an array
    local cmd_array=("node" "--import" "tsx" "./node_modules/@cucumber/cucumber/bin/cucumber.js")

    # Import support and step definition files
    cmd_array+=("--import" "support/world.ts")
    cmd_array+=("--import" "support/hooks.ts")
    cmd_array+=("--import" "steps/**/*.ts")

    # Add report formats
    # IMPORTANT: Allure must be added BEFORE progress formatter or it swallows stdout
    if [[ "$USE_ALLURE" == true ]]; then
        cmd_array+=("--format" "allure-cucumberjs/reporter")
    fi
    cmd_array+=("--format" "json:reports/cucumber-report.json")
    cmd_array+=("--format" "html:reports/cucumber-report.html")

    # Add progress output (must be LAST - after allure if used)
    if [[ "$QUIET" == true ]]; then
        cmd_array+=("--format" "progress-bar")
    else
        # Custom progress formatter for detailed scenario-level output
        cmd_array+=("--format" "./support/progress-formatter.ts")
    fi

    # Add profile-specific paths
    case "$TEST_PROFILE" in
        smoke)
            TAGS="@smoke"
            ;;
        regression)
            TAGS="@regression"
            ;;
        customer)
            cmd_array+=("--paths" "features/customer/**/*.feature")
            ;;
        admin)
            cmd_array+=("--paths" "features/admin/**/*.feature")
            ;;
        api)
            TAGS="@api"
            ;;
    esac

    # Add tags if specified
    # Note: @rate-limiting tests require RATE_LIMITING_ENABLED=true on Identity Service
    # They are excluded by default since acceptance tests typically run with rate limiting disabled
    if [[ -n "$TAGS" ]]; then
        cmd_array+=("--tags" "$TAGS and not @rate-limiting")
    else
        cmd_array+=("--tags" "not @rate-limiting")
    fi

    # Add extra arguments
    if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
        cmd_array+=("${EXTRA_ARGS[@]}")
    fi

    # Set environment variables
    export NODE_ENV=test
    if [[ "$HEADED" == true ]]; then
        export HEADED=true
        print_info "Running in headed mode (visible browser)"
    fi

    # Clean previous reports
    rm -rf reports/*.html reports/*.json allure-results/* 2>/dev/null || true
    mkdir -p reports allure-results

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

    if [[ "$USE_ALLURE" == true ]]; then
        print_header "Opening Allure Report"

        # Check if allure is installed
        if ! command -v allure &> /dev/null; then
            print_warning "Allure CLI not found. Installing via npm..."
            npm install -g allure-commandline || {
                print_error "Could not install Allure CLI"
                print_info "Install manually: npm install -g allure-commandline"
                print_info "Or view HTML report at: reports/cucumber-report.html"
                return
            }
        fi

        print_info "Starting Allure server..."
        allure serve allure-results
    else
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
            --allure)
                USE_ALLURE=true
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
    setup_node
    install_dependencies

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
