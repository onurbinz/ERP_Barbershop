# 📋 Documentação Completa — ERP Barbershop

> Sistema ERP Web modular para gestão do back-office de uma barbearia moderna.
> Desenvolvido como projeto acadêmico da disciplina **Programação Orientada a Objetos 3** — UERJ, 2026.

---

## 🚀 Como Executar (Com Docker - Recomendado)

Se você tem o Docker Desktop instalado e com virtualização ativada, este é o método mais simples. Nenhum Java, Maven ou PostgreSQL precisa ser instalado na sua máquina host.

```bash
# 1. Acesse a pasta do projeto
cd Projeto_POO3

# 2. Suba tudo com um único comando
docker compose up --build

# Aguarde o WildFly subir (~2-3 minutos no primeiro build)
# Quando aparecer "WildFly Full 26.1.3.Final started", acesse:
```

| O que | URL |
|---|---|
| **Aplicação** | http://localhost:8080/erp-barbershop |
| **Admin WildFly** | http://localhost:9990 |

**Credenciais padrão:**
- WildFly Admin: `admin` / `Admin#2026`
- Banco de dados: `erp_admin` / `erp_secret_2026`

### Outros comandos úteis

```bash
# Ver logs ao vivo
docker compose logs -f

# Parar sem apagar dados
docker compose stop

# Parar e remover containers (dados do banco sobrevivem)
docker compose down

# Parar e APAGAR todos os dados do banco
docker compose down -v

# Reconstruir a imagem após mudanças no código
docker compose up --build
```

> [!IMPORTANT]
> Na **primeira execução**, o PostgreSQL cria o banco e executa o `schema.sql` automaticamente (tabelas + papéis padrão). O WildFly só inicia depois que o banco estiver saudável.

---

## 💻 Como Executar Localmente (Sem Docker / Sem Virtualização)

Caso o seu computador não possua suporte a virtualização de hardware ativada na BIOS (ou você não consiga rodar o Docker Desktop), você pode rodar a aplicação e o banco de dados nativamente no Windows seguindo o passo a passo abaixo.

