package br.com.triumph.efd.ui;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.service.EfdBlocoKService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

public class MainApp extends JFrame {

    private final EfdBlocoKService service = new EfdBlocoKService();

    private final JTextField txtEfd = new JTextField();
    private final JTextField txtProdutos = new JTextField();
    private final JTextField txtInsumos = new JTextField();
    private final JCheckBox chkPreservarK200 = new JCheckBox("Preservar estoque K200 do EFD atual", true);
    private final JCheckBox chkIncluirBlocoK = new JCheckBox("Incluir Bloco K no novo EFD", true);
    private final JCheckBox chkIncluir0200 = new JCheckBox("Incluir 0200 gerados no novo EFD", true);
    private final JTextArea log = new JTextArea();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Aguardando arquivos...");
    private final JTabbedPane tabs = new JTabbedPane();
    private final BlocoKViewerPanel viewerPanel = new BlocoKViewerPanel();
    private final InsumosIgnoradosPanel insumosIgnoradosPanel = new InsumosIgnoradosPanel();
    private final Registro0200Panel registro0200Panel = new Registro0200Panel();

    private final JButton btnImportarEfd = new JButton("1. Importar EFD");
    private final JButton btnImportarProdutos = new JButton("2. Produtos Acabados");
    private final JButton btnImportarInsumos = new JButton("3. Insumos");
    private final JButton btnGerarBlocoK = new JButton("4. Gerar Bloco K");
    private final JButton btnGerarNovoEfd = new JButton("5. Gerar Novo EFD");
    private final JButton btnSalvarBlocoK = new JButton("Salvar Bloco K (.txt)");
    private final JButton btnSalvarEfd = new JButton("Salvar Novo EFD");

