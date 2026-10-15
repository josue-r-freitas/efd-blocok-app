package br.com.triumph.efd.service;

import br.com.triumph.efd.model.BlocoKResult;
import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.model.Registro0200Result;
import br.com.triumph.efd.util.SpedLineUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EfdMerger {

    private static final String[][] BLOCOS = {
            {"0001", "0990"},
            {"B001", "B990"},
            {"C001", "C990"},
            {"D001", "D990"},
            {"E001", "E990"},
            {"G001", "G990"},
            {"H001", "H990"},
            {"K001", "K990"},
            {"1001", "1990"},
            {"9001", "9990"},
    };

    public List<String> merge(EfdFile efdOriginal, BlocoKResult blocoK, Consumer<String> progress) {
        return merge(efdOriginal, blocoK, null, true, false, progress);
    }

    public List<String> merge(EfdFile efdOriginal,
                              BlocoKResult blocoK,
                              Registro0200Result registros0200,
                              boolean incluirBlocoK,
                              boolean incluir0200,
                              Consumer<String> progress) {

        if (!incluirBlocoK && !incluir0200) {
            throw new IllegalStateException("Selecione ao menos uma opção: incluir Bloco K ou incluir 0200 gerados.");
        }
        if (incluirBlocoK && (blocoK == null || blocoK.getLinhas().isEmpty())) {
            throw new IllegalStateException("Não há Bloco K gerado para incluir no novo EFD.");
        }
        if (incluir0200 && (registros0200 == null || registros0200.isEmpty())) {
            throw new IllegalStateException("Não há registros 0200 gerados para incluir no novo EFD.");
        }

        if (progress != null) {
            progress.accept("Montando novo EFD"
                    + (incluirBlocoK ? " + Bloco K" : "")
                    + (incluir0200 ? " + 0200 gerados" : "")
                    + "...");
        }

        List<String> origem = limparLinhasSped(efdOriginal.getLines(), progress);
        List<String> saida = new ArrayList<>(origem);

        if (incluir0200) {
            saida = inserir0200(saida, efdOriginal, registros0200, progress);
        }

        if (incluirBlocoK) {
            saida = inserirBlocoK(saida, blocoK, progress);
        }

        recalcularTotais(saida, progress);

        if (progress != null) {
            progress.accept("Novo EFD montado com " + saida.size() + " linhas (sem assinatura digital).");
        }
        return saida;
    }

    private List<String> inserir0200(List<String> origem,
                                     EfdFile efd,
                                     Registro0200Result registros0200,
                                     Consumer<String> progress) {
        List<String> novas = new ArrayList<>();

        // Garante unidade UNIT no 0190, se ainda não existir
        boolean precisa0190 = !efd.possuiUnidade0190("UNIT");
        if (precisa0190) {
            novas.add("|0190|UNIT|UNIDADE|");
        }
        novas.addAll(registros0200.getLinhas());

        int insertAt = localizarInsercao0200(origem);
        List<String> saida = new ArrayList<>(origem.size() + novas.size());
        saida.addAll(origem.subList(0, insertAt));
        saida.addAll(novas);
        saida.addAll(origem.subList(insertAt, origem.size()));

        if (progress != null) {
            progress.accept("Inseridos " + registros0200.total() + " registro(s) 0200"
                    + (precisa0190 ? " e 1 registro 0190 (UNIT)" : "")
                    + " no Bloco 0.");
        }
        return saida;
    }

    /**
     * Insere após o último 0200/0205/0220; se não houver, antes do 0990; senão no fim do Bloco 0.
     */
    private int localizarInsercao0200(List<String> linhas) {
        int ultimoItem = -1;
        int idx0990 = -1;
        for (int i = 0; i < linhas.size(); i++) {
            String reg = SpedLineUtils.registro(linhas.get(i));
            if ("0200".equals(reg) || "0205".equals(reg) || "0220".equals(reg) || "0190".equals(reg)) {
                ultimoItem = i;
            }
            if ("0990".equals(reg) && idx0990 < 0) {
                idx0990 = i;
            }
        }
        if (ultimoItem >= 0) {
            return ultimoItem + 1;
        }
        if (idx0990 >= 0) {
            return idx0990;
        }
        return linhas.size();
    }

    private List<String> inserirBlocoK(List<String> origem, BlocoKResult blocoK, Consumer<String> progress) {
        if (progress != null) {
            progress.accept("Mesclando Bloco K no EFD...");
        }

        int idxK001 = -1;
        int idxK990 = -1;
        int idx9001 = -1;

        for (int i = 0; i < origem.size(); i++) {
            String reg = SpedLineUtils.registro(origem.get(i));
            if ("K001".equals(reg) && idxK001 < 0) {
                idxK001 = i;
            }
            if ("K990".equals(reg)) {
                idxK990 = i;
            }
            if ("9001".equals(reg) && idx9001 < 0) {
                idx9001 = i;
            }
        }

        List<String> saida = new ArrayList<>(origem.size() + blocoK.getLinhas().size());
        if (idxK001 >= 0 && idxK990 >= 0) {
            saida.addAll(origem.subList(0, idxK001));
            saida.addAll(blocoK.getLinhas());
            saida.addAll(origem.subList(idxK990 + 1, origem.size()));
        } else if (idx9001 >= 0) {
            saida.addAll(origem.subList(0, idx9001));
            saida.addAll(blocoK.getLinhas());
            saida.addAll(origem.subList(idx9001, origem.size()));
        } else {
            saida.addAll(origem);
            saida.addAll(blocoK.getLinhas());
        }
        return saida;
    }

    private List<String> limparLinhasSped(List<String> linhas, Consumer<String> progress) {
        List<String> limpas = new ArrayList<>(linhas.size());
        int removidas = 0;
        boolean encerrado = false;

        for (String line : linhas) {
            if (encerrado) {
                removidas++;
                continue;
            }
            if (!SpedLineUtils.isLinhaSped(line)) {
                removidas++;
                continue;
            }
            limpas.add(line.trim());
            if (SpedLineUtils.isEncerramentoArquivo(line)) {
                encerrado = true;
            }
        }

        if (removidas > 0 && progress != null) {
            progress.accept("Removidas " + removidas + " linha(s) de assinatura digital ou conteúdo inválido.");
        }
        return limpas;
    }

    private void recalcularTotais(List<String> lines, Consumer<String> progress) {
        if (progress != null) {
            progress.accept("Recalculando totalizadores (blocos, 9900, 9990, 9999)...");
        }

        int corte = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (!SpedLineUtils.isLinhaSped(lines.get(i))) {
                corte = i;
                break;
            }
            if (SpedLineUtils.isEncerramentoArquivo(lines.get(i))) {
                corte = i + 1;
                break;
            }
        }
        if (corte < lines.size()) {
            lines.subList(corte, lines.size()).clear();
        }

        atualizarEncerramentosBloco(lines);
        reconstruirBloco9(lines);
        atualizar9999(lines);

        if (progress != null) {
            progress.accept("Totalizadores atualizados. Registro 9999 = " + lines.size() + " linhas.");
        }
    }

    private void atualizarEncerramentosBloco(List<String> lines) {
        for (String[] bloco : BLOCOS) {
            String abertura = bloco[0];
            String fechamento = bloco[1];
            int idxAbertura = -1;
            int idxFechamento = -1;

            for (int i = 0; i < lines.size(); i++) {
                String reg = SpedLineUtils.registro(lines.get(i));
                if (abertura.equals(reg)) {
                    idxAbertura = i;
                    idxFechamento = -1;
                } else if (idxAbertura >= 0 && fechamento.equals(reg)) {
                    idxFechamento = i;
                }
            }

            if (idxAbertura >= 0 && idxFechamento >= idxAbertura) {
                int qtd = idxFechamento - idxAbertura + 1;
                lines.set(idxFechamento, "|" + fechamento + "|" + qtd + "|");
            }
        }
    }

    private void reconstruirBloco9(List<String> lines) {
        int idx9001 = -1;
        int idx9990 = -1;
        int idx9999 = -1;

        for (int i = 0; i < lines.size(); i++) {
            String reg = SpedLineUtils.registro(lines.get(i));
            if ("9001".equals(reg) && idx9001 < 0) {
                idx9001 = i;
            }
            if ("9990".equals(reg)) {
                idx9990 = i;
            }
            if ("9999".equals(reg)) {
                idx9999 = i;
            }
        }

        if (idx9001 < 0 || idx9990 < 0 || idx9999 < 0) {
            return;
        }

        Map<String, Integer> contagens = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i >= idx9001 && i <= idx9999) {
                continue;
            }
            String reg = SpedLineUtils.registro(lines.get(i));
            if (reg != null) {
                contagens.merge(reg, 1, Integer::sum);
            }
        }

        List<String> novos9900 = new ArrayList<>();
        for (Map.Entry<String, Integer> e : contagens.entrySet()) {
            if (e.getValue() > 0) {
                novos9900.add("|9900|" + e.getKey() + "|" + e.getValue() + "|");
            }
        }

        novos9900.add("|9900|9001|1|");
        novos9900.add("|9900|9900|" + (novos9900.size() + 3) + "|");
        novos9900.add("|9900|9990|1|");
        novos9900.add("|9900|9999|1|");

        List<String> rebuilt = new ArrayList<>();
        rebuilt.addAll(lines.subList(0, idx9001 + 1));
        rebuilt.addAll(novos9900);
        rebuilt.add("|9990|0|");
        rebuilt.add("|9999|0|");
        lines.clear();
        lines.addAll(rebuilt);

        int newIdx9001 = -1;
        int newIdx9990 = -1;
        for (int i = 0; i < lines.size(); i++) {
            String reg = SpedLineUtils.registro(lines.get(i));
            if ("9001".equals(reg)) {
                newIdx9001 = i;
            }
            if ("9990".equals(reg)) {
                newIdx9990 = i;
            }
        }
        if (newIdx9001 >= 0 && newIdx9990 >= newIdx9001) {
            int qtdBloco9 = newIdx9990 - newIdx9001 + 1;
            lines.set(newIdx9990, "|9990|" + qtdBloco9 + "|");
        }
    }

    private void atualizar9999(List<String> lines) {
        int idx9999 = -1;
        for (int i = 0; i < lines.size(); i++) {
            if ("9999".equals(SpedLineUtils.registro(lines.get(i)))) {
                idx9999 = i;
            }
        }
        String linha9999 = "|9999|" + lines.size() + "|";
        if (idx9999 >= 0) {
            lines.set(idx9999, linha9999);
        } else {
            lines.add(linha9999);
        }
    }
}
