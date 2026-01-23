#!/usr/bin/env zsh
# =============================================================================
# Docker Management Script
# =============================================================================
# A unified script for managing Docker infrastructure and application services.
#
# Usage: ./scripts/docker-manage.sh <command> [options]
#
# Run './scripts/docker-manage.sh help' for available commands.
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Compose files
INFRA_COMPOSE="${PROJECT_ROOT}/docker-compose.yml"
APPS_COMPOSE="${PROJECT_ROOT}/docker-compose.apps.yml"

# Frontend directories
FRONTEND_DIRS=(
    "${PROJECT_ROOT}/frontend-apps/admin"
    "${PROJECT_ROOT}/frontend-apps/customer"
)

# Colors for output (using $'...' syntax for proper escape sequence interpretation in zsh)
RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
BLUE=$'\033[0;34m'
CYAN=$'\033[0;36m'
NC=$'\033[0m' # No Color

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

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running"
        exit 1
    fi
}

check_compose_file() {
    local file=$1
    if [[ ! -f "$file" ]]; then
        print_error "Compose file not found: $file"
        exit 1
    fi
}

setup_node() {
    # First check if node/npm is already available
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

sync_frontend_deps() {
    print_info "Syncing frontend dependencies (ensuring package-lock.json is up to date)..."

    if ! setup_node; then
        print_error "Cannot sync frontend dependencies without Node.js/npm"
        return 1
    fi

    local sync_failed=false

    for dir in "${FRONTEND_DIRS[@]}"; do
        if [[ -d "$dir" ]] && [[ -f "$dir/package.json" ]]; then
            local app_name=$(basename "$dir")
            print_info "Syncing $app_name dependencies..."

            # Use nvm if .nvmrc exists, run full npm install to ensure lock file is complete
            if [[ -f "$dir/.nvmrc" ]] && command -v nvm &> /dev/null; then
                (cd "$dir" && nvm use 2>/dev/null && rm -rf node_modules && npm install) || {
                    print_error "Failed to sync $app_name dependencies"
                    sync_failed=true
                }
            else
                (cd "$dir" && rm -rf node_modules && npm install) || {
                    print_error "Failed to sync $app_name dependencies"
                    sync_failed=true
                }
            fi
        fi
    done

    if $sync_failed; then
        return 1
    fi

    print_success "Frontend dependencies synced"
    return 0
}

# -----------------------------------------------------------------------------
# Infrastructure Commands
# -----------------------------------------------------------------------------

infra_up() {
    print_header "Starting Infrastructure Services"
    check_compose_file "$INFRA_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$INFRA_COMPOSE" up -d "$@"

    print_success "Infrastructure services started"
    print_info "Run './scripts/docker-manage.sh infra-status' to check service health"
}

infra_down() {
    print_header "Stopping Infrastructure Services"
    check_compose_file "$INFRA_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$INFRA_COMPOSE" down "$@"

    print_success "Infrastructure services stopped"
}

infra_status() {
    print_header "Infrastructure Services Status"
    check_compose_file "$INFRA_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$INFRA_COMPOSE" ps
}

infra_logs() {
    check_compose_file "$INFRA_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$INFRA_COMPOSE" logs "$@"
}

# -----------------------------------------------------------------------------
# Application Commands
# -----------------------------------------------------------------------------

apps_up() {
    print_header "Starting Application Services"
    check_compose_file "$APPS_COMPOSE"

    # Sync frontend dependencies before building
    sync_frontend_deps || {
        print_error "Failed to sync frontend dependencies"
        exit 1
    }

    # Check if infrastructure network exists
    if ! docker network inspect acme-network &> /dev/null; then
        print_warning "Infrastructure network 'acme-network' not found"
        print_info "Starting infrastructure services first..."
        infra_up
        echo ""
        print_info "Waiting for infrastructure to be ready..."
        sleep 10
    fi

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" up --build --force-recreate -d "$@"

    print_success "Application services started"
    print_info "Run './scripts/docker-manage.sh apps-status' to check service health"
}

apps_down() {
    print_header "Stopping Application Services"
    check_compose_file "$APPS_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" down --remove-orphans "$@"

    print_success "Application services stopped"
}

apps_status() {
    print_header "Application Services Status"
    check_compose_file "$APPS_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" ps
}

apps_logs() {
    check_compose_file "$APPS_COMPOSE"

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" logs "$@"
}

apps_build() {
    print_header "Building Application Services"
    check_compose_file "$APPS_COMPOSE"

    # Sync frontend dependencies before building
    sync_frontend_deps || {
        print_error "Failed to sync frontend dependencies"
        exit 1
    }

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" build --no-cache "$@"

    print_success "Application services built"
}

# -----------------------------------------------------------------------------
# Combined Commands
# -----------------------------------------------------------------------------

start_all() {
    print_header "Starting All Services"

    infra_up
    echo ""
    print_info "Waiting for infrastructure to be ready..."
    sleep 15
    apps_up

    print_success "All services started"
}

stop_all() {
    local remove_volumes=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -v|--volumes)
                remove_volumes=true
                shift
                ;;
            *)
                shift
                ;;
        esac
    done

    if $remove_volumes; then
        print_header "Stopping All Services and Removing Volumes"
        print_warning "Volumes will be removed - all data will be lost!"
    else
        print_header "Stopping All Services"
    fi

    if $remove_volumes; then
        docker compose -f "$APPS_COMPOSE" down --remove-orphans -v 2>/dev/null || true
        docker compose -f "$INFRA_COMPOSE" down --remove-orphans -v
    else
        apps_down 2>/dev/null || true
        infra_down
    fi

    if $remove_volumes; then
        print_success "All services stopped and volumes removed"
    else
        print_success "All services stopped"
    fi
}

