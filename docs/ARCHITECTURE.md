# Архитектура TrackFit

Статус: **обязательна к исполнению**. Эскизы кода — иллюстрация контрактов; имена и сигнатуры можно уточнять, **инварианты и границы слоёв — нельзя**. Изменение архитектуры = запись в `DECISIONS.md` (тип A) + согласование с владельцем.

---

## 1. Ключевые принципы

1. **Бизнес-логика отдельно от View — физически.** Логика в Gradle-модуле `:domain` (чистая Java 17). Android-классы туда не попадут на уровне компиляции.
2. **MVC-вариация с пассивным View и однонаправленным потоком данных:** Model → Controller ← View (через ViewModel-адаптер). Подробно §3.
3. **Время — входной параметр, а не окружение.** Вся логика — функции `(state, now, zone, command) → (state', events)`. Это делает автостарты, дедлайны, полночь и многодневное отсутствие тестируемыми без эмулятора.
4. **Состояние выводится, а не «тикает».** Процесс Android может быть убит, будильники — задержаны. Поэтому истина — сохранённые факты + часы. Любое обращение к состоянию начинается с `reconcile(now)`, который «догоняет» всё, что должно было произойти за время отсутствия (идемпотентно).
5. **Одна точка записи.** Команды последовательно выполняет однопоточный Executor, каждая — одна транзакция.
6. **Расширяемость тренера** — через композицию (правила, банки фраз, реестр), а не через ветвление.
7. **UI заменяем.** Макеты будут меняться; Fragment и View отвечают за отображение и ввод; весь UI-специфичный код сосредоточен в `:app`.

## 2. Модули

```
          ┌───────────────────────────── :app ─────────────────────────────┐
          │ Android Views, XML, Fragments, ViewModels, navigation          │
          │ Hilt wiring, Activity, ModalHost, ForegroundTriggers            │
          └───────────────┬───────────────────────────┬────────────────────┘
                          │                           │
                          ▼                           │
          ┌──────────── :infra ────────────┐          │
          │ Room, SharedPreferences, DTO,  │          │
          │ AlarmManager scheduler,        │          │
          │ notifications, receivers,      │          │
          │ SystemTimeSource, assets loader│          │
          └───────────────┬────────────────┘          │
                          ▼                           ▼
          ┌──────────────────────── :domain (Java 17   ) ──────────────────┐
          │ model · engine (pure) · coach · controller · ports              │
          └──────────────────────────────────────────────────────────────────┘
```

| Модуль | Плагин | Зависит от | Содержит |
|---|---|---|---|
| `:domain` | `java-library` | стандартная библиотека Java | Модель, движок, тренер, контроллеры, порты. **Никаких** `android.*`/`androidx.*`. |
| `:infra` | `com.android.library` | `:domain`, Room, SharedPreferences, org.json | Реализации портов. Без UI. |
| `:app` | `com.android.application` | `:domain`, `:infra` | UI, DI-сборка. |

Правило импортов: `:app` не обращается к Room/DAO/DTO из `:infra` напрямую, только через порты/интерфейсы домена (DI собирает реализации).

## 3. Слои MVC-вариации

| Роль MVC | Где | Что делает | Чего **не** делает |
|---|---|---|---|
| **Model** | `domain.model`, `domain.engine`, `domain.coach`, `domain.ports` | Сущности, инварианты, правила (статусы, циклы, дедлайны, валидация, выбор реплик), чистые функции. | Не знает про Android, БД, UI. |
| **Controller** | `domain.controller` | Принимает команды (от View), читает/пишет состояние через порты, вызывает движок, коммитит транзакцию, уведомляет Java-наблюдателей о событиях и состоянии. | Не форматирует строки/даты для экрана, независим от UI. |
| **View** | `:app` — Fragment и XML-разметка | Рисует `UiState`, отправляет `UiIntent`. Stateless. | Не содержит правил, не вызывает контроллеры, не читает время. |
| **View-adapter** | `:app` — `*ViewModel`, `*UiMapper` | Подписывается на обновления контроллеров через Java-интерфейсы, мапит Model → `UiState` (форматирование, локализация через ресурсы), мапит `UiIntent` → вызов контроллера, держит эфемерное UI-состояние (какое поле в фокусе, открыт ли диалог). | Не принимает бизнес-решений (никаких «если статус X то…»). Допустимо лишь презентационное «если нет данных — показать заглушку». |

