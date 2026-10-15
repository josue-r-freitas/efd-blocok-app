package br.com.triumph.efd.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado da geração de registros 0200 a partir de insumos ignorados.
 */
public class Registro0200Result {

    private final List<String> linhas = new ArrayList<>();
    private final List<Registro0200Item> itens = new ArrayList<>();
    private final Map<String, Registro0200Item> porCodigo = new LinkedHashMap<>();

    public List<String> getLinhas() {
        return linhas;
    }

    public List<Registro0200Item> getItens() {
        return itens;
    }

    public void addItem(Registro0200Item item) {
        if (item == null || item.getCodItem() == null || item.getCodItem().isBlank()) {
            return;
        }
        String key = item.getCodItem().trim();
        if (porCodigo.containsKey(key)) {
            return;
        }
        item.setNumero(itens.size() + 1);
        porCodigo.put(key, item);
        itens.add(item);
        linhas.add(item.toSpedLine());
    }

    public int total() {
        return itens.size();
    }

    public boolean isEmpty() {
        return itens.isEmpty();
    }

    public static class Registro0200Item {
        private int numero;
        private String codItem;
        private String descrItem;
        private String unidInv = "UNIT";
        private String tipoItem = "01";
        private String codNcm = "";
        private String codGen = "";
        private String aliqIcms = "0";
        private String cest = "";
        private String origem; // PartNumber original / Load

        public int getNumero() {
            return numero;
        }

        public void setNumero(int numero) {
            this.numero = numero;
        }

        public String getCodItem() {
            return codItem;
        }

        public void setCodItem(String codItem) {
            this.codItem = codItem;
        }

        public String getDescrItem() {
            return descrItem;
        }

        public void setDescrItem(String descrItem) {
            this.descrItem = descrItem;
        }

        public String getUnidInv() {
            return unidInv;
        }

        public void setUnidInv(String unidInv) {
            this.unidInv = unidInv;
        }

        public String getTipoItem() {
            return tipoItem;
        }

        public void setTipoItem(String tipoItem) {
            this.tipoItem = tipoItem;
        }

        public String getCodNcm() {
            return codNcm;
        }

        public void setCodNcm(String codNcm) {
            this.codNcm = codNcm;
        }

        public String getCodGen() {
            return codGen;
        }

        public void setCodGen(String codGen) {
            this.codGen = codGen;
        }

        public String getAliqIcms() {
            return aliqIcms;
        }

        public void setAliqIcms(String aliqIcms) {
            this.aliqIcms = aliqIcms;
        }

        public String getCest() {
            return cest;
        }

        public void setCest(String cest) {
            this.cest = cest;
        }

        public String getOrigem() {
            return origem;
        }

        public void setOrigem(String origem) {
            this.origem = origem;
        }

        public String toSpedLine() {
            // |0200|COD_ITEM|DESCR_ITEM|COD_BARRA|COD_ANT_ITEM|UNID_INV|TIPO_ITEM|COD_NCM|EX_IPI|COD_GEN|COD_LST|ALIQ_ICMS|CEST|
            return "|0200|"
                    + nullToEmpty(codItem) + "|"
                    + truncar(nullToEmpty(descrItem), 255) + "|"
                    + "|" // COD_BARRA
                    + "|" // COD_ANT_ITEM
                    + nullToEmpty(unidInv) + "|"
                    + nullToEmpty(tipoItem) + "|"
                    + nullToEmpty(codNcm) + "|"
                    + "|" // EX_IPI
                    + nullToEmpty(codGen) + "|"
                    + "|" // COD_LST
                    + nullToEmpty(aliqIcms) + "|"
                    + nullToEmpty(cest) + "|";
        }

        public String[] toCsvRow() {
            return new String[]{
                    String.valueOf(numero),
                    nullToEmpty(codItem),
                    nullToEmpty(descrItem),
                    nullToEmpty(unidInv),
                    nullToEmpty(tipoItem),
                    nullToEmpty(codNcm),
                    nullToEmpty(codGen),
                    nullToEmpty(aliqIcms),
                    nullToEmpty(cest),
                    nullToEmpty(origem)
            };
        }

        public static String[] cabecalhoCsv() {
            return new String[]{
                    "#", "COD_ITEM", "DESCR_ITEM", "UNID_INV", "TIPO_ITEM",
                    "COD_NCM", "COD_GEN", "ALIQ_ICMS", "CEST", "Origem"
            };
        }

        public String linhaTxt() {
            return String.join("\t", toCsvRow());
        }

        public static String cabecalhoTxt() {
            return String.join("\t", cabecalhoCsv());
        }

        private static String truncar(String s, int max) {
            if (s.length() <= max) {
                return s;
            }
            return s.substring(0, max);
        }

        private static String nullToEmpty(String s) {
            return s == null ? "" : s;
        }
    }
}