restart_all() {
    print_header "Restarting All Services"

    stop_all
    echo ""
    start_all
}

status_all() {
    print_header "All Services Status"

    echo -e "${CYAN}Infrastructure Services:${NC}"
    docker compose -f "$INFRA_COMPOSE" ps 2>/dev/null || print_warning "Infrastructure not running"

    echo ""
    echo -e "${CYAN}Application Services:${NC}"
    docker compose -f "$APPS_COMPOSE" ps 2>/dev/null || print_warning "Applications not running"
}

# -----------------------------------------------------------------------------
# Cleanup Commands
# -----------------------------------------------------------------------------

cleanup_images() {
    print_header "Cleaning Up Unused Images"

    docker image prune -f

    print_success "Unused images removed"
}

cleanup_all() {
    print_header "Full Cleanup (Images, Volumes, Networks)"

    print_warning "This will remove all unused Docker resources!"
    read "confirm?Are you sure? (y/N): "

    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        docker system prune -af --volumes
        print_success "Full cleanup completed"
    else
        print_info "Cleanup cancelled"
    fi
}

cleanup_volumes() {
    print_header "Stopping Services and Removing Volumes"

    print_warning "This will remove all data volumes!"
    read "confirm?Are you sure? (y/N): "

    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        apps_down -v 2>/dev/null || true
        docker compose -f "$INFRA_COMPOSE" down -v
        print_success "Services stopped and volumes removed"
    else
        print_info "Cleanup cancelled"
    fi
}

teardown() {
    print_header "Full Teardown - Stop All Services and Clean Up"

    print_warning "This will:"
    echo -e "  ${RED}•${NC} Stop all application services"
    echo -e "  ${RED}•${NC} Stop all infrastructure services"
    echo -e "  ${RED}•${NC} Remove all volumes (data will be lost!)"
    echo -e "  ${RED}•${NC} Remove orphaned containers"
    echo -e "  ${RED}•${NC} Remove project networks"
    echo ""

    read "confirm?Are you sure you want to proceed? (y/N): "

    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        print_info "Stopping application services..."
        docker compose -f "$APPS_COMPOSE" down --remove-orphans -v 2>/dev/null || true

        print_info "Stopping infrastructure services..."
        docker compose -f "$INFRA_COMPOSE" down --remove-orphans -v

        print_info "Removing project networks..."
        docker network rm acme-network 2>/dev/null || true

        print_info "Pruning unused images..."
        docker image prune -f

        print_success "Full teardown complete"
        print_info "All services stopped, volumes removed, and networks cleaned up"
    else
        print_info "Teardown cancelled"
    fi
}

# -----------------------------------------------------------------------------
# Utility Commands
# -----------------------------------------------------------------------------

shell_into() {
    local service=$1
    shift

    print_info "Opening shell in $service..."

    # Try apps compose first, then infra
    if docker compose -f "$APPS_COMPOSE" ps --services 2>/dev/null | grep -q "^${service}$"; then
        docker compose -f "$APPS_COMPOSE" exec "$service" "${@:-sh}"
    elif docker compose -f "$INFRA_COMPOSE" ps --services 2>/dev/null | grep -q "^${service}$"; then
        docker compose -f "$INFRA_COMPOSE" exec "$service" "${@:-sh}"
    else
        print_error "Service '$service' not found or not running"
        exit 1
    fi
}

