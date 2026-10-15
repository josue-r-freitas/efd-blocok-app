# EFD Bloco K — Aplicação Desktop

Aplicação Java (Swing) para:

1. Importar um EFD ICMS/IPI atual
2. Gerar o **Bloco K** a partir de duas planilhas (produto acabado + insumos)
3. Gerar um **novo EFD** com o Bloco K inserido/substituído

## Requisitos

- Java 17+
- Maven 3.9+

## Como compilar

```bash
cd efd-blocok-app
mvn clean package
```

O JAR executável será gerado em:

`target/efd-blocok-app-1.0.0.jar`

## Como executar

```bash
java -jar target/efd-blocok-app-1.0.0.jar
```

Ou:

```bash
mvn -q exec:java -Dexec.mainClass=br.com.triumph.efd.ui.MainApp
```

## Fluxo de uso

1. **Importar EFD** — arquivo `.txt` SPED (ex.: `SpedEFD-...102025.TXT`)
2. **Produtos Acabados** — aba geral `qry_build` (`VIN`, `Model code`, `Date`)
3. **Insumos** — planilha com uma ou mais abas (`VIN NUMBER`, `Part Number`, `Quantity`)
4. **Gerar Bloco K** — monta `K001`, `K010`, `K100`, `K200` (opcional), `K230`, `K235`, `K990`
5. **Aba "Bloco K gerado"** — visualizar linhas com filtro por registro, busca e paginação. O botão **Relatório de validação** gera uma planilha para conferir K230 e K235 com os produtos e insumos do período
6. **Exportar** — baixar em **TXT**, **CSV** ou **Excel (.xlsx)** com abas Resumo, K230, K235, K200 e Todas
7. **Aba "Insumos ignorados"** — visualizar, exportar e **gerar registros 0200** a partir dos PartNumbers sem cadastro
8. **Aba "0200 gerados"** — visualizar e exportar os 0200 criados (opcional regenerar Bloco K para incluir no K235)
9. **Gerar Novo EFD** — opções (marcadas por padrão):
   - Incluir Bloco K (se gerado)
   - Incluir 0200 gerados (se gerados)
10. Salvar os arquivos gerados

## Lógica de geração

| Origem | Registro | Conteúdo |
|--------|----------|----------|
| Produtos acabados do período, agrupados por `Model code` | **K230** | `COD_DOC_OP` e `COD_ITEM` = `Model code`; quantidade = linhas desse código no período |
| Insumos cujo `VIN NUMBER` é igual ao `VIN` desses produtos, somados por `Part Number` | **K235** | menor data desses VINs, `Part Number` e quantidade somada |
| EFD atual (opcional) | **K200** | Estoque escriturado preservado |

- O vínculo entre insumo e produto usa somente `VIN NUMBER` = `VIN`. `Load` e `Full VIN` não entram nessa associação.
- A leitura usa o período do EFD importado. Produtos saem da aba geral `qry_build`, só com `Date` nesse intervalo. Insumos entram de todas as abas daquele mês. Se o mês estiver dividido, como `MAIO (1)`, `MAIO (2)` e `MAIO (3)`, todas são lidas e as quantidades do mesmo VIN e Part Number são somadas.
- `COD_DOC_OP` e `COD_ITEM` do K230 são o `Model code` da planilha (ex.: `CN7`).
- `DT_INI_OP` é a menor `Date` desse modelo no período do EFD, e `DT_FIN_OP` a maior. `QTD_ENC` é a quantidade de linhas. Em janeiro de 2023, `CN7` fica de 02/01/2023 a 13/01/2023, com quantidade 115.
- Cada K235 do K230 agrupa um `Part Number` dos VINs daquele `Model code`. `DT_SAIDA` é a menor `Date` desses VINs no período. `COD_ITEM` é o `Part Number`. `QTD` é a soma da coluna `Quantity`, com 6 casas decimais. `COD_INS_SUBST` fica vazio.
- Part Numbers ausentes no `0200` entram no K235 com o código da planilha e continuam listados para geração do 0200.

## Observações

- A planilha de insumos pode ser muito grande (vários milhões de linhas, em várias abas); a leitura pode demorar alguns minutos. As quantidades do mesmo `VIN` e `Part Number` são somadas na leitura.
- O EFD de origem já possui Bloco K com estoque (`K200`). A opção **Preservar estoque K200** mantém esses registros e adiciona a produção (`K230`/`K235`).
- O arquivo gerado **não é assinado**. A assinatura digital do EFD original (conteúdo após `|9999|`) é **removida automaticamente** na importação e na geração do novo EFD.
- Os totalizadores são recalculados: encerramentos de bloco (`0990`, `C990`, `K990`, etc.), registros `9900`, `9990` e `9999`.
- Valide o arquivo no PVA da EFD ICMS/IPI antes da entrega.
