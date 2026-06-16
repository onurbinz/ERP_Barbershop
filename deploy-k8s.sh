#!/usr/bin/env bash
# ==============================================================================
# deploy-k8s.sh — Script de implantação completa no Kubernetes (Minikube/Kind)
#
# Uso:
#   chmod +x deploy-k8s.sh
#   ./deploy-k8s.sh            # Deploy completo
#   ./deploy-k8s.sh --clean    # Remove tudo e reimplanta do zero
#   ./deploy-k8s.sh --status   # Exibe status atual dos recursos
#
# Pré-requisitos:
#   • kubectl   instalado e configurado para o cluster local
#   • minikube  OU kind  rodando
#   • docker    instalado e daemon rodando
#   • bash 4+   (padrão em Linux/WSL/Git Bash)
#
# REGRA: Este script usa exclusivamente Bash.
#        Compatível com WSL (Windows Subsystem for Linux) e Git Bash.
# ==============================================================================

set -euo pipefail  # -e: sai ao erro | -u: sai em variável não definida | -o pipefail

# ─── Cores ANSI para output legível ──────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ─── Variáveis configuráveis ─────────────────────────────────────────────────
IMAGE_NAME="erp-barbershop"
IMAGE_TAG="latest"
NAMESPACE="erp"
K8S_DIR="./k8s"
SCHEMA_FILE="./docs/schema.sql"
WAIT_TIMEOUT="180s"   # Timeout para kubectl rollout wait

# ─── Funções utilitárias ─────────────────────────────────────────────────────
log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERRO]${NC}  $*" >&2; }
log_step()    { echo -e "\n${CYAN}━━━ $* ━━━${NC}"; }

# Verifica se um comando existe no PATH
require_cmd() {
    if ! command -v "$1" &>/dev/null; then
        log_error "Comando '$1' não encontrado. Instale-o antes de continuar."
        exit 1
    fi
}

# ─── Verificação de pré-requisitos ───────────────────────────────────────────
check_prerequisites() {
    log_step "Verificando pré-requisitos"
    require_cmd kubectl
    require_cmd docker
    require_cmd base64

    # Verifica se o cluster está acessível
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Nenhum cluster Kubernetes acessível. Inicie o Minikube ou Kind."
        log_info  "  Minikube: minikube start --memory=3072 --cpus=2"
        log_info  "  Kind:     kind create cluster --name erp-cluster"
        exit 1
    fi

    log_success "Todos os pré-requisitos atendidos."
}

# ─── Detecção do ambiente (Minikube ou outro) ─────────────────────────────────
detect_environment() {
    log_step "Detectando ambiente Kubernetes"

    if command -v minikube &>/dev/null && minikube status &>/dev/null 2>&1; then
        KUBE_ENV="minikube"
        log_info "Ambiente detectado: Minikube"
        log_info "Ativando o registry Docker interno do Minikube..."
        # Aponta o daemon Docker local para o registry interno do Minikube.
        # Assim, a imagem buildada fica disponível sem push para um registry externo.
        eval "$(minikube docker-env)"
        log_success "Docker apontando para o Minikube registry."
    else
        KUBE_ENV="other"
        log_warn "Minikube não detectado. Assumindo Kind ou cluster externo."
        log_warn "A imagem será buildada no Docker local."
        log_warn "Para Kind: kind load docker-image ${IMAGE_NAME}:${IMAGE_TAG} --name <cluster-name>"
    fi
}

# ─── Build da imagem Docker ───────────────────────────────────────────────────
build_image() {
    log_step "Build da imagem Docker (multi-stage)"
    log_info "Imagem: ${IMAGE_NAME}:${IMAGE_TAG}"

    # BuildKit habilita cache de camadas paralelo e builds mais rápidos
    DOCKER_BUILDKIT=1 docker build \
        --tag "${IMAGE_NAME}:${IMAGE_TAG}" \
        --file Dockerfile \
        --progress=plain \
        .

    log_success "Imagem '${IMAGE_NAME}:${IMAGE_TAG}' construída com sucesso."
    docker image ls "${IMAGE_NAME}" --format "  Tamanho: {{.Size}} | Criada: {{.CreatedAt}}"

    # Para Kind: carrega a imagem no cluster
    if [[ "${KUBE_ENV:-other}" == "kind" ]]; then
        CLUSTER_NAME=$(kind get clusters | head -1)
        log_info "Carregando imagem no Kind cluster: ${CLUSTER_NAME}"
        kind load docker-image "${IMAGE_NAME}:${IMAGE_TAG}" --name "${CLUSTER_NAME}"
        log_success "Imagem carregada no Kind."
    fi
}

# ─── Criação do Namespace ─────────────────────────────────────────────────────
create_namespace() {
    log_step "Namespace: ${NAMESPACE}"
    kubectl apply -f "${K8S_DIR}/namespace.yaml"
    log_success "Namespace '${NAMESPACE}' pronto."
}

# ─── ConfigMap do Schema SQL ─────────────────────────────────────────────────
create_schema_configmap() {
    log_step "ConfigMap do Schema SQL (postgres-schema-config)"

    if [[ ! -f "${SCHEMA_FILE}" ]]; then
        log_warn "Arquivo ${SCHEMA_FILE} não encontrado. Pulando criação do schema ConfigMap."
        return
    fi

    # Cria/atualiza o ConfigMap com o conteúdo do schema.sql
    # O PostgreSQL executa arquivos de /docker-entrypoint-initdb.d/ na 1ª inicialização
    kubectl create configmap postgres-schema-config \
        --namespace="${NAMESPACE}" \
        --from-file=schema.sql="${SCHEMA_FILE}" \
        --dry-run=client -o yaml \
    | kubectl apply -f -

    log_success "ConfigMap 'postgres-schema-config' criado/atualizado."
}

