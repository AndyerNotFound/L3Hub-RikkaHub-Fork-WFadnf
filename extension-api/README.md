# L3Hub extension API

This module is the build-time contract between the host and feature AARs. Features run in the
host process; this is not a runtime plugin loader and does not require IPC or hooks.

## Add a feature

1. Create an Android library module that depends on `:extension-api`.
2. Implement `L3Feature` and register contributions in `L3Feature.register`.
3. Add the module to `settings.gradle.kts` and the app's dependencies.
4. Register the feature in the host's `ExtensionRegistry`, then call `freeze` with the host's
   reserved provider types.
5. Consume provider contributions through `ProviderManager`'s `extensionProviders` parameter.

Feature IDs and provider types must be unique. Registration is atomic per feature: if a feature
throws, its provider contributions are rolled back. Contributions cannot be changed after the
registry is frozen.

The current vertical slice moves only the `gguf` and `onnx` provider construction and
registration into `:feature-local-providers`; their HTTP implementations remain in `:ai`.
Conversation state, streaming, settings, and database ownership stay in the host.

Provider registration never replaces an existing host or feature provider. Host collisions are
reported during `freeze` with the contributing feature ID.
