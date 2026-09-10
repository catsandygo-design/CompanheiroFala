# Auditoria da arquitetura atual — Companheiro Fala

Data da auditoria: 03/09/2026. Esta fase foi somente de leitura: nenhum arquivo de código ou recurso visual foi alterado.

## A. O que está errado hoje

O aplicativo é um único módulo Android nativo, escrito em Kotlin, com uma `Activity` única e interface construída programaticamente. A interação principal percorre `MainActivity` → `SpeechEngine` → `ConversationEngine` → `VoiceEngine`.

O ponto central é `ConversationEngine.kt` (480 linhas). Ele concentra menu, jogos, rotinas, perguntas pendentes, memória de preferências, regras de segurança e fallback. A maioria das decisões depende de `contains`, listas de palavras e uma enumeração privada `PendingTurn`. Isso explica as respostas engessadas e torna difícil ampliar contextos sem aumentar a cascata de `if`/`when`.

Há contexto limitado: `pending`, `currentEmotion`, `routineStep`, `mode` e `OfflineConversationBrain.lastTopic`. Ele não é um estado de conversa coeso, não possui sessão, timestamps, expiração, resumo, ambiente atual nem mensagens recentes. Uma resposta curta funciona apenas nos fluxos que têm um `PendingTurn` previamente codificado.

`SafetyEngine` tem precedência correta sobre a conversa local, mas só reconhece agressão física e medo associado a pessoa conhecida/pronomes. Não modela categorias, severidade, ambiente, pessoas autorizadas, eventos de segurança nem notificação configurável. As respostas ainda orientam para “um adulto em quem você confia”, sem selecionar uma pessoa cadastrada pelo contexto.

O fallback é global e baseado em contador (`fallbackCount`), mas apaga `pending` antes de responder. Portanto, quando há uma falha de ASR no meio de uma pergunta, o contexto é perdido. `VoiceEngine` bloqueia texto idêntico por 3,5 s, porém não há um `LoopGuard` que identifique sequências semânticas repetidas e recupere o estado.

Não existe normalizador infantil compartilhado. Cada classe normaliza acentos e pontuação por conta própria, e o reconhecimento usa somente o primeiro resultado do ASR. Há alguns aliases pontuais (“mi bateu”, “alixi”), mas não há similaridade, distância de edição, confiança nem correção contextual para jogos.

Não há IA local, IA remota, abstrações de provider ou métricas de chamadas. A única rede é o verificador/instalador de atualização de releases GitHub em `AppUpdater.kt`; não foi encontrada chave de API no código. O app básico continua utilizável sem essa conexão porque a falha é ignorada.

## B. O que pode ser reutilizado

| Área | Reuso seguro |
| --- | --- |
| Layout/personagem | `MainActivity`, `fairy_pet`, `ChildVisualView` e os botões/figuras já atendem ao foco em imagem, toque e frases curtas. |
| Fala | `SpeechEngine` usa `SpeechRecognizer` em pt-BR e já representa `IDLE`, `WAITING`, `LISTENING`, `PROCESSING` e `SPEAKING`. `VoiceEngine` usa TTS nativo pt-BR. |
| Proteção atual | A prioridade de `SafetyEngine.inspect` antes de conversa pode ser preservada e expandida. |
| Experiências offline | Rotinas, história, jogo de animais, adivinhação, letras e memória são locais e devem virar destinos do roteador. |
| Dados locais | `SharedPreferences` já armazena preferências, eventos e métricas simples. Pode hospedar configurações pequenas na primeira etapa. |
| Testes | JUnit 4 e `OfflineConversationBrainTest` dão uma base para substituir testes por diálogo de orquestrador. |

## C. O que precisa ser refatorado

