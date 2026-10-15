package br.com.triumph.efd.service;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.InsumoIgnorado;
import br.com.triumph.efd.model.OrdemProducao;
import br.com.triumph.efd.model.ProdutoAcabado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

public class BlocoKGenerator {

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");

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
                String codInsumo = c.getPartNumber() == null ? "" : c.getPartNumber().trim();
                if (codInsumo.isEmpty() || c.getQuantidade() == null || c.getQuantidade().signum() <= 0) {
                    continue;
                }
                LocalDate dtSaida = c.getDtSaida();
                if (dtSaida == null) {
                    dtSaida = op.getDtIniOp() != null ? op.getDtIniOp() : dtIni;
                }
                if (efd.resolveCodigo0200(codInsumo) == null) {
                    InsumoIgnorado ignorado = new InsumoIgnorado();
                    ignorado.setCodDocOp(op.getCodDocOp());
                    ignorado.setCodItemProduto(op.getCodItem());
                    ignorado.setDescricaoProduto(op.getDescricaoItem());
                    ignorado.setPartNumber(codInsumo);
                    ignorado.setDescricaoInsumo(c.getDescription());
                    ignorado.setQuantidade(c.getQuantidade());
                    ignorado.setDataConsumo(dtSaida);
                    ignorado.setMotivo("PartNumber não encontrado no registro 0200; K235 gerado com o código da planilha");
                    result.addInsumoIgnorado(ignorado);
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

        Map<String, List<InsumoConsumo>> porVin = new LinkedHashMap<>();
        for (InsumoConsumo i : insumos) {
            if (i.getVin() != null && !i.getVin().isBlank()) {
                porVin.computeIfAbsent(i.getVin().trim(), k -> new ArrayList<>()).add(i);
            }
        }

        Map<String, List<ProdutoAcabado>> porModelo = new LinkedHashMap<>();
        Set<String> vinsNoPeriodo = new HashSet<>();
        int foraDoPeriodo = 0;
        for (ProdutoAcabado p : produtos) {
            LocalDate d = p.getData();
            if (d == null || d.isBefore(dtIni) || d.isAfter(dtFin)) {
                foraDoPeriodo++;
                continue;
            }
            String modelo = p.getModelCode() == null ? "" : p.getModelCode().trim();
            if (modelo.isEmpty()) {
                result.addAviso("Produto VIN " + p.getVin() + " sem Model code — ignorado.");
                continue;
            }
            porModelo.computeIfAbsent(modelo, k -> new ArrayList<>()).add(p);
            if (p.getVin() != null && !p.getVin().isBlank()) {
                vinsNoPeriodo.add(p.getVin().trim());
            }
        }
        if (foraDoPeriodo > 0) {
            result.addAviso(foraDoPeriodo + " produto(s) fora do período " + dtIni + " a " + dtFin
                    + " foram ignorados.");
        }

        Map<String, OrdemProducao> ordens = new TreeMap<>();
        for (Map.Entry<String, List<ProdutoAcabado>> e : porModelo.entrySet()) {
            String modelo = e.getKey();
            List<ProdutoAcabado> lista = e.getValue();
            ProdutoAcabado primeiro = lista.get(0);

            if (efd.resolveCodigo0200(modelo) == null) {
                result.addAviso("Código de produto não encontrado no 0200: " + modelo
                        + " (" + primeiro.getModelDescription() + "). K230 será gerado com o Model code.");
            }

            OrdemProducao op = new OrdemProducao();
            String codOp = modelo.length() > 30 ? modelo.substring(0, 30) : modelo;
            op.setCodDocOp(codOp);
            op.setCodItem(modelo);
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
            op.setDtIniOp(min == null ? dtIni : min);
            op.setDtFinOp(max == null ? dtFin : max);
            op.setQtdEnc(BigDecimal.valueOf(lista.size()));

            Set<String> vinsUsados = new HashSet<>();
            for (ProdutoAcabado p : lista) {
                String vin = p.getVin() == null ? "" : p.getVin().trim();
                if (vin.isEmpty() || !vinsUsados.add(vin)) {
                    continue;
                }
                List<InsumoConsumo> relacionados = porVin.get(vin);
                if (relacionados == null) {
                    continue;
                }
                LocalDate dtSaida = p.getData() != null ? p.getData() : op.getDtFinOp();
                for (InsumoConsumo i : relacionados) {
                    op.addConsumo(i.getPartNumber(), i.getDescription(), i.getQuantity(), dtSaida);
                }
            }
            ordens.put(modelo, op);
        }

        int semProduto = 0;
        for (Map.Entry<String, List<InsumoConsumo>> e : porVin.entrySet()) {
            if (!vinsNoPeriodo.contains(e.getKey())) {
                semProduto += e.getValue().size();
            }
        }
        if (semProduto > 0) {
            result.addAviso(semProduto + " insumo(s) agregado(s) sem VIN de produto no período foram ignorados.");
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
                + formatQty(qtd, 6) + "||";
    }

    private String formatDate(LocalDate d) {
        return d == null ? "" : d.format(DDMMAAAA);
    }

    private String formatQty(BigDecimal q) {
        return formatQty(q, 3);
    }

    private String formatQty(BigDecimal q, int casas) {
        if (q == null) {
            return BigDecimal.ZERO.setScale(casas, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        }
        return q.setScale(casas, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
