# Silmaril

MUD-клиент для игры на сервере adan.ru, используя кастомный протокол.

## Возможности
Помимо стандартных возможностей mud-клиентов (триггеры, алиасы), можно отметить:

* DSL-скрипты на Kotlin
* Интеграция с telegram-ботом для лоров предметов
* А* патфайндинг в любую зону/клетку мира
* Углубленная мульти-оконность и интеграция с конкретным мадом ([подробнее](#Функционал))

![Screenshot of the program](https://res.cloudinary.com/dnmzmkffy/image/upload/v1756648642/okwwsvw2hy7jspts2ejg.png)

## Компиляция

> [!TIP]
> Этот раздел – для разработчиков. Пользовательские инструкции – ниже.
 
Программа написана на Kotlin Compose, в большинстве кода использует Material. Однако для кастомного тайтл бара, был использован [Jewel](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel). 

> [!IMPORTANT]
> В связи с этим, компиляция возможна только на [JBR](https://github.com/JetBrains/JetBrainsRuntime/releases) (21.0.8), т.к. обычная JDK не экспоузит манипуляцию тайтл баром.

> [!IMPORTANT]
> DSL-скрипты корректно распознаются только в [IntelliJ IDEA 2025.1.4.1 (Ultimate Edition)](https://www.jetbrains.com/idea/download/other.html)

Автор не является экспертом в Java, поэтому некоторые инструкции могут быть необязательными. 

*   Установите [JBR](https://github.com/JetBrains/JetBrainsRuntime/releases), чтобы `where java` в консоли указывала на JBR.
*   Откройте проект в [IntelliJ IDEA](https://www.jetbrains.com/idea/download/other.html)
*   После инициализации gradle, запустите gradle-таск `generateResourceAccessorsForMain` (меню gradle находится с правой стороны IDE)
*   (Опционально) Чтобы IDE корректно работала с DSL скриптами, зайдите в File -> Settings -> Editor -> Languages & Framework -> Kotlin -> Kotlin Scripting и нажмите **Scan Classpath**. После перезапуска, в списке должен появиться MudScriptHost (.mud.kts). Отсортируйте его, чтобы он стал предпоследним в списке.
*   (Опционально) Сделайте symlink таким образом, чтобы `Documents\Silmaril\triggers` как будто бы существовала в проекте по пути `src\main\resources\triggers`. Таким образом, вы сможете работать с DSL-скриптами в IDE, получая авто-комплит и подсветку синтаксиса.
*   Используйте таск `run` для запуска в IDE; таск `packageReleaseAppImage` для сборки.

### Знакомство с проектом
* Проект использует архитектуру MVVM, поэтому почти все Composable лежат в папке `view`, модели в `model`, а прокладки между ними в `viewmodel`.
* Используется библиотека Koin для Dependency Injection. Все factory лежат в `Modules.kt`
* Логгирование настраивается в `\src\main\resources\logback.xml` - это библиотека logback.
* Каждое игровое окно - это модель `Profile`. У него есть свой `MudConnection`, `ScriptingEngine`, `MainViewModel`, `MapViewModel`, `GroupModel`, `MobsModel`, т.е. все модели, принадлежащие одному персонажу.
* В `Main` создаются окна `MainWindow`, `MapWindow`, `GroupWindow`, `MobsWindow`, `OutputWindow`, а при переключении окна, в них скармливается другой `Profile`.
* Код еще не очень хорошо рефакторнут, напр. есть god-class `Profile`, который следовало бы разобрать. Там сейчас обрабатываются все текстовые команды, вместо отдельного менеджера. 

## Функционал

@TODO: описать что клиент умеет (патфайндинг, серверные лоры), а потом как им пользоваться - простые команды, а потом от текстовых триггеров к DSL скриптингу