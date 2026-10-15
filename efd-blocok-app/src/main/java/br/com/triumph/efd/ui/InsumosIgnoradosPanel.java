package br.com.triumph.efd.ui;

import br.com.triumph.efd.io.InsumosIgnoradosExportService;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.InsumoIgnorado;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class InsumosIgnoradosPanel extends JPanel {

    private static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InsumosIgnoradosExportService exportService = new InsumosIgnoradosExportService();
    private final JLabel lblResumo = new JLabel("Nenhum insumo ignorado registrado.");
    private final JTextField txtBusca = new JTextField(24);
    private final JButton btnFiltrar = new JButton("Filtrar");
    private final JButton btnLimpar = new JButton("Limpar");
    private final JButton btnAnterior = new JButton("◀ Anterior");
    private final JButton btnProximo = new JButton("Próximo ▶");
    private final JLabel lblPagina = new JLabel("Página 0 / 0");
    private final JButton btnExportarTxt = new JButton("Exportar TXT");
    private final JButton btnExportarCsv = new JButton("Exportar CSV");
    private final JButton btnExportarXlsx = new JButton("Exportar Excel");
    private final JButton btnGerar0200 = new JButton("Gerar registros 0200");
    private final JLabel lblExportStatus = new JLabel(" ");

    private final InsumosTableModel tableModel = new InsumosTableModel();
    private final JTable table = new JTable(tableModel);

    private BlocoKResult blocoK;
    private List<InsumoIgnorado> todosInsumos = List.of();
    private List<InsumoIgnorado> insumosFiltrados = List.of();
    private int paginaAtual = 1;
    private Runnable onGerar0200;

    public InsumosIgnoradosPanel() {
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
        filtros.add(new JLabel("Buscar:"));
        filtros.add(txtBusca);
        filtros.add(btnFiltrar);
        filtros.add(btnLimpar);
        filtros.add(btnAnterior);
        filtros.add(lblPagina);
        filtros.add(btnProximo);
        north.add(filtros, BorderLayout.SOUTH);

        JPanel export = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        export.setBorder(BorderFactory.createTitledBorder("Ações"));
        export.add(btnGerar0200);
        export.add(btnExportarTxt);
        export.add(btnExportarCsv);
        export.add(btnExportarXlsx);
        export.add(lblExportStatus);

        table.setAutoCreateRowSorter(false);
        table.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Insumos sem correspondente no registro 0200"));

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
            aplicarFiltro();
        });
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
        btnExportarTxt.addActionListener(e -> exportar(InsumosIgnoradosExportService.Formato.TXT, "insumos_ignorados.txt", "txt"));
        btnExportarCsv.addActionListener(e -> exportar(InsumosIgnoradosExportService.Formato.CSV, "insumos_ignorados.csv", "csv"));
        btnExportarXlsx.addActionListener(e -> exportar(InsumosIgnoradosExportService.Formato.XLSX, "insumos_ignorados.xlsx", "xlsx"));
        btnGerar0200.addActionListener(e -> {
            if (onGerar0200 != null) {
                onGerar0200.run();
            }
        });
    }

    public void setOnGerar0200(Runnable onGerar0200) {
        this.onGerar0200 = onGerar0200;
    }

    public void carregar(BlocoKResult result) {
        this.blocoK = result;
        todosInsumos = new ArrayList<>(result.getInsumosIgnoradosLista());
        insumosFiltrados = todosInsumos;
        paginaAtual = 1;

        int qtd = todosInsumos.size();
        lblResumo.setText(String.format(
                "<html>Insumos ignorados (sem 0200): <b>%d</b> &nbsp;|&nbsp; "
                        + "PartNumber ausente no 0200. O K235 foi gerado com o código da planilha.</html>",
                qtd));

        setEnabled(qtd > 0);
        if (qtd == 0) {
            tableModel.setInsumos(List.of());
            lblPagina.setText("Nenhum insumo ignorado");
            btnExportarTxt.setEnabled(false);
            btnExportarCsv.setEnabled(false);
            btnExportarXlsx.setEnabled(false);
            btnGerar0200.setEnabled(false);
            return;
        }
        aplicarFiltro();
    }

    public void limpar() {
        blocoK = null;
        todosInsumos = List.of();
        insumosFiltrados = List.of();
        paginaAtual = 1;
        tableModel.setInsumos(List.of());
        lblResumo.setText("Nenhum insumo ignorado registrado.");
        lblPagina.setText("Página 0 / 0");
        lblExportStatus.setText(" ");
        setEnabled(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        txtBusca.setEnabled(enabled);
        btnFiltrar.setEnabled(enabled);
        btnLimpar.setEnabled(enabled);
        btnAnterior.setEnabled(enabled);
        btnProximo.setEnabled(enabled);
        btnExportarTxt.setEnabled(enabled && blocoK != null && !todosInsumos.isEmpty());
        btnExportarCsv.setEnabled(enabled && blocoK != null && !todosInsumos.isEmpty());
        btnExportarXlsx.setEnabled(enabled && blocoK != null && !todosInsumos.isEmpty());
        btnGerar0200.setEnabled(enabled && blocoK != null && !todosInsumos.isEmpty());
        table.setEnabled(enabled);
    }

    private void aplicarFiltro() {
        String busca = txtBusca.getText() == null ? "" : txtBusca.getText().trim().toLowerCase(Locale.ROOT);
        if (busca.isEmpty()) {
            insumosFiltrados = todosInsumos;
        } else {
            insumosFiltrados = todosInsumos.stream()
                    .filter(i -> contem(i.getCodDocOp(), busca)
                            || contem(i.getCodItemProduto(), busca)
                            || contem(i.getDescricaoProduto(), busca)
                            || contem(i.getPartNumber(), busca)
                            || contem(i.getDescricaoInsumo(), busca)
                            || contem(i.getMotivo(), busca))
                    .collect(Collectors.toList());
        }
        paginaAtual = 1;
        atualizarPagina();
    }

    private boolean contem(String valor, String busca) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(busca);
    }

    private void atualizarPagina() {
        int totalPaginas = totalPaginas();
        if (totalPaginas == 0) {
            tableModel.setInsumos(List.of());
            lblPagina.setText("Nenhum resultado");
            btnAnterior.setEnabled(false);
            btnProximo.setEnabled(false);
            return;
        }
        if (paginaAtual > totalPaginas) {
            paginaAtual = totalPaginas;
        }
        int inicio = (paginaAtual - 1) * PAGE_SIZE;
        int fim = Math.min(inicio + PAGE_SIZE, insumosFiltrados.size());
        tableModel.setInsumos(insumosFiltrados.subList(inicio, fim));
        lblPagina.setText(String.format("Página %d / %d  (%d de %d linhas)",
                paginaAtual, totalPaginas, fim - inicio, insumosFiltrados.size()));
        btnAnterior.setEnabled(paginaAtual > 1);
        btnProximo.setEnabled(paginaAtual < totalPaginas);
    }

    private int totalPaginas() {
        if (insumosFiltrados.isEmpty()) {
            return 0;
        }
        return (insumosFiltrados.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private void exportar(InsumosIgnoradosExportService.Formato formato, String nomePadrao, String extensao) {
        if (blocoK == null || todosInsumos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Não há insumos ignorados para exportar.", "Aviso", JOptionPane.WARNING_MESSAGE);
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
            case TXT -> "Exportar insumos ignorados (TXT)";
            case CSV -> "Exportar insumos ignorados (CSV)";
            case XLSX -> "Exportar insumos ignorados (Excel)";
        };
        String mensagemInicial = String.format(
                "Preparando exportação de %,d insumos ignorados...", todosInsumos.size());

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
                        JOptionPane.showMessageDialog(InsumosIgnoradosPanel.this,
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
                    JOptionPane.showMessageDialog(InsumosIgnoradosPanel.this,
                            cause.getMessage(), "Erro na exportação", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        progressDialog.exibir();
        worker.execute();
    }

    private void setExportando(boolean exportando, InsumosIgnoradosExportService.Formato formato, String status) {
        boolean podeExportar = !exportando && blocoK != null && !todosInsumos.isEmpty();
        btnExportarTxt.setEnabled(podeExportar);
        btnExportarCsv.setEnabled(podeExportar);
        btnExportarXlsx.setEnabled(podeExportar);
        btnGerar0200.setEnabled(podeExportar);
        if (exportando && formato != null) {
            btnExportarTxt.setText(formato == InsumosIgnoradosExportService.Formato.TXT ? "Exportando..." : "Exportar TXT");
            btnExportarCsv.setText(formato == InsumosIgnoradosExportService.Formato.CSV ? "Exportando..." : "Exportar CSV");
            btnExportarXlsx.setText(formato == InsumosIgnoradosExportService.Formato.XLSX ? "Exportando..." : "Exportar Excel");
            lblExportStatus.setText(status == null ? "Exportando..." : status);
        } else {
            btnExportarTxt.setText("Exportar TXT");
            btnExportarCsv.setText("Exportar CSV");
            btnExportarXlsx.setText("Exportar Excel");
        }
    }

    private static class InsumosTableModel extends AbstractTableModel {
        private final String[] cols = {
                "#", "Ordem", "PartNumber", "Descrição insumo", "Quantidade", "Data", "Produto", "Motivo"
        };
        private List<InsumoIgnorado> insumos = List.of();

        void setInsumos(List<InsumoIgnorado> insumos) {
            this.insumos = insumos == null ? List.of() : insumos;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return insumos.size();
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
            InsumoIgnorado i = insumos.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> i.getNumero();
                case 1 -> i.getCodDocOp();
                case 2 -> i.getPartNumber();
                case 3 -> i.getDescricaoInsumo();
                case 4 -> i.getQuantidade();
                case 5 -> i.getDataConsumo() == null ? "" : i.getDataConsumo().format(DATA_BR);
                case 6 -> i.getCodItemProduto();
                case 7 -> i.getMotivo();
                default -> "";
            };
        }
    }
}
