// =============================================================================
// ERP Barbershop — Dashboard Chart Extenders (dashboard-charts.js)
//
// Os "extenders" são funções JavaScript chamadas pelo PrimeFaces APÓS
// o Chart.js ser inicializado, mas ANTES da renderização final.
// Eles recebem o objeto de configuração do Chart.js e podem modificá-lo
// livremente — ideal para aplicar opções avançadas não expostas no model Java.
//
// Referência PrimeFaces: https://primefaces.github.io/primefaces/12_0_0/#/components/chart
// =============================================================================

/**
 * Extender do gráfico de Barras — Faturamento Semanal.
 * Aplica tema dark, tooltips em R$ e escala Y formatada.
 *
 * "this" dentro do extender = instância do componente PrimeFaces Chart
 * this.cfg.config = objeto de configuração Chart.js (mutável)
 */
function dashboardBarExtender() {
    const config = this.cfg.config;

    // Fonte global em Inter para consistência com o CSS
    config.options = config.options || {};
    config.options.plugins = config.options.plugins || {};
    config.options.scales  = config.options.scales  || {};

    // Tooltips customizados — formata valores como R$
    config.options.plugins.tooltip = {
        backgroundColor: 'rgba(26, 29, 39, 0.95)',
        borderColor: 'rgba(108, 99, 255, 0.4)',
        borderWidth: 1,
        titleColor: '#e2e8f0',
        bodyColor: '#94a3b8',
        titleFont: { family: 'Inter', weight: '600', size: 13 },
        bodyFont:  { family: 'Inter', size: 12 },
        padding: 12,
        cornerRadius: 8,
        callbacks: {
            label: function(ctx) {
                const val = ctx.raw || 0;
                return ' R$ ' + val.toLocaleString('pt-BR', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
            }
        }
    };

    // Eixo X — labels dos dias
    config.options.scales.x = {
        grid: { color: 'rgba(46, 50, 80, 0.6)', drawBorder: false },
        ticks: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 11 }
        }
    };

    // Eixo Y — valores em R$
    config.options.scales.y = {
        grid: { color: 'rgba(46, 50, 80, 0.6)', drawBorder: false },
        ticks: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 11 },
            callback: function(value) {
                return 'R$ ' + value.toLocaleString('pt-BR', {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 0
                });
            }
        },
        beginAtZero: true
    };

    // Arredondamento das barras
    config.options.datasets = { bar: { borderRadius: 6 } };
    config.options.animation = {
        duration: 700,
        easing: 'easeOutQuart'
    };
}

/**
 * Extender do gráfico Top-5 Produtos Mais Vendidos.
 * Converte para gráfico HORIZONTAL (indexAxis: 'y') e formata eixo X
 * como número de unidades (sem casas decimais).
 */
function dashboardTop5Extender() {
    const config = this.cfg.config;

    config.options = config.options || {};
    config.options.indexAxis = 'y'; // ← Gráfico de barras HORIZONTAL

    config.options.plugins = config.options.plugins || {};
    config.options.plugins.tooltip = {
        backgroundColor: 'rgba(26, 29, 39, 0.95)',
        borderColor: 'rgba(108, 99, 255, 0.4)',
        borderWidth: 1,
        titleColor: '#e2e8f0',
        bodyColor: '#94a3b8',
        padding: 10,
        cornerRadius: 8,
        callbacks: {
            label: function(ctx) {
                return ' ' + (ctx.raw || 0) + ' unidades vendidas';
            }
        }
    };

    config.options.scales = {
        x: {
            grid: { color: 'rgba(46,50,80,0.5)', drawBorder: false },
            ticks: {
                color: '#94a3b8',
                font: { family: 'Inter', size: 11 },
                precision: 0
            },
            beginAtZero: true
        },
        y: {
            grid: { display: false },
            ticks: {
                color: '#e2e8f0',
                font: { family: 'Inter', size: 11, weight: '500' }
            }
        }
    };

    config.options.datasets = { bar: { borderRadius: 4 } };
    config.options.animation = { duration: 800, easing: 'easeOutBack' };
}

/**
 * Extender do gráfico de Pizza — Receita por Produto.
 * Aplica tema dark para fundo do canvas e tooltips com R$.
 */
function dashboardPieExtender() {
    const config = this.cfg.config;

    config.options = config.options || {};
    config.options.plugins = config.options.plugins || {};

    config.options.plugins.tooltip = {
        backgroundColor: 'rgba(26, 29, 39, 0.95)',
        borderColor: 'rgba(108, 99, 255, 0.3)',
        borderWidth: 1,
        titleColor: '#e2e8f0',
        bodyColor: '#94a3b8',
        padding: 12,
        cornerRadius: 8,
        callbacks: {
            label: function(ctx) {
                const total = ctx.chart.data.datasets[0].data.reduce((a, b) => a + b, 0);
                const pct   = total > 0 ? ((ctx.raw / total) * 100).toFixed(1) : 0;
                const val   = 'R$ ' + (ctx.raw || 0).toLocaleString('pt-BR', {
                    minimumFractionDigits: 2
                });
                return ' ' + ctx.label + ': ' + val + ' (' + pct + '%)';
            }
        }
    };

    config.options.plugins.legend = {
        position: 'bottom',
        labels: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 11 },
            padding: 16,
            usePointStyle: true,
            pointStyleWidth: 10
        }
    };

    config.options.animation = { animateRotate: true, duration: 900 };

    // Espaçamento entre fatias
    if (config.data && config.data.datasets && config.data.datasets[0]) {
        config.data.datasets[0].hoverOffset = 8;
    }
}

// =============================================================================
// Utilitário: calcula largura da barra de ranking (Top-5 tabela)
// Chamado pelo XHTML via onclick/onload para animar as barras inline.
// =============================================================================
function initRankBars() {
    const bars = document.querySelectorAll('.rank-bar-fill');
    bars.forEach(bar => {
        const target = bar.getAttribute('data-pct') || '0';
        // Pequeno delay para a animação CSS ser visível após o DOM carregar
        setTimeout(() => { bar.style.width = target + '%'; }, 100);
    });
}

// Inicia as barras quando o DOM estiver pronto
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initRankBars);
} else {
    initRankBars();
}
