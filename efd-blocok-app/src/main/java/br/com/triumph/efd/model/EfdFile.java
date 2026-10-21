package br.com.triumph.efd.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EfdFile {

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final List<String> lines = new ArrayList<>();
    private LocalDate dataInicial;
    private LocalDate dataFinal;
    private String nomeEmpresa;
    private String cnpj;
    private final Set<String> codigos0200 = new LinkedHashSet<>();
    private final Map<String, String> descricao0200 = new LinkedHashMap<>();

    public void addLine(String line) {
        lines.add(line);
        parseMetadata(line);
    }

    private void parseMetadata(String line) {
        if (!line.startsWith("|")) {
            return;
        }
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            return;
        }
        String reg = f[1];
        if ("0000".equals(reg) && f.length > 8) {
            dataInicial = parseDate(f[4]);
            dataFinal = parseDate(f[5]);
            nomeEmpresa = f[6];
            cnpj = f[7];
        } else if ("0200".equals(reg) && f.length > 3) {
            String cod = f[2] == null ? "" : f[2].trim();
            if (!cod.isEmpty()) {
                codigos0200.add(cod);
                descricao0200.put(cod, f[3] == null ? "" : f[3].trim());
            }
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim(), DDMMAAAA);
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public LocalDate getDataInicial() {
        return dataInicial;
    }

    public LocalDate getDataFinal() {
        return dataFinal;
    }

    public String getNomeEmpresa() {
        return nomeEmpresa;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Set<String> getCodigos0200() {
        return Collections.unmodifiableSet(codigos0200);
    }

    public Map<String, String> getDescricao0200() {
        return Collections.unmodifiableMap(descricao0200);
    }

    public boolean containsItem(String codigo) {
        return resolveCodigo0200(codigo) != null;
    }

    public String resolveCodigo0200(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String raw = codigo.trim();
        return codigos0200.contains(raw) ? raw : null;
    }

    /**
     * Registra um item 0200 em memória (ex.: gerado a partir de insumos ignorados),
     * para que a regeneração do Bloco K possa resolvê-lo.
     */
    public void registrarItem0200(String codigo, String descricao) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        String cod = codigo.trim();
        codigos0200.add(cod);
        descricao0200.put(cod, descricao == null ? "" : descricao.trim());
    }

    public boolean possuiUnidade0190(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            return false;
        }
        String alvo = unidade.trim();
        for (String line : lines) {
            if (!line.startsWith("|0190|")) {
                continue;
            }
            String[] f = line.split("\\|", -1);
            if (f.length > 2 && alvo.equalsIgnoreCase(f[2] == null ? "" : f[2].trim())) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return lines.size();
    }
}
