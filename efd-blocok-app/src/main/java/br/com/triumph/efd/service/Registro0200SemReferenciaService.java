package br.com.triumph.efd.service;

import br.com.triumph.efd.model.Item0200SemReferencia;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.util.SpedLineUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Localiza, no EFD já gerado, os registros 0200 que a última geração não cadastrou
 * e que não aparecem como item em nenhum bloco.
 */
public class Registro0200SemReferenciaService {

    private static final Map<String, int[]> CAMPOS_ITEM = Map.ofEntries(
            Map.entry("0221", new int[]{2}),
            Map.entry("C170", new int[]{3}),
            Map.entry("C197", new int[]{4}),
            Map.entry("C321", new int[]{2}),
            Map.entry("C425", new int[]{2}),
            Map.entry("C510", new int[]{3}),
            Map.entry("C597", new int[]{4}),
            Map.entry("C610", new int[]{3}),
            Map.entry("D197", new int[]{4}),
            Map.entry("E113", new int[]{8}),
            Map.entry("E240", new int[]{8}),
            Map.entry("G140", new int[]{3}),
            Map.entry("H010", new int[]{2}),
            Map.entry("K200", new int[]{3}),
            Map.entry("K220", new int[]{3, 4}),
            Map.entry("K230", new int[]{5}),
            Map.entry("K235", new int[]{3, 5}),
            Map.entry("K250", new int[]{3}),
            Map.entry("K255", new int[]{3, 5}),
            Map.entry("K260", new int[]{3}),
            Map.entry("K265", new int[]{2}),
            Map.entry("K270", new int[]{5}),
            Map.entry("K275", new int[]{2, 5}),
            Map.entry("K280", new int[]{3}),
            Map.entry("K291", new int[]{2}),
            Map.entry("K292", new int[]{2}),
            Map.entry("K301", new int[]{2}),
            Map.entry("K302", new int[]{2})
    );

    public List<Item0200SemReferencia> listarLinhas(List<String> linhas, Registro0200Result gerados) {
        if (linhas == null || linhas.isEmpty()) {
            return List.of();
        }
        Set<String> cadastradosAgora = new HashSet<>();
        if (gerados != null) {
            gerados.getItens().forEach(item -> {
                if (item.getCodItem() != null && !item.getCodItem().isBlank()) {
                    cadastradosAgora.add(item.getCodItem().trim());
                }
            });
        }
        Set<String> referencias = new HashSet<>();
        for (String linha : linhas) {
            coletar(linha, referencias);
        }
        List<Item0200SemReferencia> semReferencia = new ArrayList<>();
        for (String linha : linhas) {
            if (!"0200".equals(SpedLineUtils.registro(linha))) {
                continue;
            }
            String[] campos = linha.split("\\|", -1);
            String cod = campo(campos, 2);
            if (cod.isEmpty() || cadastradosAgora.contains(cod) || referencias.contains(cod)) {
                continue;
            }
            semReferencia.add(new Item0200SemReferencia(
                    cod,
                    campo(campos, 3),
                    campo(campos, 6),
                    campo(campos, 7),
                    campo(campos, 8)));
        }
        return semReferencia;
    }

    private void coletar(String linha, Set<String> referencias) {
        String reg = SpedLineUtils.registro(linha);
        int[] posicoes = CAMPOS_ITEM.get(reg);
        if (posicoes == null) {
            return;
        }
        String[] campos = linha.split("\\|", -1);
        for (int posicao : posicoes) {
            String valor = campo(campos, posicao);
            if (!valor.isEmpty()) {
                referencias.add(valor);
            }
        }
    }

    private static String campo(String[] campos, int indice) {
        if (campos == null || indice < 0 || indice >= campos.length || campos[indice] == null) {
            return "";
        }
        return campos[indice].trim();
    }
}
