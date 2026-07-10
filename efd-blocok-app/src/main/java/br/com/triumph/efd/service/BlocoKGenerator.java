package br.com.triumph.efd.service;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.OrdemProducao;
import br.com.triumph.efd.model.ProdutoAcabado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class BlocoKGenerator {

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    public BlocoKResult gerar(EfdFile efd,
                              List<ProdutoAcabado> produtos,
                              List<InsumoConsumo> insumos,
                              boolean preservarK200Existente,
                              Consumer<String> progress) {

        BlocoKResult result = new BlocoKResult();
        LocalDate dtIni = efd.getDataInicial();
        LocalDate dtFin = efd.getDataFinal();
        if (dtIni == null || dtFin == null) {
            throw new IllegalStateException("EFD sem período (registro 0000).");
        }

        if (progress != null) {
            progress.accept("Montando ordens de produção...");
        }

        Map<String, OrdemProducao> ordens = montarOrdens(efd, produtos, insumos, dtIni, dtFin, result, progress);

        List<String> linhas = result.getLinhas();
        linhas.add("|K001|0|");
        linhas.add("|K010|0|"); // leiaute completo
        linhas.add("|K100|" + formatDate(dtIni) + "|" + formatDate(dtFin) + "|");

        List<String> k200 = new ArrayList<>();
        if (preservarK200Existente) {
            for (String line : efd.getLines()) {
                if (line.startsWith("|K200|")) {
                    k200.add(line);
                }
            }
        }
        result.setQtdK200(k200.size());
        linhas.addAll(k200);

        int k230 = 0;
        int k235 = 0;
        List<OrdemProducao> ordenadas = new ArrayList<>(ordens.values());
        ordenadas.sort(Comparator
                .comparing(OrdemProducao::getDtIniOp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OrdemProducao::getCodDocOp, Comparator.nullsLast(String::compareTo)));

        for (OrdemProducao op : ordenadas) {
            if (op.getCodItem() == null || op.getCodItem().isBlank()) {
                result.addAviso("OP " + op.getCodDocOp() + " sem código de item no 0200 — ignorada.");
                continue;
            }
            linhas.add(buildK230(op));
            k230++;
            for (OrdemProducao.ConsumoInsumo c : op.getInsumos().values()) {
                String codInsumo = efd.resolveCodigo0200(c.getPartNumber());
                if (codInsumo == null) {
                    result.setInsumosIgnorados(result.getInsumosIgnorados() + 1);
                    continue;
                }
                LocalDate dtSaida = c.getDtSaida();
                if (dtSaida == null) {
                    dtSaida = op.getDtFinOp() != null ? op.getDtFinOp() : dtFin;
                }
                if (dtSaida.isBefore(dtIni)) {
                    dtSaida = dtIni;
                }
                if (dtSaida.isAfter(dtFin)) {
                    dtSaida = dtFin;
                }
                linhas.add(buildK235(dtSaida, codInsumo, c.getQuantidade()));
                k235++;
            }
        }

        // K990: quantidade de linhas do bloco K, inclusive este registro
        linhas.add("|K990|" + (linhas.size() + 1) + "|");

        result.setQtdK230(k230);
        result.setQtdK235(k235);
        result.setProdutosProcessados(produtos.size());
        result.setInsumosProcessados(insumos.size());

        if (progress != null) {
            progress.accept(String.format(
                    "Bloco K gerado: K230=%d, K235=%d, K200=%d, avisos=%d",
                    k230, k235, k200.size(), result.getAvisos().size()));
        }
        return result;
    }

    private Map<String, OrdemProducao> montarOrdens(EfdFile efd,
                                                    List<ProdutoAcabado> produtos,
                                                    List<InsumoConsumo> insumos,
                                                    LocalDate dtIni,
                                                    LocalDate dtFin,
                                                    BlocoKResult result,
                                                    Consumer<String> progress) {

        // Index insumos by Load and by VIN
        Map<String, List<InsumoConsumo>> porLoad = new LinkedHashMap<>();
        Map<String, List<InsumoConsumo>> porVin = new LinkedHashMap<>();
        for (InsumoConsumo i : insumos) {
            if (i.getLoad() != null && !i.getLoad().isBlank()) {
                porLoad.computeIfAbsent(i.getLoad().trim(), k -> new ArrayList<>()).add(i);
            }
            if (i.getVin() != null && !i.getVin().isBlank()) {
                porVin.computeIfAbsent(i.getVin().trim(), k -> new ArrayList<>()).add(i);
            }
        }

        // Group finished products by Load (preferred) or by ModelCode+Date
        Map<String, List<ProdutoAcabado>> grupos = new LinkedHashMap<>();
        for (ProdutoAcabado p : produtos) {
            LocalDate d = p.getData();
            if (d != null && (d.isBefore(dtIni) || d.isAfter(dtFin))) {
                // keep but warn — still useful if period mismatch
                result.addAviso("Produto VIN " + p.getVin() + " com data " + d + " fora do período EFD "
                        + dtIni + " a " + dtFin + ".");
            }
            String key;
            if (p.getLoad() != null && !p.getLoad().isBlank()) {
                key = "L:" + p.getLoad().trim();
            } else if (p.getVin() != null && !p.getVin().isBlank()) {
                key = "V:" + p.getVin().trim();
            } else {
                key = "M:" + p.codigoItem() + ":" + (d == null ? "NODATE" : d);
            }
            grupos.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        Map<String, OrdemProducao> ordens = new TreeMap<>();
        int seq = 1;
        for (Map.Entry<String, List<ProdutoAcabado>> e : grupos.entrySet()) {
            List<ProdutoAcabado> lista = e.getValue();
            ProdutoAcabado primeiro = lista.get(0);

            String codItemRaw = primeiro.codigoItem();
            String codItem = efd.resolveCodigo0200(codItemRaw);
            if (codItem == null) {
                codItem = resolverPorDescricao(efd, primeiro.getModelDescription());
            }
            if (codItem == null && primeiro.getVin() != null) {
                // try VIN as item code (some EFDs register per-unit)
                codItem = efd.resolveCodigo0200(primeiro.getVin());
            }
            if (codItem == null) {
                result.addAviso("Código de produto não encontrado no 0200: "
                        + codItemRaw + " / VIN " + primeiro.getVin()
                        + " (" + primeiro.getModelDescription() + "). OP será gerada com o código da planilha.");
                codItem = codItemRaw;
            }

            OrdemProducao op = new OrdemProducao();
            String codOp = primeiro.getLoad();
            if (codOp == null || codOp.isBlank()) {
                codOp = "OP" + String.format("%06d", seq++);
            }
            // COD_DOC_OP max 30
            if (codOp.length() > 30) {
                codOp = codOp.substring(0, 30);
            }
            op.setCodDocOp(codOp);
            op.setCodItem(codItem);
            op.setDescricaoItem(primeiro.getModelDescription());

            LocalDate min = null;
            LocalDate max = null;
            for (ProdutoAcabado p : lista) {
                op.addVin(p.getVin());
                LocalDate d = p.getData();
                if (d != null) {
                    if (min == null || d.isBefore(min)) min = d;
                    if (max == null || d.isAfter(max)) max = d;
                }
            }
            if (min == null) min = dtIni;
            if (max == null) max = dtFin;
            // clamp to period for DT_FIN_OP when concluded in period
            LocalDate dtIniOp = min.isAfter(dtFin) ? dtIni : min;
            LocalDate dtFinOp = max;
            if (dtFinOp.isBefore(dtIni)) {
                dtFinOp = dtFin;
            }
            if (dtFinOp.isAfter(dtFin)) {
                // OP not finished in period -> leave blank? For motorcycle assembly usually finished same day.
                // Keep within period for practical generation.
                dtFinOp = dtFin;
            }
            op.setDtIniOp(dtIniOp);
            op.setDtFinOp(dtFinOp);
            op.setQtdEnc(BigDecimal.valueOf(lista.size()));

            // Attach insumos
            List<InsumoConsumo> relacionados = new ArrayList<>();
            if (primeiro.getLoad() != null && porLoad.containsKey(primeiro.getLoad().trim())) {
                relacionados.addAll(porLoad.get(primeiro.getLoad().trim()));
            } else {
                for (ProdutoAcabado p : lista) {
                    if (p.getVin() != null && porVin.containsKey(p.getVin().trim())) {
                        relacionados.addAll(porVin.get(p.getVin().trim()));
                    }
                }
            }

            for (InsumoConsumo i : relacionados) {
                LocalDate dtSaida = i.dataConsumo();
                if (dtSaida == null) {
                    dtSaida = dtFinOp;
                }
                op.addConsumo(i.getPartNumber(), i.getDescription(), i.getQuantity(), dtSaida);
            }

            // Avoid duplicate OP codes
            String mapKey = op.getCodDocOp() + "|" + op.getCodItem();
            if (ordens.containsKey(mapKey)) {
                OrdemProducao existente = ordens.get(mapKey);
                existente.setQtdEnc(existente.getQtdEnc().add(op.getQtdEnc()));
                for (ProdutoAcabado p : lista) {
                    existente.addVin(p.getVin());
                }
                for (OrdemProducao.ConsumoInsumo c : op.getInsumos().values()) {
                    existente.addConsumo(c.getPartNumber(), c.getDescription(), c.getQuantidade(), c.getDtSaida());
                }
            } else {
                ordens.put(mapKey, op);
            }
        }

        if (progress != null) {
            progress.accept("Ordens de produção montadas: " + ordens.size());
        }
        return ordens;
    }

    private String buildK230(OrdemProducao op) {
        return "|K230|"
                + formatDate(op.getDtIniOp()) + "|"
                + formatDate(op.getDtFinOp()) + "|"
                + nullToEmpty(op.getCodDocOp()) + "|"
                + nullToEmpty(op.getCodItem()) + "|"
                + formatQty(op.getQtdEnc()) + "|";
    }

    private String buildK235(LocalDate dtSaida, String codItem, BigDecimal qtd) {
        return "|K235|"
                + formatDate(dtSaida) + "|"
                + nullToEmpty(codItem) + "|"
                + formatQty(qtd) + "||";
    }

    private String resolverPorDescricao(EfdFile efd, String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        String alvo = normalizar(descricao);
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, String> e : efd.getDescricao0200().entrySet()) {
            String desc = e.getValue();
            if (desc == null || desc.isBlank()) continue;
            String n = normalizar(desc);
            int score = 0;
            if (n.equals(alvo)) score = 100;
            else if (alvo.contains(n) || n.contains(alvo)) score = Math.min(n.length(), alvo.length());
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return bestScore >= 5 ? best : null;
    }

    private String normalizar(String s) {
        return s.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatDate(LocalDate d) {
        return d == null ? "" : d.format(DDMMAAAA);
    }

    private String formatQty(BigDecimal q) {
        if (q == null) {
            return "0,000";
        }
        return q.setScale(3, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
