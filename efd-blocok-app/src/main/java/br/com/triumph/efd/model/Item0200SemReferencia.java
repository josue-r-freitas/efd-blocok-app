package br.com.triumph.efd.model;

/**
 * Registro 0200 do EFD que não foi cadastrado na última geração e não é citado em nenhum bloco.
 */
public class Item0200SemReferencia {

    private final String codItem;
    private final String descricao;
    private final String unidInv;
    private final String tipoItem;
    private final String codNcm;

    public Item0200SemReferencia(String codItem, String descricao, String unidInv, String tipoItem, String codNcm) {
        this.codItem = codItem;
        this.descricao = descricao;
        this.unidInv = unidInv;
        this.tipoItem = tipoItem;
        this.codNcm = codNcm;
    }

    public String getCodItem() {
        return codItem;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getUnidInv() {
        return unidInv;
    }

    public String getTipoItem() {
        return tipoItem;
    }

    public String getCodNcm() {
        return codNcm;
    }
}