1. Extrair a decisão de `ConversationEngine` para `ConversationOrchestrator`; manter o motor atual como adaptador transitório dos jogos/rotinas, não como novo centro de regras.
2. Criar `ConversationState` serializável em memória de sessão: janela limitada de turnos, intenção/tópico/pergunta pendente, evento/rotina/jogo, emoção, ambiente, timestamps e expiração por inatividade/mudança clara de tópico.
3. Criar um `SpeechNormalizer` único com aliases configuráveis, normalização conservadora, candidatos e pontuação. O texto original deve continuar disponível para segurança e auditoria.
4. Criar `IntentRouter` com prioridade segurança → rotinas → jogo ativo → conversa local → providers. Respostas curtas devem ser resolvidas pela pergunta pendente antes de roteamento geral.
5. Substituir `SafetyEngine` por `ChildSafetyEngine` determinístico, com categorias, severidade, coleta mínima não indutiva e registro `SafetyEvent` local.
6. Separar `TrustedPerson`, `CurrentEnvironment` e repositórios locais. O cadastro e o acionamento de notificações não devem ficar na Activity ou no motor de diálogo.
7. Colocar TTS/ASR sob um coordenador de estado. Hoje o cancelamento antes da fala e a reativação atrasada já ajudam, mas os callbacks não possuem token de turno; uma finalização antiga pode interferir após uma troca rápida de fala. O coordenador deve invalidar callbacks antigos e impedir iniciar ASR enquanto TTS estiver ativo.
8. Criar `LoopGuard` baseado nos últimos N textos de saída, restaurando/limpando o contexto somente depois de detectar repetição real.
9. Criar interfaces `LocalLLMProvider`, `RemoteLLMProvider` e `NotificationService`. Nenhuma regra de segurança deve depender de provider.

## D. Arquivos que serão alterados/adicionados por fase

Os arquivos atuais a preservar e adaptar são:

- `app/src/main/java/br/com/companheirofala/MainActivity.kt` — apenas a ligação UI → orquestrador e coordenação de fala; o layout será preservado.
- `app/src/main/java/br/com/companheirofala/ConversationEngine.kt` — reduzir para adaptador de jogos/rotinas ou dividir gradualmente, sem remover jogos existentes.
- `app/src/main/java/br/com/companheirofala/OfflineConversationBrain.kt` — migrar conhecimento conversacional local atrás do roteador.
- `app/src/main/java/br/com/companheirofala/SafetyEngine.kt` — substituir de forma compatível por `ChildSafetyEngine`.
- `app/src/main/java/br/com/companheirofala/SpeechEngine.kt` e `VoiceEngine.kt` — integrar um coordenador robusto de estado/turno.
- `app/src/main/java/br/com/companheirofala/ChildMemory.kt`, `ParentEventRepository.kt` e `DevelopmentTracker.kt` — evoluir dados locais sem expor áudio bruto.
- `app/src/test/java/br/com/companheirofala/OfflineConversationBrainTest.kt` — complementar/substituir por testes do orquestrador e de segurança.

Arquivos novos propostos, organizados sem criar módulos Gradle desnecessários:

```text
core/conversation/ConversationState.kt
core/conversation/ConversationOrchestrator.kt
core/conversation/IntentRouter.kt
core/conversation/LoopGuard.kt
core/speech/SpeechNormalizer.kt
core/speech/VoiceInteractionCoordinator.kt
core/safety/ChildSafetyEngine.kt
core/safety/SafetyEvent.kt
core/safety/TrustedPerson.kt
core/ai/LocalLLMProvider.kt
core/ai/RemoteLLMProvider.kt
core/ai/AiUsageMetrics.kt
data/local/TrustedPeopleRepository.kt
data/local/SafetyEventRepository.kt
```

## E. Motor local recomendado

Recomendação: **llama.cpp via JNI, encapsulado por `LocalLLMProvider`, com CPU como caminho inicial e modelo GGUF quantizado baixado/instalado separadamente**.

