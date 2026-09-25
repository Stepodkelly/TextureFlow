# `ui/` — Moth Market screen + voice

`MainActivity` is the **Host**: lifecycle, permissions, and wiring. Page
behaviour lives in extracted controllers so Stream I can hang assessments
on the chat list without editing a 2k-line Activity.

| File / package | Role |
|------|------|
| `MainActivity.java` | Lifecycle, permissions, public render API, controller wiring |
| `MainSurface.java` | Shared host bag used by extracted controllers |
| `MothMarketTheme.java` | Colors, card chrome, logo / avatar helpers |
| `kit/UiKit.java` | Surfaces, text, buttons, dp + layout-param helpers |
| `chats/ChatListController.java` | Chat list, search, glow-ring registration, hidden legacy chrome |
| `chats/ChatListPresenter.java` | Pure grouping, filter, attention queue, reply/history helpers |
| `chats/ConversationController.java` | Thread page, bubbles, response / proposal confirm-cancel |
| `chats/PersonTimeline.java`, `ChatBubble.java` | Package-visible chat-list types |
| `nav/NavigationController.java`, `nav/Page.java` | Bottom nav + page visibility |
| `lighting/ReflectionLightsController.java` | Nav catch-lights + unread ring slots |
| `settings/SettingsPage.java` | Settings, sensory prefs, connection refresh |
| `flows/FlowsPage.java` | Flows placeholder |
| `voice/VoiceSessionController.java` | Speak / listen glue for the surface |
| `ConversationalVoiceController.java` | Speak / listen engine |
| `VoiceCommandParser.java` | Spoken phrases → reply drafts |
| `EyeOfHorusView.java` | Legacy eye visual (still used in some states) |
| `HapticTextureEngine.java` | Bridges UI events to sensory cues |
| `TextureBackgroundView.java` | Paper base; moth JPEG is invisible whiteness map (no watermark) |
| `MothAlbedoShader.java` | Continuous cover UVs; catch ∝ map whiteness (no tile seams) |
| `TextureDrawableFactory.java` | Drawable helpers |
| `ShakeUrgencyController.java` | Shake → urgency behavior |

`ChatListController.setAssessments(Map<personId, AttentionAssessment>)` is a
no-op seam for Stream I.

Drawables / colors: `app/src/main/res/drawable*`, `res/values/colors_moth.xml`.
