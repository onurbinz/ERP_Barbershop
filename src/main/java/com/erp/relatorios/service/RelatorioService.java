package com.erp.relatorios.service;

import com.erp.catalogo.model.Produto;
import com.erp.relatorios.model.ProdutoMaisVendidoDTO;
import com.erp.relatorios.model.VendasPorDiaDTO;
import com.erp.vendas.model.StatusVenda;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Session Bean (Stateless) responsável por todas as consultas analíticas
 * do módulo de Relatórios e Dashboard do ERP Barbershop.
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>Total de vendas do dia corrente (KPI principal do Dashboard)</li>
 *   <li>Top-5 produtos mais vendidos no mês corrente (gráfico de barras)</li>
 *   <li>Histórico de vendas dos últimos 7 dias (gráfico de linha)</li>
 *   <li>Produtos com estoque baixo (alertas do Dashboard e relatório)</li>
 *   <li>Relatório financeiro por período (exportação PDF/CSV)</li>
 * </ul>
 *
 * <h2>Decisões de Design JPQL</h2>
 * <ul>
 *   <li>Todas as queries de sumarização usam projeção via {@code NEW DTO(...)},
 *       evitando carregar entidades completas só para somar campos.</li>
 *   <li>Queries de relatório usam {@code SUPPORTS} para não abrir transação
 *       desnecessária em leituras.</li>
 *   <li>O índice composto {@code (status, data_venda)} na tabela {@code vendas}
 *       torna as queries de período O(log n) em vez de full-scan.</li>
 * </ul>
 *
 * @see com.erp.relatorios.controller.DashboardController
 * @see com.erp.relatorios.controller.RelatorioExportController
 */
@Stateless
public class RelatorioService {

    private static final Logger LOG = Logger.getLogger(RelatorioService.class.getName());

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // KPIs do Dashboard — Dia Corrente
    // =========================================================

