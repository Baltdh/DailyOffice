# Segurança e privacidade

O DailyOffice lida com comprovantes e informações financeiras. O desenho inicial segue estas regras:

- banco Room armazenado no sandbox privado do Android;
- fotos copiadas para `filesDir/receipts`;
- OCR executado localmente pelo ML Kit;
- nenhuma chave de API necessária no MVP;
- nenhuma foto é enviada ao GitHub;
- exportação só ocorre após ação explícita do usuário;
- compartilhamento de CSV usa URI temporária via FileProvider;
- backups do Android podem incluir banco e fotos, conforme configuração do aparelho.

## Antes de publicar
- avaliar criptografia do banco e dos arquivos;
- oferecer bloqueio biométrico;
- permitir desativar backup em nuvem;
- política de privacidade;
- exclusão/exportação completa dos dados;
- revisão LGPD;
- proteção contra screenshots em telas sensíveis, se desejado.
