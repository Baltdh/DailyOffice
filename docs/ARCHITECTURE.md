# DailyOffice — arquitetura

## Princípios

- **Local-first:** foto e dados ficam no aparelho por padrão.
- **Foto original preservada:** editar fornecedor/valor/classificação não substitui o comprovante.
- **OCR é sugestão, não verdade:** dados extraídos precisam ser conferidos.
- **Empresa e pessoal separados:** compras mistas exigem que as duas partes fechem exatamente o total.
- **Pendências explícitas:** pagamento pode ser Pago, Pendente, Vencido ou Cancelado.

## Fluxo implementado

Câmera/galeria → arquivo privado do app → ML Kit OCR → ReceiptParser → ClassificationEngine → revisão → Room.

Ao salvar, o app guarda Receipt e cria lançamentos Transaction para as parcelas de empresa e/ou pessoal.

## Segurança e privacidade

Nesta fase não há envio do conteúdo dos comprovantes para servidor. O OCR usa o mecanismo no dispositivo e os arquivos são armazenados em filesDir/receipts.

Comprovantes e documentos usam SHA-256 para detectar duplicidade. O CSV exporta dados financeiros e datas de baixa/vencimento. A baixa de comprovantes mistos atualiza as parcelas em uma transação Room. O fluxo mensal de caixa usa a data de baixa; o resultado mensal usa a data original do lançamento.

Datas digitadas passam por validação estrita compartilhada. A revisão bloqueia totais não positivos e parcelas negativas.

Próximos passos: backup criptografado e restauração, PDF mensal e conciliação de extratos.
