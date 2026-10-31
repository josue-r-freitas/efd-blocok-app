package br.com.triumph.efd.ui;

import br.com.triumph.efd.model.ProdutoK230Tipo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProdutosK230TipoPanel extends JPanel {

    private static final int PAGE_SIZE = 500;

    private final JLabel lblResumo = new JLabel("Disponível depois de gerar o novo EFD.");
    private final JTextField txtBusca = new JTextField(24);
    private final JButton btnFiltrar = new JButton("Filtrar");
    private final JButton btnLimparBusca = new JButton("Limpar");
    private final JButton btnAnterior = new JButton("◀ Anterior");
    private final JButton btnProximo = new JButton("Próximo ▶");
    private final JLabel lblPagina = new JLabel("Página 0 / 0");
    private final JButton btnAlterar = new JButton("Alterar tipo para 04");
    private final ItensTableModel tableModel = new ItensTableModel();
    private final JTable table = new JTable(tableModel);

    private List<ProdutoK230Tipo> todos = List.of();
    private List<ProdutoK230Tipo> filtrados = List.of();
    private int paginaAtual = 1;
    private Consumer<List<ProdutoK230Tipo>> aoAlterar;

    public ProdutosK230TipoPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel resumo = new JPanel(new GridLayout(0, 1));
        resumo.setBorder(BorderFactory.createTitledBorder("Resumo"));
        resumo.add(lblResumo);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtros.setBorder(BorderFactory.createTitledBorder("Visualização"));
        filtros.add(new JLabel("Buscar:"));
        filtros.add(txtBusca);
        filtros.add(btnFiltrar);
        filtros.add(btnLimparBusca);
        filtros.add(btnAnterior);
        filtros.add(lblPagina);
        filtros.add(btnProximo);

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.add(resumo, BorderLayout.NORTH);
        north.add(filtros, BorderLayout.SOUTH);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        acoes.setBorder(BorderFactory.createTitledBorder("Ações"));
        acoes.add(btnAlterar);

        table.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Produtos do K230 cujo TIPO_ITEM no 0200 não é 03 nem 04"));

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(acoes, BorderLayout.SOUTH);

        btnFiltrar.addActionListener(e -> aplicarFiltro());
        btnLimparBusca.addActionListener(e -> {
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
        btnAlterar.addActionListener(e -> confirmarAlteracao());
        atualizarPagina();
    }

    public void setAoAlterar(Consumer<List<ProdutoK230Tipo>> aoAlterar) {
        this.aoAlterar = aoAlterar;
    }

    public void carregar(List<ProdutoK230Tipo> itens) {
        todos = itens == null ? List.of() : new ArrayList<>(itens);
        txtBusca.setText("");
        paginaAtual = 1;
        lblResumo.setText(String.format(
                "<html>Produtos do K230 com TIPO_ITEM diferente de 03 e de 04: <b>%d</b>."
                        + " A alteração para 04 fica no EFD importado."
                        + " Em seguida use <b>5. Gerar Novo EFD</b>.</html>",
                todos.size()));
        aplicarFiltro();
    }

    public void limpar() {
        carregar(List.of());
        lblResumo.setText("Disponível depois de gerar o novo EFD.");
    }

    private void confirmarAlteracao() {
        if (todos.isEmpty() || aoAlterar == null) {
            return;
        }
        int opcao = JOptionPane.showConfirmDialog(this,
                "Alterar o TIPO_ITEM para 04 em " + todos.size() + " produto(s)?\n\n"
                        + "Depois clique em 5. Gerar Novo EFD para montar o arquivo com essa alteração.",
                "Alterar tipo para 04",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (opcao == JOptionPane.YES_OPTION) {
            aoAlterar.accept(new ArrayList<>(todos));
        }
    }

    private void aplicarFiltro() {
        String busca = txtBusca.getText() == null ? "" : txtBusca.getText().trim().toLowerCase(Locale.ROOT);
        if (busca.isEmpty()) {
            filtrados = todos;
        } else {
            filtrados = todos.stream()
                    .filter(i -> contem(i.getCodItem(), busca)
                            || contem(i.getDescricao(), busca)
                            || contem(i.getTipoItem(), busca))
                    .collect(Collectors.toList());
        }
        paginaAtual = 1;
        atualizarPagina();
    }

    private void atualizarPagina() {
        int paginas = totalPaginas();
        btnAlterar.setEnabled(!todos.isEmpty());
        if (paginas == 0) {
            tableModel.setItens(List.of(), 0);
            lblPagina.setText(todos.isEmpty() ? "Nenhum registro" : "Nenhum resultado");
            btnAnterior.setEnabled(false);
            btnProximo.setEnabled(false);
            return;
        }
        if (paginaAtual > paginas) {
            paginaAtual = paginas;
        }
        int inicio = (paginaAtual - 1) * PAGE_SIZE;
        int fim = Math.min(inicio + PAGE_SIZE, filtrados.size());
        tableModel.setItens(filtrados.subList(inicio, fim), inicio);
        lblPagina.setText(String.format("Página %d / %d  (%d de %d linhas)",
                paginaAtual, paginas, fim - inicio, filtrados.size()));
        btnAnterior.setEnabled(paginaAtual > 1);
        btnProximo.setEnabled(paginaAtual < paginas);
    }

    private int totalPaginas() {
        if (filtrados.isEmpty()) {
            return 0;
        }
        return (filtrados.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private boolean contem(String valor, String busca) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(busca);
    }

    private static class ItensTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "COD_ITEM", "DESCR_ITEM", "UNID", "TIPO_ITEM"};
        private List<ProdutoK230Tipo> itens = List.of();
        private int inicio;

        void setItens(List<ProdutoK230Tipo> itens, int inicio) {
            this.itens = itens == null ? List.of() : itens;
            this.inicio = inicio;
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
            ProdutoK230Tipo item = itens.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> inicio + rowIndex + 1;
                case 1 -> item.getCodItem();
                case 2 -> item.getDescricao();
                case 3 -> item.getUnidInv();
                case 4 -> item.getTipoItem();
                default -> "";
            };
        }
    }
}
