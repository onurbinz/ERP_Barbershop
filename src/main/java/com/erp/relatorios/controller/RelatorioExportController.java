package com.erp.relatorios.controller;

import com.erp.catalogo.model.Produto;
import com.erp.relatorios.service.RelatorioService;
import com.erp.vendas.model.ItemVenda;
import com.erp.vendas.model.Venda;

import javax.ejb.EJB;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean responsável pela exportação de relatórios em PDF e CSV.
 *
 * <h2>Estratégia de Exportação</h2>
 * <p>Em vez de usar {@code <p:dataExporter>} (que requer a tabela estar visível),
 * optamos por escrever diretamente no {@link HttpServletResponse}. Isso oferece:</p>
 * <ul>
 *   <li>Controle total sobre o conteúdo (cabeçalhos, totalizadores, formatação)</li>
 *   <li>Suporte a relatórios grandes sem limitação de paginação da DataTable</li>
 *   <li>Geração de PDF com iText sem necessidade de componente JSF intermediário</li>
 * </ul>
 *
 * <h2>iText 7 — Geração de PDF</h2>
 * <p>O método {@code exportarPdf()} utiliza a API fluente do iText 7:
 * {@code PdfWriter → PdfDocument → Document → Table}. O PDF é escrito
 * diretamente no {@code OutputStream} do response, sem criar arquivo temporário.</p>
 *
 * <h2>CSV — OpenCSV</h2>
 * <p>O método {@code exportarCsv()} usa {@code CSVWriter} do OpenCSV para garantir
 * escape correto de caracteres especiais (vírgulas em nomes, aspas, etc.).</p>
 *
 * @see RelatorioService
 */
@Named("relatorioExportController")
@RequestScoped
public class RelatorioExportController {

    private static final Logger LOG = Logger.getLogger(RelatorioExportController.class.getName());
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    @EJB
    private RelatorioService relatorioService;

    // Filtros de período (bindados via h:inputText no XHTML)
    private LocalDate dataInicio = LocalDate.now().withDayOfMonth(1);
    private LocalDate dataFim    = LocalDate.now();

    // =========================================================
    // Exportação — Relatório Financeiro em PDF
    // =========================================================

    /**
     * Exporta o Relatório Financeiro do período em PDF via iText 7.
     *
     * <p>Fluxo de resposta HTTP:</p>
     * <ol>
     *   <li>Busca as vendas do período via {@link RelatorioService#relatorioFinanceiroPorPeriodo}</li>
     *   <li>Define os headers HTTP: {@code Content-Type: application/pdf} +
     *       {@code Content-Disposition: attachment; filename=...}</li>
     *   <li>Cria o documento iText e escreve tabela de vendas + totalização</li>
     *   <li>Chama {@code FacesContext.responseComplete()} para interromper o
     *       ciclo de vida JSF após o response ser escrito</li>
     * </ol>
     *
     * <p><strong>NOTA:</strong> Requer iText 7 Core no pom.xml.
     * Se iText não estiver disponível, use Apache PDFBox ou FlyingSaucer.</p>
     */
    public void exportarFinanceiroPdf() {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim    = dataFim.plusDays(1).atStartOfDay();

        List<Venda> vendas = relatorioService.relatorioFinanceiroPorPeriodo(inicio, fim);

        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response =
            (HttpServletResponse) fc.getExternalContext().getResponse();

        String nomeArquivo = "relatorio-financeiro-" +
            LocalDateTime.now().format(FMT_ARQUIVO) + ".pdf";

        response.setContentType("application/pdf");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nomeArquivo + "\"");

