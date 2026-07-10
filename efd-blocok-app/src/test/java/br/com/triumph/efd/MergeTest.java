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

        for (String[] par : new String[][]{{"K001","K990"},{"9001","9990"}}) {
            int ab = -1, fe = -1;
            for (int i = 0; i < novo.size(); i++) {
                String r = SpedLineUtils.registro(novo.get(i));
                if (par[0].equals(r)) { ab = i; fe = -1; }
                else if (ab >= 0 && par[1].equals(r)) fe = i;
            }
            if (ab >= 0 && fe >= 0) {
                String[] f = novo.get(fe).split("\\|", -1);
                System.out.println(par[1] + " declarado=" + f[2] + " real=" + (fe - ab + 1));
            }
        }

        String[] f9999 = novo.get(novo.size()-1).split("\\|", -1);
        System.out.println("9999 declarado=" + f9999[2] + " real=" + novo.size());
    }
}
