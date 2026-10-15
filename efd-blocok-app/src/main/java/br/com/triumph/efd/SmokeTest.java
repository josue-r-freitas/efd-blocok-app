package br.com.triumph.efd;

import br.com.triumph.efd.service.EfdBlocoKService;

import java.nio.file.Path;

/**
 * Smoke test sem interface gráfica.
 * Uso: java -cp ... br.com.triumph.efd.SmokeTest [efd] [produtos.xlsx] [insumos.xlsx] [saida.txt]
 */
public class SmokeTest {

    public static void main(String[] args) throws Exception {
        Path efd = Path.of(args.length > 0 ? args[0]
                : "c:/trabalhos/EFD-blocok/SpedEFD-14808074000163-062008994-Remessa de arquivo original-102025.TXT");
        Path produtos = Path.of(args.length > 1 ? args[1]
                : "c:/trabalhos/EFD-blocok/qry_build BR_ PROJETO icmsxlsx.xlsx");
        Path insumos = Path.of(args.length > 2 ? args[2]
                : "c:/trabalhos/EFD-blocok/MOVIMENTO OUTUBRO_2025.xlsx");
        Path saida = Path.of(args.length > 3 ? args[3]
                : "c:/trabalhos/EFD-blocok/SpedEFD_com_BlocoK_GERADO.txt");

        EfdBlocoKService service = new EfdBlocoKService();
        System.out.println("Importando EFD...");
        var e = service.importarEfd(efd);
        System.out.println("Empresa: " + e.getNomeEmpresa() + " | 0200: " + e.getCodigos0200().size());

        System.out.println("Importando produtos...");
        var p = service.importarProdutos(produtos);
        System.out.println("Produtos: " + p.size());

        System.out.println("Importando insumos (pode demorar)...");
        var i = service.importarInsumos(insumos, System.out::println);
        System.out.println("Insumos: " + i.size());

        System.out.println("Gerando Bloco K...");
        var k = service.gerarBlocoK(true, System.out::println);
        System.out.println("K230=" + k.getQtdK230() + " K235=" + k.getQtdK235() + " linhas=" + k.totalLinhas());

        System.out.println("Gerando novo EFD...");
        var novo = service.gerarNovoEfd(true, false, System.out::println);
        service.salvarNovoEfd(saida);
        service.salvarBlocoK(Path.of(saida.toString().replace(".txt", "_apenas_blocoK.txt")));
        System.out.println("Salvo: " + saida + " (" + novo.size() + " linhas)");
    }
}
