# DailyOffice — Plano de implementação

## Estado auditado
O repositório contém um README inicial; nenhum projeto Android foi confirmado nos caminhos convencionais consultados. A existência de outros arquivos ainda requer verificação completa da árvore.

## MVP Android
1. Projeto Android Java/Kotlin com Gradle Wrapper e CI de build.
2. Banco local Room: lançamentos, comprovantes, categorias e contas a receber.
3. Cadastro de receita/despesa e classificação pessoal/empresa/aporte/reembolso.
4. Importação de fotos com URI persistente ou cópia privada, metadados e vínculo com lançamento.
5. Dashboard: receita bruta, despesas, saldo, lucro estimado e pendências.
6. Controle de faturamento MEI configurável por ano e data de abertura, sem tratar transferências internas como faturamento.
7. Exportação e restauração de backup com testes de integridade.

## Regras críticas
- Não armazenar imagens ou dados financeiros reais no GitHub.
- Valores monetários em centavos inteiros, nunca float.
- Toda transação tem data, valor, tipo, origem e classificação.
- Recebível agendado não é dinheiro recebido; conciliação evita duplicação.
- Aporte pessoal para a loja não é receita operacional.
- Despesa da loja paga no cartão pessoal gera registro de aporte/reembolso a conciliar.
- Titularidade do MEI deve ser configurável; não presumir que o usuário seja o titular.

## Validação antes do APK
Compilação Android, testes de cálculos, migrações de banco, importação de imagens, exportação/restauração e instalação real em dispositivo.
