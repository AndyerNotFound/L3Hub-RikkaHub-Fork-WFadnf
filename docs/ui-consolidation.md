# UI Architecture and Interaction Invariants

This document defines the ownership boundaries and behavior that must survive future UI work. It is an
architecture contract, not a verification report. Build and test results belong in commit and CI records.

## Ownership

| Area | Single source of truth | Rule |
|---|---|---|
| Route identity | `app/src/main/java/me/rerere/rikkahub/Screen.kt` | Keep route identity separate from activity and layout assembly. |
| Route registration | `appNavEntries()` in `RouteActivity.kt` | Both navigation panes use the same remembered entry provider. |
| Page transitions | `ui/navigation/NavTransitions.kt` | Both panes share one specification. Predictive pop returns the same specification as ordinary pop. |
| Back-stack operations | `ui/context/NavContext.kt` | New navigation code goes through `Navigator`; do not add a parallel back-stack policy. |
| Departure marker | `ui/navigation/NavTransitions.kt` | The only entry decorator marks an entry that is leaving and already popped. It reads the back stack and never writes navigation state. |
| Motion parameters | `ui/theme/MotionSpecs.kt` | Use the active MD3 motion scheme and existing animation settings; do not add isolated timing tables. |
| Expand/collapse motion | `ui/modifier/MotionBlur.kt` | Enter and exit use matching parameters. Do not build another blur pipeline for the same behavior. |
| MD3/Miuix component routing | `ui/style/StyledComponents.kt` | Shared visual components live in `ui.style`, not in the terminal UI package. |
| Settings groups | `ui/components/ui/CardGroup.kt` | Conditional items stay registered and use `visible` or `optionGroup(visible = ...)`. |
| Chat send decisions | `ui/pages/chat/ChatSendActions.kt` | Send, interject, no-answer, edit, and compression confirmation share one acceptance path. |
| Chat pane state | `ui/pages/chat/ChatPaneHost.kt` | Pane opening, selection, and closing have one owner. |
| Chat input fields | `ui/components/ai/ChatInputFields.kt` | Full-screen editing remains shared; completion and ordinary input keep their existing behavior. |
| Model editor | `ui/pages/setting/SettingModelEditorPage.kt` | Model identity, pricing, thresholds, and overrides are not maintained inside the provider page. |
| Chat page regions | `ui/pages/chat/ChatPage.kt` | The page file assembles regions only; composer, overlays, and pickers live in their own files. |
| Session loading and draft recovery | `service/ConversationLifecycle.kt` | Loading, creation, and interrupted-turn recovery have one owner; callers do not re-implement `session.initialize`. |
| Error reporting | `service/ChatErrorSink.kt` | Cancellation is not an error. Pages and the web event stream read the single error flow. |
| Numeric drafts | `ui/components/ui/NumberSlider.kt` | Local text, committed values, and genuine external updates remain distinct. |

## Navigation invariants

- A forward page slides in from the direction selected by `Screen.Chat.enterFromTop`; the covered page stays in
  place. Other forward routes use the existing default direction.
- Ordinary back and predictive back use the same whole-page downward exit. Do not add a second return animation.
- `Screen.MediaCreation` keeps its entry-level `None` transition so its internal container transform is not doubled.
- Back presses during an entering transition are queued, not discarded. A predictive seek that is already moving
  toward the lower page finishes by popping immediately. Each queued pop removes exactly one layer.
- `navigate` and `clearAndNavigate` clear queued pops so an old back press cannot remove a newly opened page.
- Leaving entries consume pointer input. Shared-element work is removed for a popped entry so a page cannot slide
  and fly back to its source at the same time.
- The middle and right setting panes keep independent decorator instances; their state holders must not share a
  `SaveableStateHolder` for the same content key.

## Chat invariants

- Streaming updates stay scoped to the list and other narrow readers. Do not make the page root observe the whole
  conversation for every token.
- Sending is accepted before the input is cleared or the list is scrolled. A rejected send leaves the draft intact.
- The existing context-limit branches, toast behavior, compression confirmation, edit submission, interjection,
  no-answer sending, and delayed bottom scroll are product behavior, not refactor opportunities.
- Drawer drag remains one-to-one with the pointer. Keep the existing direction, child-consumption, long-press, and
  velocity guards. Drawer offset belongs in the draw layer, not in composition-time layout decisions.
- Overview, translation, selection, quota, details, floating-bar, and context-meter gestures keep their current
  priority and settings scope. A structural cleanup must not silently choose a new product behavior for them.
- First-message empty-state exit, streaming follow-scroll, branch switching, and three-pane layout require device
  verification; compilation alone does not validate them.

## Motion and component invariants

- Disabling animations snaps supported transitions and disables motion blur. A new hard-coded `tween` must not bypass
  that setting.
- Do not replace the established page spring with a tween merely to make specifications look uniform.
- Native and Miuix `CardGroup` containers keep their distinct geometry. Shared item content must not erase that
  difference.
- `CardGroup` content is deliberately not a composable lambda. Changing that registration model breaks recomposition.
- A conditional `item`, `switchItem`, or `formItem` is never wrapped in `if`; mutually exclusive states use mutually
  exclusive `visible` values.
- `AppSwitch` performs style routing once through `ui.style`. Button shadow remains disabled for Miuix.

## Draft and editor invariants

- Numeric input preserves text such as `007` when the committed value echoes back as `7`. A genuine external change,
  such as a model switch or preset selection, replaces the draft.
- Threshold input keeps the documented 21 stops, accepts arbitrary positive integers, and keeps the existing clear,
  validation, and non-stop positioning behavior.
- A model draft is keyed by stable provider and model identity. External writes must not silently replace a draft
  being edited; a newly opened editor reads the latest saved model.
- Creating a model keeps duplicate-submission protection. Editing a model keeps pricing, price rules, key grouping,
  OCR settings, and provider overrides intact.

## Verification

Automated checks:

1. `git diff --check` and static reference checks after every structural move.
2. The JVM tests for navigation, chat-send decisions, numeric drafts, model drafts, and threshold positioning.
3. The repository CI workflow for compilation and the complete configured unit-test set.

Device acceptance remains necessary for predictive back, repeated back presses, drawer and child gestures, first
message sending, streaming follow-scroll, floating-bar focus behavior, quota/details overlays, native and Miuix
`CardGroup` transitions, model draft editing, and landscape panes. Do not describe these as verified until they have
actually been exercised on a device.
