# DailyOffice — checklist de publicação Google Play

## Status
Preparação iniciada; **não publicar antes de testes e revisão de políticas**.

## Identidade e assinatura
- [ ] Confirmar applicationId definitivo `com.dailyoffice.mei` (não pode ser alterado após publicação como atualização).
- [ ] Definir nome, ícone adaptativo, versão e materiais da loja.
- [ ] Criar chave de upload privada fora do GitHub e habilitar Play App Signing.
- [ ] Configurar assinatura de release via segredos do CI, sem registrar senhas ou keystores no repositório.
- [ ] Gerar AAB release e validar assinatura, instalação e atualização sobre versão anterior.

## Privacidade e políticas
- [ ] Disponibilizar URL pública de política de privacidade antes do envio.
- [ ] Preencher Data safety e formulário de acesso a dados com base na implementação auditada.
- [ ] Conferir uso de câmera, fotos e permissões; solicitar somente quando necessário.
- [ ] Testar exclusão de comprovantes e dados, retenção e comportamento de backups.
- [ ] Verificar se dependências/SDKs coletam dados ou fazem conexões externas.
- [ ] Preencher classificação indicativa, público-alvo, anúncios e declaração de funcionalidades financeiras conforme Play Console.
- [ ] Conferir requisitos de nível de API e testes vigentes na data de envio.

## Qualidade
- [ ] Compilar `testDebugUnitTest`, `lint` e `bundleRelease`.
- [ ] Testar migração Room e atualização sem perda de dados.
- [ ] Testar câmera, OCR, importação, duplicatas, permissões negadas e armazenamento cheio.
- [ ] Testar cálculos de faturamento, valores em centavos, aportes, retiradas e contas pendentes.
- [ ] Testar exportação e restauração de dados.
- [ ] Executar testes em aparelhos reais e teste interno/fechado da Play Console.

## Distribuição
- [ ] Criar app na Play Console e configurar ficha em português.
- [ ] Enviar AAB ao teste interno; analisar relatório pré-lançamento e ANRs/crashes.
- [ ] Cumprir eventual exigência de teste fechado da conta desenvolvedora.
- [ ] Publicar gradualmente após aprovação.

## Limitações
Este checklist não certifica conformidade, compilação ou aprovação. Não inserir CNPJs, comprovantes ou chaves reais no repositório.
