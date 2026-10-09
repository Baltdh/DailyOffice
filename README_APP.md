# DailyOffice — contador MEI pessoal

Aplicativo Android local-first para fotografar e arquivar comprovantes, extrair dados por OCR e organizar finanças pessoais e empresariais.

## Estado atual — v0.10.0

O DailyOffice já possui:

- câmera integrada para fotografar comprovantes;
- importação de imagens da galeria;
- arquivo privado das fotos originais;
- OCR no aparelho com ML Kit;
- leitura sugerida de fornecedor, data, total e número do documento;
- identificação experimental de itens e valores individuais do cupom;
- classificação por item em Empresa, Pessoal ou Revisar;
- divisão automática Empresa/Pessoal quando a soma dos itens fecha com o total;
- classificação geral Empresa, Pessoal, Misto ou Revisar;
- forma de pagamento e estados Pago, Pendente, Vencido ou Cancelado;
- busca e filtros de comprovantes;
- visualização, edição e exclusão dos comprovantes arquivados;
- assinatura SHA-256 para detectar fotos duplicadas;
- lançamentos manuais de Receita, Despesa, Aporte e Retirada;
- painel financeiro com pendências e valores a receber;
- lançamentos cancelados sem impacto nos totais financeiros;
- acompanhamento configurável do limite MEI por ano fiscal;
- faturamento do MEI limitado ao ano selecionado;
- múltiplas empresas/CNPJs no mesmo aplicativo;
- troca da empresa ativa com finanças, comprovantes e configuração MEI isolados;
- estoque físico compartilhado com saldo de propriedade separado por empresa;
- entradas, consumo, perdas e ajustes de estoque;
- transferências de estoque entre empresas com lançamento espelhado de saída/entrada;
- histórico auditável das movimentações de estoque;
- ligação de itens do comprovante ao estoque da empresa;
- quantidade sugerida pelo OCR quando há kg, g, L, ml, unidade ou multiplicação por preço;
- entrada de estoque criada junto com o comprovante, sem digitação dupla;
- contas/origens de pagamento separadas por empresa;
- diferenciação entre forma de pagamento e conta de origem;
- identificação de despesa empresarial paga com conta ou cartão pessoal do titular;
- contas padrão para PJ, caixa, cartão da empresa, contas pessoais e recebíveis do iFood;
- exportação CSV com conta de origem e indicação das linhas que entram no fluxo financeiro;
- transferências entre contas sem gerar receita ou despesa artificial;
- reembolso do titular com controle de valor já devolvido e saldo ainda pendente;
- movimentação por conta com entradas, saídas e saldo líquido do período;
- exportação CSV também registra a conta de destino de transferências e reembolsos;
- data completa de abertura do MEI por empresa;
- limite proporcional calculado automaticamente no ano de abertura;
- limite anual completo aplicado automaticamente a partir do ano seguinte;
- painel mostra simultaneamente o limite do primeiro ano e o limite anual posterior;
- exportação dos dados da empresa ativa em CSV;
- migrações do banco preservando os dados de versões anteriores.

## Fluxo de comprovantes

1. Selecionar a empresa ativa.
2. Fotografar ou importar.
3. Preservar a imagem original.
4. Calcular SHA-256 e impedir duplicatas.
5. Executar OCR no aparelho.
6. Sugerir fornecedor, data, total, documento e itens.
7. Sugerir Empresa/Pessoal por item.
8. Usuário confirma ou corrige.
9. Salvar no Room e criar os lançamentos financeiros relacionados.

## Princípios

- O OCR nunca deve ser tratado como verdade absoluta.
- Uma compra mista só é salva se Empresa + Pessoal = Total.
- Comprovante cancelado permanece como evidência, mas não entra nos totais financeiros.
- A foto original é preservada até o usuário excluir explicitamente o comprovante.
- Aporte não é receita.
- Retirada não é despesa operacional.
- O faturamento usado no limite MEI vem de lançamentos de Receita do ano fiscal selecionado.
- Dados de empresas diferentes não devem ser somados automaticamente.
- Transferência de estoque no app é controle interno e não substitui documentação fiscal eventualmente exigida entre CNPJs.

> O aplicativo auxilia a organização financeira e documental. Ele não substitui orientação contábil ou fiscal profissional.
