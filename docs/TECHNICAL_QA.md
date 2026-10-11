# DailyOffice — Auditoria técnica e regressão

Prioridade: estabilidade e correção funcional. A publicação na Google Play fica adiada.

## Pontos confirmados no código
- `MainActivity.kt` contém seis telas: Home, Captura, Revisão, Financeiro, Estoque e Documentos.
- Navegação utiliza `rememberSaveable` e retorno ao início via `BackHandler`.
- Dependências incluem Room, ML Kit, CameraX e WorkManager.

## Matriz de regressão (executar, não presumir aprovação)
| Fluxo | Verificação |
| --- | --- |
| Câmera | permissão concedida/negada, retorno sem foto, rotação e falta de espaço |
| OCR | nota ilegível, valores com vírgula, cupons extensos, duplicidade |
| Comprovantes | salvar, editar, excluir, foto original preservada |
| Finanças | receitas, despesas, aportes, retiradas, contas pagas e pendentes |
| Caixa | data de baixa diferente da data de competência |
| Misto | empresa + pessoal deve fechar exatamente o total |
| Empresas | troca de empresa não mistura CNPJ, lançamentos nem saldos |
| Estoque | entradas, saídas, perdas, transferência entre empresas |
| Dados | migração Room, atualização sem perda de dados, CSV consistente |
| Navegação | botão Voltar, rotação, app retomado após processo encerrado |

## Regras de cálculo
- Recebível agendado não é caixa disponível.
- Aporte, retirada e transferência interna não são faturamento bruto.
- Cancelamentos não entram nos totais.
- Gastos empresariais pagos por conta pessoal não são consumo pessoal.
- Limite do MEI depende do enquadramento, data de abertura e ano fiscal.
- Evitar operações monetárias com `Float`/`Double` para somas e saldos.

## Próxima execução
1. Rodar `gradle testDebugUnitTest lintDebug assembleDebug` no CI.
2. Inspecionar os logs e corrigir erros concretos.
3. Criar testes de regressão para cálculos e migrações.
4. Validar fluxos reais no Android.

Este arquivo registra cenários de teste; não significa que foram aprovados.
