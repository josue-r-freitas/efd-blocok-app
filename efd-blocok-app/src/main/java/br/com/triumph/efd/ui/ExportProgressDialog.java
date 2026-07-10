package br.com.triumph.efd.ui;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;

/**
 * Diálogo de feedback durante exportações longas (Excel, CSV, etc.).
 */
public class ExportProgressDialog extends JDialog {

    private final JLabel lblStatus = new JLabel("Iniciando...");
    private final JProgressBar progressBar = new JProgressBar();

    public ExportProgressDialog(Window owner, String titulo, String mensagemInicial) {
        super(owner, "Exportação em andamento", ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel lblTitulo = new JLabel("<html><b>" + titulo + "</b></html>");
        lblStatus.setText(mensagemInicial);

        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString(mensagemInicial);

        content.add(lblTitulo, BorderLayout.NORTH);
        content.add(lblStatus, BorderLayout.CENTER);
        content.add(progressBar, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        setMinimumSize(new Dimension(480, 150));
        setLocationRelativeTo(owner);
        pack();
    }

    public void atualizar(String mensagem) {
        Runnable update = () -> {
            lblStatus.setText(mensagem);
            progressBar.setString(mensagem);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    public void concluir(String mensagemFinal) {
        Runnable finish = () -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            progressBar.setString("Concluído");
            lblStatus.setText(mensagemFinal);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            finish.run();
        } else {
            SwingUtilities.invokeLater(finish);
        }
    }

    public void exibir() {
        setVisible(true);
        toFront();
    }
}
