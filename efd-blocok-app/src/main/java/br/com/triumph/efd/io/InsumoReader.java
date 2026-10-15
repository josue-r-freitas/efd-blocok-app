package br.com.triumph.efd.io;

import br.com.triumph.efd.model.InsumoConsumo;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.IOUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Leitura em streaming de insumos via ZIP/StAX (arquivos grandes).
 */
public class InsumoReader {

    static {
        IOUtils.setByteArrayMaxOverride(500_000_000);
    }

    public List<InsumoConsumo> read(Path path, Consumer<String> progress, LocalDate inicio, LocalDate fim) throws Exception {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"),
                "efd_insumos_" + System.currentTimeMillis() + ".xlsx");
        if (progress != null) progress.accept("Copiando planilha de insumos...");
        Files.copy(path, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (ZipFile zip = new ZipFile(temp.toFile())) {
            if (progress != null) progress.accept("Lendo shared strings...");
            List<String> shared = readSharedStrings(zip, progress);
            Map<String, String> sheets = readWorkbookSheets(zip);
            if (sheets.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma planilha encontrada no arquivo de insumos.");
            }

            Map<String, InsumoConsumo> agregados = new LinkedHashMap<>();
            int abasLidas = 0;
            List<String> abasDoPeriodo = new ArrayList<>();
            for (String nome : sheets.keySet()) {
                Month mes = mesDaAba(nome);
                if (mes != null && (inicio == null || fim == null || mesNoPeriodo(mes, inicio, fim))) {
                    abasDoPeriodo.add(nome);
                } else if (mes != null && progress != null) {
                    progress.accept("Aba fora do período, ignorada: " + nome);
                }
            }
            if (progress != null && !abasDoPeriodo.isEmpty()) {
                progress.accept("Abas do período (" + abasDoPeriodo.size() + "): " + String.join(", ", abasDoPeriodo));
            }
            for (Map.Entry<String, String> sheet : sheets.entrySet()) {
                Month mes = mesDaAba(sheet.getKey());
                boolean abaMensal = mes != null;
                if (abaMensal && !abasDoPeriodo.contains(sheet.getKey())) {
                    continue;
                }
                if (progress != null) {
                    progress.accept("Lendo aba de insumos: " + sheet.getKey());
                }
                boolean filtrarData = inicio != null && fim != null && !abaMensal;
                int linhas = parseSheet(zip, sheet.getValue(), shared, agregados, progress, inicio, fim, filtrarData);
                if (linhas >= 0) {
                    abasLidas++;
                    if (progress != null) {
                        progress.accept(sheet.getKey() + ": " + linhas + " linhas; agregados: " + agregados.size());
                    }
                }
            }
            if (abasLidas == 0 || agregados.isEmpty()) {
                throw new IllegalArgumentException(
                        "Nenhum insumo no período " + inicio + " a " + fim
                                + ". Verifique as abas do mês e as colunas VIN NUMBER, Part Number e Quantity.");
            }
            if (progress != null) {
                progress.accept("Insumos agregados por VIN e Part Number: " + agregados.size());
            }
            return new ArrayList<>(agregados.values());
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    private List<String> readSharedStrings(ZipFile zip, Consumer<String> progress) throws Exception {
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
                        if (progress != null && list.size() % 100000 == 0) {
                            progress.accept("Shared strings: " + list.size());
                        }
                    }
                }
            }
            r.close();
        }
        return list;
    }

    private Map<String, String> readWorkbookSheets(ZipFile zip) throws Exception {
        Map<String, String> ridToName = new LinkedHashMap<>();
        ZipEntry wb = zip.getEntry("xl/workbook.xml");
        try (InputStream in = zip.getInputStream(wb)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName())) {
                    String name = r.getAttributeValue(null, "name");
                    String rid = null;
                    for (int i = 0; i < r.getAttributeCount(); i++) {
                        if ("id".equals(r.getAttributeLocalName(i))) rid = r.getAttributeValue(i);
                    }
                    if (name != null && rid != null) ridToName.put(rid, name);
                }
            }
            r.close();
        }
        Map<String, String> nameToPath = new LinkedHashMap<>();
        ZipEntry rels = zip.getEntry("xl/_rels/workbook.xml.rels");
        try (InputStream in = zip.getInputStream(rels)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(r.getLocalName())) {
                    String id = r.getAttributeValue(null, "Id");
                    String target = r.getAttributeValue(null, "Target");
                    if (id != null && target != null && ridToName.containsKey(id)) {
                        String path = target.startsWith("xl/") ? target : "xl/" + target.replaceFirst("^/", "");
                        nameToPath.put(ridToName.get(id), path);
                    }
                }
            }
            r.close();
        }
        return nameToPath;
    }

    /**
     * @return linhas de insumo aceitas, ou -1 se a aba não tem o cabeçalho esperado
     */
    private int parseSheet(ZipFile zip, String sheetPath, List<String> shared,
                           Map<String, InsumoConsumo> agregados, Consumer<String> progress,
                           LocalDate inicio, LocalDate fim, boolean filtrarData) throws Exception {
        ZipEntry entry = zip.getEntry(sheetPath);
        if (entry == null) return -1;

        Map<String, Integer> cols = new HashMap<>();
        Map<Integer, String> tipos = new HashMap<>();
        boolean headerDone = false;
        int linhas = 0;
        Map<Integer, String> rowCells = new HashMap<>();

        try (InputStream in = zip.getInputStream(entry)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader r = factory.createXMLStreamReader(in);
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
                            tipos.put(col, cellType);
                        }
                    } else if ("c".equals(local)) {
                        cellRef = null;
                        cellType = null;
                    } else if ("row".equals(local)) {
                        if (!headerDone) {
                            mapHeader(rowCells, cols);
                            headerDone = true;
                            if (!(cols.containsKey("part") && cols.containsKey("qty") && cols.containsKey("vin"))) {
                                r.close();
                                return -1;
                            }
                        } else {
                            InsumoConsumo item = toInsumo(rowCells, tipos, cols, inicio, fim, filtrarData);
                            if (item != null) {
                                acumular(agregados, item);
                                linhas++;
                                if (progress != null && linhas % 100000 == 0) {
                                    progress.accept("Insumos lidos nesta aba: " + linhas);
                                }
                            }
                        }
                        rowCells.clear();
                        tipos.clear();
                    }
                }
            }
            r.close();
        }
        return headerDone ? linhas : -1;
    }

    private void acumular(Map<String, InsumoConsumo> agregados, InsumoConsumo item) {
        String chave = item.getVin().trim() + "\u0000" + item.getPartNumber().trim();
        InsumoConsumo existente = agregados.get(chave);
        if (existente == null) {
            agregados.put(chave, item);
            return;
        }
        existente.setQuantity(existente.getQuantity().add(item.getQuantity()));
        if ((existente.getDescription() == null || existente.getDescription().isBlank())
                && item.getDescription() != null) {
            existente.setDescription(item.getDescription());
        }
    }

    private void mapHeader(Map<Integer, String> cells, Map<String, Integer> cols) {
        for (Map.Entry<Integer, String> e : cells.entrySet()) {
            String h = e.getValue() == null ? "" : e.getValue().trim().toLowerCase(Locale.ROOT);
            int idx = e.getKey();
            if (h.equals("entry date") || h.equals("entrydate")) cols.put("entry", idx);
            else if (h.equals("vin number") || h.equals("vin") || h.equals("vinnumber")) cols.put("vin", idx);
            else if (h.equals("partnumber") || h.equals("part number")) cols.put("part", idx);
            else if (h.equals("description") || h.equals("descricao") || h.equals("descrição")) cols.put("desc", idx);
            else if (h.equals("quantity") || h.equals("qtd") || h.equals("quantidade")) cols.put("qty", idx);
            else if (h.equals("date") || h.equals("data")) cols.put("date", idx);
            else if (h.contains("unit price")) cols.put("unit", idx);
            else if (h.contains("total price") || h.equals("total")) cols.put("total", idx);
        }
    }

    private InsumoConsumo toInsumo(Map<Integer, String> cells, Map<Integer, String> tipos, Map<String, Integer> cols,
                                  LocalDate inicio, LocalDate fim, boolean filtrarData) {
        Integer partIdx = cols.get("part");
        String part = normalizarPartNumber(get(cells, cols, "part"), partIdx == null ? null : tipos.get(partIdx));
        BigDecimal qty = parseDecimal(get(cells, cols, "qty"));
        String vin = get(cells, cols, "vin");
        if (part == null || part.isBlank() || vin == null || vin.isBlank() || qty == null || qty.signum() <= 0) {
            return null;
        }
        InsumoConsumo item = new InsumoConsumo();
        item.setEntryDate(parseDate(get(cells, cols, "entry")));
        item.setVin(vin.trim());
        item.setPartNumber(part);
        item.setDescription(get(cells, cols, "desc"));
        item.setQuantity(qty);
        item.setDate(parseDate(get(cells, cols, "date")));
        if (filtrarData) {
            LocalDate data = item.dataConsumo();
            if (data == null || data.isBefore(inicio) || data.isAfter(fim)) {
                return null;
            }
        }
        item.setUnitPrice(parseDecimal(get(cells, cols, "unit")));
        item.setTotalPrice(parseDecimal(get(cells, cols, "total")));
        return item;
    }

    /**
     * Identifica o mês da aba mesmo quando ele está dividido em partes,
     * como "MOV. PRD - MAIO (1)", "MAIO (2)" ou "SETEMBRO - PARTE 2".
     */
    static Month mesDaAba(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String n = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        n = n.replaceAll("\\(\\s*\\d+\\s*\\)", " ");
        n = n.replaceAll("\\bpart(?:e)?\\s*\\d+\\b", " ");
        n = n.replaceAll("[-_/]\\s*\\d+\\b", " ");
        Month[] meses = Month.values();
        String[] nomes = {
                "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
                "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
        };
        for (int i = 0; i < nomes.length; i++) {
            if (n.matches(".*(?<![a-z])" + nomes[i] + "(?![a-z]).*")) {
                return meses[i];
            }
        }
        return null;
    }

    private static boolean mesNoPeriodo(Month mes, LocalDate inicio, LocalDate fim) {
        YearMonth de = YearMonth.from(inicio);
        YearMonth ate = YearMonth.from(fim);
        for (int ano = inicio.getYear(); ano <= fim.getYear(); ano++) {
            YearMonth ym = YearMonth.of(ano, mes);
            if (!ym.isBefore(de) && !ym.isAfter(ate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizarPartNumber(String text, String cellType) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String valor = text.trim();
        if ("s".equals(cellType) || "inlineStr".equals(cellType) || "str".equals(cellType)) {
            return valor;
        }
        if (valor.matches("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) {
            try {
                return new BigDecimal(valor).stripTrailingZeros().toPlainString();
            } catch (Exception ignored) {
            }
        }
        return valor;
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