    /**
     * Retorna o valor total faturado no dia corrente (vendas com status FECHADA).
     *
     * <h3>JPQL utilizado:</h3>
     * <pre>
     *   SELECT COALESCE(SUM(v.valorTotal), 0)
     *   FROM Venda v
     *   WHERE v.status = 'FECHADA'
     *     AND v.dataVenda >= :inicioDia
     *     AND v.dataVenda <  :fimDia
     * </pre>
     *
     * <p>O {@code COALESCE} garante que retorna 0 quando não há vendas,
     * evitando {@code NullPointerException} no controller.</p>
     *
     * @return total faturado hoje em R$
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public BigDecimal totalVendasHoje() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia   = inicioDia.plusDays(1);

        LOG.fine("[RelatorioService] Consultando total de vendas do dia: " + LocalDate.now());

        // COALESCE(SUM(...), 0) — nunca retorna null mesmo se não há vendas
        Object resultado = em.createQuery(
                "SELECT COALESCE(SUM(v.valorTotal), 0) " +
                "FROM Venda v " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim")
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicioDia)
            .setParameter("fim",    fimDia)
            .getSingleResult();

        // Hibernate pode retornar BigDecimal ou Double dependendo do banco
        if (resultado instanceof BigDecimal bd) return bd;
        if (resultado instanceof Double d)     return BigDecimal.valueOf(d);
        return BigDecimal.ZERO;
    }

    /**
     * Retorna o número de transações (vendas fechadas) realizadas hoje.
     *
     * @return contagem de vendas do dia
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Long contagemVendasHoje() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia   = inicioDia.plusDays(1);

        return em.createQuery(
                "SELECT COUNT(v) FROM Venda v " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim",
                Long.class)
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicioDia)
            .setParameter("fim",    fimDia)
            .getSingleResult();
    }

    /**
     * Retorna o total faturado no mês corrente.
     *
     * @return total do mês em R$
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public BigDecimal totalVendasMes() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes    = inicioMes.plusMonths(1);

        Object resultado = em.createQuery(
                "SELECT COALESCE(SUM(v.valorTotal), 0) " +
                "FROM Venda v " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim")
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicioMes)
            .setParameter("fim",    fimMes)
            .getSingleResult();

        if (resultado instanceof BigDecimal bd) return bd;
        if (resultado instanceof Double d)     return BigDecimal.valueOf(d);
        return BigDecimal.ZERO;
    }

    // =========================================================
    // Top-5 Produtos Mais Vendidos — Mês Corrente
    // =========================================================

    /**
     * Retorna os 5 produtos com maior volume de vendas no mês corrente.
     *
     * <h3>JPQL com projeção DTO (Construtor Expression):</h3>
     * <pre>
     *   SELECT NEW ProdutoMaisVendidoDTO(
     *       p.nome,
     *       SUM(iv.quantidade),
     *       SUM(iv.precoUnitario * iv.quantidade)
     *   )
     *   FROM ItemVenda iv
     *   JOIN iv.produto p
     *   JOIN iv.venda v
     *   WHERE v.status = FECHADA
     *     AND v.dataVenda BETWEEN :inicioMes AND :fimMes
     *   GROUP BY p.id, p.nome
     *   ORDER BY SUM(iv.quantidade) DESC
     * </pre>
     *
     * <p><strong>Por que GROUP BY p.id e p.nome?</strong> Em JPQL (diferente do SQL),
     * o GROUP BY deve incluir todos os campos não-agregados. Incluir p.id garante
     * unicidade mesmo se dois produtos tiverem nomes iguais.</p>
     *
     * @return lista de até 5 DTOs com nome, total vendido e receita gerada
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<ProdutoMaisVendidoDTO> top5ProdutosMes() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes    = inicioMes.plusMonths(1);

        LOG.fine("[RelatorioService] Consultando top-5 produtos do mês " + LocalDate.now().getMonthValue());

        return em.createQuery(
                "SELECT NEW com.erp.relatorios.model.ProdutoMaisVendidoDTO(" +
                "    p.nome, " +
                "    SUM(iv.quantidade), " +
                "    SUM(CAST(iv.precoUnitario AS double) * iv.quantidade)" +
                ") " +
                "FROM ItemVenda iv " +
                "JOIN iv.produto p " +
                "JOIN iv.venda v " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim " +
                "GROUP BY p.id, p.nome " +
                "ORDER BY SUM(iv.quantidade) DESC",
                ProdutoMaisVendidoDTO.class)
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicioMes)
            .setParameter("fim",    fimMes)
            .setMaxResults(5)
            .getResultList();
    }

    // =========================================================
    // Histórico de Vendas — Últimos 7 Dias (Gráfico de Barras)
    // =========================================================

    /**
     * Retorna o faturamento diário dos últimos 7 dias para o gráfico de histórico.
     *
     * <p>A query agrupa por {@code FUNCTION('DATE', v.dataVenda)} — função portável
     * que extrai a parte de data de um LocalDateTime no Hibernate/PostgreSQL.</p>
     *
     * <p>O resultado é um mapa ordenado (dia → total), garantindo que todos
     * os 7 dias apareçam no gráfico, mesmo os sem venda (preenchidos com 0.0).</p>
     *
     * @return lista de DTOs com rótulo do dia e total faturado, ordenado do mais antigo ao mais recente
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<VendasPorDiaDTO> vendasUltimos7Dias() {
        LocalDateTime inicio = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime fim    = LocalDate.now().plusDays(1).atStartOfDay();

        // Query agrupa por data (extrai DATE do TIMESTAMP)
        // Hibernate traduz FUNCTION('date', col) → date(col) no PostgreSQL
        @SuppressWarnings("unchecked")
        List<Object[]> rawResults = em.createQuery(
                "SELECT FUNCTION('date', v.dataVenda), " +
                "       COUNT(v), " +
                "       SUM(CAST(v.valorTotal AS double)) " +
                "FROM Venda v " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim " +
                "GROUP BY FUNCTION('date', v.dataVenda) " +
                "ORDER BY FUNCTION('date', v.dataVenda) ASC")
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicio)
            .setParameter("fim",    fim)
            .getResultList();

        // Constrói mapa local (data ISO → DTO) para preenchimento de gaps
        Map<LocalDate, VendasPorDiaDTO> mapa = new LinkedHashMap<>();
        for (Object[] row : rawResults) {
            // row[0] pode ser java.sql.Date ou LocalDate dependendo do Hibernate dialect
            LocalDate data;
            if (row[0] instanceof java.sql.Date sqlDate) {
                data = sqlDate.toLocalDate();
            } else if (row[0] instanceof LocalDate ld) {
                data = ld;
            } else {
                continue;
            }
            Long   qtd   = row[1] instanceof Long l ? l : 0L;
            Double total = row[2] instanceof Double d ? d : 0.0;
            mapa.put(data, new VendasPorDiaDTO(nomeDia(data), qtd, total));
        }

        // Garante que todos os 7 dias aparecem, preenchendo gaps com 0
        List<VendasPorDiaDTO> resultado = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate data = LocalDate.now().minusDays(i);
            resultado.add(mapa.getOrDefault(data,
                new VendasPorDiaDTO(nomeDia(data), 0L, 0.0)));
        }
        return resultado;
    }

    /** Retorna a abreviação do dia da semana em português (ex: "Seg", "Ter"). */
    private String nomeDia(LocalDate data) {
        // Usa Locale pt-BR para "seg.", trunca e capitaliza
        String nome = data.getDayOfWeek()
            .getDisplayName(TextStyle.SHORT, new Locale("pt", "BR"));
        return nome.length() >= 3 ? nome.substring(0, 3) : nome;
    }

