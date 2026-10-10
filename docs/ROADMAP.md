# Roadmap

## M1 — Fundação ✅
- Projeto Android Kotlin/Compose
- Room
- Modelos de comprovantes e lançamentos
- Classificação empresa/pessoal/mista
- Status pago/pendente/vencido
- Parser inicial de OCR
- Build automática no GitHub Actions

## M2 — Captura inteligente — em andamento
- [x] CameraX
- [x] ML Kit Text Recognition
- [x] Arquivo privado da foto original
- [x] SHA-256 e prevenção de duplicatas
- [x] Importação da galeria
- [x] Revisão obrigatória dos campos
- [ ] corte e rotação assistidos
- [ ] múltiplas páginas no mesmo comprovante
- [x] leitura inicial de itens individuais e classificação Empresa/Pessoal
- [x] leitura inicial de quantidade para integração com estoque
- [ ] melhoria de OCR para cupons longos, descontos complexos e impressões fracas

## M3 — Contador MEI — em andamento
- [x] despesas empresariais e pessoais
- [x] receitas
- [x] aportes e retiradas
- [x] contas pendentes/vencidas
- [x] dashboard financeiro
- [x] limite proporcional do MEI calculado pela data de abertura
- [x] limite anual completo aplicado automaticamente após o primeiro ano
- [x] ano fiscal separado no cálculo do limite
- [x] lançamentos cancelados excluídos dos totais
- [x] múltiplas empresas/CNPJs com dados isolados
- [x] porte/enquadramento por empresa: MEI, ME, EPP ou Outro
- [x] regras do teto MEI condicionadas ao enquadramento selecionado
- [x] fechamento mensal automático
- [x] relatório mensal de receitas brutas no app
- [ ] categorias e regras aprendidas pelo usuário
- [ ] alertas de vencimento com WorkManager

## M4 — Arquivo e exportação — em andamento
- [x] busca e filtros
- [x] visualizar foto arquivada
- [x] editar e excluir comprovantes
- [x] exportação CSV
- [ ] PDF mensal
- [ ] backup criptografado
- [ ] restauração
- [ ] exportar pasta organizada por ano/mês

## M5 — Multiempresa e estoque — em andamento
- [x] cadastro e troca da empresa ativa
- [x] comprovantes e lançamentos isolados por empresa
- [x] configuração MEI independente por empresa
- [x] contas bancárias/fontes de pagamento por empresa
- [x] estoque físico com propriedade por CNPJ
- [x] transferência interna de estoque entre empresas com histórico
- [x] vínculo de compras do comprovante com entradas de estoque
- [ ] vínculo da transferência entre empresas com documento fiscal/comprovante
- [x] histórico auditável de movimentações de estoque
- [x] identificação de despesa empresarial paga pelo titular
- [x] reembolso do titular e transferências entre contas
- [x] fluxo de entradas/saídas por conta
- [ ] relatório consolidado sem misturar os livros de cada CNPJ

## M6 — Regime e enquadramento — em andamento
- [x] seleção de porte/enquadramento da empresa
- [x] regime tributário separado (SIMEI, Simples Nacional, Lucro Presumido, Lucro Real e outros)
- [x] validação básica entre MEI/SIMEI e ME/EPP
- [ ] parâmetros e relatórios específicos para ME/EPP
- [ ] histórico de mudança de enquadramento sem alterar períodos anteriores

## M7 — Conciliação
- [ ] importação CSV/OFX
- [ ] associação automática comprovante ↔ transação
- [ ] fila de divergências
- [ ] importação de repasses do iFood
- [x] fechamento mensal assistido no painel
- [ ] confirmação/congelamento de fechamento mensal
- [ ] saldos iniciais e conciliação de saldo por conta
