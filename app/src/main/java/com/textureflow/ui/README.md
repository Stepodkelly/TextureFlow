# `ui/` — Moth Market screen + voice

| File | Role |
|------|------|
| `MainActivity.java` | Chat list, settings ⋮, attention loop, confirm actions |
| `MothMarketTheme.java` | Colors, card chrome, logo / avatar helpers |
| `ConversationalVoiceController.java` | Speak / listen |
| `VoiceCommandParser.java` | Spoken phrases → reply drafts |
| `EyeOfHorusView.java` | Legacy eye visual (still used in some states) |
| `HapticTextureEngine.java` | Bridges UI events to sensory cues |
| `TextureBackgroundView.java` | Paper base; moth JPEG is invisible whiteness map (no watermark) |
| `MothAlbedoShader.java` | Continuous cover UVs; catch ∝ map whiteness (no tile seams) |
| `TextureDrawableFactory.java` | Drawable helpers |
| `ShakeUrgencyController.java` | Shake → urgency behavior |

Drawables / colors: `app/src/main/res/drawable*`, `res/values/colors_moth.xml`.