Поток: `View → UiIntent → ViewModel → Controller.command() → Engine → Store(commit) → наблюдатель состояния → ViewModel(map) → UiState → View`.

**Тест «границы»:** если правило можно сформулировать словами из `trackfit.md` — оно в `:domain`. Если это про пиксели/форматирование/навигацию — в `:app`.

## 4. Доменная модель (эскиз)

Идентификаторы `ProgramId`, `DayId` и `ExerciseId` — отдельные неизменяемые типы Java со значением UUID. Модели реализуются неизменяемыми классами или Java records. Для вложенных коллекций создаются защитные копии, чтобы нельзя было изменить уже сохранённый снимок.

| Тип | Поля и ограничения |
|---|---|
| `Program` | Идентификатор, имя и упорядоченный список `CycleDay`. |
| `CycleDay.Work` | Идентификатор, имя, обязательная длительность в минутах (≥1), необязательное время автостарта (`LocalTime`) и список упражнений (≥1). |
| `CycleDay.Rest` | Только идентификатор. |
| `Exercise` | Общие поля четырёх типов — идентификатор, имя и необязательная заметка. |
| `Strength` | Обязательные подходы и повторы. Вес (кг) и отдых (секунды) опциональны. |
| `Cardio` | Обязательная длительность в секундах. Интенсивность и целевой пульс опциональны. |
| `Balance`, `Stretch` | Обязательная длительность в секундах. Подходы и сторона (`BodySide.LEFT`, `RIGHT`, `BOTH`) опциональны. |

`ExerciseSpecs` определяет обязательные и опциональные поля и состав строки упражнения по таблице макета. Эти метаданные используют и валидатор, и форма.

### Запуск и прогресс

| Тип | Поля и ограничения |
|---|---|
| `ProgramRun` | Снимок программы на момент старта, `startDayIndex`, `cycleNumber` (от 1), фаза и монотонная отметка `lastReconciledAt` типа `Instant`. Новый цикл начинается с того же стартового дня (D-08). |
| `CyclePhase` | `InProgress` содержит N слотов с последовательными датами. `AwaitingDecision` сохраняет слоты и вычисленный `CycleOutcome`. |
| `DaySlot` | Локальная дата, индекс дня программы и состояние рабочего дня. У дня отдыха состояния тренировки нет. |
| `WorkState` | Все варианты хранят `autoStartEnabled` (D-06). `NotStarted` ещё без сессии. `Running` всегда содержит `Session`. `Closed` содержит `DayResult` и завершённую сессию, если она стартовала. |
| `Session` | `startedAt`, способ старта (`MANUAL` или `AUTO`), `deadlineAt` (ранняя из границ — конец длительности и полночь, D-07), необратимые отметки в `Map<ExerciseId, Instant>`. |
| `CloseReason` | `MANUAL`, `DEADLINE`, `MIDNIGHT`. |
| `WorkStatus` | Производный статус — `NOT_STARTED`, `RUNNING`, `COMPLETED`, `INCOMPLETE`, `SKIPPED`. |
| `CycleOutcome` | `FULL_SUCCESS`, `PARTIAL_SUCCESS`, `FULL_FAILURE`. |

### Агрегат состояния, отчёты, лог

`TrainingState` содержит активный запуск (может отсутствовать) и очередь отчётов. У каждого `Report` есть идентификатор, время создания и признак просмотра. `Report.Day` сохраняет дату, имя дня, статус, число отмеченных и общее число упражнений. `Report.Cycle` хранит номер и результат цикла и закрывается только решением пользователя.

