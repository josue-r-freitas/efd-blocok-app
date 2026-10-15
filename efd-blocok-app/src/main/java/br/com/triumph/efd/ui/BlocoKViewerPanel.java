package br.com.triumph.efd.ui;

import br.com.triumph.efd.io.BlocoKExportService;
import br.com.triumph.efd.io.BlocoKValidacaoExportService;
import br.com.triumph.efd.model.BlocoKLinha;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.ProdutoAcabado;
import br.com.triumph.efd.util.SpedLineParser;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class BlocoKViewerPanel extends JPanel {

    private static final int PAGE_SIZE = 500;

    private final BlocoKExportService exportService = new BlocoKExportService();
    private final BlocoKValidacaoExportService validacaoExportService = new BlocoKValidacaoExportService();
    private final JLabel lblResumo = new JLabel("Nenhum Bloco K gerado ainda.");
    private final JComboBox<String> cmbRegistro = new JComboBox<>();
    private final JTextField txtBusca = new JTextField(20);
    private final JButton btnFiltrar = new JButton("Filtrar");
    private final JButton btnLimpar = new JButton("Limpar");
    private final JButton btnAnterior = new JButton("◀ Anterior");
    private final JButton btnProximo = new JButton("Próximo ▶");
    private final JLabel lblPagina = new JLabel("Página 0 / 0");
    private final JButton btnExportarTxt = new JButton("Exportar TXT");
    private final JButton btnExportarCsv = new JButton("Exportar CSV");
    private final JButton btnExportarXlsx = new JButton("Exportar Excel");
    private final JButton btnRelatorio = new JButton("Relatório de validação");
    private final JLabel lblExportStatus = new JLabel(" ");

    private final LinhasTableModel tableModel = new LinhasTableModel();
    private final JTable table = new JTable(tableModel);

    private BlocoKResult blocoK;
    private List<ProdutoAcabado> produtos = List.of();
    private List<InsumoConsumo> insumos = List.of();
    private List<BlocoKLinha> todasLinhas = List.of();
    private List<BlocoKLinha> linhasFiltradas = List.of();
    private int paginaAtual = 1;

    public BlocoKViewerPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUi();
        wireEvents();
        setEnabled(false);
    }

    private void buildUi() {
        JPanel north = new JPanel(new BorderLayout(8, 8));

        JPanel resumo = new JPanel(new GridLayout(0, 1, 2, 2));
        resumo.setBorder(BorderFactory.createTitledBorder("Resumo"));
        resumo.add(lblResumo);
        north.add(resumo, BorderLayout.NORTH);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtros.setBorder(BorderFactory.createTitledBorder("Visualização"));
        filtros.add(new JLabel("Registro:"));
        filtros.add(cmbRegistro);
        filtros.add(new JLabel("Buscar:"));
        filtros.add(txtBusca);
        filtros.add(btnFiltrar);
        filtros.add(btnLimpar);
        filtros.add(btnAnterior);
        filtros.add(lblPagina);
        filtros.add(btnProximo);
        north.add(filtros, BorderLayout.SOUTH);

        JPanel export = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        export.setBorder(BorderFactory.createTitledBorder("Download"));
        export.add(btnExportarTxt);
        export.add(btnExportarCsv);
        export.add(btnExportarXlsx);
        export.add(btnRelatorio);
        export.add(lblExportStatus);

        table.setAutoCreateRowSorter(false);
        table.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Linhas geradas"));

        JPanel south = new JPanel(new BorderLayout());
        south.add(export, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        btnFiltrar.addActionListener(e -> aplicarFiltro());
        btnLimpar.addActionListener(e -> {
            txtBusca.setText("");
            cmbRegistro.setSelectedIndex(0);
            aplicarFiltro();
        });
        cmbRegistro.addActionListener(e -> aplicarFiltro());
        btnAnterior.addActionListener(e -> {
            if (paginaAtual > 1) {
                paginaAtual--;
                atualizarPagina();
            }
        });
        btnProximo.addActionListener(e -> {
            int totalPaginas = totalPaginas();
            if (paginaAtual < totalPaginas) {
                paginaAtual++;
                atualizarPagina();
            }
        });
        btnExportarTxt.addActionListener(e -> exportar(BlocoKExportService.Formato.TXT, "blocoK_gerado.txt", "txt"));
        btnExportarCsv.addActionListener(e -> exportar(BlocoKExportService.Formato.CSV, "blocoK_gerado.csv", "csv"));
        btnExportarXlsx.addActionListener(e -> exportar(BlocoKExportService.Formato.XLSX, "blocoK_gerado.xlsx", "xlsx"));
        btnRelatorio.addActionListener(e -> exportarRelatorio());
    }

    public void carregar(BlocoKResult result, List<ProdutoAcabado> produtos, List<InsumoConsumo> insumos) {
        this.produtos = produtos == null ? List.of() : produtos;
        this.insumos = insumos == null ? List.of() : insumos;
        carregar(result);
    }

    public void carregar(BlocoKResult result) {
        this.blocoK = result;
        setEnabled(true);

        lblResumo.setText(String.format(
                "<html>Linhas: <b>%d</b> &nbsp;|&nbsp; K230: <b>%d</b> &nbsp;|&nbsp; K235: <b>%d</b> &nbsp;|&nbsp; "
                        + "K200: <b>%d</b> &nbsp;|&nbsp; Insumos ignorados: <b>%d</b> &nbsp;|&nbsp; Avisos: <b>%d</b></html>",
                result.totalLinhas(),
                result.getQtdK230(),
                result.getQtdK235(),
                result.getQtdK200(),
                result.getInsumosIgnorados(),
                result.getAvisos().size()));

        new SwingWorker<List<BlocoKLinha>, Void>() {
            @Override
            protected List<BlocoKLinha> doInBackground() {
                return SpedLineParser.parseAll(result.getLinhas());
            }

            @Override
            protected void done() {
                try {
                    todasLinhas = get();
                    montarFiltroRegistros();
                    paginaAtual = 1;
                    aplicarFiltro();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BlocoKViewerPanel.this,
                            "Erro ao preparar visualização: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public void limpar() {
        blocoK = null;
        produtos = List.of();
        insumos = List.of();
        todasLinhas = List.of();
        linhasFiltradas = List.of();
        paginaAtual = 1;
        tableModel.setLinhas(List.of());
        lblResumo.setText("Nenhum Bloco K gerado ainda.");
        cmbRegistro.setModel(new DefaultComboBoxModel<>(new String[]{"Todos"}));
        lblPagina.setText("Página 0 / 0");
        setEnabled(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        cmbRegistro.setEnabled(enabled);
        txtBusca.setEnabled(enabled);
        btnFiltrar.setEnabled(enabled);
        btnLimpar.setEnabled(enabled);
        btnAnterior.setEnabled(enabled);
        btnProximo.setEnabled(enabled);
        btnExportarTxt.setEnabled(enabled);
        btnExportarCsv.setEnabled(enabled);
        btnExportarXlsx.setEnabled(enabled);
        btnRelatorio.setEnabled(enabled);
        table.setEnabled(enabled);
    }

    private void montarFiltroRegistros() {
        Set<String> regs = new LinkedHashSet<>();
        regs.add("Todos");
        todasLinhas.stream()
                .map(BlocoKLinha::getRegistro)
                .filter(r -> r != null && !r.isBlank())
                .sorted(Comparator.naturalOrder())
                .forEach(regs::add);
        cmbRegistro.setModel(new DefaultComboBoxModel<>(regs.toArray(new String[0])));
    }

    private void aplicarFiltro() {
        String reg = (String) cmbRegistro.getSelectedItem();
        String busca = txtBusca.getText() == null ? "" : txtBusca.getText().trim().toLowerCase(Locale.ROOT);

        linhasFiltradas = todasLinhas.stream()
                .filter(l -> reg == null || "Todos".equals(reg) || reg.equals(l.getRegistro()))
                .filter(l -> busca.isEmpty()
                        || l.getLinhaOriginal().toLowerCase(Locale.ROOT).contains(busca)
                        || l.resumo().toLowerCase(Locale.ROOT).contains(busca))
                .collect(Collectors.toList());

        paginaAtual = 1;
        atualizarPagina();
    }

    private void atualizarPagina() {
        int totalPaginas = totalPaginas();
        if (totalPaginas == 0) {
            tableModel.setLinhas(List.of());
            lblPagina.setText("Nenhuma linha (0 resultados)");
            btnAnterior.setEnabled(false);
            btnProximo.setEnabled(false);
            return;
        }
        if (paginaAtual > totalPaginas) {
            paginaAtual = totalPaginas;
        }
        int inicio = (paginaAtual - 1) * PAGE_SIZE;
        int fim = Math.min(inicio + PAGE_SIZE, linhasFiltradas.size());
        tableModel.setLinhas(linhasFiltradas.subList(inicio, fim));
        lblPagina.setText(String.format("Página %d / %d  (%d de %d linhas)",
                paginaAtual, totalPaginas, fim - inicio, linhasFiltradas.size()));
        btnAnterior.setEnabled(paginaAtual > 1);
        btnProximo.setEnabled(paginaAtual < totalPaginas);
    }

    private int totalPaginas() {
        if (linhasFiltradas.isEmpty()) {
            return 0;
        }
        return (linhasFiltradas.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private void exportar(BlocoKExportService.Formato formato, String nomePadrao, String extensao) {
        if (blocoK == null) {
            JOptionPane.showMessageDialog(this, "Gere o Bloco K antes de exportar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("c:\\trabalhos\\EFD-blocok"));
        chooser.setSelectedFile(new File(nomePadrao));
        chooser.setFileFilter(new FileNameExtensionFilter(formato.name() + " (*." + extensao + ")", extensao));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String path = file.getAbsolutePath();
        if (!path.toLowerCase(Locale.ROOT).endsWith("." + extensao)) {
            file = new File(path + "." + extensao);
        }
        File destino = file;
        String tituloFormato = switch (formato) {
            case TXT -> "Exportar TXT";
            case CSV -> "Exportar CSV";
            case XLSX -> "Exportar Excel";
        };
        String mensagemInicial = String.format(
                "Preparando exportação de %,d linhas...", blocoK.totalLinhas());

        Window owner = SwingUtilities.getWindowAncestor(this);
        ExportProgressDialog progressDialog = new ExportProgressDialog(owner, tituloFormato, mensagemInicial);
        setExportando(true, formato, "Exportando... aguarde");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(mensagemInicial);
                exportService.exportar(destino.toPath(), formato, blocoK, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                String ultima = chunks.get(chunks.size() - 1);
                progressDialog.atualizar(ultima);
                lblExportStatus.setText(ultima);
            }

            @Override
            protected void done() {
                setExportando(false, null, null);
                try {
                    get();
                    progressDialog.concluir("Arquivo salvo com sucesso!");
                    Timer timer = new Timer(600, e -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(BlocoKViewerPanel.this,
                                "Arquivo exportado com sucesso:\n" + destino.getAbsolutePath(),
                                "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
                        lblExportStatus.setText("Exportação concluída.");
                    });
                    timer.setRepeats(false);
                    timer.start();
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    lblExportStatus.setText("Erro na exportação.");
                    JOptionPane.showMessageDialog(BlocoKViewerPanel.this,
                            cause.getMessage(), "Erro na exportação", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        progressDialog.exibir();
        worker.execute();
    }

    private void exportarRelatorio() {
        if (blocoK == null) {
            JOptionPane.showMessageDialog(this, "Gere o Bloco K antes de exportar o relatório.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("c:\\trabalhos\\EFD-blocok"));
        chooser.setSelectedFile(new File("relatorio_validacao_blocoK.xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String path = file.getAbsolutePath();
        if (!path.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            file = new File(path + ".xlsx");
        }
        File destino = file;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ExportProgressDialog progressDialog = new ExportProgressDialog(owner, "Relatório de validação", "Preparando relatório...");
        setExportando(true, null, "Gerando relatório...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                validacaoExportService.exportar(destino.toPath(), produtos, insumos, blocoK, this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                String ultima = chunks.get(chunks.size() - 1);
                progressDialog.atualizar(ultima);
                lblExportStatus.setText(ultima);
            }

            @Override
            protected void done() {
                setExportando(false, null, null);
                try {
                    get();
                    progressDialog.concluir("Relatório salvo com sucesso!");
                    Timer timer = new Timer(600, e -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(BlocoKViewerPanel.this,
                                "Relatório exportado:\n" + destino.getAbsolutePath(),
                                "Validação", JOptionPane.INFORMATION_MESSAGE);
                        lblExportStatus.setText("Relatório de validação concluído.");
                    });
                    timer.setRepeats(false);
                    timer.start();
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    lblExportStatus.setText("Erro no relatório.");
                    JOptionPane.showMessageDialog(BlocoKViewerPanel.this,
                            cause.getMessage(), "Erro na exportação", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        progressDialog.exibir();
        worker.execute();
    }

    private void setExportando(boolean exportando, BlocoKExportService.Formato formato, String status) {
        btnExportarTxt.setEnabled(!exportando && blocoK != null);
        btnExportarCsv.setEnabled(!exportando && blocoK != null);
        btnExportarXlsx.setEnabled(!exportando && blocoK != null);
        btnRelatorio.setEnabled(!exportando && blocoK != null);
        if (exportando && formato != null) {
            btnExportarTxt.setText(formato == BlocoKExportService.Formato.TXT ? "Exportando..." : "Exportar TXT");
            btnExportarCsv.setText(formato == BlocoKExportService.Formato.CSV ? "Exportando..." : "Exportar CSV");
            btnExportarXlsx.setText(formato == BlocoKExportService.Formato.XLSX ? "Exportando..." : "Exportar Excel");
            lblExportStatus.setText(status == null ? "Exportando..." : status);
        } else {
            btnExportarTxt.setText("Exportar TXT");
            btnExportarCsv.setText("Exportar CSV");
            btnExportarXlsx.setText("Exportar Excel");
        }
    }

    private static class LinhasTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "Registro", "Detalhes"};
        private List<BlocoKLinha> linhas = List.of();

        void setLinhas(List<BlocoKLinha> linhas) {
            this.linhas = linhas == null ? List.of() : linhas;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return linhas.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BlocoKLinha l = linhas.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> l.getNumero();
                case 1 -> l.getRegistro();
                case 2 -> l.resumo();
                default -> "";
            };
        }
    }
}
