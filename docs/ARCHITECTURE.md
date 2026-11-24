# Архитектура TrackFit

Статус: **обязательна к исполнению**. Эскизы кода — иллюстрация контрактов; имена и сигнатуры можно уточнять, **инварианты и границы слоёв — нельзя**. Изменение архитектуры = запись в `DECISIONS.md` (тип A) + согласование с владельцем.

---

## 1. Ключевые принципы

1. **Бизнес-логика отдельно от View — физически.** Логика в Gradle-модуле `:domain` (чистый Kotlin/JVM). Android-классы туда не попадут на уровне компиляции.
2. **MVC-вариация с пассивным View и однонаправленным потоком данных:** Model → Controller ← View (через ViewModel-адаптер). Подробно §3.
3. **Время — входной параметр, а не окружение.** Вся логика — функции `(state, now, zone, command) → (state', events)`. Это делает автостарты, дедлайны, полночь и многодневное отсутствие тестируемыми без эмулятора.
4. **Состояние выводится, а не «тикает».** Процесс Android может быть убит, будильники — задержаны. Поэтому истина — сохранённые факты + часы. Любое обращение к состоянию начинается с `reconcile(now)`, который «догоняет» всё, что должно было произойти за время отсутствия (идемпотентно).
5. **Одна точка записи.** Команды сериализуются (Mutex), каждая — одна транзакция.
6. **Расширяемость тренера** — через композицию (правила, банки фраз, реестр), а не через ветвление.
7. **UI заменяем.** Макеты будут меняться; Composable — тонкие, без логики; весь UI-специфичный код сосредоточен в `:app`.

## 2. Модули

```
          ┌───────────────────────────── :app ─────────────────────────────┐
          │ Compose UI (screens, components, theme), ViewModels, navigation│
          │ Hilt wiring, Activity, ModalHost, ForegroundTriggers            │
          └───────────────┬───────────────────────────┬────────────────────┘
                          │                           │
                          ▼                           │
          ┌──────────── :infra ────────────┐          │
          │ Room, DataStore, DTO/mappers,  │          │
          │ AlarmManager scheduler,        │          │
          │ notifications, receivers,      │          │
          │ SystemTimeSource, assets loader│          │
          └───────────────┬────────────────┘          │
                          ▼                           ▼
          ┌──────────────────────── :domain (kotlin jvm) ──────────────────┐
          │ model · engine (pure) · coach · controller · ports              │
          └──────────────────────────────────────────────────────────────────┘
```

| Модуль | Плагин | Зависит от | Содержит |
|---|---|---|---|
| `:domain` | `kotlin("jvm")` | kotlinx-coroutines-core, `javax.inject` (опц.) | Модель, движок, тренер, контроллеры, порты. **Никаких** `android.*`/`androidx.*`. |
| `:infra` | `com.android.library` | `:domain`, Room, DataStore, serialization | Реализации портов. Без UI. |
| `:app` | `com.android.application` | `:domain`, `:infra` | UI, DI-сборка. |

Правило импортов: `:app` не обращается к Room/DAO/DTO из `:infra` напрямую, только через порты/интерфейсы домена (DI собирает реализации).

## 3. Слои MVC-вариации

| Роль MVC | Где | Что делает | Чего **не** делает |
|---|---|---|---|
| **Model** | `domain.model`, `domain.engine`, `domain.coach`, `domain.ports` | Сущности, инварианты, правила (статусы, циклы, дедлайны, валидация, выбор реплик), чистые функции. | Не знает про Android, БД, UI. |
| **Controller** | `domain.controller` | Принимает команды (от View), читает/пишет состояние через порты, вызывает движок, коммитит транзакцию, публикует события и `Flow` состояния. | Не форматирует строки/даты для экрана, не знает про Compose. |
| **View** | `:app` — `ui.screens/*` (Composable) | Рисует `UiState`, отправляет `UiIntent`. Stateless. | Не содержит правил, не вызывает контроллеры, не читает время. |
| **View-adapter** | `:app` — `*ViewModel`, `*UiMapper` | Подписывается на `Flow` контроллеров, мапит Model → `UiState` (форматирование, локализация через ресурсы), мапит `UiIntent` → вызов контроллера, держит эфемерное UI-состояние (какое поле в фокусе, открыт ли диалог). | Не принимает бизнес-решений (никаких «если статус X то…»). Допустимо лишь презентационное «если нет данных — показать заглушку». |

