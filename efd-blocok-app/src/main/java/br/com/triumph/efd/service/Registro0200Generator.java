package br.com.triumph.efd.service;

import br.com.triumph.efd.io.Registro0200CatalogoReader;
import br.com.triumph.efd.io.Registro0200CatalogoReader.Catalogo;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.InsumoIgnorado;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.model.Registro0200Result.Registro0200Item;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gera registros 0200 dos insumos ignorados e dos produtos do K230
 * que ainda não existem no EFD, com os dados da listagem PPB (coluna COD_ITEM).
 */
public class Registro0200Generator {

    private final Registro0200CatalogoReader catalogoReader = new Registro0200CatalogoReader();

    public Registro0200Result gerar(List<InsumoIgnorado> ignorados,
                                    EfdFile efd,
                                    Path listagem,
                                    Consumer<String> progress) throws Exception {
        return gerar(ignorados, efd, listagem, List.of(), progress);
    }

    public Registro0200Result gerar(List<InsumoIgnorado> ignorados,
                                    EfdFile efd,
                                    Path listagem,
                                    Collection<String> codigosProduto,
                                    Consumer<String> progress) throws Exception {
        if (ignorados == null || ignorados.isEmpty()) {
            throw new IllegalStateException("Não há insumos ignorados para gerar registros 0200.");
        }
        if (listagem == null) {
            throw new IllegalStateException("Informe a planilha da listagem 0200.");
        }

        Catalogo catalogo = catalogoReader.read(listagem, progress);

        Map<String, InsumoIgnorado> unicos = new LinkedHashMap<>();
        for (InsumoIgnorado i : ignorados) {
            String part = normalizarCodigo(i.getPartNumber());
            if (part == null) {
                continue;
            }
            if (efd != null && efd.resolveCodigo0200(part) != null) {
                continue;
            }
            unicos.putIfAbsent(part, i);
        }

        if (progress != null) {
            progress.accept("Buscando " + unicos.size() + " PartNumber(s) na coluna COD_ITEM...");
        }

        Registro0200Result result = new Registro0200Result();
        for (Map.Entry<String, InsumoIgnorado> e : unicos.entrySet()) {
            Registro0200Item origem = catalogo.buscar(e.getKey());
            if (origem == null) {
                result.addNaoEncontrado(e.getKey());
                continue;
            }
            Registro0200Item item = copiar(origem);
            item.setCodItem(e.getKey());
            item.setOrigem(origemInsumo(e.getValue()));
            result.addItem(item);
        }

        incluirProdutos(codigosProduto, efd, catalogo, result, progress);

        if (progress != null) {
            progress.accept("Registros 0200 gerados: " + result.total()
                    + " (de " + unicos.size() + " PartNumbers).");
            if (!result.getProdutosGerados().isEmpty()) {
                progress.accept("Produtos incluídos no 0200: " + String.join(", ", result.getProdutosGerados()));
            }
            if (!result.getNaoEncontrados().isEmpty()) {
                progress.accept("Sem correspondência na coluna COD_ITEM: "
                        + result.getNaoEncontrados().size() + " PartNumber(s).");
            }
            if (!result.getProdutosNaoEncontrados().isEmpty()) {
                progress.accept("Produtos sem correspondência na coluna COD_ITEM: "
                        + String.join(", ", result.getProdutosNaoEncontrados()));
            }
        }
        return result;
    }

    private void incluirProdutos(Collection<String> codigosProduto,
                                 EfdFile efd,
                                 Catalogo catalogo,
                                 Registro0200Result result,
                                 Consumer<String> progress) {
        Set<String> produtos = new LinkedHashSet<>();
        if (codigosProduto != null) {
            for (String codigo : codigosProduto) {
                String part = normalizarCodigo(codigo);
                if (part != null) {
                    produtos.add(part);
                }
            }
        }
        if (produtos.isEmpty()) {
            return;
        }
        if (progress != null) {
            progress.accept("Conferindo " + produtos.size() + " produto(s) do K230 na coluna COD_ITEM...");
        }
        for (String codigo : produtos) {
            if (efd != null && efd.resolveCodigo0200(codigo) != null) {
                continue;
            }
            if (result.getItens().stream().anyMatch(item -> codigo.equals(item.getCodItem()))) {
                continue;
            }
            Registro0200Item origem = catalogo.buscar(codigo);
            if (origem == null) {
                result.addProdutoNaoEncontrado(codigo);
                continue;
            }
            Registro0200Item item = copiar(origem);
            item.setCodItem(codigo);
            item.setOrigem("produto=" + codigo);
            result.addItem(item);
            result.addProdutoGerado(codigo);
        }
    }

    private Registro0200Item copiar(Registro0200Item origem) {
        Registro0200Item item = new Registro0200Item();
        item.setCodItem(origem.getCodItem());
        item.setDescrItem(origem.getDescrItem());
        item.setCodBarra(origem.getCodBarra());
        item.setCodAntItem(origem.getCodAntItem());
        item.setUnidInv(origem.getUnidInv());
        item.setTipoItem(origem.getTipoItem());
        item.setCodNcm(origem.getCodNcm());
        item.setExIpi(origem.getExIpi());
        item.setCodGen(origem.getCodGen());
        item.setCodLst(origem.getCodLst());
        item.setAliqIcms(origem.getAliqIcms());
        item.setCest(origem.getCest());
        return item;
    }

    private String origemInsumo(InsumoIgnorado i) {
        StringBuilder sb = new StringBuilder();
        if (i.getCodDocOp() != null && !i.getCodDocOp().isBlank()) {
            sb.append("OP=").append(i.getCodDocOp());
        }
        if (i.getPartNumber() != null) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append("PN=").append(i.getPartNumber());
        }
        return sb.toString();
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return codigo.trim();
    }
}
