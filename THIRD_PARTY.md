# Third-party components

None of these are bundled in the plugin jar. Libraries are downloaded by the server from Maven Central
(plugin.yml `libraries`), models are downloaded by the plugin on first use.

| Component | Used for | License |
|---|---|---|
| [Plasmo Voice API](https://github.com/plasmoapp/plasmo-voice) | Voice chat integration (provided by the Plasmo Voice plugin) | LGPL-3.0 |
| [Vosk](https://alphacephei.com/vosk/) + JNA | `vosk` engine | Apache-2.0 (JNA: Apache-2.0 / LGPL-2.1) |
| Vosk models | `vosk` engine | Apache-2.0 (see each model's page) |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | `t-one` engine | MIT |
| [T-one](https://huggingface.co/t-tech/T-one) model by T-Bank | `t-one` engine | Apache-2.0 |
| Deepgram, OpenAI | Cloud engines (optional, your own API key, their terms apply) | — |
