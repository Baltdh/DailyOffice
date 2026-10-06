# DailyOffice MEI

Aplicativo Android offline-first para organizar comprovantes e acompanhar a rotina financeira de um MEI.

## MVP v0.1

- tira foto do comprovante ou importa uma imagem;
- OCR local com ML Kit;
- tenta identificar fornecedor, CNPJ, data, valor e forma de pagamento;
- sugere classificação: **empresa**, **pessoal**, **misto** ou **revisar**;
- guarda a foto no armazenamento privado do app;
- registra forma de pagamento e situação (**pago**, **pendente**, **cancelado**);
- lista pendências;
- registra receita bruta;
- acompanha o limite anual/proporcional do MEI a partir do mês de abertura;
- exporta despesas e receitas em CSV para backup ou envio ao contador;
- CI gera APK debug como artefato do GitHub Actions.

## Privacidade

O repositório não deve conter comprovantes, CNPJ real, dados bancários ou exportações pessoais. Os dados do usuário ficam localmente no aparelho.

## Arquitetura

- Kotlin + Jetpack Compose
- Room para banco local
- ML Kit Text Recognition para OCR no aparelho
- FileProvider + armazenamento privado para fotos
- SharedPreferences para configuração do MEI

## Fluxo

1. Abra **Adicionar**.
2. Tire uma foto.
3. O app preenche um rascunho com OCR.
4. Revise valor, fornecedor, classificação e pagamento.
5. Arquive.
6. Use **Pendências** para itens que precisam de conferência.
7. Registre faturamento bruto no **Resumo**.
8. Exporte CSV em **Config.** quando quiser fazer backup ou enviar ao contador.

## Build local

Requer JDK 17 e Gradle 8.11.1:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

O APK fica em:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Próximas etapas

Veja [docs/ROADMAP.md](docs/ROADMAP.md).

> O painel MEI é um organizador administrativo, não substitui escrituração, contador ou orientação oficial da Receita/Simples Nacional.
