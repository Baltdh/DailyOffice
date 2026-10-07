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

Próximos passos: hash SHA-256 para duplicidade, exportação/backup criptografado e restauração.
