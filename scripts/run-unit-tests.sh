#!/usr/bin/env zsh
# =============================================================================
# ACME Inc. Unit Test Runner
# =============================================================================
# Runs unit tests for backend services and frontend applications.
#
# Usage: ./scripts/run-unit-tests.sh [options]
#
# Options:
#   --all             Run all tests (default if no filter specified)
#   --backend         Run all backend service tests
#   --frontend        Run all frontend app tests
#   --identity        Run identity service tests
#   --customer        Run customer service tests
#   --notification    Run notification service tests
#   --admin           Run admin frontend tests
#   --customer-app    Run customer frontend tests
#   --parallel        Run test suites in parallel (faster but mixed output)
#   --skip-install    Skip npm install for frontend tests
#   --verbose, -v     Show verbose test output
#   --quiet, -q       Minimal output (summary only)
#   --help, -h        Show this help message
#
# Examples:
#   ./scripts/run-unit-tests.sh                    # Run all tests
#   ./scripts/run-unit-tests.sh --backend          # Run all backend tests
#   ./scripts/run-unit-tests.sh --identity         # Run identity service tests only
#   ./scripts/run-unit-tests.sh --frontend         # Run all frontend tests
#   ./scripts/run-unit-tests.sh --parallel         # Run all tests in parallel
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Backend service directories
IDENTITY_DIR="${PROJECT_ROOT}/backend-services/identity"
CUSTOMER_DIR="${PROJECT_ROOT}/backend-services/customer"
NOTIFICATION_DIR="${PROJECT_ROOT}/backend-services/notification"

# Frontend app directories
ADMIN_APP_DIR="${PROJECT_ROOT}/frontend-apps/admin"
CUSTOMER_APP_DIR="${PROJECT_ROOT}/frontend-apps/customer"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Default options
RUN_IDENTITY=false
RUN_CUSTOMER=false
RUN_NOTIFICATION=false
RUN_ADMIN_APP=false
RUN_CUSTOMER_APP=false
PARALLEL=false
SKIP_INSTALL=false
VERBOSE=false
QUIET=false

# Track results (using parallel arrays for zsh compatibility)
TEST_NAMES=()
TEST_STATUSES=()
TEST_COUNTS=()
TEST_FAILURES=()
TEST_ERRORS=()
TEST_SKIPPED=()
TOTAL_SUITES_PASSED=0
TOTAL_SUITES_FAILED=0

# Helper function to record test result
# Usage: record_result <name> <status> [tests] [failures] [errors] [skipped]
record_result() {
    local name="$1"
    local test_status="$2"
    local tests="${3:-0}"
    local failures="${4:-0}"
    local errors="${5:-0}"
    local skipped="${6:-0}"
    TEST_NAMES+=("$name")
    TEST_STATUSES+=("$test_status")
    TEST_COUNTS+=("$tests")
    TEST_FAILURES+=("$failures")
    TEST_ERRORS+=("$errors")
    TEST_SKIPPED+=("$skipped")
}

# -----------------------------------------------------------------------------
# Node.js Setup
# -----------------------------------------------------------------------------

setup_node() {
    # First check if node is already available
    if command -v node &> /dev/null && command -v npm &> /dev/null; then
        return 0
    fi

    # Set NVM_DIR
    export NVM_DIR="$HOME/.nvm"

    # Try nvm if available (check multiple locations)
    if [[ -s "/opt/homebrew/opt/nvm/nvm.sh" ]]; then
        # Homebrew installation on Apple Silicon
        source "/opt/homebrew/opt/nvm/nvm.sh" 2>/dev/null
        nvm use 2>/dev/null || true
    elif [[ -s "/usr/local/opt/nvm/nvm.sh" ]]; then
        # Homebrew installation on Intel Mac
        source "/usr/local/opt/nvm/nvm.sh" 2>/dev/null
        nvm use 2>/dev/null || true
    elif [[ -s "$NVM_DIR/nvm.sh" ]]; then
        # Standard nvm installation
        source "$NVM_DIR/nvm.sh" 2>/dev/null
        nvm use 2>/dev/null || true
    elif command -v fnm &> /dev/null; then
        eval "$(fnm env)" 2>/dev/null || true
    fi

    # Final verification
    if ! command -v npm &> /dev/null; then
        print_error "npm is not installed or not in PATH"
        print_info "Please install Node.js or use nvm/fnm"
        return 1
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

print_header() {
    if [[ "$QUIET" != "true" ]]; then
        echo -e "\n${BLUE}═══════════════════════════════════════════════════════════════${NC}"
        echo -e "${BLUE}  $1${NC}"
        echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}\n"
    fi
}

