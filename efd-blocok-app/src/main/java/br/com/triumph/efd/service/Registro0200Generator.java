package br.com.triumph.efd.service;

import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoIgnorado;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gera registros 0200 a partir dos insumos ignorados (sem correspondente no EFD).
 */
public class Registro0200Generator {

    private static final String UNID_PADRAO = "UNIT";
    private static final String TIPO_MATERIA_PRIMA = "01";

    public Registro0200Result gerar(List<InsumoIgnorado> ignorados,
                                    EfdFile efd,
                                    Consumer<String> progress) {
        if (ignorados == null || ignorados.isEmpty()) {
            throw new IllegalStateException("Não há insumos ignorados para gerar registros 0200.");
        }

        if (progress != null) {
            progress.accept("Gerando registros 0200 a partir de " + ignorados.size() + " insumos ignorados...");
        }

        // Deduplica por PartNumber (mantém primeira ocorrência com descrição)
        Map<String, InsumoIgnorado> unicos = new LinkedHashMap<>();
        for (InsumoIgnorado i : ignorados) {
            String part = normalizarCodigo(i.getPartNumber());
            if (part == null) {
                continue;
            }
            // Já existe no EFD? (não deveria, mas evita duplicar)
            if (efd != null && efd.resolveCodigo0200(part) != null) {
                continue;
            }
            unicos.putIfAbsent(part, i);
        }

        Registro0200Result result = new Registro0200Result();
        for (Map.Entry<String, InsumoIgnorado> e : unicos.entrySet()) {
            InsumoIgnorado src = e.getValue();
            Registro0200Item item = new Registro0200Item();
            item.setCodItem(e.getKey());
            item.setDescrItem(descricaoSegura(src.getDescricaoInsumo(), e.getKey()));
            item.setUnidInv(UNID_PADRAO);
            item.setTipoItem(TIPO_MATERIA_PRIMA);
            item.setAliqIcms("0");
            item.setOrigem(origem(src));
            result.addItem(item);
        }

        if (progress != null) {
            progress.accept("Registros 0200 gerados: " + result.total()
                    + " códigos únicos (de " + ignorados.size() + " ocorrências ignoradas).");
        }
        return result;
    }

    private String descricaoSegura(String descricao, String fallback) {
        if (descricao == null || descricao.isBlank()) {
            return "INSUMO " + fallback;
        }
        return descricao.trim().toUpperCase(Locale.ROOT);
    }

    private String origem(InsumoIgnorado i) {
        StringBuilder sb = new StringBuilder();
        if (i.getCodDocOp() != null && !i.getCodDocOp().isBlank()) {
            sb.append("OP=").append(i.getCodDocOp());
        }
        if (i.getPartNumber() != null) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("PN=").append(i.getPartNumber());
        }
        return sb.toString();
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return codigo.trim();
    }
}