Motivo: o projeto atual tem `minSdk 26`, Kotlin sem bibliotecas pesadas e precisa manter a IA opcional/offline. O projeto oficial do llama.cpp mantém exemplo Android e pipeline de build Android; o formato GGUF permite escolher e trocar modelos quantizados sem acoplar o restante do app ao motor. A camada JNI aumenta o trabalho de integração, mas oferece o menor lock-in e aceita modelos pequenos em português. A API Android de LLM do ExecuTorch é documentada como experimental, portanto não é a opção principal para um aplicativo infantil que exige previsibilidade. [llama.cpp Android](https://github.com/ggml-org/llama.cpp/blob/master/.github/workflows/build-android.yml) e [ExecuTorch Android](https://docs.pytorch.org/executorch/stable/llm/run-on-android.html).

Antes de incluir runtime/modelo no APK, a Fase 4 deve executar um spike em aparelhos-alvo reais, somente `arm64-v8a`. Se o conjunto de aparelhos for limitado a hardware compatível e a manutenção nativa se tornar impeditiva, a alternativa a avaliar é ExecuTorch, mas não ambas na mesma entrega.

## F. Estimativa de espaço e RAM

Estimativas de planejamento, dependentes do modelo, tokenizer, contexto e aparelho:

| Perfil | Espaço do modelo | RAM aproximada em execução | Indicação |
| --- | ---: | ---: | --- |
| 1B, GGUF Q4 | 0,7–0,9 GB | 1,2–1,8 GB | ponto de partida; aparelhos com ao menos 4 GB de RAM livre recomendados |
| 2–3B, GGUF Q4 | 1,5–2,2 GB | 2,5–4,0 GB | somente aparelhos de faixa mais alta, validar temperatura/latência |
| Sem modelo instalado | ~0 para modelo | ~0 para LLM | todas as rotinas, jogos e segurança permanecem locais |

Os valores incluem margem para pesos, cache de contexto, runtime e processo do app; não são garantia. Como referência, a documentação do llama.cpp exemplifica um arquivo 1B Q4 de 773 MB no Android. [Referência do modelo Q4](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md).

Não embutir o modelo no APK inicial: disponibilizá-lo como pacote opcional, exigir Wi-Fi/espaço suficiente, mostrar tamanho antes do download e manter o app funcional sem ele.

## G. Plano de implementação em etapas

1. **Base e testes:** introduzir modelos de estado, normalizador, roteador e `LoopGuard`; adaptar `MainActivity` só na fronteira; testar contexto, rotinas e repetição.
2. **Segurança:** implementar categorias determinísticas, ambiente, pessoas confiáveis, eventos locais e interface de notificação sem provider externo obrigatório.
3. **Voz:** colocar ASR/TTS sob coordenador com token de turno, pausa garantida e reativação após intervalo; teste de que TTS não inicia ASR.
4. **IA opcional:** adicionar somente as interfaces e o fallback desligado por padrão; realizar spike de llama.cpp e perfil de memória antes de ativar qualquer modelo.
5. **Experiência infantil:** integrar normalização fonética nos jogos, variar elogios e respostas, manter as telas/recursos existentes.
6. **Validação:** testes unitários dos oito cenários solicitados, testes de regressão dos jogos, build em CI/local e teste manual em aparelho.

## Inventário técnico

- Stack: Android nativo Kotlin 2.2.20, Android Gradle Plugin 8.13.0, Java 17; `compileSdk/targetSdk 36`, `minSdk 26`.
- Módulos: somente `:app`. Não há Fragments, ViewModels, Services de app, Compose, DI, Room/SQLite nem biblioteca HTTP.
- Activity: `MainActivity` é a única Activity e constrói a UI por código.
- STT: `android.speech.SpeechRecognizer` com `RecognizerIntent`, pt-BR, até três hipóteses solicitadas, mas somente a primeira consumida.
- TTS: `android.speech.tts.TextToSpeech`, voz pt-BR preferindo não-rede.
- Persistência: `SharedPreferences` para memória, eventos e tracker; eventos limitados a 150 itens. Não há esquema/migração/limpeza por idade.
- Rede: somente atualização por GitHub Releases via `HttpURLConnection` e `DownloadManager`; permissão `INTERNET` também está declarada.
- Testes: JUnit 4 com 9 testes unitários para conversa/jogos. Não há testes instrumentados, CI de testes visível ou Gradle Wrapper no repositório.

## Validação desta auditoria

Foi feita inspeção estática dos arquivos rastreados e das dependências. Não foi possível executar os testes: o repositório não contém `gradlew`/`gradlew.bat` e não há executável `gradle` disponível no ambiente. Isso será resolvido como parte da preparação da validação, sem afirmar que o build atual passou.
