package com.erp.relatorios.model;

import java.io.Serializable;

/**
 * DTO (Data Transfer Object) que transporta o resultado da consulta JPQL
 * de "produtos mais vendidos do mês" para o Dashboard.
 *
 * <p>Não é uma entidade JPA — não possui {@code @Entity} nem tabela própria.
 * É construído diretamente dentro da projeção JPQL via {@code NEW}.</p>
 *
 * <h3>Uso no JPQL (construtor expression):</h3>
 * <pre>
 *   SELECT NEW com.erp.relatorios.model.ProdutoMaisVendidoDTO(
 *       p.nome, SUM(iv.quantidade), SUM(iv.precoUnitario * iv.quantidade)
 *   )
 *   FROM ItemVenda iv
 *   JOIN iv.produto p
 *   ...
 * </pre>
 *
 * @see com.erp.relatorios.service.RelatorioService
 * @see com.erp.relatorios.controller.DashboardController
 */
public class ProdutoMaisVendidoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Nome do produto (p.nome). */
    private String nomeProduto;

    /** Soma total de unidades vendidas no período (SUM de iv.quantidade). */
    private Long totalVendido;

    /** Receita gerada pelo produto no período (SUM de iv.precoUnitario * iv.quantidade). */
    private Double receitaGerada;

    // ==========================================================
    // Construtor — invocado diretamente pelo JPQL "NEW ..."
    // ==========================================================

    /**
     * Construtor exigido pela expressão JPQL {@code NEW ProdutoMaisVendidoDTO(...)}.
     * Os tipos dos parâmetros devem corresponder exatamente ao que a query projeta.
     *
     * @param nomeProduto  nome do produto (String)
     * @param totalVendido total de unidades vendidas (Long — resultado do SUM(Integer))
     * @param receitaGerada receita bruta gerada (Double — resultado do SUM produto BigDecimal→Double)
     */
    public ProdutoMaisVendidoDTO(String nomeProduto, Long totalVendido, Double receitaGerada) {
        this.nomeProduto   = nomeProduto;
        this.totalVendido  = totalVendido;
        this.receitaGerada = receitaGerada != null ? receitaGerada : 0.0;
    }

    // ==========================================================
    // Getters
    // ==========================================================

    public String getNomeProduto() {
        return nomeProduto;
    }

    public Long getTotalVendido() {
        return totalVendido;
    }

    public Double getReceitaGerada() {
        return receitaGerada;
    }

    // Abreviação do nome para exibição em gráfico (evita overflow no eixo X)
    public String getNomeAbreviado() {
        if (nomeProduto == null) return "";
        return nomeProduto.length() > 18 ? nomeProduto.substring(0, 16) + "…" : nomeProduto;
    }

    @Override
    public String toString() {
        return "ProdutoMaisVendidoDTO{" +
               "nomeProduto='" + nomeProduto + '\'' +
               ", totalVendido=" + totalVendido +
               ", receitaGerada=" + receitaGerada +
               '}';
    }
}