### 📋 Pré-requisitos
1. **Java Development Kit (JDK 17)** instalado e a variável `JAVA_HOME` configurada no ambiente.
2. **Apache Maven 3.9+** instalado e adicionado ao `Path`.
3. **PostgreSQL 15** instalado localmente.
4. **WildFly 26.1.3.Final** (versão "Jakarta EE 8") baixado e extraído em uma pasta local.
   * [Link para download do WildFly 26.1.3.Final](https://www.wildfly.org/downloads/)

---

### 1️⃣ Passo 1: Configurar o Banco de Dados (PostgreSQL)
1. Abra o seu console SQL, `psql` ou o pgAdmin, e crie a base de dados e o usuário com as mesmas credenciais do projeto:
   ```sql
   CREATE DATABASE erp_db;
   CREATE USER erp_admin WITH PASSWORD 'erp_secret_2026';
   GRANT ALL PRIVILEGES ON DATABASE erp_db TO erp_admin;
   ```
2. Conecte-se ao banco `erp_db` recém-criado utilizando as novas credenciais ou como superusuário (`postgres`) e execute o script SQL do schema do projeto, localizado em:
   * [`docs/schema.sql`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/docs/schema.sql)
   *(Este comando criará todas as tabelas e inserirá os papéis/roles iniciais no banco de dados).*

---

### 2️⃣ Passo 2: Configurar o Driver JDBC do PostgreSQL no WildFly
Para que o WildFly reconheça a conexão com o PostgreSQL, siga os passos abaixo para registrar o driver JDBC:

1. Baixe o driver JDBC do PostgreSQL (`postgresql-42.6.0.jar` ou similar) do site oficial da PostgreSQL.
2. Com o WildFly desligado, crie a seguinte estrutura de pastas dentro da instalação do WildFly:
   ```
   <CAMINHO_DO_WILDFLY>/modules/system/layers/base/org/postgresql/main/
   ```
3. Copie o arquivo `.jar` do driver JDBC baixado para dentro desta pasta criada (`main/`).
4. Crie um arquivo chamado `module.xml` nessa mesma pasta com o seguinte conteúdo:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <module xmlns="urn:jboss:module:1.5" name="org.postgresql">
       <resources>
           <resource-root path="postgresql-42.6.0.jar"/>
       </resources>
       <dependencies>
           <module name="javax.api"/>
           <module name="javax.transaction.api"/>
       </dependencies>
   </module>
   ```
   *(Nota: Se a versão do seu driver for diferente, ajuste o atributo `path` de `<resource-root>` para corresponder ao nome do seu arquivo `.jar`).*

---

### 3️⃣ Passo 3: Registrar o DataSource no WildFly
Você precisa registrar o DataSource `java:jboss/datasources/ErpBarbershopDS` usado pela nossa camada de persistência.

1. Vá para a pasta do WildFly e execute o servidor:
   * Dê dois cliques em `<CAMINHO_DO_WILDFLY>/bin/standalone.bat` no Windows.
2. Em outra janela de terminal, entre no CLI do WildFly:
   ```cmd
   cd <CAMINHO_DO_WILDFLY>/bin
   jboss-cli.bat --connect
   ```
3. Copie, cole e execute os seguintes comandos no terminal do CLI para registrar o Driver e o DataSource:
   ```cli
   # 1. Registra o driver JDBC do PostgreSQL no subsistema do WildFly
   /subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)

   # 2. Adiciona o DataSource ErpBarbershopDS
   /subsystem=datasources/data-source=ErpBarbershopDS:add(jndi-name="java:jboss/datasources/ErpBarbershopDS",connection-url="jdbc:postgresql://localhost:5432/erp_db",driver-name="postgresql",user-name="erp_admin",password="erp_secret_2026",enabled="true",use-java-context="true")
   ```
4. Se tudo foi executado com sucesso, você verá a mensagem `{"outcome" => "success"}` para cada comando. Digite `exit` para sair do CLI.

---

### 4️⃣ Passo 4: Compilar e Fazer Deploy da Aplicação
1. Abra um terminal do Windows (CMD, PowerShell ou Git Bash) na pasta raiz do seu projeto (`Projeto_POO3`).
2. Compile a aplicação com o Maven:
   ```bash
   mvn clean package
   ```
   *(Este comando executará os testes e gerará o arquivo `target/erp-barbershop.war`)*.
3. Copie o arquivo gerado `target/erp-barbershop.war` para o diretório de deploys automáticos do seu WildFly local:
   ```
   <CAMINHO_DO_WILDFLY>/standalone/deployments/
   ```
4. O console do WildFly detectará o arquivo e fará a inicialização da aplicação automaticamente. Quando ver a mensagem de sucesso no console (`Deployed "erp-barbershop.war"`), a aplicação estará pronta!

---

### 5️⃣ Passo 6: Acesso à Aplicação
* **URL da Aplicação**: http://localhost:8080/erp-barbershop
* **Console de Administração do WildFly**: http://localhost:9990

---

## 🏗️ Stack Tecnológica

| Camada | Tecnologia | Versão | Papel |
|---|---|---|---|
| **Servidor de App** | WildFly | 26.1.3 Final | Container Jakarta EE 8 |
| **Front-end** | JSF (JavaServer Faces) | 2.3 | Framework de telas web |
| **Componentes UI** | PrimeFaces | 12.0.0 | DataTable, Charts, Dialog, etc. |
| **Back-end** | EJB (Enterprise JavaBeans) | 3.2 | Lógica de negócio + transações |
| **Persistência** | JPA / Hibernate | 2.2 / 5.3 | ORM para o banco de dados |
| **Banco de Dados** | PostgreSQL | 15 | Banco relacional |
| **Segurança** | Spring Security | 5.8.14 | Autenticação + RBAC |
| **Relatórios PDF** | iText 7 | 7.2.5 | Geração de arquivos PDF |
| **Relatórios CSV** | OpenCSV | 5.9 | Exportação para planilhas |
| **Build** | Maven | 3.9 | Compilação e empacotamento |
| **Contêiner** | Docker + Compose | 3.9 | Ambiente de execução |
| **Orquestração** | Kubernetes | — | Produção (manifestos em `k8s/`) |
| **Linguagem** | Java | 17 (LTS) | Back-end |

---

## 📁 Estrutura de Diretórios

```
Projeto_POO3/
│
├── 📄 docker-compose.yml          ← Sobe tudo com um comando
├── 📄 Dockerfile                  ← Build multi-stage Maven → WildFly
├── 📄 .dockerignore               ← Arquivos ignorados no build Docker
├── 📄 pom.xml                     ← Dependências Maven
├── 📄 deploy-k8s.sh               ← Script de deploy Kubernetes (Bash)
│
├── 📁 docker/
│   └── wildfly-cli/
│       └── configure-datasource.cli  ← Configura conexão DB no WildFly
│
├── 📁 k8s/                        ← Manifestos Kubernetes (produção)
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── persistence.yaml
│   ├── deployment.yaml
│   └── service.yaml
│
├── 📁 docs/
│   ├── schema.sql                 ← Script DDL do banco (executado automaticamente)
│   └── documentacao.md           ← Documentação técnica completa
│
└── 📁 src/main/
    ├── 📁 java/com/erp/
    │   ├── identidade/            ← Módulo: Usuários e Segurança
    │   ├── catalogo/              ← Módulo: Produtos e Categorias
    │   ├── compras/               ← Módulo: Fornecedores
    │   ├── vendas/                ← Módulo: Vendas e PDV
    │   └── relatorios/            ← Módulo: Dashboard e Exportações
    │
    ├── 📁 resources/
    │   └── META-INF/
    │       └── persistence.xml   ← Configuração JPA
    │
    └── 📁 webapp/
        ├── WEB-INF/
        │   ├── web.xml           ← Configuração do servlet container
        │   ├── beans.xml         ← Habilita CDI
        │   └── faces-config.xml  ← Configuração JSF
        ├── resources/
        │   ├── css/erp.css       ← Design system (tema dark)
        │   ├── css/dashboard.css ← Estilos do dashboard
        │   └── js/dashboard-charts.js ← Extenders Chart.js
        └── pages/
            ├── identidade/       ← Tela de usuários
            ├── catalogo/         ← Tela de produtos
            ├── compras/          ← Tela de fornecedores
            ├── vendas/           ← Tela de PDV
            └── relatorios/       ← Dashboard e relatórios
