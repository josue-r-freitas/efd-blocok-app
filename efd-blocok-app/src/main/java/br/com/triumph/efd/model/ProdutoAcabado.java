package br.com.triumph.efd.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProdutoAcabado {

    private String vin;
    private String fullVin;
    private LocalDate data;
    private String orderRef;
    private String modelRange;
    private String modelCode;
    private String modelDescription;
    private String colour;
    private String load;
    private BigDecimal cost;
    private BigDecimal sales;

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getFullVin() {
        return fullVin;
    }

    public void setFullVin(String fullVin) {
        this.fullVin = fullVin;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public String getModelRange() {
        return modelRange;
    }

    public void setModelRange(String modelRange) {
        this.modelRange = modelRange;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelDescription() {
        return modelDescription;
    }

    public void setModelDescription(String modelDescription) {
        this.modelDescription = modelDescription;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getLoad() {
        return load;
    }

    public void setLoad(String load) {
        this.load = load;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public BigDecimal getSales() {
        return sales;
    }

    public void setSales(BigDecimal sales) {
        this.sales = sales;
    }

    public String chaveProducao() {
        if (load != null && !load.isBlank()) {
            return load.trim();
        }
        if (vin != null && !vin.isBlank()) {
            return vin.trim();
        }
        return fullVin == null ? "" : fullVin.trim();
    }

    public String codigoItem() {
        if (modelCode != null && !modelCode.isBlank()) {
            return modelCode.trim();
        }
        return vin == null ? "" : vin.trim();
    }
}