print_subheader() {
    if [[ "$QUIET" != "true" ]]; then
        echo -e "\n${CYAN}───────────────────────────────────────────────────────────────${NC}"
        echo -e "${CYAN}  $1${NC}"
        echo -e "${CYAN}───────────────────────────────────────────────────────────────${NC}\n"
    fi
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
    if [[ "$QUIET" != "true" ]]; then
        echo -e "${CYAN}ℹ $1${NC}"
    fi
}

show_help() {
    cat << EOF
${BLUE}ACME Inc. Unit Test Runner${NC}

${YELLOW}Usage:${NC} ./scripts/run-unit-tests.sh [options]

${YELLOW}Test Selection:${NC}
  --all             Run all tests (default if no filter specified)
  --backend         Run all backend service tests (identity, customer, notification)
  --frontend        Run all frontend app tests (admin, customer-app)

${YELLOW}Individual Services:${NC}
  --identity        Run identity service tests only
  --customer        Run customer service tests only
  --notification    Run notification service tests only
  --admin           Run admin frontend tests only
  --customer-app    Run customer frontend tests only

${YELLOW}Execution Options:${NC}
  --parallel        Run test suites in parallel (faster but mixed output)
  --skip-install    Skip npm install for frontend tests
  --verbose, -v     Show verbose test output
  --quiet, -q       Minimal output (summary only)

${YELLOW}Other:${NC}
  --help, -h        Show this help message

${YELLOW}Examples:${NC}
  ./scripts/run-unit-tests.sh                        # Run all tests sequentially
  ./scripts/run-unit-tests.sh --backend              # Run all backend tests
  ./scripts/run-unit-tests.sh --identity --customer  # Run specific services
  ./scripts/run-unit-tests.sh --frontend --parallel  # Run frontend tests in parallel
  ./scripts/run-unit-tests.sh --all --quiet          # Run all tests with minimal output

${YELLOW}Exit Codes:${NC}
  0  All tests passed
  1  One or more tests failed
  2  Invalid arguments or configuration error

EOF
}

# -----------------------------------------------------------------------------
# Test Result Parsing
# -----------------------------------------------------------------------------