Поток: `View → UiIntent → ViewModel → Controller.command() → Engine → Store(commit) → Flow<State> → ViewModel(map) → UiState → View`.

**Тест «границы»:** если правило можно сформулировать словами из `trackfit.md` — оно в `:domain`. Если это про пиксели/форматирование/навигацию — в `:app`.

## 4. Доменная модель (эскиз)

```kotlin
// ids — value classes над String (UUID)
@JvmInline value class ProgramId(val value: String)
@JvmInline value class DayId(val value: String)
@JvmInline value class ExerciseId(val value: String)

data class Program(val id: ProgramId, val name: String, val days: List<CycleDay>)

sealed interface CycleDay {
    val id: DayId
    data class Work(
        override val id: DayId,
        val name: String,
        val durationMinutes: Int,          // >= 1, жёсткий дедлайн
        val autoStart: LocalTime?,         // null = только ручной старт
        val exercises: List<Exercise>,     // >= 1 (валидатор)
    ) : CycleDay
    data class Rest(override val id: DayId) : CycleDay   // без имени/настроек
}

sealed interface Exercise {
    val id: ExerciseId; val name: String; val note: String?
    data class Strength(…, val sets: Int, val reps: Int, val weightKg: Double?, val restSeconds: Int?) : Exercise
    data class Cardio(…, val durationSeconds: Int, val intensity: String?, val targetPulse: Int?) : Exercise
    data class Balance(…, val durationSeconds: Int, val sets: Int?, val side: BodySide?) : Exercise
    data class Stretch(…, val durationSeconds: Int, val sets: Int?, val side: BodySide?) : Exercise
}
enum class BodySide { LEFT, RIGHT, BOTH }
```

`ExerciseSpecs` — метаданные типов (обязательные/опциональные поля; что показывать в списке). Валидатор и форма берут правила отсюда (единственный источник, соответствует таблице макета).

### Запуск и прогресс

```kotlin
data class ProgramRun(
    val program: Program,              // СНАПШОТ на момент старта (программа неизменяема пока активна, но снапшот защищает движок и лог)
    val startDayIndex: Int,            // выбранный стартовый день; новый цикл стартует с него же (D-08)
    val cycleNumber: Int,              // 1, 2, …
    val phase: CyclePhase,
    val lastReconciledAt: Instant,     // монотонно растёт
)

sealed interface CyclePhase {
    data class InProgress(val slots: List<DaySlot>) : CyclePhase          // slots.size == N, даты подряд
    data class AwaitingDecision(val outcome: CycleOutcome, val slots: List<DaySlot>) : CyclePhase
}

data class DaySlot(val date: LocalDate, val dayIndex: Int, val work: WorkState?)   // work == null → день отдыха

sealed interface WorkState {
    val autoStartEnabled: Boolean                                          // false для первого слота, если время уже прошло (D-06)
    data class NotStarted(override val autoStartEnabled: Boolean) : WorkState
    data class Running(val session: Session, …) : WorkState
    data class Closed(val result: DayResult, val session: Session?, …) : WorkState   // session == null → пропущен без запуска
}

data class Session(
    val startedAt: Instant, val startKind: StartKind /* MANUAL|AUTO */,
    val deadlineAt: Instant,                       // min(startedAt + duration, конец суток слота) (D-07)
    val checked: Map<ExerciseId, Instant>,         // отметки, необратимы
)
enum class CloseReason { MANUAL, DEADLINE, MIDNIGHT }
enum class WorkStatus { NOT_STARTED, RUNNING, COMPLETED, INCOMPLETE, SKIPPED }   // производный для UI/логики
enum class CycleOutcome { FULL_SUCCESS, PARTIAL_SUCCESS, FULL_FAILURE }
```

Недопустимые состояния непредставимы: `Closed` не может быть «Выполняется»; `Running` всегда имеет `Session`.

### Агрегат состояния, отчёты, лог

