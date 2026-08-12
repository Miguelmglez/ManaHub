### Voice recognition (offline, Vosk)
Grammar-restricted Vosk models, **one language per game session** (never `Set<VoiceLanguage>` in
`start()`). Per-language download/delete from R2 (`voice-models/{en|es|de}.zip` in bucket
`manahub-assets`); a language is selectable only when its model is `Ready`; the active language can't be
deleted. To add a language: enum entry + `CommandGrammar` phrases + upload zip.
- → memory: `project_voice_controls`, `feedback_voice_test_architecture`