# Parse JUnit XML test reports and return counts
# Usage: parse_junit_reports <reports_dir>
# Sets: PARSED_TESTS, PARSED_FAILURES, PARSED_ERRORS, PARSED_SKIPPED
parse_junit_reports() {
    local reports_dir="$1"
    PARSED_TESTS=0
    PARSED_FAILURES=0
    PARSED_ERRORS=0
    PARSED_SKIPPED=0

    if [[ ! -d "$reports_dir" ]]; then
        return 1
    fi

    # Check if any XML files exist
    local xml_count=$(find "$reports_dir" -maxdepth 1 -name "*.xml" 2>/dev/null | wc -l)
    if [[ $xml_count -eq 0 ]]; then
        return 1
    fi

    for xml_file in "$reports_dir"/*.xml; do
        if [[ -f "$xml_file" ]]; then
            # Extract attributes from testsuite element
            local tests=$(grep -o 'tests="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*')
            local failures=$(grep -o 'failures="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*')
            local errors=$(grep -o 'errors="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*')
            local skipped=$(grep -o 'skipped="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*')

            PARSED_TESTS=$((PARSED_TESTS + ${tests:-0}))
            PARSED_FAILURES=$((PARSED_FAILURES + ${failures:-0}))
            PARSED_ERRORS=$((PARSED_ERRORS + ${errors:-0}))
            PARSED_SKIPPED=$((PARSED_SKIPPED + ${skipped:-0}))
        fi
    done

    return 0
}

# Format test counts for display
format_test_counts() {
    local tests=$1
    local failures=$2
    local errors=$3
    local skipped=$4

    local result="${tests} tests"
    if [[ $failures -gt 0 ]]; then
        result="${result}, ${failures} failed"
    fi
    if [[ $errors -gt 0 ]]; then
        result="${result}, ${errors} errors"
    fi
    if [[ $skipped -gt 0 ]]; then
        result="${result}, ${skipped} skipped"
    fi
    echo "$result"
}

# -----------------------------------------------------------------------------
# Test Runner Functions
# -----------------------------------------------------------------------------

run_gradle_tests() {
    local service_name="$1"
    local service_dir="$2"
    local start_time
    local end_time
    local duration
    local exit_code=0
    local gradle_cmd

    if [[ ! -d "$service_dir" ]]; then
        print_error "Directory not found: $service_dir"
        record_result "$service_name" "SKIPPED"
        return 1
    fi

    # Determine gradle command (prefer wrapper, fallback to system gradle)
    if [[ -x "${service_dir}/gradlew" ]]; then
        gradle_cmd="./gradlew"
    elif command -v gradle &> /dev/null; then
        gradle_cmd="gradle"
    else
        print_error "No Gradle found for ${service_name}. Install Gradle or add gradlew wrapper."
        record_result "$service_name" "SKIPPED"
        return 1
    fi

    print_subheader "Running ${service_name} tests"
    print_info "Directory: ${service_dir}"
    print_info "Using: ${gradle_cmd}"

    start_time=$(date +%s)

    # Run Gradle clean and tests (always run fresh, no caching)
    if [[ "$VERBOSE" == "true" ]]; then
        (cd "$service_dir" && $gradle_cmd clean test --info) || exit_code=$?
    elif [[ "$QUIET" == "true" ]]; then
        (cd "$service_dir" && $gradle_cmd clean test --quiet) || exit_code=$?
    else
        (cd "$service_dir" && $gradle_cmd clean test) || exit_code=$?
    fi

    end_time=$(date +%s)
    duration=$((end_time - start_time))

    # Parse test results from JUnit XML reports
    local test_counts=""
    local reports_dir="${service_dir}/build/test-results/test"
    local parsed_tests=0
    local parsed_failures=0
    local parsed_errors=0
    local parsed_skipped=0

    if parse_junit_reports "$reports_dir"; then
        parsed_tests=$PARSED_TESTS
        parsed_failures=$PARSED_FAILURES
        parsed_errors=$PARSED_ERRORS
        parsed_skipped=$PARSED_SKIPPED
        test_counts=$(format_test_counts $parsed_tests $parsed_failures $parsed_errors $parsed_skipped)
    fi

    if [[ $exit_code -eq 0 ]]; then
        if [[ -n "$test_counts" ]]; then
            print_success "${service_name}: ${test_counts} (${duration}s)"
        else
            print_success "${service_name} tests passed (${duration}s)"
        fi
        record_result "$service_name" "PASSED" "$parsed_tests" "$parsed_failures" "$parsed_errors" "$parsed_skipped"
        ((TOTAL_SUITES_PASSED++))
    else
        if [[ -n "$test_counts" ]]; then
            print_error "${service_name}: ${test_counts} (${duration}s)"
        else
            print_error "${service_name} tests failed (${duration}s)"
        fi
        record_result "$service_name" "FAILED" "$parsed_tests" "$parsed_failures" "$parsed_errors" "$parsed_skipped"
        ((TOTAL_SUITES_FAILED++))
    fi

    return $exit_code
}

run_npm_tests() {
    local app_name="$1"
    local app_dir="$2"
    local start_time
    local end_time
    local duration
    local exit_code=0

    if [[ ! -d "$app_dir" ]]; then
        print_error "Directory not found: $app_dir"
        record_result "$app_name" "SKIPPED" 0 0 0 0
        return 1
    fi

    # Ensure Node.js/npm is available
    if ! setup_node; then
        record_result "$app_name" "SKIPPED" 0 0 0 0
        return 1
    fi

    print_subheader "Running ${app_name} tests"
    print_info "Directory: ${app_dir}"

    start_time=$(date +%s)

    # Install dependencies if needed
    if [[ "$SKIP_INSTALL" != "true" ]]; then
        print_info "Installing dependencies..."
        (cd "$app_dir" && npm install --silent) || {
            print_error "Failed to install dependencies for ${app_name}"
            record_result "$app_name" "FAILED" 0 0 0 0
            ((TOTAL_SUITES_FAILED++))
            return 1
        }
    fi

    # Run npm tests
    if [[ "$VERBOSE" == "true" ]]; then
        (cd "$app_dir" && npm test -- --reporter=verbose) || exit_code=$?
    elif [[ "$QUIET" == "true" ]]; then
        (cd "$app_dir" && npm test -- --reporter=dot) || exit_code=$?
    else
        (cd "$app_dir" && npm test) || exit_code=$?
    fi

    end_time=$(date +%s)
    duration=$((end_time - start_time))

    # TODO: Parse Vitest output for test counts (currently not implemented)
    # For now, we don't have test count parsing for npm/Vitest tests

    if [[ $exit_code -eq 0 ]]; then
        print_success "${app_name} tests passed (${duration}s)"
        record_result "$app_name" "PASSED" 0 0 0 0
        ((TOTAL_SUITES_PASSED++))
    else
        print_error "${app_name} tests failed (${duration}s)"
        record_result "$app_name" "FAILED" 0 0 0 0
        ((TOTAL_SUITES_FAILED++))
    fi

    return $exit_code
}

run_tests_parallel() {
    local pids=()
    local exit_codes=()

    print_info "Running tests in parallel..."

    # Start backend tests
    if [[ "$RUN_IDENTITY" == "true" ]]; then
        (run_gradle_tests "identity-service" "$IDENTITY_DIR" 2>&1) &
        pids+=($!)
    fi

    if [[ "$RUN_CUSTOMER" == "true" ]]; then
        (run_gradle_tests "customer-service" "$CUSTOMER_DIR" 2>&1) &
        pids+=($!)
    fi

    if [[ "$RUN_NOTIFICATION" == "true" ]]; then
        (run_gradle_tests "notification-service" "$NOTIFICATION_DIR" 2>&1) &
        pids+=($!)
    fi

    # Start frontend tests
    if [[ "$RUN_ADMIN_APP" == "true" ]]; then
        (run_npm_tests "admin-app" "$ADMIN_APP_DIR" 2>&1) &
        pids+=($!)
    fi

    if [[ "$RUN_CUSTOMER_APP" == "true" ]]; then
        (run_npm_tests "customer-app" "$CUSTOMER_APP_DIR" 2>&1) &
        pids+=($!)
    fi

    # Wait for all tests to complete
    for pid in "${pids[@]}"; do
        wait "$pid" || ((TOTAL_FAILED++))
    done
}

run_tests_sequential() {
    local overall_exit=0

    # Run backend tests
    if [[ "$RUN_IDENTITY" == "true" ]]; then
        run_gradle_tests "identity-service" "$IDENTITY_DIR" || overall_exit=1
    fi

    if [[ "$RUN_CUSTOMER" == "true" ]]; then
        run_gradle_tests "customer-service" "$CUSTOMER_DIR" || overall_exit=1
    fi

    if [[ "$RUN_NOTIFICATION" == "true" ]]; then
        run_gradle_tests "notification-service" "$NOTIFICATION_DIR" || overall_exit=1
    fi

    # Run frontend tests
    if [[ "$RUN_ADMIN_APP" == "true" ]]; then
        run_npm_tests "admin-app" "$ADMIN_APP_DIR" || overall_exit=1
    fi

    if [[ "$RUN_CUSTOMER_APP" == "true" ]]; then
        run_npm_tests "customer-app" "$CUSTOMER_APP_DIR" || overall_exit=1
    fi

    return $overall_exit
}

print_summary() {
    print_header "Test Summary"

    # Calculate grand totals
    local grand_total_tests=0
    local grand_total_passed=0
    local grand_total_failed=0
    local grand_total_skipped=0

    # Find the longest service name for column width
    local max_name_len=20  # fixed width for service column
    local i

    # Table structure
    local col_status=8
    local col_passed=8
    local col_failed=8
    local col_skipped=8
    local divider="${CYAN}----------------------+---------+----------+----------+----------${NC}"

    echo -e "${BOLD}Results:${NC}"
    echo -e "$divider"
    printf "${BOLD}%-20s${NC} ${CYAN}|${NC} ${BOLD}%-7s${NC} ${CYAN}|${NC} ${BOLD}${GREEN}%8s${NC} ${CYAN}|${NC} ${BOLD}${RED}%8s${NC} ${CYAN}|${NC} ${BOLD}${YELLOW}%8s${NC}\n" \
        "Service" "Status" "Passed" "Failed" "Skipped"
    echo -e "$divider"

    # Table rows
    for (( i=1; i<=${#TEST_NAMES[@]}; i++ )); do
        local service="${TEST_NAMES[$i]}"
        local test_status="${TEST_STATUSES[$i]}"
        local tests="${TEST_COUNTS[$i]:-0}"
        local failures="${TEST_FAILURES[$i]:-0}"
        local errors="${TEST_ERRORS[$i]:-0}"
        local skipped="${TEST_SKIPPED[$i]:-0}"

        # Calculate passed tests for this service
        local total_failures=$((failures + errors))
        local passed=$((tests - total_failures - skipped))
        if [[ $passed -lt 0 ]]; then
            passed=0
        fi

        # Accumulate grand totals
        grand_total_tests=$((grand_total_tests + tests))
        grand_total_passed=$((grand_total_passed + passed))
        grand_total_failed=$((grand_total_failed + total_failures))
        grand_total_skipped=$((grand_total_skipped + skipped))

        # Format and print row - determine color based on status
        local status_color=""
        if [[ "$test_status" == "PASSED" ]]; then
            status_color="$GREEN"
        elif [[ "$test_status" == "FAILED" ]]; then
            status_color="$RED"
        elif [[ "$test_status" == "SKIPPED" ]]; then
            status_color="$YELLOW"
        else
            status_color="$NC"
        fi

        # Use printf for alignment, echo -e for colors
        echo -en "${status_color}$(printf '%-20s' "$service")${NC}"
        echo -en " ${CYAN}|${NC} "
        echo -en "${status_color}$(printf '%-7s' "$test_status")${NC}"
        echo -en " ${CYAN}|${NC} "
        echo -en "${GREEN}$(printf '%8s' "$passed")${NC}"
        echo -en " ${CYAN}|${NC} "
        if [[ $total_failures -gt 0 ]]; then
            echo -en "${RED}$(printf '%8s' "$total_failures")${NC}"
        else
            printf '%8s' "$total_failures"
        fi
        echo -en " ${CYAN}|${NC} "
        if [[ $skipped -gt 0 ]]; then
            echo -e "${YELLOW}$(printf '%8s' "$skipped")${NC}"
        else
            printf '%8s\n' "$skipped"
        fi
    done

    echo -e "$divider"

    # Totals row with colors
    echo -en "${BOLD}$(printf '%-20s' "TOTAL")${NC}"
    echo -en " ${CYAN}|${NC} "
    printf '%-7s' ""
    echo -en " ${CYAN}|${NC} "
    echo -en "${GREEN}${BOLD}$(printf '%8s' "$grand_total_passed")${NC}"
    echo -en " ${CYAN}|${NC} "
    if [[ $grand_total_failed -gt 0 ]]; then
        echo -en "${RED}${BOLD}$(printf '%8s' "$grand_total_failed")${NC}"
    else
        printf '%8s' "$grand_total_failed"
    fi
    echo -en " ${CYAN}|${NC} "
    if [[ $grand_total_skipped -gt 0 ]]; then
        echo -e "${YELLOW}${BOLD}$(printf '%8s' "$grand_total_skipped")${NC}"
    else
        printf '%8s\n' "$grand_total_skipped"
    fi
    echo -e "$divider"

    echo ""
    echo -e "${BOLD}Suites:${NC} ${GREEN}${TOTAL_SUITES_PASSED} passed${NC}, ${RED}${TOTAL_SUITES_FAILED} failed${NC}"

    if [[ $TOTAL_SUITES_FAILED -gt 0 ]]; then
        echo -e "\n${RED}Some tests failed!${NC}"
        return 1
    else
        echo -e "\n${GREEN}All tests passed!${NC}"
        return 0
    fi
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local any_filter_set=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --all)
                RUN_IDENTITY=true
                RUN_CUSTOMER=true
                RUN_NOTIFICATION=true
                RUN_ADMIN_APP=true
                RUN_CUSTOMER_APP=true
                any_filter_set=true
                shift
                ;;
            --backend)
                RUN_IDENTITY=true
                RUN_CUSTOMER=true
                RUN_NOTIFICATION=true
                any_filter_set=true
                shift
                ;;
            --frontend)
                RUN_ADMIN_APP=true
                RUN_CUSTOMER_APP=true
                any_filter_set=true
                shift
                ;;
            --identity)
                RUN_IDENTITY=true
                any_filter_set=true
                shift
                ;;
            --customer)
                RUN_CUSTOMER=true
                any_filter_set=true
                shift
                ;;
            --notification)
                RUN_NOTIFICATION=true
                any_filter_set=true
                shift
                ;;
            --admin)
                RUN_ADMIN_APP=true
                any_filter_set=true
                shift
                ;;
            --customer-app)
                RUN_CUSTOMER_APP=true
                any_filter_set=true
                shift
                ;;
            --parallel)
                PARALLEL=true
                shift
                ;;
            --skip-install)
                SKIP_INSTALL=true
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
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
                print_error "Unknown option: $1"
                echo "Use --help for usage information."
                exit 2
                ;;
        esac
    done

    # If no filter specified, run all tests
    if [[ "$any_filter_set" != "true" ]]; then
        RUN_IDENTITY=true
        RUN_CUSTOMER=true
        RUN_NOTIFICATION=true
        RUN_ADMIN_APP=true
        RUN_CUSTOMER_APP=true
    fi

    # Print header
    print_header "ACME Inc. Unit Test Runner"

    # Show what will be tested
    local test_list=""
    [[ "$RUN_IDENTITY" == "true" ]] && test_list+="identity-service "
    [[ "$RUN_CUSTOMER" == "true" ]] && test_list+="customer-service "
    [[ "$RUN_NOTIFICATION" == "true" ]] && test_list+="notification-service "
    [[ "$RUN_ADMIN_APP" == "true" ]] && test_list+="admin-app "
    [[ "$RUN_CUSTOMER_APP" == "true" ]] && test_list+="customer-app "

    print_info "Test suites: ${test_list}"
    [[ "$PARALLEL" == "true" ]] && print_info "Mode: Parallel execution"
    [[ "$PARALLEL" != "true" ]] && print_info "Mode: Sequential execution"

    # Run tests
    local start_time
    local end_time
    local total_duration

    start_time=$(date +%s)

    if [[ "$PARALLEL" == "true" ]]; then
        run_tests_parallel
    else
        run_tests_sequential || true
    fi

    end_time=$(date +%s)
    total_duration=$((end_time - start_time))

    # Print summary
    print_summary
    local summary_exit=$?

    print_info "Total time: ${total_duration}s"

    exit $summary_exit
}

main "$@"