```kotlin
data class TrainingState(
    val run: ProgramRun?,                 // null → программа не активна
    val reports: List<Report>,            // очередь неотмеченных/непросмотренных
)
sealed interface Report { val id: ReportId; val createdAt: Instant; val viewed: Boolean
    data class Day(…, val date: LocalDate, val dayName: String, val status: WorkStatus, val checked: Int, val total: Int) : Report
    data class Cycle(…, val cycleNumber: Int, val outcome: CycleOutcome) : Report   // закрывается только решением
}
data class WorkoutLogEntry(/* date, programName, dayName, status, closeReason, startedAt, closedAt, cycleNumber, exercises: List<ExerciseResult(name,type,checked)> */)
```

Лог — неизменяемая денормализованная запись (не ссылается на программу по ID); не очищается при остановке/смене программы (D-12).

## 5. Модель времени: reconcile

### 5.1 Контракт

```kotlin
interface TimeSource { fun now(): Instant; fun zone(): ZoneId }   // порт; единственное место чтения часов

object TrainingEngine {
    fun reconcile(s: TrainingState, now: Instant, zone: ZoneId, log: LogFacts): EngineResult
    fun startProgram(s, program, startDayIndex, now, zone): Outcome<EngineResult>
    fun stopProgram(s, now, zone): Outcome<EngineResult>
    fun startWorkout(s, now, zone): Outcome<EngineResult>
    fun checkExercise(s, exerciseId, now, zone): Outcome<EngineResult>
    fun finishWorkout(s, now, zone): Outcome<EngineResult>
    fun decideCycle(s, decision: CycleDecision, now, zone): Outcome<EngineResult>
    fun nextTransitionAt(s, zone): Instant?      // ближайший момент, когда reconcile что-то изменит
}
data class EngineResult(val state: TrainingState, val logAppends: List<WorkoutLogEntry>, val events: List<DomainEvent>)
```

Все команды **внутри** вызывают `reconcile(now)` первым шагом, затем применяют действие. Так команда «отметить упражнение» после просроченного дедлайна честно получит `SessionNotRunning`, даже если ни один таймер не сработал.

### 5.2 Алгоритм `reconcile` (для `phase = InProgress`)

Идти по слотам в порядке дат, для каждого слота с `date ≤ today(now, zone)`:

1. **Рабочий, `NotStarted`, `autoStartEnabled`, автостарт задан**: `startInstant = date.atTime(autoStart).in(zone)`. Если `now ≥ startInstant` → перейти в `Running` с `startedAt = startInstant` (а не `now`!), `startKind = AUTO`, `deadlineAt = min(startInstant + duration, конец суток date)`.
2. **`Running`**: если `now ≥ deadlineAt` → закрыть в `deadlineAt` (причина `DEADLINE`, либо `MIDNIGHT`, если дедлайн совпал с границей суток). Статус по F-36.
3. **Рабочий `NotStarted`, `date < today`** → закрыть как `Skipped`, причина `MIDNIGHT`, без сессии.
4. **День отдыха** — без состояния; считается пройденным при `date < today`.
5. Каждое закрытие рабочего слота → `WorkoutLogEntry` + событие `WorkoutClosed`; отчёт `Report.Day`, **если** это закрытие не завершает цикл.
6. **Конец цикла**: когда закрыт последний слот (рабочий — закрыт; отдыха — `date < today`), вычислить `CycleOutcome` по статусам **до** любых сбросов; `phase = AwaitingDecision`; создать `Report.Cycle` (вместо `Report.Day` для последнего рабочего слота); событие `CycleCompleted`.
7. `lastReconciledAt = max(lastReconciledAt, now)` (монотонность, D-25).

Идемпотентность: повторный вызов с тем же/более ранним `now` ничего не меняет. Если результат равен входу — контроллер не пишет в БД.

`AwaitingDecision`: календарь заморожен, слоты только для чтения. Решение (`decideCycle`):
- `SwitchProgram` → `run = null`, отчёт помечается просмотренным. Контроллер сообщает UI открыть PROGRAMS.
- `ContinueSameProgram` → новый `InProgress` с `cycleNumber+1`, слоты начинаются с **завтрашней** даты (от даты решения), `dayIndex` идёт от `startDayIndex`; статусы чистые; отчёт просмотрен.

