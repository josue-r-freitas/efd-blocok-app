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
2. **Produtos Acabados** — planilha `qry_build` (VIN, Model code, Date, Load)
3. **Insumos** — planilha `MOVIMENTO` (Load, VIN Number, PartNumber, Quantity, Date)
4. **Gerar Bloco K** — monta `K001`, `K010`, `K100`, `K200` (opcional), `K230`, `K235`, `K990`
5. **Aba "Bloco K gerado"** — visualizar linhas com filtro por registro, busca e paginação
6. **Exportar** — baixar em **TXT**, **CSV** ou **Excel (.xlsx)** com abas Resumo, K230, K235, K200 e Todas
7. **Gerar Novo EFD** — substitui o Bloco K existente e recalcula `9900` / `9990` / `9999`
8. Salvar os arquivos gerados

## Lógica de geração

| Origem | Registro | Conteúdo |
|--------|----------|----------|
| Produto acabado agrupado por `Load` | **K230** | Ordem de produção, código do modelo (0200), quantidade |
| Insumos do mesmo `Load`/`VIN` | **K235** | PartNumber + Quantity consumida |
| EFD atual (opcional) | **K200** | Estoque escriturado preservado |

- `COD_ITEM` do K230 = `Model code` da planilha (ex.: `CR8`, `LG2`), resolvido contra o registro `0200` do EFD
- `COD_ITEM` do K235 = `PartNumber`, também resolvido no `0200`
- Insumos sem correspondente no `0200` são ignorados e listados no log

## Observações

- A planilha de insumos pode ser muito grande (~1 milhão de linhas); a leitura pode demorar alguns minutos.
- O EFD de origem já possui Bloco K com estoque (`K200`). A opção **Preservar estoque K200** mantém esses registros e adiciona a produção (`K230`/`K235`).
- O arquivo gerado **não é assinado**. A assinatura digital do EFD original (conteúdo após `|9999|`) é **removida automaticamente** na importação e na geração do novo EFD.
- Os totalizadores são recalculados: encerramentos de bloco (`0990`, `C990`, `K990`, etc.), registros `9900`, `9990` e `9999`.
- Valide o arquivo no PVA da EFD ICMS/IPI antes da entrega.
