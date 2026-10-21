package br.com.triumph.efd.ui;

import javax.swing.JFileChooser;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Lembra a última pasta usada ao escolher um arquivo para importar.
 */
public final class PastaRecente {

    private static final String CHAVE = "ultimaPasta";
    private static final File PADRAO = new File("c:\\trabalhos\\EFD-blocok");

    private PastaRecente() {
    }

    public static File atual() {
        String salva = preferencias().get(CHAVE, "");
        if (!salva.isBlank()) {
            File pasta = new File(salva);
            if (pasta.isDirectory()) {
                return pasta;
            }
        }
        return PADRAO.isDirectory() ? PADRAO : new File(".");
    }

    public static void lembrar(JFileChooser chooser) {
        if (chooser == null) {
            return;
        }
        File selecionado = chooser.getSelectedFile();
        File pasta = selecionado != null ? selecionado.getParentFile() : chooser.getCurrentDirectory();
        if (pasta != null && pasta.isDirectory()) {
            preferencias().put(CHAVE, pasta.getAbsolutePath());
        }
    }

    private static Preferences preferencias() {
        return Preferences.userNodeForPackage(PastaRecente.class);
    }
}
