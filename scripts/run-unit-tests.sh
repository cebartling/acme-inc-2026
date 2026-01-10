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

# Track results (using parallel arrays for Bash 3.x compatibility)
TEST_NAMES=()
TEST_STATUSES=()
TOTAL_PASSED=0
TOTAL_FAILED=0

# Helper function to record test result
record_result() {
    local name="$1"
    local status="$2"
    TEST_NAMES+=("$name")
    TEST_STATUSES+=("$status")
}

# Helper function to get test result
get_result() {
    local name="$1"
    local i
    for i in "${!TEST_NAMES[@]}"; do
        if [[ "${TEST_NAMES[$i]}" == "$name" ]]; then
            echo "${TEST_STATUSES[$i]}"
            return
        fi
    done
    echo "UNKNOWN"
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

    # Run Gradle tests
    if [[ "$VERBOSE" == "true" ]]; then
        (cd "$service_dir" && $gradle_cmd test --info) || exit_code=$?
    elif [[ "$QUIET" == "true" ]]; then
        (cd "$service_dir" && $gradle_cmd test --quiet) || exit_code=$?
    else
        (cd "$service_dir" && $gradle_cmd test) || exit_code=$?
    fi

    end_time=$(date +%s)
    duration=$((end_time - start_time))

    if [[ $exit_code -eq 0 ]]; then
        print_success "${service_name} tests passed (${duration}s)"
        record_result "$service_name" "PASSED"
        ((TOTAL_PASSED++))
    else
        print_error "${service_name} tests failed (${duration}s)"
        record_result "$service_name" "FAILED"
        ((TOTAL_FAILED++))
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
        record_result "$app_name" "SKIPPED"
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
            record_result "$app_name" "FAILED"
            ((TOTAL_FAILED++))
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

    if [[ $exit_code -eq 0 ]]; then
        print_success "${app_name} tests passed (${duration}s)"
        record_result "$app_name" "PASSED"
        ((TOTAL_PASSED++))
    else
        print_error "${app_name} tests failed (${duration}s)"
        record_result "$app_name" "FAILED"
        ((TOTAL_FAILED++))
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

    echo -e "${BOLD}Results:${NC}"
    local i
    for i in "${!TEST_NAMES[@]}"; do
        local service="${TEST_NAMES[$i]}"
        local status="${TEST_STATUSES[$i]}"
        case "$status" in
            PASSED)
                echo -e "  ${GREEN}✓${NC} ${service}: ${GREEN}${status}${NC}"
                ;;
            FAILED)
                echo -e "  ${RED}✗${NC} ${service}: ${RED}${status}${NC}"
                ;;
            SKIPPED)
                echo -e "  ${YELLOW}○${NC} ${service}: ${YELLOW}${status}${NC}"
                ;;
        esac
    done

    echo ""
    echo -e "${BOLD}Total:${NC} ${GREEN}${TOTAL_PASSED} passed${NC}, ${RED}${TOTAL_FAILED} failed${NC}"

    if [[ $TOTAL_FAILED -gt 0 ]]; then
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
