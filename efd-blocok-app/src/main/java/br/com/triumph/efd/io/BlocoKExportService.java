package br.com.triumph.efd.io;

import br.com.triumph.efd.model.BlocoKLinha;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.util.SpedLineParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlocoKExportService {

    public enum Formato {
        TXT, CSV, XLSX
    }

    public void exportar(Path destino, Formato formato, BlocoKResult blocoK, Consumer<String> progress) throws IOException {
        switch (formato) {
            case TXT -> exportarTxt(destino, blocoK.getLinhas(), progress);
            case CSV -> exportarCsv(destino, blocoK, progress);
            case XLSX -> exportarXlsx(destino, blocoK, progress);
        }
    }

    public void exportarTxt(Path destino, List<String> linhas, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando TXT...");
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.ISO_8859_1)) {
            for (int i = 0; i < linhas.size(); i++) {
                w.write(linhas.get(i));
                w.write("\r\n");
                if (progress != null && i > 0 && i % 100000 == 0) {
                    progress.accept("TXT: " + i + " / " + linhas.size());
                }
            }
        }
        if (progress != null) progress.accept("TXT salvo: " + destino.getFileName());
    }

    public void exportarCsv(Path destino, BlocoKResult blocoK, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando CSV...");
        List<BlocoKLinha> parsed = SpedLineParser.parseAll(blocoK.getLinhas());
        int maxCampos = SpedLineParser.maxCampos(parsed);

        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('\ufeff'); // BOM UTF-8 para Excel
            escreverCsvLinha(w, BlocoKLinha.cabecalhoCsvMax(maxCampos));
            for (int i = 0; i < parsed.size(); i++) {
                escreverCsvLinha(w, parsed.get(i).toCsvRow());
                if (progress != null && i > 0 && i % 100000 == 0) {
                    progress.accept("CSV: " + i + " / " + parsed.size());
                }
            }
        }
        if (progress != null) progress.accept("CSV salvo: " + destino.getFileName());
    }

    public void exportarXlsx(Path destino, BlocoKResult blocoK, Consumer<String> progress) throws IOException {
        if (progress != null) {
            progress.accept("Excel: preparando " + formatar(blocoK.totalLinhas()) + " linhas...");
        }
        List<BlocoKLinha> parsed = SpedLineParser.parseAll(blocoK.getLinhas());

        Map<String, List<BlocoKLinha>> porRegistro = new LinkedHashMap<>();
        for (BlocoKLinha l : parsed) {
            porRegistro.computeIfAbsent(l.getRegistro(), k -> new ArrayList<>()).add(l);
        }

        try (SXSSFWorkbook wb = new SXSSFWorkbook(500)) {
            wb.setCompressTempFiles(true);
            if (progress != null) progress.accept("Excel: criando aba Resumo...");
            criarAbaResumo(wb, blocoK);
            criarAbaRegistro(wb, "K230", porRegistro.get("K230"),
                    new String[]{"Linha", "DT_INI_OP", "DT_FIN_OP", "COD_DOC_OP", "COD_ITEM", "QTD_ENC"}, progress);
            criarAbaRegistro(wb, "K235", porRegistro.get("K235"),
                    new String[]{"Linha", "DT_SAIDA", "COD_ITEM", "QTD", "COD_INS_SUBST"}, progress);
            criarAbaRegistro(wb, "K200", porRegistro.get("K200"),
                    new String[]{"Linha", "DT_EST", "COD_ITEM", "QTD", "IND_EST", "COD_PART"}, progress);
            criarAbaTodas(wb, parsed, progress);

            if (progress != null) progress.accept("Excel: gravando arquivo no disco...");
            try (OutputStream out = Files.newOutputStream(destino)) {
                wb.write(out);
            }
            wb.dispose();
        }
        if (progress != null) progress.accept("Excel salvo: " + destino.getFileName());
    }

    private void criarAbaResumo(SXSSFWorkbook wb, BlocoKResult blocoK) {
        Sheet sheet = wb.createSheet("Resumo");
        int r = 0;
        r = linhaResumo(sheet, r, "Total de linhas", String.valueOf(blocoK.totalLinhas()));
        r = linhaResumo(sheet, r, "K230 (itens produzidos)", String.valueOf(blocoK.getQtdK230()));
        r = linhaResumo(sheet, r, "K235 (insumos consumidos)", String.valueOf(blocoK.getQtdK235()));
        r = linhaResumo(sheet, r, "K200 (estoque)", String.valueOf(blocoK.getQtdK200()));
        r = linhaResumo(sheet, r, "Produtos processados", String.valueOf(blocoK.getProdutosProcessados()));
        r = linhaResumo(sheet, r, "Insumos processados", String.valueOf(blocoK.getInsumosProcessados()));
        r = linhaResumo(sheet, r, "Insumos ignorados (sem 0200)", String.valueOf(blocoK.getInsumosIgnorados()));
        r = linhaResumo(sheet, r, "Avisos", String.valueOf(blocoK.getAvisos().size()));
        int avisoRow = r;
        for (String aviso : blocoK.getAvisos()) {
            Row row = sheet.createRow(avisoRow++);
            row.createCell(0).setCellValue(aviso);
        }
        sheet.setColumnWidth(0, 12000);
        sheet.setColumnWidth(1, 6000);
    }

    private int linhaResumo(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private void criarAbaRegistro(SXSSFWorkbook wb, String nome, List<BlocoKLinha> linhas,
                                String[] headers, Consumer<String> progress) {
        if (linhas == null || linhas.isEmpty()) {
            if (progress != null) progress.accept("Excel: aba " + nome + " vazia (ignorada)");
            return;
        }
        if (progress != null) {
            progress.accept("Excel: criando aba " + nome + " (" + formatar(linhas.size()) + " linhas)...");
        }
        Sheet sheet = wb.createSheet(nome);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        for (int i = 0; i < linhas.size(); i++) {
            BlocoKLinha l = linhas.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(l.getNumero());
            for (int c = 0; c < l.getCampos().size(); c++) {
                row.createCell(c + 1).setCellValue(l.getCampos().get(c));
            }
            if (progress != null && i > 0 && i % 50000 == 0) {
                progress.accept("Excel " + nome + ": " + formatar(i) + " / " + formatar(linhas.size()));
            }
        }
    }

    private void criarAbaTodas(SXSSFWorkbook wb, List<BlocoKLinha> linhas, Consumer<String> progress) {
        if (progress != null) {
            progress.accept("Excel: criando aba Todas (" + formatar(linhas.size()) + " linhas)...");
        }
        Sheet sheet = wb.createSheet("Todas");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Linha");
        header.createCell(1).setCellValue("Registro");
        header.createCell(2).setCellValue("Conteudo");
        for (int i = 0; i < linhas.size(); i++) {
            BlocoKLinha l = linhas.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(l.getNumero());
            row.createCell(1).setCellValue(l.getRegistro());
            row.createCell(2).setCellValue(l.getLinhaOriginal());
            if (progress != null && i > 0 && i % 50000 == 0) {
                progress.accept("Excel Todas: " + formatar(i) + " / " + formatar(linhas.size()));
            }
        }
    }

    private static String formatar(int valor) {
        return String.format("%,d", valor).replace(',', '.');
    }

    private void escreverCsvLinha(BufferedWriter w, String[] cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) w.write(';');
            w.write(escaparCsv(cols[i] == null ? "" : cols[i]));
        }
        w.write("\r\n");
    }

    private String escaparCsv(String value) {
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