```

---

## 🗄️ Banco de Dados — Tabelas

O schema é criado automaticamente pelo PostgreSQL no Docker ou executado manualmente localmente.
O arquivo SQL está em [`docs/schema.sql`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/docs/schema.sql).

### 📊 Visão geral das tabelas

```
papeis ──────────────────────────── usuario_papel ──── usuarios
                                                            │
                                                            │ (FK)
log_acessos ────────────────────────────────────────────────┘

categorias ──── produtos ──── fornecedores
                    │
                    │ (FK via item)
                itens_venda ──── vendas ──── usuarios
```

### Tabela `papeis` — Papéis do sistema (RBAC)

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nome` | VARCHAR(50) UNIQUE | Nome do papel: `ROLE_ADMIN`, `ROLE_GERENTE`, `ROLE_BARBEIRO`, `ROLE_CAIXA` |

**Dados iniciais:** Os 4 papéis são criados automaticamente pelo `schema.sql`.

### Tabela `usuarios` — Usuários do sistema

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nome` | VARCHAR(150) NOT NULL | Nome completo |
| `email` | VARCHAR(200) UNIQUE | Login do usuário |
| `senha` | VARCHAR(255) | Hash BCrypt (nunca texto puro) |
| `ativo` | BOOLEAN DEFAULT true | `false` = conta desativada (soft delete) |

### Tabela `usuario_papel` — Vínculo Usuário ↔ Papel (N:N)

| Coluna | Tipo | Descrição |
|---|---|---|
| `usuario_id` | BIGINT FK | Referência a `usuarios.id` |
| `papel_id` | BIGINT FK | Referência a `papeis.id` |
| PK composta | — | `(usuario_id, papel_id)` |

### Tabela `categorias` — Categorias de produtos/serviços

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nome` | VARCHAR(100) UNIQUE | Ex: Cortes, Barba, Pomadas |
| `descricao` | VARCHAR(500) | Descrição opcional |

