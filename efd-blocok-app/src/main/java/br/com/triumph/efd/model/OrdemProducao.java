package br.com.triumph.efd.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrdemProducao {

    private String codDocOp;
    private String codItem;
    private String descricaoItem;
    private LocalDate dtIniOp;
    private LocalDate dtFinOp;
    private BigDecimal qtdEnc = BigDecimal.ZERO;
    private final List<String> vins = new ArrayList<>();
    private final Map<String, ConsumoInsumo> insumos = new LinkedHashMap<>();

    public String getCodDocOp() {
        return codDocOp;
    }

    public void setCodDocOp(String codDocOp) {
        this.codDocOp = codDocOp;
    }

    public String getCodItem() {
        return codItem;
    }

    public void setCodItem(String codItem) {
        this.codItem = codItem;
    }

    public String getDescricaoItem() {
        return descricaoItem;
    }

    public void setDescricaoItem(String descricaoItem) {
        this.descricaoItem = descricaoItem;
    }

    public LocalDate getDtIniOp() {
        return dtIniOp;
    }

    public void setDtIniOp(LocalDate dtIniOp) {
        this.dtIniOp = dtIniOp;
    }

    public LocalDate getDtFinOp() {
        return dtFinOp;
    }

    public void setDtFinOp(LocalDate dtFinOp) {
        this.dtFinOp = dtFinOp;
    }

    public BigDecimal getQtdEnc() {
        return qtdEnc;
    }

    public void setQtdEnc(BigDecimal qtdEnc) {
        this.qtdEnc = qtdEnc;
    }

    public void addVin(String vin) {
        if (vin != null && !vin.isBlank() && !vins.contains(vin)) {
            vins.add(vin);
        }
    }

    public List<String> getVins() {
        return vins;
    }

    public Map<String, ConsumoInsumo> getInsumos() {
        return insumos;
    }

    public void addConsumo(String partNumber, String description, BigDecimal qty, LocalDate dtSaida) {
        if (partNumber == null || partNumber.isBlank() || qty == null) {
            return;
        }
        String key = partNumber.trim();
        ConsumoInsumo c = insumos.get(key);
        if (c == null) {
            c = new ConsumoInsumo(key, description, qty, dtSaida);
            insumos.put(key, c);
        } else {
            c.addQuantidade(qty);
            if (dtSaida != null && (c.getDtSaida() == null || dtSaida.isBefore(c.getDtSaida()))) {
                c.setDtSaida(dtSaida);
            }
        }
    }

    public static class ConsumoInsumo {
        private final String partNumber;
        private String description;
        private BigDecimal quantidade;
        private LocalDate dtSaida;

        public ConsumoInsumo(String partNumber, String description, BigDecimal quantidade, LocalDate dtSaida) {
            this.partNumber = partNumber;
            this.description = description;
            this.quantidade = quantidade == null ? BigDecimal.ZERO : quantidade;
            this.dtSaida = dtSaida;
        }

        public String getPartNumber() {
            return partNumber;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getQuantidade() {
            return quantidade;
        }

        public void addQuantidade(BigDecimal qty) {
            if (qty != null) {
                this.quantidade = this.quantidade.add(qty);
            }
        }

        public LocalDate getDtSaida() {
            return dtSaida;
        }

        public void setDtSaida(LocalDate dtSaida) {
            this.dtSaida = dtSaida;
        }
    }
}
