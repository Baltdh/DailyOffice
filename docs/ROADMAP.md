# Roadmap DailyOffice

## v0.1 — base funcional
- [x] Arquivo local de comprovantes com foto
- [x] OCR local
- [x] Empresa / pessoal / misto / revisar
- [x] Dinheiro, débito PJ, débito pessoal, crédito e Pix
- [x] Pago / pendente / cancelado
- [x] Receita bruta manual
- [x] Limite MEI configurável e proporcional ao mês de abertura
- [x] Exportação CSV
- [x] Teste do parser e CI para APK debug

## v0.2 — conferência contábil
- [ ] Tela de detalhe com edição e visualização da foto
- [ ] Divisão de uma nota mista em itens pessoais e empresariais
- [ ] Contas a pagar com vencimento e lembretes
- [ ] Parcelas e cartões por final
- [ ] Fornecedores recorrentes e regras de classificação aprendidas localmente
- [ ] Busca por fornecedor, valor, data e CNPJ
- [ ] Fechamento mensal (receita, compras, retiradas, DAS, estoque)
- [ ] Importação das planilhas antigas do DailyOffice

## v0.3 — MEI
- [ ] Relatório Mensal de Receitas Brutas
- [ ] DAS: competência, vencimento, pago/pendente e comprovante
- [ ] Alertas configuráveis de 80%, 90%, 95% e 100% do limite
- [ ] Projeção de faturamento usando média móvel
- [ ] Exportação PDF/ZIP com comprovantes por mês
- [ ] Backup criptografado escolhido pelo usuário

## v0.4 — automação
- [ ] Reconciliação entre comprovante e lançamento bancário
- [ ] Importação de CSV/PDF bancário
- [ ] Importação de relatórios iFood
- [ ] Sugestão de duplicidade por valor/data/fornecedor
- [ ] OCR de itens da nota para separar automaticamente compras mistas

## Princípios
1. Offline-first.
2. Nenhum dado financeiro real no GitHub.
3. Toda classificação automática precisa ser revisável.
4. Valores fiscais exibidos como controle/estimativa quando a fonte não for oficial.
5. Nunca apagar comprovante automaticamente.
