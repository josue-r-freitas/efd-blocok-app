package br.com.triumph.efd.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class InsumoIgnorado {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private int numero;
    private String codDocOp;
    private String codItemProduto;
    private String descricaoProduto;
    private String partNumber;
    private String descricaoInsumo;
    private BigDecimal quantidade;
    private LocalDate dataConsumo;
    private String motivo;

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getCodDocOp() {
        return codDocOp;
    }

    public void setCodDocOp(String codDocOp) {
        this.codDocOp = codDocOp;
    }

    public String getCodItemProduto() {
        return codItemProduto;
    }

    public void setCodItemProduto(String codItemProduto) {
        this.codItemProduto = codItemProduto;
    }

    public String getDescricaoProduto() {
        return descricaoProduto;
    }

    public void setDescricaoProduto(String descricaoProduto) {
        this.descricaoProduto = descricaoProduto;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getDescricaoInsumo() {
        return descricaoInsumo;
    }

    public void setDescricaoInsumo(String descricaoInsumo) {
        this.descricaoInsumo = descricaoInsumo;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public LocalDate getDataConsumo() {
        return dataConsumo;
    }

    public void setDataConsumo(LocalDate dataConsumo) {
        this.dataConsumo = dataConsumo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String resumo() {
        return String.format("%s | %s | qtd %s",
                nullToEmpty(codDocOp),
                nullToEmpty(partNumber),
                formatQty(quantidade));
    }

    public String linhaTxt() {
        return String.join("\t",
                String.valueOf(numero),
                nullToEmpty(codDocOp),
                nullToEmpty(codItemProduto),
                nullToEmpty(descricaoProduto),
                nullToEmpty(partNumber),
                nullToEmpty(descricaoInsumo),
                formatQty(quantidade),
                formatDate(dataConsumo),
                nullToEmpty(motivo));
    }

    public static String cabecalhoTxt() {
        return String.join("\t",
                "#", "Load/OP", "Cod_Produto", "Desc_Produto",
                "PartNumber", "Desc_Insumo", "Quantidade", "Data", "Motivo");
    }

    public String[] toCsvRow() {
        return new String[]{
                String.valueOf(numero),
                nullToEmpty(codDocOp),
                nullToEmpty(codItemProduto),
                nullToEmpty(descricaoProduto),
                nullToEmpty(partNumber),
                nullToEmpty(descricaoInsumo),
                formatQty(quantidade),
                formatDate(dataConsumo),
                nullToEmpty(motivo)
        };
    }

    public static String[] cabecalhoCsv() {
        return new String[]{
                "#", "Load/OP", "Cod_Produto", "Desc_Produto",
                "PartNumber", "Desc_Insumo", "Quantidade", "Data", "Motivo"
        };
    }

    private static String formatDate(LocalDate d) {
        return d == null ? "" : d.format(DATA_BR);
    }

    private static String formatQty(BigDecimal q) {
        if (q == null) {
            return "0,000";
        }
        return q.setScale(3, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