`WorkoutLogEntry` сохраняет дату, имена программы и дня, статус, причину закрытия, время старта (если был) и закрытия, номер цикла и результаты упражнений (имя, тип, отметка). Это самостоятельная неизменяемая запись истории, которая сохраняется при остановке или смене программы (D-12).

## 5. Модель времени: reconcile

### 5.1 Контракт

```java
interface TimeSource {
    Instant now();
    ZoneId zone();
}

record EngineResult(TrainingState state, List<WorkoutLogEntry> logAppends,
                    List<DomainEvent> events) {}

interface TrainingEngine {
    EngineResult reconcile(TrainingState state, Instant now, ZoneId zone, LogFacts log);
    Optional<Instant> nextTransitionAt(TrainingState state, ZoneId zone);
}
```

Команды `startProgram` (программа и стартовый индекс), `stopProgram`, `startWorkout`, `checkExercise` (идентификатор упражнения), `finishWorkout` и `decideCycle` (решение) получают текущее состояние, время, пояс и факты лога, нужные для reconcile. Результат включает обновлённое состояние, записи лога, события и типизированный исход запрошенного действия.

Все команды **внутри** вызывают `reconcile(now)` первым шагом, затем применяют действие. Так команда «отметить упражнение» после просроченного дедлайна честно получит `SessionNotRunning`, даже если ни один таймер не сработал.

### 5.2 Алгоритм `reconcile` (для `phase = InProgress`)

Идти по слотам в порядке дат, для каждого слота с `date ≤ today(now, zone)`:

1. **Рабочий, `NotStarted`, `autoStartEnabled`, автостарт задан**: `startInstant = date.atTime(autoStart).atZone(zone).toInstant()`. Если `now ≥ startInstant` → перейти в `Running` с `startedAt = startInstant` (а не `now`!), `startKind = AUTO`, `deadlineAt = min(startInstant + duration, конец суток date)`.
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

Чистая функция: минимум из ближайших автостартов, дедлайнов, полуночей слотов. Её используют (а) foreground-триггер UI (один отложенный вызов до следующего момента вместо опроса), (б) `NotificationPlanner`.

### 5.5 Единый писатель

UI (через ViewModel) и alarm-приёмник получают через DI один экземпляр `TrainingController` и отправляют команды на общий однопоточный `Executor`. Каждая команда целиком выполняется в очереди писателя.

1. Загрузить актуальное состояние и факты лога. Прочитать время и пояс через `TimeSource`.
2. Выполнить reconcile и запрошенное действие. Сохранить изменения, которые вывел reconcile, даже если после дедлайна само действие вернуло `SessionNotRunning`.
3. При изменениях одной транзакцией записать состояние, отчёты и дополнения к логу. После успешной записи уведомить наблюдателей и опубликовать события. При повторном reconcile с тем же результатом запись пропускается.
4. Вернуть вызывающему результат через Java API, например `CompletionStage<Outcome<T>>`. ViewModel передаёт новое состояние на главный поток.

Java-порт наблюдения возвращает подписку с методом снятия. Наблюдатель состояния получает текущий снимок при подписке, затем зафиксированные обновления по порядку. Разовые события получают только активные подписчики.

## 6. Контроллеры (публичная поверхность домена для View)

| Контроллер | Команды / потоки |
|---|---|
| `ProgramController` | подписка на список `ProgramSummary`, `load(id)`, `save(draft)` (валидация, запрет при активной), `createDraft()`, операции над `ProgramDraft` — чистые функции в `domain.engine` |
| `TrainingController` | `state`, `startProgram`, `stopProgram`, `startWorkout`, `checkExercise`, `finishWorkout`, `decideCycle`, `markReportViewed`, `reconcile`, `nextTransitionAt` |
| `CoachController` | подписка на текущую реплику, `coaches`, `selectedCoach`, `select(id)`, `dismissLine()`, `onAppEntered()`, подписка на события домена |
| `ProfileController` | подписка на `Profile`, `update(...)` |
| `RecordsController` | подписка на список рекордов, `save/delete` |
| `SettingsController` | `pushEnabled`, `setPushEnabled` |

