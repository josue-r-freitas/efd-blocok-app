package br.com.triumph.efd.io;

import br.com.triumph.efd.model.ProdutoAcabado;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.IOUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Leitura leve de produtos acabados via ZIP/StAX (evita OOM com links externos do Excel).
 */
public class ProdutoAcabadoReader {

    static {
        IOUtils.setByteArrayMaxOverride(500_000_000);
    }

    public List<ProdutoAcabado> read(Path path) throws Exception {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"),
                "efd_produtos_" + System.currentTimeMillis() + ".xlsx");
        Files.copy(path, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (ZipFile zip = new ZipFile(temp.toFile())) {
            List<String> shared = readSharedStrings(zip);
            Map<String, String> sheetMap = readWorkbookSheets(zip);

            String targetPath = null;
            for (Map.Entry<String, String> e : sheetMap.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT);
                if (name.contains("qry_build") || name.contains("build") || name.contains("produto")) {
                    targetPath = e.getValue();
                    break;
                }
            }
            if (targetPath == null && !sheetMap.isEmpty()) {
                targetPath = sheetMap.values().iterator().next();
            }
            if (targetPath == null) {
                throw new IllegalArgumentException("Nenhuma planilha encontrada no arquivo de produtos.");
            }

            List<ProdutoAcabado> result = parseSheet(zip, targetPath, shared);
            if (result.isEmpty()) {
                // tenta outras abas
                for (String sheetPath : sheetMap.values()) {
                    if (sheetPath.equals(targetPath)) continue;
                    result = parseSheet(zip, sheetPath, shared);
                    if (!result.isEmpty()) break;
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException(
                        "Nenhum produto acabado encontrado. Verifique colunas Vin / Model code / Date.");
            }
            return result;
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    private List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> list = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return list;
        try (InputStream in = zip.getInputStream(entry)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            StringBuilder sb = new StringBuilder();
            boolean inSi = false;
            boolean inT = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if ("si".equals(local)) { inSi = true; sb.setLength(0); }
                    else if ("t".equals(local) && inSi) inT = true;
                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    if (inT) sb.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String local = r.getLocalName();
                    if ("t".equals(local)) inT = false;
                    else if ("si".equals(local)) {
                        inSi = false;
                        list.add(sb.toString());
                    }
                }
            }
            r.close();
        }
        return list;
    }

    private Map<String, String> readWorkbookSheets(ZipFile zip) throws Exception {
        Map<String, String> ridToName = new HashMap<>();
        ZipEntry wb = zip.getEntry("xl/workbook.xml");
        try (InputStream in = zip.getInputStream(wb)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName())) {
                    String name = r.getAttributeValue(null, "name");
                    String rid = null;
                    for (int i = 0; i < r.getAttributeCount(); i++) {
                        if ("id".equals(r.getAttributeLocalName(i))) {
                            rid = r.getAttributeValue(i);
                        }
                    }
                    if (name != null && rid != null) ridToName.put(rid, name);
                }
            }
            r.close();
        }

        Map<String, String> nameToPath = new HashMap<>();
        ZipEntry rels = zip.getEntry("xl/_rels/workbook.xml.rels");
        try (InputStream in = zip.getInputStream(rels)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(r.getLocalName())) {
                    String id = r.getAttributeValue(null, "Id");
                    String target = r.getAttributeValue(null, "Target");
                    if (id != null && target != null && ridToName.containsKey(id)) {
                        String path = target.startsWith("/") ? target.substring(1)
                                : target.startsWith("xl/") ? target : "xl/" + target;
                        nameToPath.put(ridToName.get(id), path);
                    }
                }
            }
            r.close();
        }
        return nameToPath;
    }

    private List<ProdutoAcabado> parseSheet(ZipFile zip, String sheetPath, List<String> shared) throws Exception {
        ZipEntry entry = zip.getEntry(sheetPath);
        if (entry == null) return List.of();

        Map<String, Integer> cols = new HashMap<>();
        Integer loadCol = null;
        boolean headerDone = false;
        List<ProdutoAcabado> result = new ArrayList<>();
        Map<Integer, String> rowCells = new HashMap<>();

        try (InputStream in = zip.getInputStream(entry)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            String cellRef = null;
            String cellType = null;
            boolean inV = false;
            StringBuilder v = new StringBuilder();

            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if ("c".equals(local)) {
                        cellRef = r.getAttributeValue(null, "r");
                        cellType = r.getAttributeValue(null, "t");
                        v.setLength(0);
                    } else if ("v".equals(local) || "t".equals(local)) {
                        inV = true;
                        v.setLength(0);
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    if (inV) v.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String local = r.getLocalName();
                    if ("v".equals(local) || "t".equals(local)) {
                        inV = false;
                        if (cellRef != null) {
                            int col = colIndex(cellRef);
                            String raw = v.toString();
                            String value = raw;
                            if ("s".equals(cellType) && !raw.isBlank()) {
                                int idx = Integer.parseInt(raw);
                                value = idx >= 0 && idx < shared.size() ? shared.get(idx) : raw;
                            }
                            rowCells.put(col, value);
                        }
                    } else if ("c".equals(local)) {
                        cellRef = null;
                        cellType = null;
                    } else if ("row".equals(local)) {
                        if (!headerDone) {
                            mapHeader(rowCells, cols);
                            headerDone = true;
                            if (!cols.containsKey("vin")) {
                                rowCells.clear();
                                // keep scanning? if first row isn't header, fail this sheet
                                r.close();
                                return List.of();
                            }
                        } else {
                            if (loadCol == null && !cols.containsKey("load")) {
                                for (Map.Entry<Integer, String> e : rowCells.entrySet()) {
                                    if (e.getValue() != null && e.getValue().matches("5SH\\d+")) {
                                        loadCol = e.getKey();
                                        cols.put("load", loadCol);
                                        break;
                                    }
                                }
                            }
                            ProdutoAcabado p = toProduto(rowCells, cols);
                            if (p != null) result.add(p);
                        }
                        rowCells.clear();
                    }
                }
            }
            r.close();
        }
        return result;
    }

    private void mapHeader(Map<Integer, String> cells, Map<String, Integer> cols) {
        for (Map.Entry<Integer, String> e : cells.entrySet()) {
            String h = e.getValue() == null ? "" : e.getValue().trim().toLowerCase(Locale.ROOT);
            int idx = e.getKey();
            if (h.equals("vin") || h.equals("vin number")) cols.put("vin", idx);
            else if (h.equals("full vin") || h.equals("fullvin")) cols.put("fullvin", idx);
            else if (h.equals("date") || h.equals("data")) cols.put("date", idx);
            else if (h.equals("order ref") || h.equals("orderref")) cols.put("order", idx);
            else if (h.equals("model range") || h.equals("modelrange")) cols.put("range", idx);
            else if (h.equals("model code") || h.equals("modelcode")) cols.put("model", idx);
            else if (h.equals("model description") || h.equals("modeldescription")) cols.put("desc", idx);
            else if (h.equals("colour") || h.equals("color")) cols.put("colour", idx);
            else if (h.equals("load")) cols.put("load", idx);
            else if (h.equals("cost")) cols.put("cost", idx);
            else if (h.equals("sales")) cols.put("sales", idx);
        }
    }

    private ProdutoAcabado toProduto(Map<Integer, String> cells, Map<String, Integer> cols) {
        String vin = get(cells, cols, "vin");
        String model = get(cells, cols, "model");
        if ((vin == null || vin.isBlank()) && (model == null || model.isBlank())) {
            return null;
        }
        ProdutoAcabado p = new ProdutoAcabado();
        p.setVin(vin);
        p.setFullVin(get(cells, cols, "fullvin"));
        p.setData(parseDate(get(cells, cols, "date")));
        p.setOrderRef(get(cells, cols, "order"));
        p.setModelRange(get(cells, cols, "range"));
        p.setModelCode(model);
        p.setModelDescription(get(cells, cols, "desc"));
        p.setColour(get(cells, cols, "colour"));
        p.setLoad(get(cells, cols, "load"));
        p.setCost(parseDecimal(get(cells, cols, "cost")));
        p.setSales(parseDecimal(get(cells, cols, "sales")));
        return p;
    }

    private String get(Map<Integer, String> cells, Map<String, Integer> cols, String key) {
        Integer idx = cols.get(key);
        if (idx == null) return null;
        String v = cells.get(idx);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static int colIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isLetter(c)) col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
            else break;
        }
        return col - 1;
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            if (text.matches("\\d{8}")) {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("ddMMyyyy"));
            }
            if (text.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(text.substring(0, 10));
            }
            if (text.matches("\\d+(\\.\\d+)?")) {
                double serial = Double.parseDouble(text);
                if (serial > 20000 && serial < 80000) {
                    return DateUtil.getLocalDateTime(serial).toLocalDate();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static BigDecimal parseDecimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String v = text.trim();
            if (v.contains(",") && v.contains(".")) v = v.replace(".", "").replace(",", ".");
            else if (v.contains(",")) v = v.replace(",", ".");
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }
}