### 5.3 Старт программы

`startProgram`: проверка `run == null` (иначе `Rejected(ActiveProgramExists)` — D-03; политика изолирована в `StartProgramPolicy`, чтобы смена на «заменить с подтверждением» была правкой одного класса). Слоты: `date = today + k`, `dayIndex = (startDayIndex + k) mod N`, `k ∈ [0, N)`. Для слота `k = 0`: `autoStartEnabled = (autoStart != null && autoStart > now.localTime)`.

### 5.4 `nextTransitionAt`

Чистая функция: минимум из ближайших автостартов, дедлайнов, полуночей слотов. Её используют (а) foreground-триггер UI (один `delay` до следующего момента вместо опроса), (б) `NotificationPlanner`.

### 5.5 Единый писатель

```kotlin
class TrainingController(private val store: TrainingStateStore, private val time: TimeSource,
                         private val events: DomainEventBus, …) {
    private val mutex = Mutex()
    val state: Flow<TrainingState> = store.observe()
    suspend fun <T> command(block: (TrainingState, Instant, ZoneId) -> Outcome<EngineResult, T>): Outcome<T> =
        mutex.withLock {
            val s = store.load(); val now = time.now(); val zone = time.zone()
            val r = block(s, now, zone)                    // внутри — reconcile + действие
            if (r is Success && r.result.changed) store.commit(r.result.state, r.result.logAppends)  // ОДНА транзакция
            r.result.events.forEach(events::publish)       // после коммита
            r
        }
    suspend fun reconcile() = command { s, n, z -> TrainingEngine.reconcile(s, n, z, …).asOutcome() }
}
```

Alarm-приёмник и UI вызывают один и тот же `TrainingController`; гонки исключены Mutex'ом.

## 6. Контроллеры (публичная поверхность домена для View)

| Контроллер | Команды / потоки |
|---|---|
| `ProgramController` | `programs: Flow<List<ProgramSummary>>`, `load(id)`, `save(draft)` (валидация, запрет при активной), `createDraft()`, операции над `ProgramDraft` — чистые функции в `domain.engine` |
| `TrainingController` | `state`, `startProgram`, `stopProgram`, `startWorkout`, `checkExercise`, `finishWorkout`, `decideCycle`, `markReportViewed`, `reconcile`, `nextTransitionAt` |
| `CoachController` | `currentLine: StateFlow<CoachLine?>`, `coaches`, `selectedCoach`, `select(id)`, `dismissLine()`, `onAppEntered()`, подписка на события домена |
| `ProfileController` | `profile: Flow<Profile>`, `update(...)` |
| `RecordsController` | `records: Flow<…>`, `save/delete` |
| `SettingsController` | `pushEnabled`, `setPushEnabled` |

Ошибки — типизированные `DomainError` (sealed), а не исключения: `ActiveProgramExists`, `ProgramNotEditable`, `ValidationFailed(List<ValidationIssue>)`, `NotToday`, `SessionNotRunning`, `AlreadyChecked`, `NoActiveProgram`, … ViewModel мапит их в текст/эффект.

## 7. Персистентность (`:infra`)

Порты в `:domain`: `ProgramRepository`, `TrainingStateStore`, `WorkoutLogRepository`, `CoachMemoryStore`, `ProfileRepository`, `RecordsRepository`, `SettingsRepository`. Реализации — Room/DataStore.

- **Программы** (редактируемый каталог): таблицы `program`, `program_day` (`kind`, позиция, name, duration_min, autostart_min_of_day), `exercise` (`type`-дискриминатор + nullable-колонки параметров, позиция).
- **Прогресс запуска** (`TrainingState`): одна строка `training_state(id=1, run_json, schema_version)`; `run` сериализуется DTO (kotlinx.serialization), **не** доменными классами: переименование доменного класса не должно ломать сохранённые данные. Таблица `report`. Снапшот программы лежит внутри `run_json`.
- **Лог**: `workout_log` (скаляры + `exercises_json`). Append-only.
- **Тренер**: `coach_memory` (последняя реплика, служебные отметки правил).
- **Профиль, рекорды**: Room (`profile` — одна строка; `personal_record` + `record_param`).
- **Настройки** (`pushEnabled`, `selectedCoachId`): DataStore Preferences.
- `TrainingStateStore.commit(state, logAppends)` — одна `@Transaction`: обновляет `training_state`, `report`, добавляет в `workout_log`.
- Room: `exportSchema = true`, схемы в репозитории (`app/schemas` или `infra/schemas`), миграции с тестами. `fallbackToDestructiveMigration` запрещён. Даты — ISO-8601 строки/epoch millis; время суток — минуты от полуночи.
- Round-trip тест на каждый маппер DTO ↔ домен.

