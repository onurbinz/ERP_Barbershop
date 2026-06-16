package com.erp.relatorios.controller;

import com.erp.catalogo.model.Produto;
import com.erp.relatorios.model.ProdutoMaisVendidoDTO;
import com.erp.relatorios.model.VendasPorDiaDTO;
import com.erp.relatorios.service.RelatorioService;

import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.bar.BarChartDataSet;
import org.primefaces.model.charts.bar.BarChartModel;
import org.primefaces.model.charts.bar.BarChartOptions;
import org.primefaces.model.charts.optionconfig.legend.Legend;
import org.primefaces.model.charts.optionconfig.title.Title;
import org.primefaces.model.charts.pie.PieChartDataSet;
import org.primefaces.model.charts.pie.PieChartModel;
import org.primefaces.model.charts.pie.PieChartOptions;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean (ViewScoped + CDI) que controla o painel Dashboard.
 *
 * <h2>Ciclo de vida</h2>
 * <p>Por ser {@code @ViewScoped}, o bean vive enquanto o usuário estiver
 * na mesma view JSF. Ao navegar para outra página e voltar, {@code @PostConstruct}
 * é chamado novamente, atualizando os dados do Dashboard.</p>
 *
 * <h2>Fluxo de dados: JPQL → Chart Model → XHTML</h2>
 * <ol>
 *   <li>{@code @PostConstruct init()} chama o {@link RelatorioService} via {@code @EJB}</li>
 *   <li>Os dados brutos (DTOs + BigDecimal) são convertidos em modelos PrimeFaces
 *       ({@link BarChartModel}, {@link PieChartModel})</li>
 *   <li>O XHTML referencia esses modelos via EL: {@code model="#{dashboardController.graficoVendas}"}</li>
 *   <li>O PrimeFaces serializa o model para JSON e injeta no Chart.js do cliente</li>
 * </ol>
 *
 * @see RelatorioService
 */
