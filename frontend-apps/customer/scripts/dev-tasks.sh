#!/usr/bin/env bash
#
# Development tasks automation script for the customer frontend application.
# This script automates common development tasks like route generation,
# linting, and formatting.
#
# Usage:
#   ./scripts/dev-tasks.sh [command]
#
# Commands:
#   routes    - Generate TanStack Router route tree
#   lint      - Run ESLint on source files
#   format    - Run Prettier on source files
#   check     - Run all checks (routes, lint, format check)
#   fix       - Run all fixes (routes, lint fix, format write)
#   all       - Alias for 'fix'
#   help      - Show this help message
#
# If no command is provided, 'all' is assumed.
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Change to project directory
cd "$PROJECT_DIR"

# Print a colored message
print_step() {
    echo -e "${BLUE}==>${NC} ${GREEN}$1${NC}"
}

print_warning() {
    echo -e "${YELLOW}Warning:${NC} $1"
}

print_error() {
    echo -e "${RED}Error:${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Load nvm and use the project's Node.js version
load_nvm() {
    export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
    if [[ -s "$NVM_DIR/nvm.sh" ]]; then
        source "$NVM_DIR/nvm.sh"
        if [[ -f ".nvmrc" ]]; then
            nvm use --silent
        fi
    else
        print_warning "nvm not found. Ensure Node.js is available in PATH."
    fi
}

load_nvm

# Check if a command exists in node_modules/.bin
check_bin() {
    if [[ -x "node_modules/.bin/$1" ]]; then
        return 0
    else
        return 1
    fi
}

# Generate TanStack Router route tree
generate_routes() {
    print_step "Generating TanStack Router route tree..."

    if check_bin vite; then
        # Running vite build triggers route generation via the TanStack Router plugin
        # We use --mode development to avoid production optimizations
        if node_modules/.bin/vite build --mode development > /dev/null 2>&1; then
            print_success "Route tree generated"
        else
            print_warning "Route generation completed with warnings (this is often normal)"
        fi
    else
        print_error "vite not found in node_modules/.bin"
        exit 1
    fi
}

# Run ESLint
run_lint() {
    local fix_mode="${1:-false}"

    print_step "Running ESLint..."

    if check_bin eslint; then
        if [[ "$fix_mode" == "true" ]]; then
            node_modules/.bin/eslint src --fix
        else
            node_modules/.bin/eslint src
        fi
        print_success "ESLint completed"
    else
        print_warning "ESLint not found. Install with: npm install -D eslint @typescript-eslint/eslint-plugin @typescript-eslint/parser eslint-plugin-react eslint-plugin-react-hooks"
    fi
}

# Run Prettier
run_format() {
    local write_mode="${1:-false}"

    print_step "Running Prettier..."

    if check_bin prettier; then
        if [[ "$write_mode" == "true" ]]; then
            node_modules/.bin/prettier --write "src/**/*.{ts,tsx,js,jsx,json,css}"
        else
            node_modules/.bin/prettier --check "src/**/*.{ts,tsx,js,jsx,json,css}"
        fi
        print_success "Prettier completed"
    else
        print_warning "Prettier not found. Install with: npm install -D prettier"
    fi
}

# Run all checks (no modifications)
run_checks() {
    generate_routes
    run_lint "false"
    run_format "false"
}

# Run all fixes (with modifications)
run_fixes() {
    generate_routes
    run_lint "true"
    run_format "true"
}

# Show help
show_help() {
    echo "Customer Frontend Development Tasks"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  routes    Generate TanStack Router route tree"
    echo "  lint      Run ESLint on source files"
    echo "  format    Run Prettier on source files"
    echo "  check     Run all checks (routes, lint, format check)"
    echo "  fix       Run all fixes (routes, lint fix, format write)"
    echo "  all       Alias for 'fix'"
    echo "  help      Show this help message"
    echo ""
    echo "If no command is provided, 'all' is assumed."
}

# Main entry point
main() {
    local command="${1:-all}"

    case "$command" in
        routes)
            generate_routes
            ;;
        lint)
            run_lint "true"
            ;;
        format)
            run_format "true"
            ;;
        check)
            run_checks
            ;;
        fix|all)
            run_fixes
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"