## 8. Платформа (`:infra` + `:app`)

### 8.1 Триггеры reconcile

Reconcile вызывается: при `ON_START` приложения; при старте процесса (`Application.onCreate` — не блокируя UI); по alarm-приёмнику; по `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`; в foreground — один отложенный вызов на момент `nextTransitionAt`. Опроса раз в секунду для логики **нет**. Тик раз в секунду — только для отрисовки обратного отсчёта на видимом экране (`TickerFlow`, считает `deadlineAt − now` локально).

### 8.2 Уведомления

```kotlin
// :domain
data class PlannedNotification(val id: Int, val kind: NotificationKind, val at: Instant)
object NotificationPlanner {
    fun plan(state: TrainingState, settings: Settings, now: Instant, zone: ZoneId): List<PlannedNotification>
    fun shouldShow(kind: NotificationKind, stateAtFire: TrainingState, appInForeground: Boolean, settings: Settings): Boolean
}
interface ReminderScheduler { fun replaceAll(plan: List<PlannedNotification>) }   // порт
interface NotificationTextSource { fun text(kind: NotificationKind): String }       // шов: позже можно подключить банки фраз тренера
```

- План: `DAILY_REMINDER` на 12:00 для будущих/текущего рабочих слотов в пределах текущего цикла (горизонт ≤ 7 суток); `AUTOSTART_LATE` в `autoStartAt` слотов с `autoStartEnabled`. Ничего на 00:00.
- `shouldShow` решает **в момент срабатывания** после `reconcile`: напоминание 12:00 — только если слот сегодня рабочий и `NotStarted`; «опоздание» — только если сессия `Running/AUTO` и приложение не на экране; всё — только если `pushEnabled`.
- Реализация (`:infra`): `AlarmManager.setExactAndAllowWhileIdle` при `canScheduleExactAlarms()`, иначе `setAndAllowWhileIdle` (деградация допустима: логика от этого не страдает, т. к. состояние догоняется reconcile). Переустановка всего плана после каждого `commit`, при запуске, boot, смене времени/пояса. Приёмник использует `goAsync()`, вызывает `TrainingController.reconcile()` → `shouldShow` → показ.
- Каналы создаются при старте. Разрешение `POST_NOTIFICATIONS` — по F-53. **Foreground Service не используется.**

### 8.3 Присутствие

Порт `AppPresence { val isForeground: Boolean }` (реализация на `ProcessLifecycleOwner`).

## 9. Слой представления (`:app`)

### 9.1 Контракт ViewModel

```kotlin
abstract class ScreenViewModel<S, I, E>(initial: S) : ViewModel() {
    val state: StateFlow<S>            // единственный источник отрисовки
    abstract fun onIntent(intent: I)   // единственный вход
    val effects: Flow<E>               // разовые события: навигация, snackbar
}
```

Composable экрана: `@Composable fun XScreen(state: XUiState, onIntent: (XIntent) -> Unit)` — **stateless**; отдельная обёртка `XRoute()` получает ViewModel и вызывает `XScreen`. У каждого `XScreen` есть `@Preview` с фейковым `UiState` (это же основа для быстрой правки под новый UI).

