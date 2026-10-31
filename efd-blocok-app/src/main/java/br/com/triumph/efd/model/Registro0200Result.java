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
    private final List<String> naoEncontrados = new ArrayList<>();
    private final List<String> produtosGerados = new ArrayList<>();
    private final List<String> produtosNaoEncontrados = new ArrayList<>();

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

    public List<String> getNaoEncontrados() {
        return naoEncontrados;
    }

    public void addNaoEncontrado(String partNumber) {
        if (partNumber == null || partNumber.isBlank()) {
            return;
        }
        String codigo = partNumber.trim();
        if (!naoEncontrados.contains(codigo)) {
            naoEncontrados.add(codigo);
        }
    }

    public List<String> getProdutosGerados() {
        return produtosGerados;
    }

    public void addProdutoGerado(String codigo) {
        adicionarUnico(produtosGerados, codigo);
    }

    public List<String> getProdutosNaoEncontrados() {
        return produtosNaoEncontrados;
    }

    public void addProdutoNaoEncontrado(String codigo) {
        adicionarUnico(produtosNaoEncontrados, codigo);
    }

    private static void adicionarUnico(List<String> lista, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        String valor = codigo.trim();
        if (!lista.contains(valor)) {
            lista.add(valor);
        }
    }

    public static class Registro0200Item {
        private int numero;
        private String codItem;
        private String descrItem;
        private String unidInv = "UNIT";
        private String tipoItem = "01";
        private String codBarra = "";
        private String codAntItem = "";
        private String codNcm = "";
        private String exIpi = "";
        private String codGen = "";
        private String codLst = "";
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

        public String getCodBarra() {
            return codBarra;
        }

        public void setCodBarra(String codBarra) {
            this.codBarra = codBarra;
        }

        public String getCodAntItem() {
            return codAntItem;
        }

        public void setCodAntItem(String codAntItem) {
            this.codAntItem = codAntItem;
        }

        public String getExIpi() {
            return exIpi;
        }

        public void setExIpi(String exIpi) {
            this.exIpi = exIpi;
        }

        public String getCodLst() {
            return codLst;
        }

        public void setCodLst(String codLst) {
            this.codLst = codLst;
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
                    + nullToEmpty(codBarra) + "|"
                    + nullToEmpty(codAntItem) + "|"
                    + nullToEmpty(unidInv) + "|"
                    + nullToEmpty(tipoItem) + "|"
                    + nullToEmpty(codNcm) + "|"
                    + nullToEmpty(exIpi) + "|"
                    + nullToEmpty(codGen) + "|"
                    + nullToEmpty(codLst) + "|"
                    + nullToEmpty(aliqIcms) + "|"
                    + nullToEmpty(cest) + "|";
        }

        public String[] toCsvRow() {
            return new String[]{
                    String.valueOf(numero),
                    nullToEmpty(codItem),
                    nullToEmpty(descrItem),
                    nullToEmpty(codBarra),
                    nullToEmpty(codAntItem),
                    nullToEmpty(unidInv),
                    nullToEmpty(tipoItem),
                    nullToEmpty(codNcm),
                    nullToEmpty(exIpi),
                    nullToEmpty(codGen),
                    nullToEmpty(codLst),
                    nullToEmpty(aliqIcms),
                    nullToEmpty(cest),
                    nullToEmpty(origem)
            };
        }

        public static String[] cabecalhoCsv() {
            return new String[]{
                    "#", "COD_ITEM", "DESCR_ITEM", "COD_BARRA", "COD_ANT_ITEM",
                    "UNID_INV", "TIPO_ITEM", "COD_NCM", "EX_IPI", "COD_GEN",
                    "COD_LST", "ALIQ_ICMS", "CEST", "Origem"
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
