package br.com.triumph.efd.io;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.ProdutoAcabado;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Planilha para conferir o K230 e o K235 gerados com os produtos e insumos do período.
 */
public class BlocoKValidacaoExportService {

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter EXIBICAO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Última linha válida do Excel, já com o cabeçalho na linha 0. */
    private static final int ULTIMA_LINHA_EXCEL = 1_048_575;

    public void exportar(Path destino,
                         List<ProdutoAcabado> produtos,
                         List<InsumoConsumo> insumos,
                         BlocoKResult blocoK,
                         Consumer<String> progress) throws IOException {
        List<ProdutoAcabado> baseProdutos = produtos == null ? List.of() : produtos;
        List<InsumoConsumo> baseInsumos = insumos == null ? List.of() : insumos;

        if (progress != null) {
            progress.accept("Montando conferência a partir dos produtos e insumos...");
        }
        Map<String, ResumoModelo> porModelo = resumirProdutos(baseProdutos);
        Map<String, Map<String, ResumoInsumo>> porModeloParte = resumirInsumos(baseProdutos, baseInsumos);
        List<K230Gerado> k230s = lerK230(blocoK);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(500)) {
            wb.setCompressTempFiles(true);
            CellStyle header = headerStyle(wb);

            if (progress != null) progress.accept("Excel: aba Orientação...");
            criarOrientacao(wb, header, blocoK, porModelo.size());

            if (progress != null) progress.accept("Excel: aba K230...");
            criarK230(wb, header, k230s, porModelo);

            if (progress != null) progress.accept("Excel: aba K235...");
            criarK235(wb, header, k230s, porModeloParte, progress);

            if (progress != null) progress.accept("Excel: aba Produtos...");
            criarProdutos(wb, header, baseProdutos, progress);

            if (progress != null) progress.accept("Excel: aba Insumos vinculados...");
            criarInsumos(wb, header, baseProdutos, baseInsumos, progress);

            if (progress != null) progress.accept("Excel: gravando arquivo...");
            try (OutputStream out = Files.newOutputStream(destino)) {
                wb.write(out);
            }
            wb.dispose();
        }
        if (progress != null) progress.accept("Relatório salvo: " + destino.getFileName());
    }

    private void criarOrientacao(SXSSFWorkbook wb, CellStyle header, BlocoKResult blocoK, int modelos) {
        Sheet sheet = wb.createSheet("Orientação");
        String[] linhas = {
                "Use este relatório para conferir o Bloco K gerado com as planilhas do período.",
                "K230: um registro por Model code. QTD_ENC é a quantidade de linhas desse modelo. DT_INI_OP é a menor Date e DT_FIN_OP a maior.",
                "K235: um registro por Part Number dos VINs daquele modelo. QTD é a soma de Quantity. DT_SAIDA é a menor Date desses VINs. COD_INS_SUBST fica vazio.",
                "A coluna Conferência compara o registro gerado com a base lida. Sim indica que os valores batem.",
                "A aba Produtos lista as motos que entraram na quantidade do K230. Filtre pelo Model code e conte as linhas.",
                "A aba Insumos vinculados lista o consumo de cada VIN. Filtre pelo modelo e pelo Part Number para somar a Quantity.",
                "Se essa lista passar de 1.048.576 linhas, o Excel a divide em Insumos vinculados 2, 3 e assim por diante.",
                "K230 gerados: " + blocoK.getQtdK230(),
                "K235 gerados: " + blocoK.getQtdK235(),
                "Modelos na base do período: " + modelos
        };
        for (int i = 0; i < linhas.length; i++) {
            Row row = sheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(linhas[i]);
            if (i == 0) {
                cell.setCellStyle(header);
            }
        }
        sheet.setColumnWidth(0, 28000);
    }

    private void criarK230(SXSSFWorkbook wb, CellStyle header, List<K230Gerado> gerados, Map<String, ResumoModelo> porModelo) {
        Sheet sheet = wb.createSheet("K230");
        String[] cols = {
                "Model code", "DT_INI_OP", "DT_FIN_OP", "COD_DOC_OP", "COD_ITEM", "QTD_ENC",
                "Qtd linhas na planilha", "Menor Date", "Maior Date", "Datas conferem", "Quantidade confere"
        };
        escreverCabecalho(sheet, header, cols);
        int r = 1;
        for (K230Gerado g : gerados) {
            ResumoModelo base = porModelo.get(g.modelo);
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(g.modelo);
            row.createCell(c++).setCellValue(formatar(g.dtIni));
            row.createCell(c++).setCellValue(formatar(g.dtFin));
            row.createCell(c++).setCellValue(g.codDocOp);
            row.createCell(c++).setCellValue(g.codItem);
            row.createCell(c++).setCellValue(formatarQtd(g.qtd, 3));
            if (base == null) {
                row.createCell(c++).setCellValue("");
                row.createCell(c++).setCellValue("");
                row.createCell(c++).setCellValue("");
                row.createCell(c++).setCellValue("Não");
                row.createCell(c).setCellValue("Não");
            } else {
                row.createCell(c++).setCellValue(base.quantidade);
                row.createCell(c++).setCellValue(formatar(base.menor));
                row.createCell(c++).setCellValue(formatar(base.maior));
                boolean datas = mesmaData(g.dtIni, base.menor) && mesmaData(g.dtFin, base.maior);
                boolean qtd = g.qtd != null && g.qtd.compareTo(BigDecimal.valueOf(base.quantidade)) == 0;
                row.createCell(c++).setCellValue(datas ? "Sim" : "Não");
                row.createCell(c).setCellValue(qtd ? "Sim" : "Não");
            }
        }
        finalizar(sheet, cols.length, r);
    }

    private void criarK235(SXSSFWorkbook wb, CellStyle header, List<K230Gerado> gerados,
                           Map<String, Map<String, ResumoInsumo>> porModeloParte, Consumer<String> progress) {
        Sheet sheet = wb.createSheet("K235");
        String[] cols = {
                "Model code", "DT_SAIDA", "COD_ITEM", "QTD", "COD_INS_SUBST",
                "Soma Quantity", "Menor Date dos VINs", "Data confere", "Quantidade confere", "COD_INS_SUBST vazio"
        };
        escreverCabecalho(sheet, header, cols);
        int r = 1;
        for (K230Gerado op : gerados) {
            Map<String, ResumoInsumo> partes = porModeloParte.getOrDefault(op.modelo, Map.of());
            for (K235Gerado g : op.insumos) {
                ResumoInsumo base = partes.get(g.codItem);
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(op.modelo);
                row.createCell(c++).setCellValue(formatar(g.dtSaida));
                row.createCell(c++).setCellValue(g.codItem);
                row.createCell(c++).setCellValue(formatarQtd(g.qtd, 6));
                row.createCell(c++).setCellValue(g.codInsSubst);
                if (base == null) {
                    row.createCell(c++).setCellValue("");
                    row.createCell(c++).setCellValue("");
                    row.createCell(c++).setCellValue("Não");
                    row.createCell(c++).setCellValue("Não");
                } else {
                    row.createCell(c++).setCellValue(formatarQtd(base.quantidade, 6));
                    row.createCell(c++).setCellValue(formatar(base.menorData));
                    boolean data = mesmaData(g.dtSaida, base.menorData);
                    boolean qtd = g.qtd != null && base.quantidade != null && g.qtd.compareTo(base.quantidade) == 0;
                    row.createCell(c++).setCellValue(data ? "Sim" : "Não");
                    row.createCell(c++).setCellValue(qtd ? "Sim" : "Não");
                }
                row.createCell(c).setCellValue(g.codInsSubst == null || g.codInsSubst.isBlank() ? "Sim" : "Não");
                if (progress != null && r % 50000 == 0) {
                    progress.accept("K235 conferidos: " + (r - 1));
                }
            }
        }
        finalizar(sheet, cols.length, r);
    }

    private void criarProdutos(SXSSFWorkbook wb, CellStyle header, List<ProdutoAcabado> produtos, Consumer<String> progress) {
        Sheet sheet = wb.createSheet("Produtos");
        String[] cols = {"VIN", "Date", "Model code", "Descrição"};
        escreverCabecalho(sheet, header, cols);
        int r = 1;
        for (ProdutoAcabado p : produtos) {
            String modelo = p.getModelCode() == null ? "" : p.getModelCode().trim();
            if (modelo.isEmpty()) {
                continue;
            }
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getVin() == null ? "" : p.getVin());
            row.createCell(1).setCellValue(formatar(p.getData()));
            row.createCell(2).setCellValue(modelo);
            row.createCell(3).setCellValue(p.getModelDescription() == null ? "" : p.getModelDescription());
            if (progress != null && r % 20000 == 0) {
                progress.accept("Produtos no relatório: " + (r - 1));
            }
        }
        finalizar(sheet, cols.length, r);
    }

    private void criarInsumos(SXSSFWorkbook wb, CellStyle header, List<ProdutoAcabado> produtos,
                              List<InsumoConsumo> insumos, Consumer<String> progress) {
        String[] cols = {"VIN", "Model code", "Date do produto", "Part Number", "Descrição", "Quantity"};

        Map<String, ProdutoAcabado> porVin = new LinkedHashMap<>();
        for (ProdutoAcabado p : produtos) {
            if (p.getVin() == null || p.getVin().isBlank()) {
                continue;
            }
            String modelo = p.getModelCode() == null ? "" : p.getModelCode().trim();
            if (modelo.isEmpty()) {
                continue;
            }
            porVin.putIfAbsent(p.getVin().trim(), p);
        }

        Folha folha = abrirFolha(wb, header, "Insumos vinculados", 1, cols);
        int gravadas = 0;
        for (InsumoConsumo i : insumos) {
            if (i.getVin() == null || i.getVin().isBlank()) {
                continue;
            }
            ProdutoAcabado produto = porVin.get(i.getVin().trim());
            if (produto == null) {
                continue;
            }
            folha = proximaFolha(wb, header, "Insumos vinculados", cols, folha);
            Row row = folha.sheet.createRow(folha.linha++);
            row.createCell(0).setCellValue(i.getVin().trim());
            row.createCell(1).setCellValue(produto.getModelCode().trim());
            row.createCell(2).setCellValue(formatar(produto.getData()));
            row.createCell(3).setCellValue(i.getPartNumber() == null ? "" : i.getPartNumber());
            row.createCell(4).setCellValue(i.getDescription() == null ? "" : i.getDescription());
            row.createCell(5).setCellValue(formatarQtd(i.getQuantity(), 6));
            gravadas++;
            if (progress != null && gravadas % 50000 == 0) {
                progress.accept("Insumos no relatório: " + gravadas + " (" + folha.sheet.getSheetName() + ")");
            }
        }
        finalizar(folha.sheet, cols.length, folha.linha);
    }

    private Folha abrirFolha(SXSSFWorkbook wb, CellStyle header, String base, int parte, String[] cols) {
        String nome = parte <= 1 ? base : base + " " + parte;
        if (nome.length() > 31) {
            nome = nome.substring(0, 31);
        }
        Folha folha = new Folha();
        folha.sheet = wb.createSheet(nome);
        folha.parte = parte;
        escreverCabecalho(folha.sheet, header, cols);
        return folha;
    }

    private Folha proximaFolha(SXSSFWorkbook wb, CellStyle header, String base, String[] cols, Folha atual) {
        if (atual.linha <= ULTIMA_LINHA_EXCEL) {
            return atual;
        }
        finalizar(atual.sheet, cols.length, atual.linha);
        return abrirFolha(wb, header, base, atual.parte + 1, cols);
    }

    private Map<String, ResumoModelo> resumirProdutos(List<ProdutoAcabado> produtos) {
        Map<String, ResumoModelo> mapa = new LinkedHashMap<>();
        for (ProdutoAcabado p : produtos) {
            String modelo = p.getModelCode() == null ? "" : p.getModelCode().trim();
            if (modelo.isEmpty()) {
                continue;
            }
            ResumoModelo resumo = mapa.computeIfAbsent(modelo, ResumoModelo::new);
            resumo.quantidade++;
            LocalDate data = p.getData();
            if (data != null) {
                if (resumo.menor == null || data.isBefore(resumo.menor)) {
                    resumo.menor = data;
                }
                if (resumo.maior == null || data.isAfter(resumo.maior)) {
                    resumo.maior = data;
                }
            }
        }
        return mapa;
    }

    private Map<String, Map<String, ResumoInsumo>> resumirInsumos(List<ProdutoAcabado> produtos, List<InsumoConsumo> insumos) {
        Map<String, List<InsumoConsumo>> porVin = new LinkedHashMap<>();
        for (InsumoConsumo i : insumos) {
            if (i.getVin() != null && !i.getVin().isBlank()) {
                porVin.computeIfAbsent(i.getVin().trim(), k -> new ArrayList<>()).add(i);
            }
        }
        Map<String, List<ProdutoAcabado>> porModelo = new LinkedHashMap<>();
        for (ProdutoAcabado p : produtos) {
            String modelo = p.getModelCode() == null ? "" : p.getModelCode().trim();
            if (!modelo.isEmpty()) {
                porModelo.computeIfAbsent(modelo, k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, Map<String, ResumoInsumo>> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, List<ProdutoAcabado>> e : porModelo.entrySet()) {
            Map<String, ResumoInsumo> partes = new LinkedHashMap<>();
            List<String> vins = new ArrayList<>();
            for (ProdutoAcabado p : e.getValue()) {
                if (p.getVin() == null || p.getVin().isBlank()) {
                    continue;
                }
                String vin = p.getVin().trim();
                if (vins.contains(vin)) {
                    continue;
                }
                vins.add(vin);
                List<InsumoConsumo> doVin = porVin.get(vin);
                if (doVin == null) {
                    continue;
                }
                for (InsumoConsumo i : doVin) {
                    if (i.getPartNumber() == null || i.getPartNumber().isBlank() || i.getQuantity() == null) {
                        continue;
                    }
                    String part = i.getPartNumber().trim();
                    ResumoInsumo resumo = partes.computeIfAbsent(part, ResumoInsumo::new);
                    resumo.quantidade = resumo.quantidade.add(i.getQuantity());
                    LocalDate data = p.getData();
                    if (data != null && (resumo.menorData == null || data.isBefore(resumo.menorData))) {
                        resumo.menorData = data;
                    }
                }
            }
            resultado.put(e.getKey(), partes);
        }
        return resultado;
    }

    private List<K230Gerado> lerK230(BlocoKResult blocoK) {
        List<K230Gerado> lista = new ArrayList<>();
        K230Gerado atual = null;
        if (blocoK == null) {
            return lista;
        }
        for (String linha : blocoK.getLinhas()) {
            if (linha.startsWith("|K230|")) {
                atual = K230Gerado.from(linha);
                lista.add(atual);
            } else if (linha.startsWith("|K235|") && atual != null) {
                atual.insumos.add(K235Gerado.from(linha));
            }
        }
        return lista;
    }

    private void escreverCabecalho(Sheet sheet, CellStyle header, String[] cols) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(header);
        }
        sheet.createFreezePane(0, 1);
    }

    private void finalizar(Sheet sheet, int colunas, int linhas) {
        if (linhas > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, linhas - 1, 0, colunas - 1));
        }
        for (int i = 0; i < colunas; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }

    private CellStyle headerStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static boolean mesmaData(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }

    private static String formatar(LocalDate data) {
        return data == null ? "" : data.format(EXIBICAO);
    }

    private static String formatarQtd(BigDecimal qtd, int casas) {
        if (qtd == null) {
            return "";
        }
        return qtd.setScale(casas, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static LocalDate parseData(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return LocalDate.parse(texto.trim(), DDMMAAAA);
    }

    private static BigDecimal parseQtd(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return new BigDecimal(texto.trim().replace(".", "").replace(",", "."));
    }

    private static String campo(String linha, int indice) {
        String[] partes = linha.split("\\|", -1);
        return indice < partes.length ? partes[indice] : "";
    }

    private static final class Folha {
        private Sheet sheet;
        private int linha = 1;
        private int parte = 1;
    }

    private static final class ResumoModelo {
        private final String modelo;
        private int quantidade;
        private LocalDate menor;
        private LocalDate maior;

        private ResumoModelo(String modelo) {
            this.modelo = modelo;
        }
    }

    private static final class ResumoInsumo {
        private final String partNumber;
        private BigDecimal quantidade = BigDecimal.ZERO;
        private LocalDate menorData;

        private ResumoInsumo(String partNumber) {
            this.partNumber = partNumber;
        }
    }

    private static final class K230Gerado {
        private final String modelo;
        private final String codDocOp;
        private final String codItem;
        private final LocalDate dtIni;
        private final LocalDate dtFin;
        private final BigDecimal qtd;
        private final List<K235Gerado> insumos = new ArrayList<>();

        private K230Gerado(String modelo, String codDocOp, String codItem, LocalDate dtIni, LocalDate dtFin, BigDecimal qtd) {
            this.modelo = modelo;
            this.codDocOp = codDocOp;
            this.codItem = codItem;
            this.dtIni = dtIni;
            this.dtFin = dtFin;
            this.qtd = qtd;
        }

        private static K230Gerado from(String linha) {
            String codDoc = campo(linha, 4);
            return new K230Gerado(codDoc, codDoc, campo(linha, 5), parseData(campo(linha, 2)), parseData(campo(linha, 3)), parseQtd(campo(linha, 6)));
        }
    }

    private static final class K235Gerado {
        private final LocalDate dtSaida;
        private final String codItem;
        private final BigDecimal qtd;
        private final String codInsSubst;

        private K235Gerado(LocalDate dtSaida, String codItem, BigDecimal qtd, String codInsSubst) {
            this.dtSaida = dtSaida;
            this.codItem = codItem;
            this.qtd = qtd;
            this.codInsSubst = codInsSubst;
        }

        private static K235Gerado from(String linha) {
            return new K235Gerado(parseData(campo(linha, 2)), campo(linha, 3), parseQtd(campo(linha, 4)), campo(linha, 5));
        }
    }
}
