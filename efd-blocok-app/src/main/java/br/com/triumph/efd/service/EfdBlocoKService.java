package br.com.triumph.efd.service;

import br.com.triumph.efd.io.EfdReader;
import br.com.triumph.efd.io.InsumoReader;
import br.com.triumph.efd.io.ProdutoAcabadoReader;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.InsumoIgnorado;
import br.com.triumph.efd.model.Item0200SemReferencia;
import br.com.triumph.efd.model.ProdutoAcabado;
import br.com.triumph.efd.model.ProdutoK230Tipo;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EfdBlocoKService {

    private final EfdReader efdReader = new EfdReader();
    private final ProdutoAcabadoReader produtoReader = new ProdutoAcabadoReader();
    private final InsumoReader insumoReader = new InsumoReader();
    private final BlocoKGenerator blocoKGenerator = new BlocoKGenerator();
    private final Registro0200Generator registro0200Generator = new Registro0200Generator();
    private final Registro0200SemReferenciaService semReferenciaService = new Registro0200SemReferenciaService();
    private final EfdMerger efdMerger = new EfdMerger();

    private EfdFile efdAtual;
    private List<ProdutoAcabado> produtos;
    private List<InsumoConsumo> insumos;
    private BlocoKResult blocoK;
    private Registro0200Result registros0200;
    private List<String> novoEfd;
    private boolean ultimoIncluirBlocoK;
    private boolean ultimoIncluir0200;

    public EfdFile importarEfd(Path path) throws Exception {
        efdAtual = efdReader.read(path);
        produtos = null;
        insumos = null;
        blocoK = null;
        registros0200 = null;
        novoEfd = null;
        return efdAtual;
    }

    public List<ProdutoAcabado> importarProdutos(Path path) throws Exception {
        exigirPeriodo();
        produtos = produtoReader.read(path, efdAtual.getDataInicial(), efdAtual.getDataFinal());
        return produtos;
    }

    public List<InsumoConsumo> importarInsumos(Path path, Consumer<String> progress) throws Exception {
        exigirPeriodo();
        insumos = insumoReader.read(path, progress, efdAtual.getDataInicial(), efdAtual.getDataFinal());
        return insumos;
    }

    private void exigirPeriodo() {
        if (efdAtual == null || efdAtual.getDataInicial() == null || efdAtual.getDataFinal() == null) {
            throw new IllegalStateException("Importe o EFD atual antes das planilhas. A leitura usa o período do arquivo.");
        }
    }

    public BlocoKResult gerarBlocoK(boolean preservarK200, Consumer<String> progress) {
        if (efdAtual == null) {
            throw new IllegalStateException("Importe o EFD atual primeiro.");
        }
        if (produtos == null || produtos.isEmpty()) {
            throw new IllegalStateException("Importe a planilha de produtos acabados.");
        }
        if (insumos == null || insumos.isEmpty()) {
            throw new IllegalStateException("Importe a planilha de insumos.");
        }
        blocoK = blocoKGenerator.gerar(efdAtual, produtos, insumos, preservarK200, progress);
        novoEfd = null;
        return blocoK;
    }

    /**
     * Gera registros 0200 a partir dos insumos ignorados do último Bloco K.
     * Os códigos passam a ser reconhecidos pelo EFD em memória.
     */
    public Registro0200Result gerarRegistros0200(Path listagem, Consumer<String> progress) {
        if (efdAtual == null) {
            throw new IllegalStateException("Importe o EFD atual primeiro.");
        }
        if (blocoK == null || blocoK.getInsumosIgnoradosLista().isEmpty()) {
            throw new IllegalStateException("Não há insumos ignorados. Gere o Bloco K primeiro.");
        }

        try {
            registros0200 = registro0200Generator.gerar(
                    blocoK.getInsumosIgnoradosLista(), efdAtual, listagem, codigosProduto(blocoK), progress);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        for (Registro0200Item item : registros0200.getItens()) {
            efdAtual.registrarItem0200(item.getCodItem(), item.getDescrItem());
        }

        novoEfd = null;
        if (progress != null && registros0200.total() > 0) {
            progress.accept("Códigos 0200 registrados em memória. Regenerar o Bloco K para incluir estes insumos no K235.");
        }
        return registros0200;
    }

    private Set<String> codigosProduto(BlocoKResult blocoK) {
        Set<String> codigos = new LinkedHashSet<>();
        if (blocoK == null) {
            return codigos;
        }
        for (String linha : blocoK.getLinhas()) {
            if (linha == null || !linha.startsWith("|K230|")) {
                continue;
            }
            String[] campos = linha.split("\\|", -1);
            if (campos.length > 5 && campos[5] != null && !campos[5].isBlank()) {
                codigos.add(campos[5].trim());
            }
        }
        for (InsumoIgnorado insumo : blocoK.getInsumosIgnoradosLista()) {
            String produto = insumo.getCodItemProduto();
            if (produto != null && !produto.isBlank()) {
                codigos.add(produto.trim());
            }
        }
        return codigos;
    }

    public List<String> gerarNovoEfd(boolean incluirBlocoK, boolean incluir0200, Consumer<String> progress) throws Exception {
        if (efdAtual == null) {
            throw new IllegalStateException("Importe o EFD atual primeiro.");
        }
        if (!incluirBlocoK && !incluir0200) {
            throw new IllegalStateException("Marque ao menos uma opção: Incluir Bloco K ou Incluir 0200 gerados.");
        }
        if (incluirBlocoK && blocoK == null) {
            throw new IllegalStateException("Gere o Bloco K antes de incluí-lo no novo EFD.");
        }
        if (incluir0200 && (registros0200 == null || registros0200.isEmpty())) {
            throw new IllegalStateException("Gere os registros 0200 (aba Insumos ignorados) antes de incluí-los.");
        }

        ultimoIncluirBlocoK = incluirBlocoK;
        ultimoIncluir0200 = incluir0200;
        novoEfd = efdMerger.merge(efdAtual, blocoK, registros0200, incluirBlocoK, incluir0200, progress);
        return novoEfd;
    }

    /** Compatibilidade: inclui Bloco K (comportamento anterior). */
    public List<String> gerarNovoEfd(Consumer<String> progress) throws Exception {
        return gerarNovoEfd(true, registros0200 != null && !registros0200.isEmpty(), progress);
    }

    public void salvarNovoEfd(Path path) throws Exception {
        if (novoEfd == null) {
            throw new IllegalStateException("Gere o novo EFD antes de salvar.");
        }
        efdReader.write(path, novoEfd);
    }

    public void salvarBlocoK(Path path) throws Exception {
        if (blocoK == null) {
            throw new IllegalStateException("Gere o Bloco K antes de salvar.");
        }
        efdReader.write(path, blocoK.getLinhas());
    }

    public void salvarRegistros0200(Path path) throws Exception {
        if (registros0200 == null || registros0200.isEmpty()) {
            throw new IllegalStateException("Gere os registros 0200 antes de salvar.");
        }
        efdReader.write(path, registros0200.getLinhas());
    }

    public EfdFile getEfdAtual() {
        return efdAtual;
    }

    public List<ProdutoAcabado> getProdutos() {
        return produtos;
    }

    public List<InsumoConsumo> getInsumos() {
        return insumos;
    }

    public BlocoKResult getBlocoK() {
        return blocoK;
    }

    public Registro0200Result getRegistros0200() {
        return registros0200;
    }

    public List<Item0200SemReferencia> listar0200SemReferencia() {
        if (novoEfd == null) {
            return List.of();
        }
        return semReferenciaService.listarLinhas(novoEfd, registros0200);
    }

    public int remover0200SemReferencia(List<Item0200SemReferencia> itens) throws Exception {
        if (efdAtual == null || novoEfd == null || itens == null || itens.isEmpty()) {
            return 0;
        }
        Set<String> codigos = itens.stream()
                .map(Item0200SemReferencia::getCodItem)
                .filter(cod -> cod != null && !cod.isBlank())
                .collect(Collectors.toSet());
        int removidos = efdAtual.removerItens0200(codigos);
        if (removidos > 0) {
            novoEfd = efdMerger.merge(efdAtual, blocoK, registros0200, ultimoIncluirBlocoK, ultimoIncluir0200, null);
        }
        return removidos;
    }

    public List<ProdutoK230Tipo> listarProdutosK230TipoDivergente() {
        if (novoEfd == null) {
            return List.of();
        }
        Set<String> produzidos = new LinkedHashSet<>();
        for (String linha : novoEfd) {
            if (linha == null || !linha.startsWith("|K230|")) {
                continue;
            }
            String[] campos = linha.split("\\|", -1);
            if (campos.length > 5 && campos[5] != null && !campos[5].isBlank()) {
                produzidos.add(campos[5].trim());
            }
        }
        Map<String, ProdutoK230Tipo> divergentes = new LinkedHashMap<>();
        for (String linha : novoEfd) {
            if (linha == null || !linha.startsWith("|0200|")) {
                continue;
            }
            String[] campos = linha.split("\\|", -1);
            if (campos.length <= 7) {
                continue;
            }
            String cod = campos[2] == null ? "" : campos[2].trim();
            if (!produzidos.contains(cod) || divergentes.containsKey(cod)) {
                continue;
            }
            String tipo = campos[7] == null ? "" : campos[7].trim();
            if (tipoProduzido(tipo)) {
                continue;
            }
            divergentes.put(cod, new ProdutoK230Tipo(
                    cod,
                    campos.length > 3 && campos[3] != null ? campos[3].trim() : "",
                    campos.length > 6 && campos[6] != null ? campos[6].trim() : "",
                    tipo));
        }
        return new ArrayList<>(divergentes.values());
    }

    public int alterarTipoProdutosK230Para04(List<ProdutoK230Tipo> itens) {
        if (efdAtual == null || itens == null || itens.isEmpty()) {
            return 0;
        }
        Set<String> codigos = itens.stream()
                .map(ProdutoK230Tipo::getCodItem)
                .filter(cod -> cod != null && !cod.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        Set<String> alterados = new LinkedHashSet<>(efdAtual.alterarTipoItem0200(codigos, "04"));
        if (registros0200 != null) {
            for (Registro0200Item item : registros0200.getItens()) {
                String cod = item.getCodItem() == null ? "" : item.getCodItem().trim();
                if (!codigos.contains(cod) || "04".equals(item.getTipoItem() == null ? "" : item.getTipoItem().trim())) {
                    continue;
                }
                item.setTipoItem("04");
                alterados.add(cod);
            }
        }
        if (!alterados.isEmpty()) {
            novoEfd = null;
        }
        return alterados.size();
    }

    private static boolean tipoProduzido(String tipo) {
        if (tipo == null) {
            return false;
        }
        String valor = tipo.trim();
        if (valor.length() == 1 && Character.isDigit(valor.charAt(0))) {
            valor = "0" + valor;
        }
        return "03".equals(valor) || "04".equals(valor);
    }

    public List<String> getNovoEfd() {
        return novoEfd;
    }
}