### Tabela `fornecedores` — Fornecedores de produtos

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nome` | VARCHAR(200) NOT NULL | Razão social |
| `cnpj` | CHAR(14) UNIQUE | 14 dígitos numéricos sem formatação |
| `email_contato` | VARCHAR(200) | Email de contato |
| `telefone` | VARCHAR(20) | Telefone opcional |

**Constraint:** `CHECK (cnpj ~ '^[0-9]{14}$')` — apenas dígitos.

### Tabela `produtos` — Catálogo de produtos e serviços

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nome` | VARCHAR(150) NOT NULL | Nome do produto/serviço |
| `descricao` | VARCHAR(500) | Descrição opcional |
| `preco` | NUMERIC(10,2) | Preço de venda (≥ 0) |
| `quantidade_estoque` | INTEGER | `NULL` para serviços sem estoque |
| `quantidade_minima` | INTEGER | Gatilho de alerta de reposição |
| `ativo` | BOOLEAN DEFAULT true | Soft delete — histórico preservado |
| `categoria_id` | BIGINT FK NOT NULL | Categoria do produto |
| `fornecedor_id` | BIGINT FK NULL | `NULL` para serviços internos |

**Índices:** `(ativo)` parcial, `(categoria_id)`, `(fornecedor_id)`.

### Tabela `vendas` — Vendas / Comandas

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `data_venda` | TIMESTAMP DEFAULT NOW() | Momento da abertura |
| `valor_total` | NUMERIC(12,2) DEFAULT 0 | Total calculado (≥ 0) |
| `forma_pagamento` | VARCHAR(20) | `DINHEIRO`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `PIX`, `TRANSFERENCIA` |
| `status` | VARCHAR(15) DEFAULT 'ABERTA' | `ABERTA`, `FECHADA`, `CANCELADA` |
| `usuario_id` | BIGINT FK NOT NULL | Operador que realizou a venda |

**Índices:** `(usuario_id)`, `(data_venda)`, `(status)`.

### Tabela `itens_venda` — Itens de cada venda

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `quantidade` | INTEGER NOT NULL | Quantidade vendida (> 0) |
| `preco_unitario` | NUMERIC(10,2) | **Snapshot** do preço no momento da venda |
| `venda_id` | BIGINT FK NOT NULL | Venda à qual pertence (CASCADE DELETE) |
| `produto_id` | BIGINT FK NOT NULL | Produto vendido |

> ⚠️ `preco_unitario` é um **snapshot**: guarda o preço do produto no momento da venda. Se o produto for reajustado depois, o histórico não muda.

### Tabela `log_acessos` — Auditoria (append-only)

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `data_hora` | TIMESTAMP NOT NULL | Momento da ação |
| `acao` | VARCHAR(100) | Ex: `LOGIN`, `LOGOUT`, `VENDA`, `ESTORNO` |
| `ip` | VARCHAR(45) | IP do cliente (suporta IPv4 e IPv6) |
| `resultado` | VARCHAR(10) | `SUCESSO` ou `ERRO` |
| `usuario_id` | BIGINT FK NOT NULL | Quem executou a ação (RESTRICT — nunca apaga) |

