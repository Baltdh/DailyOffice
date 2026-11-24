# DailyOffice

Aplicativo Android de controle financeiro pessoal e empresarial, com foco em microempreendedores.

## Versão 0.2.0 — leitura de comprovantes e apuração

- Importação de foto de nota fiscal ou comprovante pela galeria/arquivos.
- OCR local com Google ML Kit (modelo Latin empacotado no APK): a imagem e o texto não são enviados a um servidor pelo recurso de leitura.
- Identificação contextual do total a pagar, valor total, total geral e valor pago; subtotal, desconto, tributos, CPF/CNPJ, datas e troco recebem tratamento para reduzir falsos positivos.
- Prévia do texto reconhecido, linha usada e nível heurístico de confiança.
- O valor sugerido não é salvo automaticamente: o usuário confere e pode editar descrição, valor e categoria.
- Valores armazenados como centavos inteiros e analisados com BigDecimal, sem somas monetárias em double.
- Resumo separado de faturamento empresarial, despesas empresariais, resultado dos lançamentos e movimentações pessoais.
- Histórico dos 20 lançamentos mais recentes, com data e hora, exclusão individual protegida por confirmação e atualização automática dos totais.
- Migração SQLite compatível adiciona a coluna de data/hora sem recriar a tabela nem apagar lançamentos anteriores; registros antigos são identificados como sem data.
- Exportação do histórico completo para CSV UTF-8 com delimitador `;`, incluindo data/hora quando disponível; campos com aspas, quebras de linha e delimitadores são escapados.
- Aportes pessoais e transferências entre contas próprias não entram no faturamento.

## Regras financeiras

- Faturamento bruto não é lucro e não depende do recebimento de repasse.
- Transferências entre contas próprias não representam receita ou despesa.
- Aporte pessoal na empresa não representa faturamento.
- Despesa empresarial paga pela pessoa física continua classificada como despesa da empresa, sem virar despesa pessoal.
- Valores identificados por OCR são sugestões; confira o documento antes de salvar.
- Dados financeiros permanecem no banco local do aplicativo; use a exportação CSV para manter uma cópia externa periódica. O CSV contém ID, data/hora, categoria, valor e descrição. Lançamentos anteriores à migração aparecem sem data, pois o aplicativo não inventa essa informação. Não coloque chaves, notas ou comprovantes no Git.

## Tecnologia e validação

- Android nativo em Java; minSdk 26, targetSdk 35.
- Google ML Kit Text Recognition com modelo Latin empacotado para disponibilidade imediata e leitura offline.
- Testes unitários para formatos monetários brasileiros, seleção de totais em notas simuladas e escape seguro dos campos CSV e formatação de datas.
- GitHub Actions executa testDebugUnitTest e assembleDebug.

## Limitações conhecidas

- O OCR depende da nitidez, iluminação, enquadramento e resolução da foto.
- A versão atual recebe imagens (image/*); PDF e documentos fiscais eletrônicos estruturados ainda não são importados diretamente.
- O parser usa regras contextuais e não substitui conferência humana nem validação fiscal.