    // =========================================================
    // Alertas de Estoque Baixo
    // =========================================================

    /**
     * Retorna todos os produtos ativos com estoque abaixo ou igual ao mínimo.
     *
     * <h3>JPQL:</h3>
     * <pre>
     *   SELECT p FROM Produto p
     *   WHERE p.ativo = true
     *     AND p.quantidadeEstoque IS NOT NULL
     *     AND p.quantidadeMinima IS NOT NULL
     *     AND p.quantidadeEstoque <= p.quantidadeMinima
     *   ORDER BY p.quantidadeEstoque ASC
     * </pre>
     *
     * <p>Ordenação crescente por estoque coloca os mais críticos (menor estoque)
     * no topo da lista de alertas.</p>
     *
     * @return lista de produtos em alerta de estoque, do mais crítico ao menos crítico
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Produto> produtosEstoqueBaixo() {
        return em.createQuery(
                "SELECT p FROM Produto p " +
                "WHERE p.ativo = true " +
                "  AND p.quantidadeEstoque IS NOT NULL " +
                "  AND p.quantidadeMinima IS NOT NULL " +
                "  AND p.quantidadeEstoque <= p.quantidadeMinima " +
                "ORDER BY p.quantidadeEstoque ASC",
                Produto.class)
            .getResultList();
    }

    /**
     * Conta quantos produtos estão em alerta de estoque baixo.
     * Usado para exibir o badge de notificação no Dashboard.
     *
     * @return total de produtos em alerta
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Long contagemEstoqueBaixo() {
        return em.createQuery(
                "SELECT COUNT(p) FROM Produto p " +
                "WHERE p.ativo = true " +
                "  AND p.quantidadeEstoque IS NOT NULL " +
                "  AND p.quantidadeMinima IS NOT NULL " +
                "  AND p.quantidadeEstoque <= p.quantidadeMinima",
                Long.class)
            .getSingleResult();
    }

    // =========================================================
    // Relatório Financeiro por Período (para exportação)
    // =========================================================

    /**
     * Retorna todas as vendas fechadas em um intervalo de datas,
     * com {@code FETCH JOIN} dos itens e produto para evitar N+1.
     *
     * <p><strong>LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto</strong> —
     * carrega toda a hierarquia em uma única query SQL, sem lazy-loading adicional.
     * Isso é essencial para exportação de relatórios, pois o contexto JPA já
     * estará fechado quando o Servlet processar os dados.</p>
     *
     * <p>O {@code DISTINCT} evita duplicatas geradas pelo JOIN (cada item
     * gera uma linha extra no result set SQL, mas o JPQL + DISTINCT retorna
     * cada Venda uma única vez com a coleção de itens preenchida).</p>
     *
     * @param inicio data/hora de início do período (inclusiva)
     * @param fim    data/hora de fim do período (exclusiva)
     * @return lista de vendas com itens e produtos carregados (eager)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<com.erp.vendas.model.Venda> relatorioFinanceiroPorPeriodo(
            LocalDateTime inicio, LocalDateTime fim) {

        LOG.info(String.format("[RelatorioService] Relatório financeiro | período: %s → %s", inicio, fim));

        return em.createQuery(
                "SELECT DISTINCT v FROM Venda v " +
                "LEFT JOIN FETCH v.itens i " +
                "LEFT JOIN FETCH i.produto " +
                "LEFT JOIN FETCH v.usuario " +
                "WHERE v.status = :status " +
                "  AND v.dataVenda >= :inicio " +
                "  AND v.dataVenda < :fim " +
                "ORDER BY v.dataVenda ASC",
                com.erp.vendas.model.Venda.class)
            .setParameter("status", StatusVenda.FECHADA)
            .setParameter("inicio", inicio)
            .setParameter("fim",    fim)
            .getResultList();
    }

    /**
     * Retorna todos os produtos ativos com informações de estoque,
     * incluindo os que estão em alerta — para o relatório de estoque.
     *
     * @return lista de produtos ativos ordenada por nome
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Produto> relatorioEstoqueCompleto() {
        return em.createQuery(
                "SELECT p FROM Produto p " +
                "LEFT JOIN FETCH p.categoria " +
                "WHERE p.ativo = true " +
                "  AND p.quantidadeEstoque IS NOT NULL " +
                "ORDER BY p.quantidadeEstoque ASC, p.nome ASC",
                Produto.class)
            .getResultList();
    }
}