rebuild_service() {
    local service=$1

    print_header "Rebuilding Service: $service"

    # Sync frontend dependencies if rebuilding a frontend service
    if [[ "$service" == *"frontend"* ]] || [[ "$service" == "customer-frontend" ]] || [[ "$service" == "admin-frontend" ]]; then
        sync_frontend_deps || {
            print_error "Failed to sync frontend dependencies"
            exit 1
        }
    fi

    cd "$PROJECT_ROOT"
    docker compose -f "$APPS_COMPOSE" up --build --force-recreate -d "$service"

    print_success "Service '$service' rebuilt and restarted"
}

health_check() {
    print_header "Health Check"

    local all_healthy=true

    echo -e "${CYAN}Checking infrastructure services...${NC}"

    # Check PostgreSQL
    if docker compose -f "$INFRA_COMPOSE" exec -T postgres pg_isready -U postgres &>/dev/null; then
        print_success "PostgreSQL: healthy"
    else
        print_error "PostgreSQL: unhealthy or not running"
        all_healthy=false
    fi

    # Check Kafka
    if docker compose -f "$INFRA_COMPOSE" exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &>/dev/null; then
        print_success "Kafka: healthy"
    else
        print_error "Kafka: unhealthy or not running"
        all_healthy=false
    fi

    # Check Schema Registry
    if curl -sf http://localhost:8081/subjects &>/dev/null; then
        print_success "Schema Registry: healthy"
    else
        print_error "Schema Registry: unhealthy or not running"
        all_healthy=false
    fi

    # Check MongoDB
    if docker compose -f "$INFRA_COMPOSE" exec -T mongodb mongosh --quiet --eval "db.adminCommand('ping')" &>/dev/null; then
        print_success "MongoDB: healthy"
    else
        print_error "MongoDB: unhealthy or not running"
        all_healthy=false
    fi

    # Check Redis
    if docker compose -f "$INFRA_COMPOSE" exec -T redis redis-cli ping &>/dev/null; then
        print_success "Redis: healthy"
    else
        print_error "Redis: unhealthy or not running"
        all_healthy=false
    fi

    # Check Debezium Connect
    local debezium_port="${DEBEZIUM_PORT:-8083}"
    if curl -sf "http://localhost:${debezium_port}/connectors" &>/dev/null; then
        print_success "Debezium Connect: healthy"
    else
        print_error "Debezium Connect: unhealthy or not running"
        all_healthy=false
    fi

    # Check Vault
    local vault_port="${VAULT_PORT:-8200}"
    if curl -sf "http://localhost:${vault_port}/v1/sys/health" &>/dev/null; then
        print_success "Vault: healthy"
    else
        print_error "Vault: unhealthy or not running"
        all_healthy=false
    fi

    echo ""
    echo -e "${CYAN}Checking observability services...${NC}"

    # Check Prometheus
    local prometheus_port="${PROMETHEUS_PORT:-9090}"
    if curl -sf "http://localhost:${prometheus_port}/-/healthy" &>/dev/null; then
        print_success "Prometheus: healthy"
    else
        print_error "Prometheus: unhealthy or not running"
        all_healthy=false
    fi

    # Check Loki
    local loki_port="${LOKI_PORT:-3100}"
    if curl -sf "http://localhost:${loki_port}/ready" &>/dev/null; then
        print_success "Loki: healthy"
    else
        print_error "Loki: unhealthy or not running"
        all_healthy=false
    fi

    # Check Tempo
    local tempo_port="${TEMPO_PORT:-3200}"
    if curl -sf "http://localhost:${tempo_port}/ready" &>/dev/null; then
        print_success "Tempo: healthy"
    else
        print_error "Tempo: unhealthy or not running"
        all_healthy=false
    fi

    # Check Grafana
    local grafana_port="${GRAFANA_PORT:-3000}"
    if curl -sf "http://localhost:${grafana_port}/api/health" &>/dev/null; then
        print_success "Grafana: healthy"
    else
        print_error "Grafana: unhealthy or not running"
        all_healthy=false
    fi

    echo ""
    echo -e "${CYAN}Checking application services...${NC}"

    # Check Identity Service
    local identity_port="${IDENTITY_SERVICE_PORT:-10300}"
    if curl -sf "http://localhost:${identity_port}/actuator/health" &>/dev/null; then
        print_success "Identity Service: healthy"
    else
        print_error "Identity Service: unhealthy or not running"
        all_healthy=false
    fi

    # Check Customer Frontend
    local customer_frontend_port="${CUSTOMER_FRONTEND_PORT:-7600}"
    if curl -sf "http://localhost:${customer_frontend_port}/" &>/dev/null; then
        print_success "Customer Frontend: healthy"
    else
        print_error "Customer Frontend: unhealthy or not running"
        all_healthy=false
    fi

    echo ""
    if $all_healthy; then
        print_success "All services are healthy!"
    else
        print_warning "Some services are unhealthy or not running"
    fi
}

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------