        try (OutputStream os = response.getOutputStream()) {
            gerarPdfFinanceiro(os, vendas, inicio, fim);
            os.flush();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[RelatorioExport] Erro ao gerar PDF financeiro", e);
        } finally {
            fc.responseComplete(); // Impede o JSF de renderizar a view após o response
        }
    }

    /**
     * Constrói o PDF do Relatório Financeiro usando iText 7.
     *
     * <p>Estrutura do PDF gerado:</p>
     * <ul>
     *   <li>Cabeçalho: título + período + data de geração</li>
     *   <li>Tabela: ID | Data | Operador | Forma Pgto | Itens | Total</li>
     *   <li>Rodapé: total geral do período</li>
     * </ul>
     *
     * @param os     OutputStream do HttpServletResponse
     * @param vendas lista de vendas com itens já carregados (FETCH JOIN)
     * @param inicio início do período
     * @param fim    fim do período
     */
    private void gerarPdfFinanceiro(OutputStream os, List<Venda> vendas,
                                    LocalDateTime inicio, LocalDateTime fim)
            throws Exception {

        // ─── iText 7 API ─────────────────────────────────────────────────────
        com.itextpdf.kernel.pdf.PdfWriter   writer   = new com.itextpdf.kernel.pdf.PdfWriter(os);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc   = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document        document  = new com.itextpdf.layout.Document(pdfDoc);

        com.itextpdf.kernel.colors.Color corTitulo =
            com.itextpdf.kernel.colors.ColorConstants.DARK_GRAY;
        com.itextpdf.kernel.colors.Color corCabecalho =
            new com.itextpdf.kernel.colors.DeviceRgb(108, 99, 255); // --color-primary

        // Título
        com.itextpdf.layout.element.Paragraph titulo =
            new com.itextpdf.layout.element.Paragraph("ERP Barbershop — Relatório Financeiro")
                .setFontSize(16)
                .setBold()
                .setFontColor(corCabecalho)
                .setMarginBottom(4);
        document.add(titulo);

        String periodoStr = String.format("Período: %s a %s  |  Gerado em: %s",
            inicio.format(FMT_DATA),
            fim.minusDays(1).format(FMT_DATA),
            LocalDateTime.now().format(FMT_DATA));
        document.add(new com.itextpdf.layout.element.Paragraph(periodoStr)
            .setFontSize(9)
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY)
            .setMarginBottom(12));

        // Tabela de vendas (6 colunas)
        float[] largurasColunas = {1f, 2.5f, 2f, 1.8f, 1f, 1.8f};
        com.itextpdf.layout.element.Table tabela =
            new com.itextpdf.layout.element.Table(largurasColunas)
                .useAllAvailableWidth()
                .setMarginBottom(8);

        // Cabeçalho da tabela
        String[] headers = {"ID", "Data / Hora", "Operador", "Pagamento", "Itens", "Total (R$)"};
        for (String h : headers) {
            tabela.addHeaderCell(
                new com.itextpdf.layout.element.Cell()
                    .add(new com.itextpdf.layout.element.Paragraph(h).setBold().setFontSize(9))
                    .setBackgroundColor(corCabecalho)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                    .setPadding(5)
            );
        }

        // Linhas de dados
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (Venda v : vendas) {
            String operador = v.getUsuario() != null ? v.getUsuario().getNome() : "—";
            tabela.addCell(celulaTexto(String.valueOf(v.getId())));
            tabela.addCell(celulaTexto(v.getDataVenda().format(FMT_DATA)));
            tabela.addCell(celulaTexto(operador));
            tabela.addCell(celulaTexto(v.getFormaPagamento().name()));
            tabela.addCell(celulaTexto(String.valueOf(v.getItens().size())));
            tabela.addCell(celulaTexto(String.format("R$ %,.2f", v.getValorTotal())));
            totalGeral = totalGeral.add(v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO);
        }

        document.add(tabela);

        // Totalizador
        String totalStr = String.format("Total do Período: R$ %,.2f  |  %d venda(s)",
            totalGeral, vendas.size());
        document.add(new com.itextpdf.layout.element.Paragraph(totalStr)
            .setBold()
            .setFontSize(11)
            .setFontColor(corCabecalho));

        document.close();
        // ─────────────────────────────────────────────────────────────────────
    }

    /** Cria uma célula de texto padrão para a tabela iText. */
    private com.itextpdf.layout.element.Cell celulaTexto(String texto) {
        return new com.itextpdf.layout.element.Cell()
            .add(new com.itextpdf.layout.element.Paragraph(texto).setFontSize(9))
            .setPadding(4);
    }

    // =========================================================
    // Exportação — Relatório Financeiro em CSV (OpenCSV)
    // =========================================================

    /**
     * Exporta o Relatório Financeiro em CSV via OpenCSV.
     *
     * <p>Cada linha do CSV representa uma venda. Para obter o detalhe
     * por item, use o método {@link #exportarFinanceiroCsvDetalhado()} abaixo.</p>
     *
     * <p>BOM UTF-8 (bytes 0xEF,0xBB,0xBF) é inserido no início do arquivo
     * para que o Excel reconheça o encoding correto ao abrir o CSV.</p>
     */
    public void exportarFinanceiroCsv() {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim    = dataFim.plusDays(1).atStartOfDay();

        List<Venda> vendas = relatorioService.relatorioFinanceiroPorPeriodo(inicio, fim);

        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response =
            (HttpServletResponse) fc.getExternalContext().getResponse();

        String nomeArquivo = "relatorio-financeiro-" +
            LocalDateTime.now().format(FMT_ARQUIVO) + ".csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nomeArquivo + "\"");

        try (OutputStream os = response.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

            // BOM UTF-8 — essencial para o Excel abrir sem quebrar acentos
            os.write(0xEF);
            os.write(0xBB);
            os.write(0xBF);

            // OpenCSV: CSVWriter com separador ponto-e-vírgula (padrão BR)
            com.opencsv.CSVWriter writer = new com.opencsv.CSVWriter(
                osw,
                ';',                                // separador: ponto-e-vírgula
                com.opencsv.CSVWriter.DEFAULT_QUOTE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_LINE_END
            );

            // Linha de cabeçalho
            writer.writeNext(new String[]{
                "ID", "Data/Hora", "Operador", "Forma Pagamento",
                "Qtd Itens", "Valor Total (R$)", "Status"
            });

            // Linhas de dados
            for (Venda v : vendas) {
                String operador = v.getUsuario() != null ? v.getUsuario().getNome() : "";
                writer.writeNext(new String[]{
                    String.valueOf(v.getId()),
                    v.getDataVenda() != null ? v.getDataVenda().format(FMT_DATA) : "",
                    operador,
                    v.getFormaPagamento() != null ? v.getFormaPagamento().name() : "",
                    String.valueOf(v.getItens().size()),
                    v.getValorTotal() != null
                        ? String.format("%.2f", v.getValorTotal()).replace('.', ',') : "0,00",
                    v.getStatus() != null ? v.getStatus().name() : ""
                });
            }
            writer.flush();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[RelatorioExport] Erro ao gerar CSV financeiro", e);
        } finally {
            fc.responseComplete();
        }
    }

    // =========================================================
    // Exportação — Relatório de Estoque Baixo em CSV
    // =========================================================

    /**
     * Exporta o Relatório de Estoque Baixo em CSV.
     *
     * <p>Lista todos os produtos ativos cujo estoque está no limite ou
     * abaixo do mínimo configurado, ordenados do mais crítico ao menos crítico.</p>
     */
    public void exportarEstoqueBaixoCsv() {
        List<Produto> produtos = relatorioService.produtosEstoqueBaixo();

        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response =
            (HttpServletResponse) fc.getExternalContext().getResponse();

        String nomeArquivo = "estoque-baixo-" +
            LocalDateTime.now().format(FMT_ARQUIVO) + ".csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nomeArquivo + "\"");

        try (OutputStream os = response.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

            os.write(0xEF); os.write(0xBB); os.write(0xBF); // BOM UTF-8

            com.opencsv.CSVWriter writer = new com.opencsv.CSVWriter(
                osw, ';',
                com.opencsv.CSVWriter.DEFAULT_QUOTE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_LINE_END
            );

            writer.writeNext(new String[]{
                "ID", "Nome do Produto", "Categoria",
                "Estoque Atual", "Estoque Mínimo",
                "Defasagem", "Preço (R$)"
            });

            for (Produto p : produtos) {
                int atual   = p.getQuantidadeEstoque() != null ? p.getQuantidadeEstoque() : 0;
                int minimo  = p.getQuantidadeMinima()  != null ? p.getQuantidadeMinima()  : 0;
                int deficit = Math.max(0, minimo - atual);
                String categoria = p.getCategoria() != null ? p.getCategoria().getNome() : "";

                writer.writeNext(new String[]{
                    String.valueOf(p.getId()),
                    p.getNome(),
                    categoria,
                    String.valueOf(atual),
                    String.valueOf(minimo),
                    String.valueOf(deficit),
                    p.getPreco() != null
                        ? String.format("%.2f", p.getPreco()).replace('.', ',') : "0,00"
                });
            }
            writer.flush();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[RelatorioExport] Erro ao gerar CSV estoque", e);
        } finally {
            fc.responseComplete();
        }
    }

    // =========================================================
    // Exportação — Estoque Baixo em PDF
    // =========================================================

    /**
     * Exporta o Relatório de Estoque Baixo em PDF via iText 7.
     */
    public void exportarEstoqueBaixoPdf() {
        List<Produto> produtos = relatorioService.produtosEstoqueBaixo();

        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response =
            (HttpServletResponse) fc.getExternalContext().getResponse();

        String nomeArquivo = "estoque-baixo-" +
            LocalDateTime.now().format(FMT_ARQUIVO) + ".pdf";

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nomeArquivo + "\"");

        try (OutputStream os = response.getOutputStream()) {
            gerarPdfEstoque(os, produtos);
            os.flush();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[RelatorioExport] Erro ao gerar PDF estoque", e);
        } finally {
            fc.responseComplete();
        }
    }

    /** Constrói o PDF do Relatório de Estoque Baixo. */
    private void gerarPdfEstoque(OutputStream os, List<Produto> produtos) throws Exception {
        com.itextpdf.kernel.pdf.PdfWriter   writer  = new com.itextpdf.kernel.pdf.PdfWriter(os);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc  = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document        document = new com.itextpdf.layout.Document(pdfDoc);

        com.itextpdf.kernel.colors.Color corAlerta =
            new com.itextpdf.kernel.colors.DeviceRgb(245, 158, 11); // --color-warning

        document.add(new com.itextpdf.layout.element.Paragraph(
            "ERP Barbershop — Relatório de Estoque Baixo")
            .setFontSize(16).setBold()
            .setFontColor(corAlerta).setMarginBottom(4));

        document.add(new com.itextpdf.layout.element.Paragraph(
            "Gerado em: " + LocalDateTime.now().format(FMT_DATA) +
            "  |  Total de produtos em alerta: " + produtos.size())
            .setFontSize(9)
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY)
            .setMarginBottom(12));

        if (produtos.isEmpty()) {
            document.add(new com.itextpdf.layout.element.Paragraph(
                "✓ Nenhum produto com estoque abaixo do mínimo. Estoque em dia!")
                .setFontSize(11)
                .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(34, 197, 94)));
        } else {
            float[] cols = {0.5f, 2.5f, 1.5f, 1f, 1f, 1f, 1.2f};
            com.itextpdf.layout.element.Table tabela =
                new com.itextpdf.layout.element.Table(cols).useAllAvailableWidth();

            for (String h : new String[]{"ID", "Produto", "Categoria",
                    "Atual", "Mínimo", "Déficit", "Preço"}) {
                tabela.addHeaderCell(
                    new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(h)
                            .setBold().setFontSize(9))
                        .setBackgroundColor(corAlerta)
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                        .setPadding(5)
                );
            }

            for (Produto p : produtos) {
                int atual  = p.getQuantidadeEstoque() != null ? p.getQuantidadeEstoque() : 0;
                int minimo = p.getQuantidadeMinima()  != null ? p.getQuantidadeMinima()  : 0;
                String cat = p.getCategoria() != null ? p.getCategoria().getNome() : "—";

                // Colorir linha crítica (estoque = 0) em vermelho sutil
                com.itextpdf.kernel.colors.Color bgLinha = atual == 0
                    ? new com.itextpdf.kernel.colors.DeviceRgb(255, 230, 230)
                    : com.itextpdf.kernel.colors.ColorConstants.WHITE;

                for (String valor : new String[]{
                        String.valueOf(p.getId()), p.getNome(), cat,
                        String.valueOf(atual), String.valueOf(minimo),
                        String.valueOf(Math.max(0, minimo - atual)),
                        String.format("R$ %,.2f", p.getPreco())}) {
                    tabela.addCell(
                        new com.itextpdf.layout.element.Cell()
                            .add(new com.itextpdf.layout.element.Paragraph(valor).setFontSize(9))
                            .setBackgroundColor(bgLinha)
                            .setPadding(4)
                    );
                }
            }
            document.add(tabela);
        }
        document.close();
    }

    // =========================================================
    // Getters e Setters — bindados ao h:inputText do XHTML
    // =========================================================

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
}
