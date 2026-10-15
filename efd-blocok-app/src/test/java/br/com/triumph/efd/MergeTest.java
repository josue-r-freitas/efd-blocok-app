package br.com.triumph.efd;

import br.com.triumph.efd.io.EfdReader;
import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.service.EfdMerger;
import br.com.triumph.efd.util.SpedLineUtils;

import java.nio.file.Path;
import java.util.List;

public class MergeTest {
    public static void main(String[] args) throws Exception {
        Path efdPath = Path.of("c:/trabalhos/EFD-blocok/SpedEFD-14808074000163-062008994-Remessa de arquivo original-102025.TXT");
        var efd = new EfdReader().read(efdPath);
        System.out.println("Importado: " + efd.size() + " linhas SPED");
        System.out.println("Ultima linha: " + efd.getLines().get(efd.size() - 1));

        // fake minimal bloco K for merge test
        BlocoKResult k = new BlocoKResult();
        k.getLinhas().add("|K001|0|");
        k.getLinhas().add("|K010|0|");
        k.getLinhas().add("|K100|01102025|31102025|");
        k.getLinhas().add("|K230|01102025|31102025|OPTEST|CR8|1,000|");
        k.getLinhas().add("|K235|31102025|1100407|1,000||");
        k.getLinhas().add("|K990|6|");
        k.setQtdK230(1);
        k.setQtdK235(1);

        List<String> novo = new EfdMerger().merge(efd, k, System.out::println);
        System.out.println("Novo EFD: " + novo.size() + " linhas");
        System.out.println("Ultima: " + novo.get(novo.size() - 1));

        long lixo = novo.stream().filter(SpedLineUtils::isAssinaturaOuLixo).count();
        System.out.println("Linhas invalidas: " + lixo);

        conferirFechamento(novo, "0990", "0000", "0990");
        conferirFechamento(novo, "K990", "K001", "K990");
        int idx9001 = indice(novo, "9001");
        int idx9990 = indice(novo, "9990");
        int idx9999 = indice(novo, "9999");
        int qtdBloco9 = idx9999 - idx9001 + 1;
        int declarado9990 = quantidade(novo.get(idx9990));
        System.out.println("9990 declarado=" + declarado9990 + " real=" + qtdBloco9);
        if (declarado9990 != qtdBloco9) {
            throw new IllegalStateException("9990 deveria contar do 9001 ao 9999: declarado="
                    + declarado9990 + " real=" + qtdBloco9);
        }

        String[] f9999 = novo.get(novo.size() - 1).split("\\|", -1);
        System.out.println("9999 declarado=" + f9999[2] + " real=" + novo.size());
        if (!String.valueOf(novo.size()).equals(f9999[2])) {
            throw new IllegalStateException("9999 declarado=" + f9999[2] + " real=" + novo.size());
        }
    }

    private static void conferirFechamento(List<String> linhas, String registro, String abertura, String fechamento) {
        int ab = indice(linhas, abertura);
        int fe = indice(linhas, fechamento);
        int real = fe - ab + 1;
        int declarado = quantidade(linhas.get(fe));
        System.out.println(registro + " declarado=" + declarado + " real=" + real);
        if (declarado != real) {
            throw new IllegalStateException(registro + " declarado=" + declarado + " real=" + real);
        }
    }

    private static int indice(List<String> linhas, String registro) {
        int idx = -1;
        for (int i = 0; i < linhas.size(); i++) {
            if (registro.equals(SpedLineUtils.registro(linhas.get(i)))) {
                idx = i;
            }
        }
        if (idx < 0) {
            throw new IllegalStateException("Registro não encontrado: " + registro);
        }
        return idx;
    }

    private static int quantidade(String linha) {
        String[] campos = linha.split("\\|", -1);
        return Integer.parseInt(campos[2]);
    }
}
