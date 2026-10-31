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

    /**
     * Remove os registros 0200 informados e os filhos ligados a cada um (0205, 0206, 0210, 0220 e 0221).
     *
     * @return quantidade de registros 0200 removidos
     */
    public int removerItens0200(Set<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return 0;
        }
        Set<String> filhos = Set.of("0205", "0206", "0210", "0220", "0221");
        List<String> restantes = new ArrayList<>(lines.size());
        int removidos = 0;
        boolean removendoFilhos = false;
        for (String line : lines) {
            String[] campos = line.split("\\|", -1);
            String reg = campos.length > 1 ? campos[1] : "";
            if ("0200".equals(reg)) {
                String cod = campos.length > 2 && campos[2] != null ? campos[2].trim() : "";
                if (codigos.contains(cod)) {
                    removendoFilhos = true;
                    removidos++;
                    codigos0200.remove(cod);
                    descricao0200.remove(cod);
                    continue;
                }
                removendoFilhos = false;
            } else if (removendoFilhos && filhos.contains(reg)) {
                continue;
            } else {
                removendoFilhos = false;
            }
            restantes.add(line);
        }
        lines.clear();
        lines.addAll(restantes);
        return removidos;
    }

    /**
     * Troca o TIPO_ITEM dos registros 0200 informados.
     *
     * @return códigos cuja linha foi alterada
     */
    public Set<String> alterarTipoItem0200(Set<String> codigos, String novoTipo) {
        Set<String> alterados = new LinkedHashSet<>();
        if (codigos == null || codigos.isEmpty() || novoTipo == null || novoTipo.isBlank()) {
            return alterados;
        }
        String tipo = novoTipo.trim();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("|0200|")) {
                continue;
            }
            String[] campos = line.split("\\|", -1);
            if (campos.length <= 7) {
                continue;
            }
            String cod = campos[2] == null ? "" : campos[2].trim();
            if (!codigos.contains(cod)) {
                continue;
            }
            String atual = campos[7] == null ? "" : campos[7].trim();
            if (tipo.equals(atual)) {
                continue;
            }
            campos[7] = tipo;
            lines.set(i, String.join("|", campos));
            alterados.add(cod);
        }
        return alterados;
    }
}
