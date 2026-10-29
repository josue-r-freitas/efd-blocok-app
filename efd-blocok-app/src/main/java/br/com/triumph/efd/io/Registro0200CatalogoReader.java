package br.com.triumph.efd.io;

import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lê a listagem de itens PPB e indexa cada linha 0200 pelo COD_ITEM.
 */
public class Registro0200CatalogoReader {

    public static final Path ARQUIVO_PADRAO = Path.of(
            "C:/trabalhos/EFD-blocok/TRIUMPH/Registro 0200_Listagen Itens PPB_TRIUMPH MN.xlsx");

    public Catalogo read(Path path, Consumer<String> progress) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalStateException("Planilha de itens 0200 não encontrada: "
                    + (path == null ? ARQUIVO_PADRAO : path));
        }
        if (progress != null) {
            progress.accept("Lendo listagem 0200: " + path.getFileName());
        }

        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
        Map<String, Registro0200Item> porCodigo = new LinkedHashMap<>();
        // A listagem PPB comprime xl/styles.xml abaixo do limite padrão do POI (0,01).
        ZipSecureFile.setMinInflateRatio(0.001);
        try (InputStream in = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("A planilha de itens 0200 não possui abas.");
            }
            int headerRow = localizarCabecalho(sheet, formatter);
            Map<String, Integer> colunas = mapearColunas(sheet.getRow(headerRow), formatter);
            if (!colunas.containsKey("COD_ITEM")) {
                throw new IllegalArgumentException("A planilha de itens 0200 não tem a coluna COD_ITEM.");
            }
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String codItem = texto(row, colunas.get("COD_ITEM"), formatter);
                if (codItem.isBlank()) {
                    continue;
                }
                String reg = texto(row, colunas.get("REG"), formatter);
                if (!reg.isBlank() && !"0200".equals(reg)) {
                    continue;
                }
                Registro0200Item item = new Registro0200Item();
                item.setCodItem(codItem);
                item.setDescrItem(texto(row, colunas.get("DESCR_ITEM"), formatter));
                item.setCodBarra(texto(row, colunas.get("COD_BARRA"), formatter));
                item.setCodAntItem(texto(row, colunas.get("COD_ANT_ITEM"), formatter));
                item.setUnidInv(texto(row, colunas.get("UNID_INV"), formatter));
                item.setTipoItem(texto(row, colunas.get("TIPO_ITEM"), formatter));
                item.setCodNcm(texto(row, colunas.get("COD_NCM"), formatter));
                item.setExIpi(texto(row, colunas.get("EX_IPI"), formatter));
                item.setCodGen(genero(texto(row, colunas.get("COD_GEN"), formatter), item.getCodNcm()));
                item.setCodLst(texto(row, colunas.get("COD_LST"), formatter));
                item.setAliqIcms(aliquota(texto(row, colunas.get("ALIQ_ICMS"), formatter)));
                item.setCest(texto(row, colunas.get("CEST"), formatter));
                porCodigo.putIfAbsent(codItem, item);
            }
        }

        if (porCodigo.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item 0200 encontrado na listagem: " + path);
        }
        if (progress != null) {
            progress.accept("Listagem 0200: " + porCodigo.size() + " códigos na coluna COD_ITEM.");
        }
        return new Catalogo(path, porCodigo);
    }

    private int localizarCabecalho(Sheet sheet, DataFormatter formatter) {
        int limite = Math.min(sheet.getLastRowNum(), 20);
        for (int i = 0; i <= limite; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.getLastCellNum(); c++) {
                if ("COD_ITEM".equals(normalizarCabecalho(formatter.formatCellValue(row.getCell(c))))) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Cabeçalho COD_ITEM não encontrado na planilha de itens 0200.");
    }

    private Map<String, Integer> mapearColunas(Row header, DataFormatter formatter) {
        Map<String, Integer> colunas = new LinkedHashMap<>();
        if (header == null) {
            return colunas;
        }
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String nome = normalizarCabecalho(formatter.formatCellValue(header.getCell(c)));
            if (!nome.isBlank()) {
                colunas.putIfAbsent(nome, c);
            }
        }
        return colunas;
    }

    private String texto(Row row, Integer coluna, DataFormatter formatter) {
        if (row == null || coluna == null || coluna < 0) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(coluna)).trim();
    }

    /**
     * A listagem deixa COD_GEN em branco. No 0200 esse campo são os dois primeiros dígitos do NCM.
     */
    private String genero(String codGen, String codNcm) {
        if (codGen != null && !codGen.isBlank()) {
            return codGen.trim();
        }
        if (codNcm == null) {
            return "";
        }
        String ncm = codNcm.trim();
        if (ncm.length() >= 2 && Character.isDigit(ncm.charAt(0)) && Character.isDigit(ncm.charAt(1))) {
            return ncm.substring(0, 2);
        }
        return "";
    }

    private String aliquota(String valor) {
        if (valor == null || valor.isBlank()) {
            return "0";
        }
        return valor.trim().replace('.', ',');
    }

    private String normalizarCabecalho(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    public static final class Catalogo {
        private final Path arquivo;
        private final Map<String, Registro0200Item> porCodigo;

        private Catalogo(Path arquivo, Map<String, Registro0200Item> porCodigo) {
            this.arquivo = arquivo;
            this.porCodigo = porCodigo;
        }

        public Path getArquivo() {
            return arquivo;
        }

        public int tamanho() {
            return porCodigo.size();
        }

        public Registro0200Item buscar(String partNumber) {
            if (partNumber == null || partNumber.isBlank()) {
                return null;
            }
            String codigo = partNumber.trim();
            Registro0200Item item = porCodigo.get(codigo);
            if (item != null) {
                return item;
            }
            return porCodigo.get(codigo.toUpperCase(Locale.ROOT));
        }
    }
}
