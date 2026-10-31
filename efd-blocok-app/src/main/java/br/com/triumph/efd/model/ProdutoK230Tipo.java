package br.com.triumph.efd.model;

/**
 * Produto do K230 cujo 0200 está com TIPO_ITEM diferente de 03 e de 04.
 */
public class ProdutoK230Tipo {

    private final String codItem;
    private final String descricao;
    private final String unidInv;
    private final String tipoItem;

    public ProdutoK230Tipo(String codItem, String descricao, String unidInv, String tipoItem) {
        this.codItem = codItem;
        this.descricao = descricao;
        this.unidInv = unidInv;
        this.tipoItem = tipoItem;
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
}
