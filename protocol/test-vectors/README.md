# Interoperability fixtures

These keys and passphrases are public test data. Never use them for real secrets. The fixture bundle must be byte-identical across Android, Companion and PWA. Tests cover both protocol generations and malformed authenticated records. `hello-world-v0.txt` preserves the original published tutorial backup, with password `we are all mad here`.

The JVM generator regenerates test-only v1/legacy vectors; keep the published tutorial sample fixed. The PWA can emit additional cross-language vectors by setting `OMS_INTEROP_OUT` to a temporary path when running `npm test`; verify them with the JVM runner.
