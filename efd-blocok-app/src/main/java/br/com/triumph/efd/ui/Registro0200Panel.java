package br.com.triumph.efd.ui;

import br.com.triumph.efd.io.Registro0200ExportService;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Registro0200Panel extends JPanel {

    private static final int PAGE_SIZE = 500;

    private final Registro0200ExportService exportService = new Registro0200ExportService();
    private final JLabel lblResumo = new JLabel("Nenhum registro 0200 gerado ainda.");
    private final JTextField txtBusca = new JTextField(24);
    private final JButton btnFiltrar = new JButton("Filtrar");
    private final JButton btnLimpar = new JButton("Limpar");
    private final JButton btnAnterior = new JButton("◀ Anterior");
    private final JButton btnProximo = new JButton("Próximo ▶");
    private final JLabel lblPagina = new JLabel("Página 0 / 0");
    private final JButton btnExportarTxt = new JButton("Exportar TXT");
    private final JButton btnExportarCsv = new JButton("Exportar CSV");
    private final JButton btnExportarXlsx = new JButton("Exportar Excel");
    private final JLabel lblExportStatus = new JLabel(" ");

    private final ItensTableModel tableModel = new ItensTableModel();
    private final JTable table = new JTable(tableModel);

    private Registro0200Result result;
    private List<Registro0200Item> todos = List.of();
    private List<Registro0200Item> filtrados = List.of();
    private int paginaAtual = 1;

    public Registro0200Panel() {
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
        export.setBorder(BorderFactory.createTitledBorder("Download"));
        export.add(btnExportarTxt);
        export.add(btnExportarCsv);
        export.add(btnExportarXlsx);
        export.add(lblExportStatus);

        table.setAutoCreateRowSorter(false);
        table.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Registros 0200 gerados a partir dos insumos ignorados"));

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(export, BorderLayout.SOUTH);
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
            if (paginaAtual < totalPaginas()) {
                paginaAtual++;
                atualizarPagina();
            }
        });
        btnExportarTxt.addActionListener(e -> exportar(Registro0200ExportService.Formato.TXT, "registros_0200_gerados.txt", "txt"));
        btnExportarCsv.addActionListener(e -> exportar(Registro0200ExportService.Formato.CSV, "registros_0200_gerados.csv", "csv"));
        btnExportarXlsx.addActionListener(e -> exportar(Registro0200ExportService.Formato.XLSX, "registros_0200_gerados.xlsx", "xlsx"));
    }

    public void carregar(Registro0200Result result) {
        this.result = result;
        todos = result == null ? List.of() : new ArrayList<>(result.getItens());
        filtrados = todos;
        paginaAtual = 1;

        int qtd = todos.size();
        lblResumo.setText(String.format(
                "<html>Registros 0200 gerados: <b>%d</b> &nbsp;|&nbsp; "
                        + "UNID_INV=<b>UNIT</b> &nbsp;|&nbsp; TIPO_ITEM=<b>01</b> (matéria-prima)</html>",
                qtd));

        setEnabled(qtd > 0);
        if (qtd == 0) {
            tableModel.setItens(List.of());
            lblPagina.setText("Nenhum registro");
            return;
        }
        aplicarFiltro();
    }

    public void limpar() {
        result = null;
        todos = List.of();
        filtrados = List.of();
        paginaAtual = 1;
        tableModel.setItens(List.of());
        lblResumo.setText("Nenhum registro 0200 gerado ainda.");
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
        boolean pode = enabled && result != null && !todos.isEmpty();
        btnExportarTxt.setEnabled(pode);
        btnExportarCsv.setEnabled(pode);
        btnExportarXlsx.setEnabled(pode);
        table.setEnabled(enabled);
    }

    private void aplicarFiltro() {
        String busca = txtBusca.getText() == null ? "" : txtBusca.getText().trim().toLowerCase(Locale.ROOT);
        if (busca.isEmpty()) {
            filtrados = todos;
        } else {
            filtrados = todos.stream()
                    .filter(i -> contem(i.getCodItem(), busca)
                            || contem(i.getDescrItem(), busca)
                            || contem(i.getOrigem(), busca))
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
            tableModel.setItens(List.of());
            lblPagina.setText("Nenhum resultado");
            btnAnterior.setEnabled(false);
            btnProximo.setEnabled(false);
            return;
        }
        if (paginaAtual > totalPaginas) {
            paginaAtual = totalPaginas;
        }
        int inicio = (paginaAtual - 1) * PAGE_SIZE;
        int fim = Math.min(inicio + PAGE_SIZE, filtrados.size());
        tableModel.setItens(filtrados.subList(inicio, fim));
        lblPagina.setText(String.format("Página %d / %d  (%d de %d linhas)",
                paginaAtual, totalPaginas, fim - inicio, filtrados.size()));
        btnAnterior.setEnabled(paginaAtual > 1);
        btnProximo.setEnabled(paginaAtual < totalPaginas);
    }

    private int totalPaginas() {
        if (filtrados.isEmpty()) {
            return 0;
        }
        return (filtrados.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private void exportar(Registro0200ExportService.Formato formato, String nomePadrao, String extensao) {
        if (result == null || todos.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Não há registros 0200 para exportar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("c:\\trabalhos\\EFD-blocok"));
        chooser.setSelectedFile(new File(nomePadrao));
        chooser.setFileFilter(new FileNameExtensionFilter(formato.name() + " (*." + extensao + ")", extensao));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getAbsolutePath().toLowerCase(Locale.ROOT).endsWith("." + extensao)) {
            file = new File(file.getAbsolutePath() + "." + extensao);
        }
        File destino = file;

        Window owner = SwingUtilities.getWindowAncestor(this);
        ExportProgressDialog progressDialog = new ExportProgressDialog(owner, "Exportar 0200", "Exportando...");
        setExportando(true);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                exportService.exportar(destino.toPath(), formato, result, this::publish);
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
                setExportando(false);
                try {
                    get();
                    progressDialog.concluir("Arquivo salvo!");
                    Timer timer = new Timer(500, e -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(Registro0200Panel.this,
                                "Arquivo exportado:\n" + destino.getAbsolutePath(),
                                "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
                        lblExportStatus.setText("Exportação concluída.");
                    });
                    timer.setRepeats(false);
                    timer.start();
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(Registro0200Panel.this,
                            cause.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        progressDialog.exibir();
        worker.execute();
    }

    private void setExportando(boolean exportando) {
        boolean pode = !exportando && result != null && !todos.isEmpty();
        btnExportarTxt.setEnabled(pode);
        btnExportarCsv.setEnabled(pode);
        btnExportarXlsx.setEnabled(pode);
    }

    private static class ItensTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "COD_ITEM", "DESCR_ITEM", "UNID", "TIPO", "Origem"};
        private List<Registro0200Item> itens = List.of();

        void setItens(List<Registro0200Item> itens) {
            this.itens = itens == null ? List.of() : itens;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return itens.size();
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
            Registro0200Item i = itens.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> i.getNumero();
                case 1 -> i.getCodItem();
                case 2 -> i.getDescrItem();
                case 3 -> i.getUnidInv();
                case 4 -> i.getTipoItem();
                case 5 -> i.getOrigem();
                default -> "";
            };
        }
    }
}