    public MainApp() {
        super("EFD Bloco K — Triumph Manaus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 760));
        setLocationRelativeTo(null);
        buildUi();
        wireEvents();
        setButtonsState();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Arquivos de entrada"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 0;

        addFileRow(form, gc, 0, "EFD atual (.txt):", txtEfd, btnImportarEfd);
        addFileRow(form, gc, 1, "Produtos acabados (.xlsx):", txtProdutos, btnImportarProdutos);
        addFileRow(form, gc, 2, "Insumos / movimento (.xlsx):", txtInsumos, btnImportarInsumos);

        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 3; gc.weightx = 1;
        form.add(chkPreservarK200, gc);

        JPanel opcoesEfd = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        opcoesEfd.setBorder(BorderFactory.createTitledBorder("Opções do novo EFD"));
        opcoesEfd.add(chkIncluirBlocoK);
        opcoesEfd.add(chkIncluir0200);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setBorder(BorderFactory.createTitledBorder("Processamento"));
        actions.add(btnGerarBlocoK);
        actions.add(btnSalvarBlocoK);
        actions.add(btnGerarNovoEfd);
        actions.add(btnSalvarEfd);

        JPanel mid = new JPanel(new BorderLayout(4, 4));
        mid.add(opcoesEfd, BorderLayout.NORTH);
        mid.add(actions, BorderLayout.SOUTH);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de processamento"));

        tabs.addTab("Log", logScroll);
        tabs.addTab("Bloco K gerado", viewerPanel);
        tabs.addTab("Insumos ignorados", insumosIgnoradosPanel);
        tabs.addTab("0200 gerados", registro0200Panel);

        progress.setStringPainted(true);
        progress.setIndeterminate(false);

        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.add(progress, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.add(form, BorderLayout.CENTER);
        north.add(mid, BorderLayout.SOUTH);

        root.add(north, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void addFileRow(JPanel form, GridBagConstraints gc, int row,
                            String label, JTextField field, JButton button) {
        gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        gc.gridx = 0;
        form.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1;
        field.setEditable(false);
        form.add(field, gc);
        gc.gridx = 2; gc.weightx = 0;
        form.add(button, gc);
    }

    private void wireEvents() {
        insumosIgnoradosPanel.setOnGerar0200(this::gerarRegistros0200);

        chkIncluirBlocoK.addActionListener(e -> setButtonsState());
        chkIncluir0200.addActionListener(e -> setButtonsState());

        btnImportarEfd.addActionListener(e -> escolherArquivo(
                "Selecionar EFD",
                new FileNameExtensionFilter("EFD / Texto (*.txt)", "txt"),
                file -> runAsync("Importando EFD...", () -> {
                    EfdFile efd = service.importarEfd(file.toPath());
                    SwingUtilities.invokeLater(() -> {
                        boolean tinhaPlanilha = !txtProdutos.getText().isBlank() || !txtInsumos.getText().isBlank();
                        txtEfd.setText(file.getAbsolutePath());
                        txtProdutos.setText("");
                        txtInsumos.setText("");
                        viewerPanel.limpar();
                        insumosIgnoradosPanel.limpar();
                        registro0200Panel.limpar();
                        appendLog("EFD importado: " + efd.getNomeEmpresa()
                                + " | CNPJ " + efd.getCnpj()
                                + " | Período " + efd.getDataInicial() + " a " + efd.getDataFinal()
                                + " | Linhas " + efd.size()
                                + " | Itens 0200: " + efd.getCodigos0200().size());
                        if (tinhaPlanilha) {
                            appendLog("Planilhas anteriores descartadas. Importe de novo produtos e insumos; a leitura fica limitada a "
                                    + efd.getDataInicial() + " a " + efd.getDataFinal() + ".");
                        }
                        setButtonsState();
                    });
                })));

        btnImportarProdutos.addActionListener(e -> escolherArquivo(
                "Selecionar planilha de produtos acabados",
                new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx", "xls"),
                file -> runAsync("Importando produtos acabados...", () -> {
                    var produtos = service.importarProdutos(file.toPath());
                    SwingUtilities.invokeLater(() -> {
                        txtProdutos.setText(file.getAbsolutePath());
                        appendLog("Produtos acabados no período: " + produtos.size());
                        setButtonsState();
                    });
                })));

        btnImportarInsumos.addActionListener(e -> escolherArquivo(
                "Selecionar planilha de insumos",
                new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx", "xls"),
                file -> runAsync("Importando insumos (pode demorar)...", () -> {
                    var insumos = service.importarInsumos(file.toPath(), this::appendLogSafe);
                    SwingUtilities.invokeLater(() -> {
                        txtInsumos.setText(file.getAbsolutePath());
                        appendLog("Insumos no período: " + insumos.size());
                        setButtonsState();
                    });
                })));

        btnGerarBlocoK.addActionListener(e -> runAsync("Gerando Bloco K...", () -> {
            BlocoKResult r = service.gerarBlocoK(chkPreservarK200.isSelected(), this::appendLogSafe);
            SwingUtilities.invokeLater(() -> onBlocoKGerado(r, false));
        }));

        btnSalvarBlocoK.addActionListener(e -> {
            File dest = escolherSalvar("Salvar Bloco K", "blocoK_gerado.txt", "txt");
            if (dest == null) return;
            runAsync("Salvando Bloco K...", () -> {
                service.salvarBlocoK(dest.toPath());
                SwingUtilities.invokeLater(() -> appendLog("Bloco K salvo em: " + dest.getAbsolutePath()));
            });
        });

        btnGerarNovoEfd.addActionListener(e -> {
            boolean incluirK = chkIncluirBlocoK.isSelected();
            boolean incluir0200 = chkIncluir0200.isSelected();
            if (!incluirK && !incluir0200) {
                JOptionPane.showMessageDialog(this,
                        "Marque ao menos uma opção:\n• Incluir Bloco K\n• Incluir 0200 gerados",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            runAsync("Gerando novo EFD...", () -> {
                List<String> novo = service.gerarNovoEfd(incluirK, incluir0200, this::appendLogSafe);
                SwingUtilities.invokeLater(() -> {
                    appendLog("=== Novo EFD gerado ===");
                    appendLog("Linhas: " + novo.size());
                    appendLog("Incluiu Bloco K: " + (incluirK ? "sim" : "não"));
                    appendLog("Incluiu 0200 gerados: " + (incluir0200 ? "sim (" + service.getRegistros0200().total() + " itens)" : "não"));
                    setButtonsState();
                });
            });
        });

        btnSalvarEfd.addActionListener(e -> {
            File dest = escolherSalvar("Salvar novo EFD", "SpedEFD_com_BlocoK.txt", "txt");
            if (dest == null) return;
            runAsync("Salvando novo EFD...", () -> {
                service.salvarNovoEfd(dest.toPath());
                SwingUtilities.invokeLater(() -> {
                    appendLog("Novo EFD salvo em: " + dest.getAbsolutePath());
                    JOptionPane.showMessageDialog(this,
                            "Novo EFD gerado com sucesso.\n" + dest.getAbsolutePath(),
                            "Concluído", JOptionPane.INFORMATION_MESSAGE);
                });
            });
        });
    }

    private void gerarRegistros0200() {
        if (service.getBlocoK() == null || service.getBlocoK().getInsumosIgnoradosLista().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Não há insumos ignorados para gerar 0200.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        runAsync("Gerando registros 0200...", () -> {
            Registro0200Result r = service.gerarRegistros0200(this::appendLogSafe);
            SwingUtilities.invokeLater(() -> {
                appendLog("=== Registros 0200 gerados ===");
                appendLog("Códigos únicos: " + r.total());
                appendLog("UNID_INV=UNIT | TIPO_ITEM=01 (matéria-prima)");
                registro0200Panel.carregar(r);
                chkIncluir0200.setSelected(true);
                tabs.setSelectedIndex(3);
                setButtonsState();

                int opcao = JOptionPane.showConfirmDialog(this,
                        "Foram gerados " + r.total() + " registros 0200.\n\n"
                                + "Deseja regenerar o Bloco K agora para incluir estes insumos no K235?",
                        "Regenerar Bloco K?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (opcao == JOptionPane.YES_OPTION) {
                    regenerarBlocoKApos0200();
                } else {
                    appendLog("Dica: regenere o Bloco K (botão 4) para que os novos 0200 entrem no K235.");
                }
            });
        });
    }

    private void regenerarBlocoKApos0200() {
        runAsync("Regenerando Bloco K com novos 0200...", () -> {
            BlocoKResult r = service.gerarBlocoK(chkPreservarK200.isSelected(), this::appendLogSafe);
            SwingUtilities.invokeLater(() -> onBlocoKGerado(r, true));
        });
    }

    private void onBlocoKGerado(BlocoKResult r, boolean apos0200) {
        appendLog(apos0200 ? "=== Bloco K regenerado (com 0200 novos) ===" : "=== Bloco K gerado ===");
        appendLog("Linhas: " + r.totalLinhas());
        appendLog("K230 (itens produzidos): " + r.getQtdK230());
        appendLog("K235 (insumos consumidos): " + r.getQtdK235());
        appendLog("K200 preservados: " + r.getQtdK200());
        appendLog("Insumos ignorados (sem 0200): " + r.getInsumosIgnorados());
        if (r.getInsumosIgnorados() > 0) {
            appendLog("Use a aba 'Insumos ignorados' → botão 'Gerar registros 0200'.");
        }
        if (!r.getAvisos().isEmpty()) {
            appendLog("Avisos (" + r.getAvisos().size() + "):");
            int max = Math.min(30, r.getAvisos().size());
            for (int i = 0; i < max; i++) {
                appendLog("  - " + r.getAvisos().get(i));
            }
            if (r.getAvisos().size() > max) {
                appendLog("  ... +" + (r.getAvisos().size() - max) + " avisos");
            }
        }
        viewerPanel.carregar(r, service.getProdutos(), service.getInsumos());
        insumosIgnoradosPanel.carregar(r);
        chkIncluirBlocoK.setSelected(true);
        if (apos0200) {
            tabs.setSelectedIndex(1);
        } else {
            tabs.setSelectedIndex(r.getInsumosIgnorados() > 0 ? 2 : 1);
        }
        setButtonsState();
    }

    private void setButtonsState() {
        boolean hasEfd = service.getEfdAtual() != null;
        boolean hasProd = service.getProdutos() != null && !service.getProdutos().isEmpty();
        boolean hasIns = service.getInsumos() != null && !service.getInsumos().isEmpty();
        boolean hasK = service.getBlocoK() != null;
        boolean has0200 = service.getRegistros0200() != null && !service.getRegistros0200().isEmpty();
        boolean hasNovo = service.getNovoEfd() != null;

        if (hasK) {
            chkIncluirBlocoK.setEnabled(true);
        } else {
            chkIncluirBlocoK.setEnabled(false);
            chkIncluirBlocoK.setSelected(false);
        }
        if (has0200) {
            chkIncluir0200.setEnabled(true);
        } else {
            chkIncluir0200.setEnabled(false);
            chkIncluir0200.setSelected(false);
        }

        boolean podeGerarEfd = (chkIncluirBlocoK.isSelected() && hasK)
                || (chkIncluir0200.isSelected() && has0200);

        btnImportarProdutos.setEnabled(hasEfd);
        btnImportarInsumos.setEnabled(hasEfd);
        btnGerarBlocoK.setEnabled(hasEfd && hasProd && hasIns);
        btnSalvarBlocoK.setEnabled(hasK);
        btnGerarNovoEfd.setEnabled(podeGerarEfd);
        btnSalvarEfd.setEnabled(hasNovo);
    }

    private void escolherArquivo(String title, FileNameExtensionFilter filter, FileConsumer consumer) {
        JFileChooser chooser = new JFileChooser(new File("c:\\trabalhos\\EFD-blocok"));
        chooser.setDialogTitle(title);
        chooser.setFileFilter(filter);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            consumer.accept(chooser.getSelectedFile());
        }
    }

    private File escolherSalvar(String title, String nomePadrao, String extensao) {
        JFileChooser chooser = new JFileChooser(new File("c:\\trabalhos\\EFD-blocok"));
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(nomePadrao));
        chooser.setFileFilter(new FileNameExtensionFilter(extensao.toUpperCase() + " (*." + extensao + ")", extensao));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void runAsync(String message, ThrowingRunnable task) {
        setBusy(true, message);
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                task.run();
                return null;
            }

            @Override
            protected void done() {
                setBusy(false, "Pronto");
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    appendLog("ERRO: " + cause.getMessage());
                    JOptionPane.showMessageDialog(MainApp.this,
                            cause.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
                setButtonsState();
            }
        };
        worker.execute();
    }

    private void setBusy(boolean busy, String message) {
        progress.setIndeterminate(busy);
        progress.setString(message);
        status.setText(message);
        btnImportarEfd.setEnabled(!busy);
        if (busy) {
            btnImportarProdutos.setEnabled(false);
            btnImportarInsumos.setEnabled(false);
            btnGerarBlocoK.setEnabled(false);
            btnGerarNovoEfd.setEnabled(false);
            btnSalvarBlocoK.setEnabled(false);
            btnSalvarEfd.setEnabled(false);
            chkIncluirBlocoK.setEnabled(false);
            chkIncluir0200.setEnabled(false);
        }
    }

    private void appendLog(String msg) {
        log.append(msg + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void appendLogSafe(String msg) {
        SwingUtilities.invokeLater(() -> appendLog(msg));
    }

    @FunctionalInterface
    private interface FileConsumer {
        void accept(File file);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default L&F
            }
            MainApp app = new MainApp();
            app.setVisible(true);
            app.appendLog("Aplicação iniciada. Importe o EFD e as duas planilhas para gerar o Bloco K.");
            app.appendLog("Fluxo: Gerar Bloco K → Insumos ignorados → Gerar 0200 → (opcional) regenerar K → Gerar Novo EFD.");
        });
    }
}
