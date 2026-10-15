package br.com.triumph.efd.io;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.InsumoIgnorado;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class InsumosIgnoradosExportService {

    public enum Formato {
        TXT, CSV, XLSX
    }

    public void exportar(Path destino, Formato formato, BlocoKResult blocoK, Consumer<String> progress) throws IOException {
        List<InsumoIgnorado> itens = blocoK.getInsumosIgnoradosLista();
        switch (formato) {
            case TXT -> exportarTxt(destino, itens, progress);
            case CSV -> exportarCsv(destino, itens, progress);
            case XLSX -> exportarXlsx(destino, itens, progress);
        }
    }

    public void exportarTxt(Path destino, List<InsumoIgnorado> itens, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando insumos ignorados (TXT)...");
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write(InsumoIgnorado.cabecalhoTxt());
            w.write("\r\n");
            for (int i = 0; i < itens.size(); i++) {
                w.write(itens.get(i).linhaTxt());
                w.write("\r\n");
                if (progress != null && i > 0 && i % 10000 == 0) {
                    progress.accept("TXT: " + formatar(i) + " / " + formatar(itens.size()));
                }
            }
        }
        if (progress != null) progress.accept("TXT salvo: " + destino.getFileName());
    }

    public void exportarCsv(Path destino, List<InsumoIgnorado> itens, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando insumos ignorados (CSV)...");
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('\ufeff');
            escreverCsvLinha(w, InsumoIgnorado.cabecalhoCsv());
            for (int i = 0; i < itens.size(); i++) {
                escreverCsvLinha(w, itens.get(i).toCsvRow());
                if (progress != null && i > 0 && i % 10000 == 0) {
                    progress.accept("CSV: " + formatar(i) + " / " + formatar(itens.size()));
                }
            }
        }
        if (progress != null) progress.accept("CSV salvo: " + destino.getFileName());
    }

    public void exportarXlsx(Path destino, List<InsumoIgnorado> itens, Consumer<String> progress) throws IOException {
        if (progress != null) {
            progress.accept("Excel: preparando " + formatar(itens.size()) + " insumos ignorados...");
        }
        String[] headers = InsumoIgnorado.cabecalhoCsv();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(500)) {
            wb.setCompressTempFiles(true);
            if (progress != null) progress.accept("Excel: criando aba Insumos ignorados...");
            Sheet sheet = wb.createSheet("Insumos ignorados");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < itens.size(); i++) {
                String[] cols = itens.get(i).toCsvRow();
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < cols.length; c++) {
                    row.createCell(c).setCellValue(cols[c]);
                }
                if (progress != null && i > 0 && i % 10000 == 0) {
                    progress.accept("Excel: " + formatar(i) + " / " + formatar(itens.size()));
                }
            }
            if (progress != null) progress.accept("Excel: gravando arquivo no disco...");
            try (OutputStream out = Files.newOutputStream(destino)) {
                wb.write(out);
            }
            wb.dispose();
        }
        if (progress != null) progress.accept("Excel salvo: " + destino.getFileName());
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

    private static String formatar(int valor) {
        return String.format("%,d", valor).replace(',', '.');
    }
}