> Esta tabela é **imutável**: registros nunca devem ser alterados ou deletados.

---

## ⚙️ Back-end — Módulos DDD

A aplicação segue uma arquitetura em camadas dentro de cada módulo:
`Controller (CDI/JSF) → Service (EJB @Stateless) → Repository (JPA) → Entity`

### Módulo `identidade` — Usuários e Segurança

**Entidades:** [`Usuario`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/Usuario.java), [`Papel`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/Papel.java), [`LogAcesso`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/LogAcesso.java)

| Classe | Tipo | Responsabilidade |
|---|---|---|
| `UsuarioService` | `@Stateless` EJB | CRUD de usuários, hash BCrypt, validação de duplicatas |
| `UsuarioRepository` | JPA | Queries: busca por email, listagem ativa |
| `UsuarioController` | `@ViewScoped` CDI | Gerencia o formulário de usuários na tela |
| `UsuarioDetailsService` | Spring | Carrega `UserDetails` para o Spring Security |
| `SecurityConfig` | Spring `@Configuration` | Regras RBAC, login, logout, CSRF, headers |
| `LoginAttemptService` | `@Singleton` EJB | Rastreia falhas de login por IP (brute-force) |
| `AuditoriaFilter` | Servlet Filter | Grava `LogAcesso` em toda requisição |

### Módulo `catalogo` — Produtos e Categorias

**Entidades:** `Produto`, `Categoria`

| Classe | Tipo | Responsabilidade |
|---|---|---|
| `ProdutoService` | `@Stateless` EJB | CRUD de produtos, controle de estoque, soft delete |
| `ProdutoRepository` | JPA | Queries: busca por categoria, estoque baixo, ativos |
| `ProdutoController` | `@ViewScoped` CDI | Formulário e listagem de produtos na tela |

### Módulo `compras` — Fornecedores

**Entidades:** [`Fornecedor`](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/compras/model/Fornecedor.java), `SugestaoCompra`

| Classe | Tipo | Responsabilidade |
|---|---|---|
| `FornecedorService` | `@Stateless` EJB | CRUD de fornecedores, validação de CNPJ |
| `FornecedorRepository` | JPA | Queries: busca por CNPJ, listagem |
| `FornecedorController` | `@ViewScoped` CDI | Formulário de fornecedores |

### Módulo `vendas` — PDV (Ponto de Venda)

**Entidades:** `Venda` (Aggregate Root), `ItemVenda`

| Classe | Tipo | Responsabilidade |
|---|---|---|
| `VendaService` | `@Stateless` EJB | Abre/fecha/cancela venda, adiciona itens, `PESSIMISTIC_WRITE` no estoque |
| `VendaRepository` | JPA | Queries: vendas abertas por usuário, histórico |
| `PdvController` | `@ViewScoped` CDI | Controla o fluxo do carrinho no PDV |

> `PESSIMISTIC_WRITE` é usado ao decrementar estoque — evita race condition quando dois atendentes vendem o último item simultaneamente.

### Módulo `relatorios` — Dashboard e Exportações

| Classe | Tipo | Responsabilidade |
|---|---|---|
| `RelatorioService` | `@Stateless` EJB | 5 queries JPQL analíticas (KPIs, top-5, histórico, alertas) |
| `DashboardController` | `@ViewScoped` CDI | Carrega dados e monta os modelos dos gráficos PrimeFaces |
| `RelatorioExportController` | `@RequestScoped` CDI | Exporta PDF/CSV diretamente no `HttpServletResponse` |
| `ProdutoMaisVendidoDTO` | DTO | Projeção JPQL (`NEW`) para top-5 produtos |
| `VendasPorDiaDTO` | DTO | Projeção JPQL para histórico diário |

