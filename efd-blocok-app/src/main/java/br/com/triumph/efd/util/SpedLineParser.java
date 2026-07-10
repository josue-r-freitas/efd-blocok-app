package br.com.triumph.efd.util;

import br.com.triumph.efd.model.BlocoKLinha;

import java.util.ArrayList;
import java.util.List;

public final class SpedLineParser {

    private SpedLineParser() {
    }

    public static BlocoKLinha parse(int numero, String linha) {
        if (linha == null || linha.isBlank()) {
            return new BlocoKLinha(numero, "", "", new String[0]);
        }
        String trimmed = linha.trim();
        String[] parts = trimmed.split("\\|", -1);
        // formato |REG|campo1|...| -> parts[0]="", parts[1]=REG, parts[2+]=campos
        String registro = parts.length > 1 ? parts[1] : "";
        String[] campos;
        if (parts.length <= 2) {
            campos = new String[0];
        } else {
            campos = new String[parts.length - 2];
            System.arraycopy(parts, 2, campos, 0, campos.length);
        }
        return new BlocoKLinha(numero, registro, trimmed, campos);
    }

    public static List<BlocoKLinha> parseAll(List<String> linhas) {
        List<BlocoKLinha> result = new ArrayList<>(linhas.size());
        for (int i = 0; i < linhas.size(); i++) {
            result.add(parse(i + 1, linhas.get(i)));
        }
        return result;
    }

    public static int maxCampos(List<BlocoKLinha> linhas) {
        int max = 0;
        for (BlocoKLinha l : linhas) {
            max = Math.max(max, l.getCampos().size());
        }
        return max;
    }
}