@Named("dashboardController")
@ViewScoped
public class DashboardController implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(DashboardController.class.getName());

    // Paleta de cores do design system do ERP (extraída de erp.css)
    private static final String COR_PRIMARY  = "rgba(108, 99, 255, 0.85)";
    private static final String COR_PRIMARY_BORDER = "rgba(108, 99, 255, 1)";
    private static final String COR_SUCCESS  = "rgba(34, 197, 94, 0.85)";
    private static final String COR_WARNING  = "rgba(245, 158, 11, 0.85)";
    private static final String COR_DANGER   = "rgba(239, 68, 68, 0.85)";
    private static final String COR_INFO     = "rgba(59, 130, 246, 0.85)";
    private static final List<String> PALETA_PIZZA = Arrays.asList(
        "rgba(108,99,255,0.85)", "rgba(34,197,94,0.85)",
        "rgba(245,158,11,0.85)", "rgba(59,130,246,0.85)",
        "rgba(239,68,68,0.85)"
    );
    private static final List<String> PALETA_PIZZA_BORDA = Arrays.asList(
        "rgba(108,99,255,1)", "rgba(34,197,94,1)",
        "rgba(245,158,11,1)", "rgba(59,130,246,1)",
        "rgba(239,68,68,1)"
    );

    // =========================================================
    // Injeções
    // =========================================================

    @EJB
    private RelatorioService relatorioService;

    // =========================================================
    // Estado do Bean
    // =========================================================

    // KPIs
    private BigDecimal totalVendasHoje;
    private Long       contagemVendasHoje;
    private BigDecimal totalVendasMes;
    private Long       contagemEstoqueBaixo;
    private String     totalVendasHojeFormatado;
    private String     totalVendasMesFormatado;

    // Dados brutos para tabelas
    private List<ProdutoMaisVendidoDTO> top5Produtos;
    private List<Produto>               produtosEstoqueBaixo;
    private List<VendasPorDiaDTO>       vendasSemana;

    // Modelos de gráfico PrimeFaces (Chart.js)
    private BarChartModel graficoVendasSemana;
    private BarChartModel graficoTop5Produtos;
    private PieChartModel graficoPizza;

    // =========================================================
    // Inicialização — PostConstruct
    // =========================================================

    /**
     * Inicializa todos os dados do Dashboard logo após a criação do bean.
     * Chamado automaticamente pelo CDI após injeção de dependências.
     *
     * <p>Cada bloco try-catch garante que uma falha em uma consulta
     * não derruba o Dashboard inteiro — degradação graciosa.</p>
     */
    @PostConstruct
    public void init() {
        LOG.info("[DashboardController] Inicializando painel...");
        carregarKpis();
        carregarTop5Produtos();
        carregarVendasSemana();
        carregarAlertasEstoque();
        construirGraficoBarras();
        construirGraficoTop5();
        construirGraficoPizza();
        LOG.info("[DashboardController] Dashboard carregado com sucesso.");
    }

    // =========================================================
    // Loaders — separados para permitir refresh parcial via AJAX
    // =========================================================

    /**
     * Carrega os KPIs numéricos e formata para exibição.
     * Formato monetário pt-BR: R$ 1.234,56
     */
    private void carregarKpis() {
        try {
            NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

            totalVendasHoje        = relatorioService.totalVendasHoje();
            contagemVendasHoje     = relatorioService.contagemVendasHoje();
            totalVendasMes         = relatorioService.totalVendasMes();
            contagemEstoqueBaixo   = relatorioService.contagemEstoqueBaixo();

            totalVendasHojeFormatado = fmt.format(totalVendasHoje);
            totalVendasMesFormatado  = fmt.format(totalVendasMes);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DashboardController] Erro ao carregar KPIs", e);
            totalVendasHoje          = BigDecimal.ZERO;
            totalVendasMes           = BigDecimal.ZERO;
            contagemVendasHoje       = 0L;
            contagemEstoqueBaixo     = 0L;
            totalVendasHojeFormatado = "R$ 0,00";
            totalVendasMesFormatado  = "R$ 0,00";
        }
    }

    private void carregarTop5Produtos() {
        try {
            top5Produtos = relatorioService.top5ProdutosMes();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DashboardController] Erro ao carregar top-5 produtos", e);
            top5Produtos = new ArrayList<>();
        }
    }

    private void carregarVendasSemana() {
        try {
            vendasSemana = relatorioService.vendasUltimos7Dias();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DashboardController] Erro ao carregar histórico semanal", e);
            vendasSemana = new ArrayList<>();
        }
    }

    private void carregarAlertasEstoque() {
        try {
            produtosEstoqueBaixo = relatorioService.produtosEstoqueBaixo();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DashboardController] Erro ao carregar alertas de estoque", e);
            produtosEstoqueBaixo = new ArrayList<>();
        }
    }

    // =========================================================
    // Construção dos Chart Models PrimeFaces
    // =========================================================

    /**
     * Constrói o {@link BarChartModel} de faturamento dos últimos 7 dias.
     *
     * <h3>Estrutura do modelo Chart.js:</h3>
     * <pre>
     *   data.labels   = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom"]
     *   dataset.data  = [1200.0, 980.0, 1450.0, ...]   (totalFaturado)
     *   dataset.backgroundColor = cor primária com transparência
     * </pre>
     *
     * <p>O PrimeFaces serializa este model para JSON e o injeta no
     * {@code <p:chart>} que renderiza via Chart.js no browser.</p>
     */
    private void construirGraficoBarras() {
        graficoVendasSemana = new BarChartModel();
        ChartData data = new ChartData();

        BarChartDataSet dataset = new BarChartDataSet();
        dataset.setLabel("Faturamento (R$)");

        List<Number> valores = new ArrayList<>();
        List<String> labels  = new ArrayList<>();
        List<String> cores   = new ArrayList<>();
        List<String> borda   = new ArrayList<>();

        for (VendasPorDiaDTO dto : vendasSemana) {
            labels.add(dto.getDiaSemana());
            valores.add(dto.getTotalFaturado());
            cores.add(COR_PRIMARY);
            borda.add(COR_PRIMARY_BORDER);
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(cores);
        dataset.setBorderColor(borda);
        dataset.setBorderWidth(2);

        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoVendasSemana.setData(data);

        // Opções: desabilita animação de título embutida do PF para usar HTML customizado
        BarChartOptions opts = new BarChartOptions();
        opts.setMaintainAspectRatio(false);
        Legend legend = new Legend();
        legend.setDisplay(false);
        opts.setLegend(legend);
        graficoVendasSemana.setOptions(opts);
        graficoVendasSemana.setExtender("dashboardBarExtender");
    }

    /**
     * Constrói o {@link BarChartModel} (horizontal) de top-5 mais vendidos.
     *
     * <p>O gráfico horizontal (indexAxis: 'y') facilita a leitura dos
     * nomes dos produtos sem sobreposição no eixo X.</p>
     */
    private void construirGraficoTop5() {
        graficoTop5Produtos = new BarChartModel();
        ChartData data = new ChartData();

        BarChartDataSet dataset = new BarChartDataSet();
        dataset.setLabel("Unidades Vendidas");

        List<Number> valores  = new ArrayList<>();
        List<String> labels   = new ArrayList<>();
        List<String> cores    = new ArrayList<>();

        String[] coresPaleta = {COR_PRIMARY, COR_SUCCESS, COR_INFO, COR_WARNING, COR_DANGER};

        for (int i = 0; i < top5Produtos.size(); i++) {
            ProdutoMaisVendidoDTO dto = top5Produtos.get(i);
            labels.add(dto.getNomeAbreviado());
            valores.add(dto.getTotalVendido());
            cores.add(coresPaleta[i % coresPaleta.length]);
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(cores);
        dataset.setBorderWidth(0);

        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoTop5Produtos.setData(data);

        BarChartOptions opts = new BarChartOptions();
        opts.setMaintainAspectRatio(false);
        Legend legend = new Legend();
        legend.setDisplay(false);
        opts.setLegend(legend);
        graficoTop5Produtos.setOptions(opts);
        // Extender JS converte para gráfico horizontal e aplica estilo dark
        graficoTop5Produtos.setExtender("dashboardTop5Extender");
    }

    /**
     * Constrói o {@link PieChartModel} de distribuição de receita por produto.
     *
     * <p>Usa os mesmos dados do top-5, mas exibe como pizza para mostrar
     * a proporção de cada produto na receita total do mês.</p>
     */
    private void construirGraficoPizza() {
        graficoPizza = new PieChartModel();
        ChartData data = new ChartData();

        PieChartDataSet dataset = new PieChartDataSet();

        List<Number> valores = new ArrayList<>();
        List<String> labels  = new ArrayList<>();

        for (ProdutoMaisVendidoDTO dto : top5Produtos) {
            labels.add(dto.getNomeAbreviado());
            valores.add(dto.getReceitaGerada());
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(PALETA_PIZZA);
        dataset.setBorderColor(PALETA_PIZZA_BORDA);
        dataset.setBorderWidth(2);

        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoPizza.setData(data);

        PieChartOptions opts = new PieChartOptions();
        opts.setMaintainAspectRatio(false);
        Legend legend = new Legend();
        legend.setDisplay(true);
        legend.setPosition("bottom");
        opts.setLegend(legend);
        graficoPizza.setOptions(opts);
        graficoPizza.setExtender("dashboardPieExtender");
    }

    // =========================================================
    // Ações JSF — Refresh por AJAX
    // =========================================================

    /**
     * Recarrega todos os dados do Dashboard.
     * Chamado pelo botão "Atualizar" com {@code <p:ajax>}.
     */
    public void atualizar() {
        init();
        LOG.info("[DashboardController] Dashboard atualizado manualmente.");
    }

    // =========================================================
    // Helpers de apresentação
    // =========================================================

    /** @return CSS class de severidade do alerta de estoque. */
    public String classeAlertaEstoque(Produto p) {
        if (p.getQuantidadeEstoque() == null) return "";
        if (p.getQuantidadeEstoque() == 0)    return "row-critico";
        return "row-alerta";
    }

    /** @return true se há ao menos um produto em alerta de estoque. */
    public boolean isTemAlertaEstoque() {
        return contagemEstoqueBaixo != null && contagemEstoqueBaixo > 0;
    }

    // =========================================================
    // Getters — expostos ao EL do Facelets
    // =========================================================

    public BigDecimal getTotalVendasHoje()        { return totalVendasHoje; }
    public Long       getContagemVendasHoje()     { return contagemVendasHoje; }
    public BigDecimal getTotalVendasMes()         { return totalVendasMes; }
    public Long       getContagemEstoqueBaixo()   { return contagemEstoqueBaixo; }
    public String     getTotalVendasHojeFormatado() { return totalVendasHojeFormatado; }
    public String     getTotalVendasMesFormatado()  { return totalVendasMesFormatado; }
    public List<ProdutoMaisVendidoDTO> getTop5Produtos()       { return top5Produtos; }
    public List<Produto>               getProdutosEstoqueBaixo() { return produtosEstoqueBaixo; }
    public List<VendasPorDiaDTO>       getVendasSemana()       { return vendasSemana; }
    public BarChartModel getGraficoVendasSemana()  { return graficoVendasSemana; }
    public BarChartModel getGraficoTop5Produtos()  { return graficoTop5Produtos; }
    public PieChartModel getGraficoPizza()         { return graficoPizza; }
}
