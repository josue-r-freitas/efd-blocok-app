package br.com.triumph.efd.model;

import java.util.ArrayList;
import java.util.List;

public class BlocoKResult {

    private final List<String> linhas = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<InsumoIgnorado> insumosIgnoradosLista = new ArrayList<>();
    private int qtdK230;
    private int qtdK235;
    private int qtdK200;
    private int produtosProcessados;
    private int insumosProcessados;
    private int insumosIgnorados;

    public List<String> getLinhas() {
        return linhas;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public void addAviso(String aviso) {
        avisos.add(aviso);
    }

    public int getQtdK230() {
        return qtdK230;
    }

    public void setQtdK230(int qtdK230) {
        this.qtdK230 = qtdK230;
    }

    public int getQtdK235() {
        return qtdK235;
    }

    public void setQtdK235(int qtdK235) {
        this.qtdK235 = qtdK235;
    }

    public int getQtdK200() {
        return qtdK200;
    }

    public void setQtdK200(int qtdK200) {
        this.qtdK200 = qtdK200;
    }

    public int getProdutosProcessados() {
        return produtosProcessados;
    }

    public void setProdutosProcessados(int produtosProcessados) {
        this.produtosProcessados = produtosProcessados;
    }

    public int getInsumosProcessados() {
        return insumosProcessados;
    }

    public void setInsumosProcessados(int insumosProcessados) {
        this.insumosProcessados = insumosProcessados;
    }

    public int getInsumosIgnorados() {
        return insumosIgnorados;
    }

    public void setInsumosIgnorados(int insumosIgnorados) {
        this.insumosIgnorados = insumosIgnorados;
    }

    public List<InsumoIgnorado> getInsumosIgnoradosLista() {
        return insumosIgnoradosLista;
    }

    public void addInsumoIgnorado(InsumoIgnorado insumo) {
        if (insumo != null) {
            insumo.setNumero(insumosIgnoradosLista.size() + 1);
            insumosIgnoradosLista.add(insumo);
            insumosIgnorados = insumosIgnoradosLista.size();
        }
    }

    public int totalLinhas() {
        return linhas.size();
    }
}
