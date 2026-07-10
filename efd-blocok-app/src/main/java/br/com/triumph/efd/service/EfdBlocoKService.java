package br.com.triumph.efd.service;

import br.com.triumph.efd.io.EfdReader;
import br.com.triumph.efd.io.InsumoReader;
import br.com.triumph.efd.io.ProdutoAcabadoReader;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoConsumo;
import br.com.triumph.efd.model.ProdutoAcabado;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class EfdBlocoKService {

    private final EfdReader efdReader = new EfdReader();
    private final ProdutoAcabadoReader produtoReader = new ProdutoAcabadoReader();
    private final InsumoReader insumoReader = new InsumoReader();
    private final BlocoKGenerator blocoKGenerator = new BlocoKGenerator();
    private final EfdMerger efdMerger = new EfdMerger();

    private EfdFile efdAtual;
    private List<ProdutoAcabado> produtos;
    private List<InsumoConsumo> insumos;
    private BlocoKResult blocoK;
    private List<String> novoEfd;

    public EfdFile importarEfd(Path path) throws Exception {
        efdAtual = efdReader.read(path);
        return efdAtual;
    }

    public List<ProdutoAcabado> importarProdutos(Path path) throws Exception {
        produtos = produtoReader.read(path);
        return produtos;
    }

    public List<InsumoConsumo> importarInsumos(Path path, Consumer<String> progress) throws Exception {
        insumos = insumoReader.read(path, progress);
        return insumos;
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
        return blocoK;
    }

    public List<String> gerarNovoEfd(Consumer<String> progress) throws Exception {
        if (efdAtual == null) {
            throw new IllegalStateException("Importe o EFD atual primeiro.");
        }
        if (blocoK == null) {
            throw new IllegalStateException("Gere o Bloco K antes de montar o novo EFD.");
        }
        novoEfd = efdMerger.merge(efdAtual, blocoK, progress);
        return novoEfd;
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

    public List<String> getNovoEfd() {
        return novoEfd;
    }
}
