# ✂️ ERP Barbershop

ERP corporativo modular para o back-office de uma **Barbearia Moderna**.

## 🏗️ Stack Tecnológica

| Camada         | Tecnologia                              |
|----------------|-----------------------------------------|
| Front-end      | JSF 2.3 + PrimeFaces 12                |
| Back-end       | EJB 3.2                                |
| Persistência   | JPA 2.2 / Hibernate (WildFly 26.1)     |
| Segurança      | Spring Security 5.8                    |
| Servidor       | WildFly 26.1 (Jakarta EE 8)           |
| Banco de Dados | PostgreSQL 15                          |
| Infra          | Docker + Kubernetes (Etapa 6)          |

## 📦 Módulos DDD

| Módulo         | Pacote                   | Responsabilidade                          |
|----------------|--------------------------|-------------------------------------------|
| Identidade     | `com.erp.identidade`     | Usuários, papéis, autenticação, auditoria |
| Catálogo       | `com.erp.catalogo`       | Serviços, produtos, preços                |
| Compras        | `com.erp.compras`        | Fornecedores, pedidos de compra, estoque  |
| Vendas         | `com.erp.vendas`         | Agendamentos, comandas, pagamentos        |
| Relatórios     | `com.erp.relatorios`     | Dashboards, extrações financeiras         |

## 🚀 Como Rodar — Docker Compose (Desenvolvimento)

```bash
# Subir tudo (build + deploy + banco)
docker compose up --build -d

# Ver logs
docker compose logs -f

# Parar tudo
docker compose down
```

**Acessos:**
- Aplicação: http://localhost:8080/erp-barbershop
- Admin Console WildFly: http://localhost:9990 (`admin` / `Admin#2026`)
- PostgreSQL: `localhost:5432` — banco `erp_db` (`erp_admin` / `erp_secret_2026`)

## ☸️ Deploy no Kubernetes (Etapa 6)

### Pré-requisitos
```bash
# Instalar Minikube (WSL/Linux/Git Bash)
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# Iniciar cluster com recursos suficientes para WildFly
minikube start --memory=3072 --cpus=2 --driver=docker
```

### Deploy completo (script automatizado — Bash)
```bash
# Torna o script executável
chmod +x deploy-k8s.sh

# Deploy completo: build → apply manifestos → wait → exibe URL
./deploy-k8s.sh

# Verificar status dos recursos
./deploy-k8s.sh --status

# Ver logs do WildFly em tempo real
./deploy-k8s.sh --logs

# Remover tudo (cleanup)
./deploy-k8s.sh --clean
```

### Deploy manual (passo a passo — Bash)
```bash
# 1. Aponta Docker para o registry interno do Minikube
eval $(minikube docker-env)

# 2. Build da imagem Docker (multi-stage)
DOCKER_BUILDKIT=1 docker build -t erp-barbershop:latest .

# 3. Aplica manifestos em ordem de dependência
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/persistence.yaml

# 4. Cria ConfigMap com o script DDL do schema
kubectl create configmap postgres-schema-config \
  --namespace=erp \
  --from-file=schema.sql=docs/schema.sql \
  --dry-run=client -o yaml | kubectl apply -f -

# 5. Sobe Deployments e Services
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# 6. Acompanha os pods em tempo real
kubectl get pods -n erp -w

# 7. Obtém a URL de acesso via Minikube
minikube service erp-app-service -n erp --url
```

### Comandos úteis de operação (Bash)
```bash
# Todos os recursos do namespace
kubectl get all -n erp

# Logs do WildFly ao vivo
kubectl logs -n erp -l component=app -f --tail=100

# Acesso ao Management Console (port-forward)
kubectl port-forward svc/erp-mgmt-service 9990:9990 -n erp

# Reiniciar após nova imagem
kubectl rollout restart deployment/erp-barbershop-deployment -n erp

# Escalar réplicas
kubectl scale deployment/erp-barbershop-deployment --replicas=3 -n erp

# Descriptografar um secret (apenas Base64, não criptografia)
kubectl get secret erp-barbershop-secrets -n erp \
  -o jsonpath='{.data.db-password}' | base64 -d
```

## 📁 Estrutura do Projeto

```
Projeto_POO3/
├── Dockerfile                 ← Multi-stage: Maven build + WildFly runtime
├── .dockerignore
├── docker-compose.yml         ← Ambiente de desenvolvimento local
├── deploy-k8s.sh              ← Script de deploy Kubernetes (Bash)
├── pom.xml
├── docker/
│   └── wildfly-cli/
│       └── configure-datasource.cli  ← Configura DataSource via env vars
├── k8s/
│   ├── namespace.yaml         ← Isolamento: namespace "erp"
│   ├── configmap.yaml         ← Configs não-sensíveis (host, porta, JVM opts)
│   ├── secret.yaml            ← Credenciais em Base64
│   ├── persistence.yaml       ← PersistentVolume + PVC para PostgreSQL
│   ├── deployment.yaml        ← Deployments: PostgreSQL + WildFly
│   └── service.yaml           ← Services: ClusterIP (DB) + NodePort (App)
├── src/main/
│   ├── java/com/erp/
│   │   ├── identidade/
│   │   ├── catalogo/
│   │   ├── compras/
│   │   ├── vendas/
│   │   └── relatorios/
│   ├── resources/META-INF/persistence.xml
│   └── webapp/
└── docs/
    ├── schema.sql
    └── documentacao.md
```