# ─── Aplicação dos Manifestos K8s ────────────────────────────────────────────
apply_manifests() {
    log_step "Aplicando manifestos Kubernetes"

    # Ordem importa: namespace → config → secret → persistence → deploy → service
    local manifests=(
        "${K8S_DIR}/namespace.yaml"
        "${K8S_DIR}/configmap.yaml"
        "${K8S_DIR}/secret.yaml"
        "${K8S_DIR}/persistence.yaml"
        "${K8S_DIR}/deployment.yaml"
        "${K8S_DIR}/service.yaml"
    )

    for manifest in "${manifests[@]}"; do
        if [[ -f "${manifest}" ]]; then
            log_info "Aplicando: ${manifest}"
            kubectl apply -f "${manifest}"
        else
            log_warn "Manifesto não encontrado: ${manifest} — pulando."
        fi
    done

    log_success "Todos os manifestos aplicados."
}

# ─── Aguarda os Deployments ficarem prontos ───────────────────────────────────
wait_for_deployments() {
    log_step "Aguardando Pods ficarem prontos (timeout: ${WAIT_TIMEOUT})"

    log_info "Aguardando PostgreSQL..."
    kubectl rollout status deployment/postgres-deployment \
        --namespace="${NAMESPACE}" \
        --timeout="${WAIT_TIMEOUT}"
    log_success "PostgreSQL pronto."

    log_info "Aguardando WildFly (pode levar até 3 minutos no 1º deploy)..."
    kubectl rollout status deployment/erp-barbershop-deployment \
        --namespace="${NAMESPACE}" \
        --timeout="${WAIT_TIMEOUT}"
    log_success "WildFly pronto."
}

# ─── Exibe status e URL de acesso ────────────────────────────────────────────
show_status() {
    log_step "Status do cluster"

    echo ""
    log_info "Pods:"
    kubectl get pods --namespace="${NAMESPACE}" -o wide

    echo ""
    log_info "Services:"
    kubectl get services --namespace="${NAMESPACE}"

    echo ""
    log_info "PersistentVolumeClaims:"
    kubectl get pvc --namespace="${NAMESPACE}"

    # Exibe URL de acesso
    echo ""
    if [[ "${KUBE_ENV:-other}" == "minikube" ]]; then
        log_step "URL de acesso"
        minikube service erp-app-service --namespace="${NAMESPACE}" --url 2>/dev/null \
            | while read -r url; do
                log_success "Aplicação: ${url}/erp-barbershop"
              done
    else
        NODE_PORT=30080
        # Tenta descobrir o IP do Node
        NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null || echo "<node-ip>")
        log_success "Aplicação: http://${NODE_IP}:${NODE_PORT}/erp-barbershop"
        log_info "  Alternativa (port-forward):"
        log_info "  kubectl port-forward svc/erp-app-service ${NODE_PORT}:8080 -n ${NAMESPACE}"
        log_info "  Acesse: http://localhost:${NODE_PORT}/erp-barbershop"
    fi

    echo ""
    log_info "WildFly Management Console (via port-forward):"
    log_info "  kubectl port-forward svc/erp-mgmt-service 9990:9990 -n ${NAMESPACE}"
    log_info "  Acesse: http://localhost:9990  (admin / Admin#2026)"
}

# ─── Limpeza completa ─────────────────────────────────────────────────────────
clean_all() {
    log_step "Removendo todos os recursos do namespace '${NAMESPACE}'"
    log_warn "ATENÇÃO: Todos os dados do PostgreSQL serão perdidos!"
    read -r -p "Confirmar remoção? [s/N]: " confirm
    if [[ "${confirm,,}" != "s" ]]; then
        log_info "Operação cancelada."
        exit 0
    fi

    kubectl delete namespace "${NAMESPACE}" --ignore-not-found=true
    # PV é cluster-scoped, não pertence ao namespace
    kubectl delete pv postgres-pv --ignore-not-found=true

    log_success "Namespace '${NAMESPACE}' e PersistentVolume removidos."
}

# ─── Logs em tempo real dos Pods ─────────────────────────────────────────────
follow_logs() {
    log_step "Logs do WildFly (Ctrl+C para sair)"
    kubectl logs \
        --namespace="${NAMESPACE}" \
        --selector="component=app" \
        --follow \
        --tail=100
}

# ─── Parsing de argumentos ────────────────────────────────────────────────────
main() {
    echo -e "${CYAN}"
    echo "  ╔═══════════════════════════════════════════╗"
    echo "  ║   ERP Barbershop — Deploy Kubernetes      ║"
    echo "  ║   WildFly 26.1 + PostgreSQL 15 + k8s      ║"
    echo "  ╚═══════════════════════════════════════════╝"
    echo -e "${NC}"

    case "${1:-deploy}" in
        --clean)
            check_prerequisites
            clean_all
            ;;
        --status)
            check_prerequisites
            KUBE_ENV="other"  # Sem detecção para status
            show_status
            ;;
        --logs)
            check_prerequisites
            follow_logs
            ;;
        --build-only)
            check_prerequisites
            detect_environment
            build_image
            ;;
        deploy|"")
            check_prerequisites
            detect_environment
            build_image
            create_namespace
            create_schema_configmap
            apply_manifests
            wait_for_deployments
            show_status
            log_success "Deploy concluído com sucesso!"
            ;;
        *)
            echo "Uso: $0 [deploy|--clean|--status|--logs|--build-only]"
            exit 1
            ;;
    esac
}

main "$@"