---

## 🖥️ Front-end — Páginas e Componentes

### Tecnologia

O front-end usa **JSF 2.3** (JavaServer Faces) com componentes **PrimeFaces 12**. As páginas são arquivos `.xhtml` (Facelets). O estilo usa **CSS puro** com tema dark personalizado.

### Páginas disponíveis

| Página | Acesso | Descrição |
|---|---|---|
| `/login.xhtml` | Público | Tela de login com email + senha |
| `/pages/relatorios/dashboard.xhtml` | Admin | Painel com KPIs e gráficos |
| `/pages/relatorios/relatorios.xhtml` | Admin | Exportação de relatórios PDF/CSV |
| `/pages/identidade/usuarios.xhtml` | Admin | CRUD de usuários do sistema |
| `/pages/catalogo/produtos.xhtml` | Admin + Default | CRUD de produtos e serviços |
| `/pages/vendas/pdv.xhtml` | Admin + Default | Ponto de venda (carrinho) |
| `/pages/compras/fornecedores.xhtml` | Admin + Default | CRUD de fornecedores |

### Componentes PrimeFaces usados

| Componente | Onde | Função |
|---|---|---|
| `<p:dataTable>` | Produtos, Usuários, Fornecedores | Tabela com paginação, ordenação e filtro |
| `<p:dialog>` | Formulários de CRUD | Modal de criação/edição |
| `<p:chart>` | Dashboard | Gráficos de barras e pizza (Chart.js) |
| `<p:datePicker>` | Relatórios | Seletor de período para exportação |
| `<p:commandButton>` | Toda a aplicação | Botões com AJAX |
| `<p:inputText>` | Formulários | Campos de texto com validação |
| `<p:growl>` | Toda a aplicação | Notificações de sucesso/erro |
| `<p:confirmDialog>` | Deleções | Confirmação antes de excluir |
| `<p:panelGrid>` | Formulários | Layout de formulários em grid |

### Design System

O projeto usa um **tema dark** próprio definido em `erp.css` com variáveis CSS:

```css
--color-bg:       #0f1117  /* Fundo da página */
--color-surface:  #1a1d27  /* Cards e painéis */
--color-primary:  #6c63ff  /* Cor de destaque (roxo) */
--color-success:  #22c55e  /* Verde */
--color-warning:  #f59e0b  /* Amarelo */
--color-danger:   #ef4444  /* Vermelho */
```

### Dashboard — Gráficos

| Gráfico | Tipo | Dados |
|---|---|---|
| Faturamento Semanal | Barras verticais | Soma das vendas dos últimos 7 dias |
| Top-5 Mais Vendidos | Barras horizontais | Produtos com mais unidades vendidas no mês |
| Receita por Produto | Pizza | Participação % de cada produto na receita |

Os gráficos usam **extenders Chart.js** para aplicar o tema dark (tooltips em R$, fundo escuro, cores harmoniosas).

---

## 🔐 Segurança

### Autenticação

- Login via **email + senha**
- Senha armazenada como **hash BCrypt com custo 12** (~250ms por verificação)
- **Bloqueio por IP** após 5 tentativas falhas (liberado após 30 minutos)

### Autorização (RBAC)

| Papel | Acesso |
|---|---|
| `ROLE_ADMIN` | Tudo: usuários, relatórios, dashboard, produtos, vendas, compras |
| `ROLE_DEFAULT` | Limitado: produtos, vendas, compras (sem usuários e relatórios) |

> Os papéis `ROLE_GERENTE`, `ROLE_BARBEIRO` e `ROLE_CAIXA` existem no banco mas ainda não possuem regras de autorização mapeadas — podem ser adicionadas expandindo o `SecurityConfig`.

### Proteções implementadas