Ошибки — типизированные `DomainError` (sealed), а не исключения: `ActiveProgramExists`, `ProgramNotEditable`, `ValidationFailed(List<ValidationIssue>)`, `NotToday`, `SessionNotRunning`, `AlreadyChecked`, `NoActiveProgram`, … ViewModel мапит их в текст/эффект.

## 7. Персистентность (`:infra`)

Порты в `:domain`: `ProgramRepository`, `TrainingStateStore`, `WorkoutLogRepository`, `CoachMemoryStore`, `ProfileRepository`, `RecordsRepository`, `SettingsRepository`. Реализации — Room/SharedPreferences.

- **Программы** (редактируемый каталог): таблицы `program`, `program_day` (`kind`, позиция, name, duration_min, autostart_min_of_day), `exercise` (`type`-дискриминатор + nullable-колонки параметров, позиция).
- **Прогресс запуска** (`TrainingState`): одна строка `training_state(id=1, run_json, schema_version)`; Java-маппер в `:infra` сохраняет `run` через DTO и `org.json` с явными ключами. Формат сохраняет совместимость при переименовании доменных классов. Таблица `report`. Снапшот программы лежит внутри `run_json`.
- **Лог**: `workout_log` (скаляры + `exercises_json`). Append-only.
- **Тренер**: `coach_memory` (последняя реплика, служебные отметки правил).
- **Профиль, рекорды**: Room (`profile` — одна строка; `personal_record` + `record_param`).
- **Настройки** (`pushEnabled`, `selectedCoachId`): SharedPreferences.
- `TrainingStateStore.commit(state, logAppends)` — одна `@Transaction`: обновляет `training_state`, `report`, добавляет в `workout_log`.
- Room: `exportSchema = true`, схемы в репозитории (`app/schemas` или `infra/schemas`), миграции с тестами. `fallbackToDestructiveMigration` запрещён. Даты — ISO-8601 строки/epoch millis; время суток — минуты от полуночи.
- Round-trip тест на каждый маппер DTO ↔ домен.

## 8. Платформа (`:infra` + `:app`)

### 8.1 Триггеры reconcile

Reconcile вызывается: при `ON_START` приложения; при старте процесса (`Application.onCreate` — не блокируя UI); по alarm-приёмнику; по `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`; в foreground — один отложенный вызов на момент `nextTransitionAt`. Опроса раз в секунду для логики **нет**. Тик раз в секунду — только для отрисовки обратного отсчёта на видимом экране (обновление через Handler главного потока, расчёт `deadlineAt − now` локально, снятие callback при остановке экрана).

### 8.2 Уведомления

```java
record PlannedNotification(int id, NotificationKind kind, Instant at) {}

interface NotificationPlanner {
    List<PlannedNotification> plan(TrainingState state, Settings settings, Instant now, ZoneId zone);
    boolean shouldShow(NotificationKind kind, TrainingState stateAtFire,
                       boolean appInForeground, Settings settings);
}

interface ReminderScheduler {
    void replaceAll(List<PlannedNotification> plan);
}

interface NotificationTextSource {
    String text(NotificationKind kind);
}
```

`ReminderScheduler` и `NotificationTextSource` — порты. Источник текстов допускает подключение банков фраз тренера.

- План: `DAILY_REMINDER` на 12:00 для будущих/текущего рабочих слотов в пределах текущего цикла (горизонт ≤ 7 суток); `AUTOSTART_LATE` в `autoStartAt` слотов с `autoStartEnabled`. Ничего на 00:00.
- `shouldShow` решает **в момент срабатывания** после `reconcile`: напоминание 12:00 — только если слот сегодня рабочий и `NotStarted`; «опоздание» — только если сессия `Running/AUTO` и приложение не на экране; всё — только если `pushEnabled`.
- Реализация (`:infra`): `AlarmManager.setExactAndAllowWhileIdle` при `canScheduleExactAlarms()`, иначе `setAndAllowWhileIdle` (деградация допустима: логика от этого не страдает, т. к. состояние догоняется reconcile). Переустановка всего плана после каждого `commit`, при запуске, boot, смене времени/пояса. Приёмник использует `goAsync()`, вызывает `TrainingController.reconcile()` → `shouldShow` → показ.
- Каналы создаются при старте. Разрешение `POST_NOTIFICATIONS` — по F-53. **Foreground Service не используется.**

