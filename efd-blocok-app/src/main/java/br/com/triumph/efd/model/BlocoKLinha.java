package br.com.triumph.efd.model;

import java.util.List;

public class BlocoKLinha {

    private final int numero;
    private final String registro;
    private final String linhaOriginal;
    private final List<String> campos;

    public BlocoKLinha(int numero, String registro, String linhaOriginal, String[] campos) {
        this.numero = numero;
        this.registro = registro;
        this.linhaOriginal = linhaOriginal;
        this.campos = campos == null ? List.of() : List.of(campos);
    }

    public int getNumero() {
        return numero;
    }

    public String getRegistro() {
        return registro;
    }

    public String getLinhaOriginal() {
        return linhaOriginal;
    }

    public List<String> getCampos() {
        return campos;
    }

    public String getCampo(int index) {
        return index >= 0 && index < campos.size() ? campos.get(index) : "";
    }

    public String resumo() {
        if (campos.isEmpty()) {
            return linhaOriginal;
        }
        return String.join(" | ", campos);
    }

    public String[] toCsvRow() {
        String[] row = new String[campos.size() + 2];
        row[0] = String.valueOf(numero);
        row[1] = registro;
        for (int i = 0; i < campos.size(); i++) {
            row[i + 2] = campos.get(i);
        }
        return row;
    }

    public static String[] cabecalhoCsvMax(int maxCampos) {
        String[] h = new String[maxCampos + 2];
        h[0] = "Linha";
        h[1] = "Registro";
        for (int i = 0; i < maxCampos; i++) {
            h[i + 2] = "Campo" + (i + 1);
        }
        return h;
    }
}
