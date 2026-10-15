package br.com.triumph.efd.io;

import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;
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

public class Registro0200ExportService {

    public enum Formato {
        TXT, CSV, XLSX
    }

    public void exportar(Path destino, Formato formato, Registro0200Result result, Consumer<String> progress) throws IOException {
        List<Registro0200Item> itens = result.getItens();
        switch (formato) {
            case TXT -> exportarTxt(destino, result, progress);
            case CSV -> exportarCsv(destino, itens, progress);
            case XLSX -> exportarXlsx(destino, itens, progress);
        }
    }

    private void exportarTxt(Path destino, Registro0200Result result, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando 0200 (TXT SPED)...");
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.ISO_8859_1)) {
            for (String linha : result.getLinhas()) {
                w.write(linha);
                w.write("\r\n");
            }
        }
        if (progress != null) progress.accept("TXT salvo: " + destino.getFileName());
    }

    private void exportarCsv(Path destino, List<Registro0200Item> itens, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando 0200 (CSV)...");
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('\ufeff');
            escreverCsv(w, Registro0200Item.cabecalhoCsv());
            for (Registro0200Item item : itens) {
                escreverCsv(w, item.toCsvRow());
            }
        }
        if (progress != null) progress.accept("CSV salvo: " + destino.getFileName());
    }

    private void exportarXlsx(Path destino, List<Registro0200Item> itens, Consumer<String> progress) throws IOException {
        if (progress != null) progress.accept("Exportando 0200 (Excel)...");
        String[] headers = Registro0200Item.cabecalhoCsv();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Sheet sheet = wb.createSheet("0200 gerados");
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
            }
            try (OutputStream out = Files.newOutputStream(destino)) {
                wb.write(out);
            }
            wb.dispose();
        }
        if (progress != null) progress.accept("Excel salvo: " + destino.getFileName());
    }

    private void escreverCsv(BufferedWriter w, String[] cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) w.write(';');
            w.write(escapar(cols[i] == null ? "" : cols[i]));
        }
        w.write("\r\n");
    }

    private String escapar(String value) {
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
