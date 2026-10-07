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
- [ ] melhoria de OCR para cupons longos, descontos, quantidades e impressões fracas

## M3 — Contador MEI — em andamento
- [x] despesas empresariais e pessoais
- [x] receitas
- [x] aportes e retiradas
- [x] contas pendentes/vencidas
- [x] dashboard financeiro
- [x] limite proporcional configurável do MEI
- [x] ano fiscal separado no cálculo do limite
- [x] lançamentos cancelados excluídos dos totais
- [x] múltiplas empresas/CNPJs com dados isolados
- [ ] fechamento mensal
- [ ] relatório mensal de receitas brutas
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
- [ ] contas bancárias/fontes de pagamento por empresa
- [ ] estoque físico com propriedade por CNPJ
- [ ] transferência documentada de estoque entre empresas
- [ ] relatório consolidado sem misturar os livros de cada CNPJ

## M6 — Conciliação
- [ ] importação CSV/OFX
- [ ] associação automática comprovante ↔ transação
- [ ] fila de divergências
- [ ] importação de repasses do iFood
- [ ] fechamento mensal assistido