### 8.3 Присутствие

Порт `AppPresence` с методом `boolean isForeground()` (реализация на `ProcessLifecycleOwner`).

## 9. Слой представления (`:app`)

### 9.1 Контракт ViewModel

```java
abstract class ScreenViewModel<S, I> extends ViewModel {
    abstract LiveData<S> state();
    abstract void onIntent(I intent);
}
```

Fragment подписывается на состояние через `getViewLifecycleOwner()`, передаёт `UiState` в `render` и направляет действия из View Binding во ViewModel. При уничтожении представления освобождает binding. Разовые эффекты (переход, snackbar) доставляются один раз. Отчёты остаются в очереди §9.3 до подтверждения. XML-разметку каждого экрана можно просмотреть в Layout Editor с демонстрационными значениями через атрибуты `tools:`.


`XUiState` — только данные для отрисовки (строки уже локализованы маппером или `@StringRes` + аргументы; цвета/иконки — через enum'ы презентации). `XUiMapper` — чистые функции Model → UiState, покрываются JVM-тестами.

### 9.2 Навигация

Одна Activity с `NavHostFragment`, XML-графами, типизированными аргументами переходов и `BottomNavigationView`. Нижняя панель из 4 вкладок с **раздельными back stack**. Графы:

- **Главная**: `Home` → `Programs` → `ProgramEditor` → `DayEditor`; `Home` → `Workout`.
- **Прогресс**: `Progress` (заглушка).
- **Тренер**: `CoachList` → `CoachCard`.
- **Профиль**: `Profile` → `Settings` | `ProfileEditor` | `Records` → `RecordView`.

Модальные окна (выбор стартового дня, типы/форма упражнения, редактор рекорда, итоги) — диалоги/bottom sheet, вызываются состоянием/эффектами, не отдельными маршрутами (кроме случаев, когда макет явно требует иного).

### 9.3 `ModalHost` (очередь модалок)

В корне Activity находится `ModalHost`, который показывает **одну** модалку за раз из упорядоченной очереди. Источники: (а) `TrainingController.state.reports` (непросмотренные отчёты; порядок D-13; показывать только после того как HOME отрисован); (б) локальные запросы экранов. Закрытие отчёта → `markReportViewed` (для `Report.Cycle` закрытие только через `decideCycle`). Так итоги работают одинаково при ручном завершении, автозакрытии в фоне и холодном старте.

### 9.4 Тема и строки

Тема и токены — в XML-ресурсах `res/values/` (единое место для смены внешнего вида под новый UI). Строки — только `res/values/strings.xml`. Иконки в макете — эмодзи-заглушки; временно использовать векторные drawable с иконками Material, пометить `TODO(ui)`.

## 10. Подсистема тренера (`domain.coach`)

Требование: усложнение логики тренера (новые триггеры, условия, персонажи, приоритеты, контекст) = **добавление** классов/данных, а не правка движка или UI.

### 10.1 Конвейер

```
DomainEvent ─► CoachEvent ─► Rules ─► LineIntent[] ─► Arbiter ─► PhrasePicker ─► CoachLine ─► (UI)
                                  ▲                                  ▲
                         CoachContext (read-only факты)        PhraseBank(coachId, key)
```

| Тип | Контракт |
|---|---|
| `CoachEvent` | Варианты — `AppEntered(at)`, `ReportPresented(report)` (D-14), `SessionClosed(sessionId)` для отмены отложенной мотивации и `Tick(at)` для видимого экрана активной сессии. |
| `CoachContext` | Текущие время и дата, активный запуск и сегодняшний слот (если есть), память тренера, `LogFacts` с историей и числом полных завершений за дату, выбранный `CoachDefinition`. |
| `CoachRule` | Методы `RuleId id()` и `Optional<LineIntent> evaluate(CoachEvent event, CoachContext context)`. Вычисление по входным данным без побочных эффектов. |
| `LineIntent` | Ключ фразы, множество тегов (включая `SAME_DAY_REPEAT`, `SOFT`), приоритет, множество подавляемых правил и время отложенной доставки (если нужно). По умолчанию оба множества пусты. |
| `CoachArbiter` | Выбирает победителя по приоритету SYSTEM > RESULT > MOTIVATION, учитывает подавление и десять минут тишины. |
| `PhraseBank`, `PhrasePicker` | Банк выдаёт список кандидатов по тренеру, ключу и тегам. Селектор выбирает реплику, получает `Random` через конструктор для детерминированных тестов. |
| `CoachDefinition`, `CoachRegistry` | Описание содержит идентификатор, имя, характер, био, демо-фразу, путь аватара и список дополнительных правил (по умолчанию пуст). Реестр собирает общие правила и `extraRules` выбранного тренера по идентификаторам. |

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

Контроллер сообщает подписчикам текущую реплику или её отсутствие. Время жизни/сброс — D-15. UI-компонент `CoachBar` (аватар + облачко) отображает полученную реплику, при её отсутствии скрывает облачко. Размещение — D-26.

## 11. DI и структура пакетов

Hilt и Room используют процессоры аннотаций Java (`annotationProcessor`). Hilt-модули в `:app` (`di/`): `DomainModule` (контроллеры, движок, реестр тренера), `InfraModule` (Room, репозитории, планировщик, `TimeSource`, `AppPresence`). `:domain` не использует аннотации Hilt (конструкторная инъекция вручную в `@Provides`).

```
:domain  app.trackfit.domain
   model/ engine/ coach/ controller/ ports/ time/
:infra   app.trackfit.infra
   db/ (entities, dao, migrations) dto/ mapping/ prefs/ notifications/ alarms/ assets/ time/
:app     app.trackfit.app
   di/ navigation/ ui/{components,home,programs,editor,workout,coach,profile,records,settings,progress,modal}/
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

**`:app`**: Java-тесты `UiMapper` и ViewModel с фейковыми портами и контроллерами, наблюдателем LiveData и управляемым исполнителем. Для ключевых модальных окон можно добавить Espresso-тесты. Для каждого экрана доступен предпросмотр XML в Layout Editor.

## 15. Автоматические архитектурные ограничения (`checkArchitecture`)

Gradle-задача (в корневом `build.gradle` или `buildSrc`/convention-плагин), входит в гейт. Проверки сканированием исходников:

1. В `domain/src/**` нет импортов `android.`, `androidx.`, `dagger.`, `hilt.`.
2. В `domain/src/main/**` нет вызовов `Instant.now(`, `LocalDate.now(`, `LocalDateTime.now(`, `LocalTime.now(`, `System.currentTimeMillis(`, `ZoneId.systemDefault(` (кроме файла с реализацией `TimeSource`, если она окажется в домене — она должна быть в `:infra`).
3. В `app/src/main/**/ui/**` нет импортов `app.trackfit.domain.engine`, `app.trackfit.domain.controller`, `app.trackfit.domain.ports`, `app.trackfit.infra`. View и Fragment видят только `UiState`/`UiIntent`/примитивы и модельные типы, разрешённые явным allow-list'ом (`docs`‑комментарий в задаче).
4. В `app/src/main/**/*.java` нет строковых литералов с кириллицей. В пользовательских атрибутах XML-разметки и меню текст и описания иконок ссылаются на `@string/…` (значения `tools:` служат только для предпросмотра).
5. В `:infra` нет импортов `app.trackfit.app`.
6. Прикладные и тестовые исходники всех трёх модулей пишутся на Java. Задача обнаруживает файлы `*.kt` под `app/src`, `domain/src` и `infra/src`.

Нарушение = ошибка сборки. Исключения — только через явный allow-list в самой задаче с комментарием `// D-xx`.
