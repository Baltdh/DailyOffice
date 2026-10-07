# DailyOffice — contador MEI pessoal

Aplicativo Android local-first para fotografar e arquivar comprovantes, extrair dados por OCR e organizar finanças pessoais e empresariais.

## Estado atual — v0.3.0

O DailyOffice já possui:

- câmera integrada para fotografar comprovantes;
- importação de imagens da galeria;
- arquivo privado das fotos originais;
- OCR no aparelho com ML Kit;
- leitura sugerida de fornecedor, data, total e número do documento;
- classificação Empresa, Pessoal, Misto ou Revisar;
- separação obrigatória da parte empresarial e pessoal nas compras mistas;
- forma de pagamento e estados Pago, Pendente, Vencido ou Cancelado;
- busca e filtros de comprovantes;
- visualização, edição e exclusão dos comprovantes arquivados;
- assinatura SHA-256 para detectar fotos duplicadas;
- lançamentos manuais de Receita, Despesa, Aporte e Retirada;
- painel financeiro com pendências;
- acompanhamento configurável do limite MEI;
- exportação dos dados em CSV;
- migração do banco da v0.2 para v0.3 sem apagar os dados.

## Fluxo de comprovantes

1. Fotografar ou importar.
2. Preservar a imagem original.
3. Calcular SHA-256 e impedir duplicatas.
4. Executar OCR no aparelho.
5. Sugerir fornecedor, data, total e documento.
6. Sugerir classificação.
7. Usuário confirma ou corrige.
8. Salvar no Room e criar os lançamentos financeiros relacionados.

## Princípios

- O OCR nunca deve ser tratado como verdade absoluta.
- Uma compra mista só é salva se Empresa + Pessoal = Total.
- A foto original é preservada até o usuário excluir explicitamente o comprovante.
- Aporte não é receita.
- Retirada não é despesa operacional.
- O faturamento usado no limite MEI vem de lançamentos de Receita, não de aportes.

> O aplicativo auxilia a organização financeira e documental. Ele não substitui orientação contábil ou fiscal profissional.
