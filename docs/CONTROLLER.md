# Controller guide

## GameSir G8 Galileo (and other USB-C / Bluetooth pads)

Plug the phone into the G8 and that's it. Android reports it as a standard gamepad, the engine forwards it to
the game as an **XInput (Xbox) controller**, and Azeroid switches on WoW Classic's built-in **gamepad mode**
(`GamePadEnable 1` in `Config.wtf`) every time you press PLAY.

In Azeroid's own menus: **D-pad / stick** moves, **A** selects, **B** goes back.

### In-game defaults (WoW's native gamepad mode)

| Control | Action |
|---|---|
| Left stick | Move |
| Right stick | Camera / cursor |
| A / B / X / Y | Action bar buttons (hold a trigger for more) |
| LT / RT | Modifiers for more action bar slots |
| D-pad | Target / interact / menus |
| Menu (≡) | Game menu |

Rebind everything in-game under **Options → Gamepad** (Classic 1.15+), or use the `/console GamePad...` commands.
The community addon **ConsolePort** gives a much more console-like UI and is worth trying.

## On-screen controller

*Quick settings → On-screen controller*:

- **Auto** (default): shown only when **no** physical controller is connected when you press PLAY.
- **On**: always shown, for example to add extra buttons next to the G8.
- **Off**: never shown.

The on-screen layout is a full virtual Xbox pad, so it works with WoW's gamepad mode the same way the G8 does.

To toggle it **while playing**: use the Back gesture → *Input Controls* → tick/untick *Show touchscreen controls*.
There you can also pick or edit layouts. *Advanced → Open engine tools → Controls* has the full layout editor.

## Keyboard & mouse (DeX / Bluetooth)

These work as well. Mouse and keyboard pass straight through to the game.