show_help() {
    cat << EOF
${BLUE}ACME Inc. Docker Management Script${NC}

${YELLOW}Usage:${NC} ./scripts/docker-manage.sh <command> [options]

${YELLOW}Infrastructure Commands:${NC}
  infra-up [services...]     Start infrastructure services
  infra-down [options]       Stop infrastructure services
  infra-status               Show infrastructure services status
  infra-logs [options]       View infrastructure logs (use -f to follow)

${YELLOW}Application Commands:${NC}
  apps-up [services...]      Build and start application services
  apps-down [options]        Stop application services
  apps-status                Show application services status
  apps-logs [options]        View application logs (use -f to follow)
  apps-build [services...]   Build application images without starting

${YELLOW}Combined Commands:${NC}
  start                      Start all services (infra + apps)
  stop [-v|--volumes]        Stop all services (use -v to remove volumes)
  restart                    Restart all services
  status                     Show status of all services

${YELLOW}Cleanup Commands:${NC}
  cleanup                    Remove unused Docker images
  cleanup-all                Remove all unused Docker resources (with confirmation)
  cleanup-volumes            Stop services and remove all data volumes (with confirmation)
  teardown                   Full teardown: stop all, remove volumes/networks/orphans

${YELLOW}Utility Commands:${NC}
  shell <service> [cmd]      Open shell in a running container
  rebuild <service>          Rebuild and restart a specific service
  health                     Check health of all services

${YELLOW}Examples:${NC}
  ./scripts/docker-manage.sh start              # Start everything
  ./scripts/docker-manage.sh stop               # Stop all services (keep data)
  ./scripts/docker-manage.sh stop -v            # Stop all and remove volumes
  ./scripts/docker-manage.sh apps-up            # Start apps only
  ./scripts/docker-manage.sh apps-logs -f       # Follow app logs
  ./scripts/docker-manage.sh shell postgres     # Open psql shell
  ./scripts/docker-manage.sh rebuild identity-service
  ./scripts/docker-manage.sh health             # Check all services

${YELLOW}Environment:${NC}
  Copy .env.example to .env to customize ports and credentials.

EOF
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    check_docker

    local command="${1:-help}"
    shift || true

    case "$command" in
        # Infrastructure
        infra-up)
            infra_up "$@"
            ;;
        infra-down)
            infra_down "$@"
            ;;
        infra-status)
            infra_status
            ;;
        infra-logs)
            infra_logs "$@"
            ;;

        # Applications
        apps-up)
            apps_up "$@"
            ;;
        apps-down)
            apps_down "$@"
            ;;
        apps-status)
            apps_status
            ;;
        apps-logs)
            apps_logs "$@"
            ;;
        apps-build)
            apps_build "$@"
            ;;

        # Combined
        start)
            start_all
            ;;
        stop)
            stop_all "$@"
            ;;
        restart)
            restart_all
            ;;
        status)
            status_all
            ;;

        # Cleanup
        cleanup)
            cleanup_images
            ;;
        cleanup-all)
            cleanup_all
            ;;
        cleanup-volumes)
            cleanup_volumes
            ;;
        teardown)
            teardown
            ;;

        # Utility
        shell)
            if [[ -z "${1:-}" ]]; then
                print_error "Service name required"
                echo "Usage: ./scripts/docker-manage.sh shell <service> [command]"
                exit 1
            fi
            shell_into "$@"
            ;;
        rebuild)
            if [[ -z "${1:-}" ]]; then
                print_error "Service name required"
                echo "Usage: ./scripts/docker-manage.sh rebuild <service>"
                exit 1
            fi
            rebuild_service "$1"
            ;;
        health)
            health_check
            ;;

        # Help
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