| Proteção | Mecanismo |
|---|---|
| **CSRF** | Token automático Spring Security em todos os forms |
| **XSS** | Header `X-XSS-Protection` + CSP (Content-Security-Policy) |
| **Clickjacking** | Header `X-Frame-Options: DENY` |
| **Session Fixation** | Migração de sessão no login (`migrateSession()`) |
| **Sessão múltipla** | Máximo 1 sessão ativa por usuário |
| **Session timeout** | 30 minutos de inatividade |
| **Auditoria** | Todo acesso gravado em `log_acessos` |

### HTTPS (Produção)

O HTTPS está **desabilitado por padrão** para facilitar o desenvolvimento com Docker Compose. Para ativar em produção:
1. Configure o keystore SSL no WildFly
2. Descomente o `security-constraint` em `web.xml`
3. Descomente `.requiresChannel().anyRequest().requiresSecure()` em `SecurityConfig.java`

---

## 🐳 Infraestrutura Docker

### Como funciona o Dockerfile

```
Stage 1 (builder)           Stage 2 (runtime)
──────────────────          ──────────────────────────
maven:3.9-jdk17             wildfly:26.1.3.Final-jdk17
│
├─ COPY pom.xml             ├─ Configura usuário admin
├─ RUN mvn dependency:go-offline  ← cache de deps
├─ COPY src/               ├─ COPY CLI datasource script
├─ RUN mvn package          ├─ RUN jboss-cli.sh (configura banco)
└─ target/erp-barbershop.war ──► COPY → /deployments/
                            └─ CMD standalone.sh -b 0.0.0.0
```

**Por que multi-stage?** A imagem final não contém Maven, JDK completo, nem código-fonte. Apenas o WAR compilado e o WildFly.

### Como funciona o Docker Compose

```
docker compose up --build
       │
       ├─► [1] Constrói a imagem wildfly (Dockerfile multi-stage)
       │
       ├─► [2] Inicia postgres:15-alpine
       │        ├─ Cria banco "erp_db"
       │        ├─ Executa schema.sql (tabelas + papéis padrão)
       │        └─ Healthcheck: pg_isready (aguarda até estar pronto)
       │
       └─► [3] Inicia wildfly (após postgres passar no healthcheck)
                ├─ Lê variáveis DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
                ├─ WildFly conecta ao banco via JNDI datasource
                └─ Deploy automático do erp-barbershop.war
```

### Variáveis de Ambiente

| Variável | Valor padrão | Onde é usada |
|---|---|---|
| `DB_HOST` | `postgres` | Hostname do banco no Docker Compose |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `erp_db` | Nome do banco de dados |
| `DB_USER` | `erp_admin` | Usuário do banco |
| `DB_PASSWORD` | `erp_secret_2026` | Senha do banco |

---

## ☸️ Infraestrutura Kubernetes (Produção)

Os manifestos em `k8s/` permitem rodar o sistema em um cluster Kubernetes (Minikube, Kind ou cloud).

| Arquivo | Função |
|---|---|
| `namespace.yaml` | Cria o namespace `erp` para isolar os recursos |
| `configmap.yaml` | Configurações não-sensíveis (host, porta, JVM opts) |
| `secret.yaml` | Credenciais em Base64 (senha do banco, admin WildFly) |
| `persistence.yaml` | Volume persistente de 2GB para o PostgreSQL |
| `deployment.yaml` | 2 réplicas WildFly + 1 réplica PostgreSQL com probes de saúde |
| `service.yaml` | ClusterIP (banco), NodePort 30080 (app), ClusterIP (admin) |

```bash
# Deploy Kubernetes (requer Minikube instalado)
chmod +x deploy-k8s.sh
./deploy-k8s.sh
```

---

## 🔍 Problemas Encontrados e Corrigidos

Durante a análise final do projeto, foram identificados e corrigidos **2 problemas** que impediam a execução via Docker Compose:

### Problema 1 — HTTPS forçado no `web.xml`
**Sintoma:** O navegador ficava redirecionando em loop ou exibia "ERR_TOO_MANY_REDIRECTS".

