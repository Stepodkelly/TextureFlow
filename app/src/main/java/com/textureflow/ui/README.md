# `ui/` — Moth Market screen + voice

| File | Role |
|------|------|
| `MainActivity.java` | Chat list, settings ⋮, attention loop, confirm actions |
| `MothMarketTheme.java` | Colors, card chrome, logo / avatar helpers |
| `ConversationalVoiceController.java` | Speak / listen |
| `VoiceCommandParser.java` | Spoken phrases → reply drafts |
| `EyeOfHorusView.java` | Legacy eye visual (still used in some states) |
| `HapticTextureEngine.java` | Bridges UI events to sensory cues |
| `TextureBackgroundView.java` | Background texture scrolling |
| `TextureDrawableFactory.java` | Drawable helpers |
| `ShakeUrgencyController.java` | Shake → urgency behavior |

Drawables / colors: `app/src/main/res/drawable*`, `res/values/colors_moth.xml`.
