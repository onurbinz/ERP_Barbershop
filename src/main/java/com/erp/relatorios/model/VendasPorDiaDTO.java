package com.erp.relatorios.model;

import java.io.Serializable;

/**
 * DTO que transporta o resultado da consulta de vendas agrupadas por dia
 * para alimentar o gráfico de linha/barra do histórico semanal no Dashboard.
 *
 * <p>Construído via expressão JPQL {@code NEW VendasPorDiaDTO(...)}.</p>
 *
 * @see com.erp.relatorios.service.RelatorioService
 */
public class VendasPorDiaDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Rótulo do dia (ex: "Seg", "Ter", "Qua") — formatado no service. */
    private String diaSemana;

    /** Número de vendas fechadas naquele dia. */
    private Long quantidadeVendas;

    /** Soma do valor_total das vendas daquele dia. */
    private Double totalFaturado;

    // ==========================================================
    // Construtor — invocado pelo JPQL "NEW VendasPorDiaDTO(...)"
    // ==========================================================

    public VendasPorDiaDTO(String diaSemana, Long quantidadeVendas, Double totalFaturado) {
        this.diaSemana       = diaSemana;
        this.quantidadeVendas = quantidadeVendas;
        this.totalFaturado   = totalFaturado != null ? totalFaturado : 0.0;
    }

    // ==========================================================
    // Getters
    // ==========================================================

    public String getDiaSemana() {
        return diaSemana;
    }

    public Long getQuantidadeVendas() {
        return quantidadeVendas;
    }

    public Double getTotalFaturado() {
        return totalFaturado;
    }

    @Override
    public String toString() {
        return "VendasPorDiaDTO{" +
               "diaSemana='" + diaSemana + '\'' +
               ", quantidadeVendas=" + quantidadeVendas +
               ", totalFaturado=" + totalFaturado +
               '}';
    }
}