`XUiState` — только данные для отрисовки (строки уже локализованы маппером или `@StringRes` + аргументы; цвета/иконки — через enum'ы презентации). `XUiMapper` — чистые функции Model → UiState, покрываются JVM-тестами.

### 9.2 Навигация

Single-Activity, Navigation Compose, типобезопасные маршруты. Нижняя панель из 4 вкладок с **раздельными back stack**. Графы:

- **Главная**: `Home` → `Programs` → `ProgramEditor` → `DayEditor`; `Home` → `Workout`.
- **Прогресс**: `Progress` (заглушка).
- **Тренер**: `CoachList` → `CoachCard`.
- **Профиль**: `Profile` → `Settings` | `ProfileEditor` | `Records` → `RecordView`.

Модальные окна (выбор стартового дня, типы/форма упражнения, редактор рекорда, итоги) — диалоги/bottom sheet, вызываются состоянием/эффектами, не отдельными маршрутами (кроме случаев, когда макет явно требует иного).

### 9.3 `ModalHost` (очередь модалок)

В корне `MainScaffold` стоит `ModalHost`, который показывает **одну** модалку за раз из упорядоченной очереди. Источники: (а) `TrainingController.state.reports` (непросмотренные отчёты; порядок D-13; показывать только после того как HOME отрисован); (б) локальные запросы экранов. Закрытие отчёта → `markReportViewed` (для `Report.Cycle` закрытие только через `decideCycle`). Так итоги работают одинаково при ручном завершении, автозакрытии в фоне и холодном старте.

### 9.4 Тема и строки

Тема и токены — в `ui.theme` (единое место для смены внешнего вида под новый UI). Строки — только `res/values/strings.xml`. Иконки в макете — эмодзи-заглушки; временно использовать Material Icons, пометить `TODO(ui)`.

## 10. Подсистема тренера (`domain.coach`)

Требование: усложнение логики тренера (новые триггеры, условия, персонажи, приоритеты, контекст) = **добавление** классов/данных, а не правка движка или UI.

### 10.1 Конвейер

```
DomainEvent ─► CoachEvent ─► Rules ─► LineIntent[] ─► Arbiter ─► PhrasePicker ─► CoachLine ─► (UI)
                                  ▲                                  ▲
                         CoachContext (read-only факты)        PhraseBank(coachId, key)
```

```kotlin
sealed interface CoachEvent {
    data class AppEntered(val at: Instant) : CoachEvent
    data class ReportPresented(val report: Report) : CoachEvent      // реплика привязана к показу отчёта (D-14)
    data class SessionClosed(val sessionId: …) : CoachEvent          // аннулирует отложенные мотивации
    data class Tick(val at: Instant) : CoachEvent                    // только пока экран виден и идёт сессия
}

data class CoachContext(
    val now: Instant, val today: LocalDate, val run: ProgramRun?,
    val todaySlot: DaySlot?, val memory: CoachMemory,
    val log: LogFacts,                 // например: fullCompletionsOn(date), lastEntries(n)
    val coach: CoachDefinition,
)

interface CoachRule {
    val id: RuleId
    fun evaluate(event: CoachEvent, ctx: CoachContext): LineIntent?    // чистая функция
}

data class LineIntent(
    val key: PhraseKey,                // что сказать: AutoStartGreeting, RestDayGreeting, DayCompleted, DayIncomplete, DaySkipped, CycleFullSuccess, …
    val variant: Set<PhraseTag> = emptySet(),   // SAME_DAY_REPEAT, SOFT, … — уточняют выбор фразы
    val priority: Priority,            // SYSTEM > RESULT > MOTIVATION
    val suppresses: Set<RuleId> = emptySet(),
    val deliverAt: Instant? = null,    // отложенные (мотивация)
)

class CoachArbiter       // по приоритету выбирает победителя, применяет suppresses и правило 10 минут тишины для MOTIVATION
interface PhraseBank { fun phrases(coachId: CoachId, key: PhraseKey, tags: Set<PhraseTag>): List<Phrase> }
interface PhrasePicker { fun pick(candidates: List<Phrase>): Phrase }   // инъекция Random → детерминируемые тесты

data class CoachDefinition(val id: CoachId, val displayName: String, val characterTitle: String, val bio: String,
                           val previewPhrase: String, val avatarAsset: String,
                           val extraRules: List<RuleId> = emptyList())    // персонаж-специфичные правила
class CoachRegistry(rules: Map<RuleId, CoachRule>, coaches: List<CoachDefinition>) {
    fun rulesFor(coach: CoachId): List<CoachRule>   // общие + extraRules
}
```

### 10.2 Правила v1 (реализовать ровно эти)

| Правило | Событие | Выход |
|---|---|---|
| `AutoStartGreetingRule` | `AppEntered`, сегодня `Running` с `startKind=AUTO` | `AutoStartGreeting`, priority SYSTEM. |
| `RestDayGreetingRule` | `AppEntered`, сегодняшний слот — отдых | `RestDayGreeting`, SYSTEM. |
| `DayResultRule` | `ReportPresented(Report.Day)` | `DayCompleted`/`DayIncomplete`/`DaySkipped`, RESULT; теги `SAME_DAY_REPEAT` (если `log.fullCompletionsOn(today) > 0` на момент до записи) для успеха — усиленная похвала, для частичного — тег `SOFT`. |
| `CycleResultRule` | `ReportPresented(Report.Cycle)` | `CycleFullSuccess`/`CyclePartial`/`CycleFailure`, RESULT; подавляет `DayResultRule`. |
| `MotivationRule` | `Tick` в сессии | `Motivation`, MOTIVATION; ≤1 за сессию; условия времени — `MotivationPolicy` (D-16); аннулируется `SessionClosed`; отменяется любым событием выше. |

### 10.3 Данные

`assets/coaches/<id>.json`: `{id, displayName, characterTitle, bio, previewPhrase, avatar, extraRules:[…], phrases:{ "<PhraseKey>": [ {"text": "...", "tags": [...]} ] }}`. Загрузчик — в `:infra`, валидация схемы при загрузке (отсутствующий ключ фраз → понятная ошибка в debug, fallback-фраза в release). Контент — плейсхолдеры (`TODO(content)`).

### 10.4 Показ

`CoachController.currentLine: StateFlow<CoachLine?>`. Время жизни/сброс — D-15. UI-компонент `CoachBar` (аватар + облачко) просто отображает `CoachLine?`. Размещение — D-26.

## 11. DI и структура пакетов

Hilt-модули в `:app` (`di/`): `DomainModule` (контроллеры, движок, реестр тренера), `InfraModule` (Room, репозитории, планировщик, `TimeSource`, `AppPresence`). `:domain` не использует аннотации Hilt (конструкторная инъекция вручную в `@Provides`).

```
:domain  app.trackfit.domain
   model/ engine/ coach/ controller/ ports/ time/
:infra   app.trackfit.infra
   db/ (entities, dao, migrations) dto/ mapping/ prefs/ notifications/ alarms/ assets/ time/
:app     app.trackfit.app
   di/ navigation/ ui/{theme,components,home,programs,editor,workout,coach,profile,records,settings,progress,modal}/ 
   triggers/ (ForegroundTriggers, receivers регистрируются в манифесте)
```

## 12. Обработка ошибок и логирование

Домен — `Outcome`/`DomainError`, без исключений для ожидаемых ситуаций. Инфраструктурные исключения (диск) ловятся на границе контроллера и превращаются в `DomainError.StorageFailure`. Логирование — `Log`/Timber-подобная обёртка в `:app`, в домене — через порт `Logger` (опц.). Не логировать персональные данные.

## 13. Рецепты расширения (как менять, не ломая архитектуру)

| Задача | Что менять |
|---|---|
| Новое правило реплики тренера | Класс `CoachRule` в `domain.coach.rules`, регистрация в `CoachModule`, ключи фраз в JSON, тест. UI, движок запуска — не трогать. |
| Новый тренер | JSON в `assets/coaches/` (+ `extraRules` при необходимости), фото. Кода не требуется. |
| Новое условие контекста (напр. «серия из 3 успешных циклов») | Поле/метод в `LogFacts`/`CoachContext` + порт-реализация в `:infra`; правило использует. |
| Новый тип отчёта/уведомления | Вариант `Report`/`NotificationKind`, ветка в `NotificationPlanner`/`ModalHost`-маппере, тест. |
| Новый тип упражнения | Вариант `Exercise`, запись в `ExerciseSpecs`, DTO/колонки + миграция, форма в `:app`, строка списка в маппере. Валидатор/движок не меняются. |
| Правило «старт при активной программе — заменять» | Одна `StartProgramPolicy` (D-03), экран 1К. |
| Новый макет UI | AGENTS.md §9. Только `:app`. |
| Заполнить вкладку «Прогресс» | Читать `WorkoutLogRepository`; новый `ProgressController` (read-only) + экран. Домен/БД уже готовы. |

## 14. Тестовая стратегия

**`:domain` (JUnit, без Android)** — основной объём. `FakeTimeSource`, фабрики сценариев (`program(...)`, `runStartedOn(...)`). Обязательные сценарии `TrainingEngine`:

1. Старт с произвольного дня; даты слотов; wrap по кругу.
2. Ручной старт/завершение: 0, 1…n−1, n отметок → статусы.
3. Необратимость отметок; повторная отметка; отметка после дедлайна → `SessionNotRunning`.
4. Автостарт при закрытом приложении: `reconcile` на 19:00 при автостарте 18:30 и длительности 30 мин → закрыт `Skipped` (startedAt=18:30).
5. Автостарт и приложение открыто в 18:40 (`Running`, остаток 20 мин для 60-мин. дня).
6. Дедлайн через полночь: старт 23:30, длительность 90 мин → закрытие в 00:00, причина `MIDNIGHT`.
7. Многодневное отсутствие (3–5 суток): пропущенные дни → `Skipped`, отчёты по порядку, итог цикла.
8. Последний день — рабочий (закрыт вручную) → сразу `Report.Cycle`, `Report.Day` не создан.
9. Последний день — отдых → цикл закрывается в 00:00 (при reconcile), `AwaitingDecision`.
10. Итоги цикла: все `Завершено` / все `Пропущено` / микс; расчёт до сброса.
11. `decideCycle`: Switch (run=null), Continue (новый цикл со следующих суток, тот же стартовый индекс, `cycleNumber+1`, чистые статусы).
12. Старт первого слота после времени автостарта → автостарт отключён (D-06).
13. Остановка очищает прогресс, лог остаётся.
14. Смена часового пояса/сдвиг часов назад: ничего закрытое не открывается (D-25). Граница DST.
15. Идемпотентность `reconcile`.
16. `nextTransitionAt` для каждого состояния.
17. Повторный успех в те же сутки (контекст для тренера).
18. `ProgramValidator`: все правила F-07; `ExerciseSpecs`.

**`domain.coach`**: приоритеты, `suppresses`, 10-минутная тишина, сдвиг +10, ≤1 мотивации за сессию, аннулирование, детерминизм с фиксированным `Random`; «рецептный» тест — добавление фиктивного правила и фиктивного тренера без правок остального кода.

**`:infra`** (Robolectric/in-memory Room): round-trip мапперов, транзакционность `commit`, миграции, `NotificationPlanner`→`ReminderScheduler` (через фейк).

**`:app`**: тесты `UiMapper` и ViewModel (с фейковыми портами/контроллерами, Turbine). Compose UI-тесты — по минимуму (по желанию агента на ключевых модалках); `@Preview` обязательны.

## 15. Автоматические архитектурные ограничения (`checkArchitecture`)

Gradle-задача (в корневом `build.gradle.kts` или `buildSrc`/convention-плагин), входит в гейт. Проверки сканированием исходников:

1. В `domain/src/**` нет импортов `android.`, `androidx.`, `dagger.`, `hilt.`, `org.jetbrains.compose`.
2. В `domain/src/main/**` нет вызовов `Instant.now(`, `LocalDate.now(`, `LocalDateTime.now(`, `LocalTime.now(`, `System.currentTimeMillis(`, `ZoneId.systemDefault(` (кроме файла с реализацией `TimeSource`, если она окажется в домене — она должна быть в `:infra`).
3. В `app/src/main/**/ui/**` нет импортов `app.trackfit.domain.engine`, `app.trackfit.domain.controller`, `app.trackfit.domain.ports`, `app.trackfit.infra`. Composable видят только `UiState`/`UiIntent`/примитивы и модельные типы, разрешённые явным allow-list'ом (`docs`‑комментарий в задаче).
4. В `app/src/main/**/*.kt` нет строковых литералов с кириллицей (кроме `@Preview`-данных в файлах `*Preview*.kt`).
5. В `:infra` нет импортов `app.trackfit.app`.

Нарушение = ошибка сборки. Исключения — только через явный allow-list в самой задаче с комментарием `// D-xx`.
