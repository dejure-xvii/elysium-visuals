# Elysium Visuals

[![build](https://github.com/kirillyalovenko463-hash/elysium-visuals/actions/workflows/build.yml/badge.svg)](https://github.com/kirillyalovenko463-hash/elysium-visuals/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/kirillyalovenko463-hash/elysium-visuals)](https://github.com/kirillyalovenko463-hash/elysium-visuals/releases/latest)

Клиентский визуальный мод для Minecraft на **Fabric**: ClickGUI с темами (включая Liquid Glass),
настраиваемый HUD и десятки визуальных и удобных модулей. Мод работает только на клиенте —
на сервер ставить не нужно.

## Скриншоты

> Скриншоты-заглушки — скоро будут заменены настоящими.

| ClickGUI | Темы |
|---|---|
| ![ClickGUI](docs/screenshots/clickgui.svg) | ![Темы](docs/screenshots/themes.svg) |
| **HUD** | **Эффекты** |
| ![HUD](docs/screenshots/hud.svg) | ![Эффекты](docs/screenshots/effects.svg) |

## Возможности

- **ClickGUI** с поиском, анимациями и темами: Elysium, Тёмная, Светлая, Liquid Glass и своя тема с выбором цветов.
- **HUD**: ватермарка, таргет, бинды, эффекты, задержки, друзья, броня, координаты, информация о сервере и мире.
- **Render**: CustomSky, Ambience, Hands, TargetESP, Trail, JumpCircle, KillEffect, LootBeams, Particles,
  MotionBlur, Predictions, BlockOverlay, Crosshair, SwingAnimation, ViewModel, ShulkerPreview, NoRender и др.
- **Player**: AutoSprint, AutoTool, Armor HUD, Item Counter, Health Alert, NoFriendDamage.
- **Farm**: Loot Tracker, Session Timer, Tool Guard, Inventory Full.
- **Utils**: друзья, уведомления, NameProtect, Death Point, ItemScroller, AutoAccept, FakePlayer и др.
- **Конфиги** — сохранение и загрузка нескольких профилей настроек.

## Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5+** для Minecraft **26.2**.
2. Скачайте [Fabric API](https://modrinth.com/mod/fabric-api) для 26.2 и положите в папку `mods`.
3. Скачайте `elysium-visuals-<версия>.jar` со страницы [Releases](https://github.com/kirillyalovenko463-hash/elysium-visuals/releases/latest)
   (не `-sources.jar`) и положите в папку `mods`:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Запустите игру с профилем Fabric. Нужна **Java 25+**.

## Использование

- **Правый Shift** — открыть ClickGUI (клавишу можно сменить в «Настройки → Управление»).
- Команды в чате (префикс `.`):

| Команда | Описание |
|---|---|
| `.help` | список команд |
| `.cfg <save\|load\|remove\|reset\|list> [имя]` | управление конфигами |
| `.friend <add\|remove\|list> [ник]` | список друзей |
| `.fakeplayer <spawn\|remove>` | локальная копия игрока для проверки эффектов |
| `.panic` | полностью скрыть клиент до перезапуска игры |

## Сборка из исходников

```sh
./gradlew build
```

Готовый мод появится в `build/libs/`. Нужен JDK 25.

## Релизы

Релизы собираются автоматически GitHub Actions: достаточно запушить тег вида `vX.Y.Z`:

```sh
git tag v1.0.1
git push origin v1.0.1
```

Версия мода берётся из имени тега.

## Лицензия

[CC0 1.0](LICENSE). Шрифт Inter распространяется под [SIL Open Font License](src/client/resources/assets/elysium-visuals/font/inter-ofl.txt).
