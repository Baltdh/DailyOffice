# DailyOffice — contador MEI pessoal

Aplicativo Android local-first para fotografar e arquivar comprovantes, extrair dados por OCR e organizar finanças pessoais e empresariais.

## Estado atual — v0.5.0

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

> O aplicativo auxilia a organização financeira e documental. Ele não substitui orientação contábil ou fiscal profissional.
