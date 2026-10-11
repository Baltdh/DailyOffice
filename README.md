# DailyOffice

Aplicativo Android de controle financeiro pessoal e empresarial, com foco em microempreendedores.

## Situação do repositório
Este repositório foi iniciado apenas com um README. O código Android ainda precisa ser implementado e compilado.

## Escopo da primeira versão (MVP)
- Lançamentos de receitas, despesas, transferências e aportes.
- Separação entre contas pessoais e empresariais.
- Registro de despesas empresariais pagas com recursos pessoais, sem confundi-las com consumo pessoal.
- Anexo de fotos de comprovantes e consulta posterior.
- Contas a pagar e a receber, com status pendente/pago.
- Painel de faturamento bruto e acompanhamento do limite MEI proporcional ao ano de abertura.
- Exportação e importação de backup local.

## Regras financeiras
- Faturamento bruto não é lucro e não depende de recebimento do repasse.
- Transferências entre contas próprias não representam receita.
- Aporte pessoal na empresa não representa faturamento.
- Despesa empresarial paga pela pessoa física deve ser identificada como despesa da empresa e aporte/reembolso, conforme a operação.
- Dados financeiros devem ser armazenados localmente, sem chaves ou comprovantes no Git.

## Próximas etapas técnicas
1. Criar projeto Android Java/Kotlin com Gradle Wrapper e CI.
2. Implementar banco Room com migrações e testes.
3. Implementar formulários, categorias e painel financeiro.
4. Implementar captura de comprovantes pelo seletor de documentos/câmera e cópia privada.
5. Validar cálculos, exportação, restauração e permissões.
6. Gerar APK de teste em GitHub Actions e validar instalação.

Nenhum APK foi produzido nesta etapa.