**Causa:** O `<transport-guarantee>CONFIDENTIAL</transport-guarantee>` no `web.xml` instruía o WildFly a redirecionar toda requisição HTTP para HTTPS. Como o Docker Compose não configura SSL, o redirect não tinha destino.

**Correção:** O bloco `<security-constraint>` foi comentado no `web.xml`. Para produção com SSL, basta descomentar.

### Problema 2 — HTTPS forçado no Spring Security
**Sintoma:** Mesmo sem o bloco do `web.xml`, o Spring Security ainda redirecionava para HTTPS.

**Causa:** `.requiresChannel().anyRequest().requiresSecure()` no `SecurityConfig.java`.

**Correção:** O bloco foi comentado. Ambos precisam ser ativados juntos quando SSL for configurado.

### Problema 3 — Schema SQL não era criado automaticamente
**Sintoma:** O banco subia vazio; o WildFly falhava ao tentar validar as entidades JPA.

**Causa:** O `docker-compose.yml` não montava o `schema.sql` no PostgreSQL.

**Correção:** Adicionado o volume `./docs/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro` no `docker-compose.yml`. O PostgreSQL executa automaticamente arquivos `.sql` do diretório `/docker-entrypoint-initdb.d/` na primeira inicialização.

---

## 🛠️ Guia de Solução de Problemas

### A aplicação não abre no navegador
```bash
# Verifique se os containers estão rodando
docker compose ps

# Veja os logs do WildFly
docker compose logs wildfly --tail=50

# O WildFly demora ~2-3 min. Aguarde a mensagem:
# "WildFly Full 26.1.3.Final (WildFly Core 18.1.1.Final) started"
```

### Erro de conexão com o banco
```bash
# Verifique se o PostgreSQL está saudável
docker compose ps postgres
# Status deve ser "healthy", não "starting"

# Veja os logs do banco
docker compose logs postgres --tail=30
```

### Dados foram perdidos após reiniciar
```bash
# Verifique se o volume existe
docker volume ls | grep pgdata

# Se não existir, recrie:
docker compose down
docker compose up --build
```

### Quero recriar o banco do zero (apagar todos os dados)
```bash
docker compose down -v   # -v remove os volumes
docker compose up --build
```

### Porta 8080 já está em uso
```bash
# Mude a porta no docker-compose.yml:
# ports:
#   - "8090:8080"   ← Porta externa diferente
# Acesse: http://localhost:8090/erp-barbershop
```

---

## 📊 Requisições JPQL — Relatórios

O `RelatorioService` contém 5 queries analíticas otimizadas:

| Query | Técnica | Descrição |
|---|---|---|
| Total vendas hoje/mês | `COALESCE(SUM(...), 0)` | Retorna 0 quando não há vendas (sem NPE) |
| Top-5 mais vendidos | `NEW DTO(...)` + `GROUP BY p.id` | Projeção direta sem carregar entidades completas |
| Histórico 7 dias | `FUNCTION('date',...)` + preenchimento de gaps em Java | Todos os 7 dias aparecem, mesmo sem venda |
| Alertas de estoque | `quantidade_estoque <= quantidade_minima` | Índice parcial acelera a query |
| Relatório financeiro | `DISTINCT + LEFT JOIN FETCH` | Anti-N+1: carrega hierarquia em 1 query |

---

## 🎓 Contexto Acadêmico

| Item | Detalhe |
|---|---|
| **Disciplina** | Programação Orientada a Objetos 3 (POO3) |
| **Instituição** | UERJ — Universidade do Estado do Rio de Janeiro |
| **Período** | 5º Período, 2026 |
| **Padrão arquitetural** | DDD (Domain-Driven Design) em monolito modular |
| **Etapas do projeto** | 6 etapas: Modelagem → Banco → Segurança → Vendas → Relatórios → DevOps |
