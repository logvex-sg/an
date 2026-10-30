# Third-party notices — optional local models

LOGVEX CRYSTAL itself is MIT-licensed (`LICENSE`) and bundles **no** third-party AI runtime and
**no** model weights. The optional models listed below are downloaded by the user on demand from
their upstream publisher and are **not** redistributed with the client. They are recorded here
for attribution and so a user can see exactly what they are installing.

## Bundled components

None. The inference runtime is resolved reflectively from the classpath at run time
(`ai/model/LlamaRuntimeBinding`) and is not shipped with the mod.

## Optional models (downloaded by the user)

### SmolLM2 360M Instruct (default)

- Upstream model: `HuggingFaceTB/SmolLM2-360M-Instruct`
- GGUF quantisation: `bartowski/SmolLM2-360M-Instruct-GGUF`
- Licence: **Apache-2.0**
- File: `SmolLM2-360M-Instruct-Q4_K_M.gguf` (270,590,880 bytes)
- SHA-256: `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2`

### Qwen2.5 0.5B Instruct (opt-in)

- Upstream model: `Qwen/Qwen2.5-0.5B-Instruct`
- GGUF quantisation: `bartowski/Qwen2.5-0.5B-Instruct-GGUF`
- Licence: **Apache-2.0**
- File: `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` (397,808,192 bytes)
- SHA-256: `6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653`

Both are Apache-2.0, which permits redistribution and requires only attribution and the licence
text, which the user receives from the upstream publisher. No model is loaded or executed
without the user explicitly downloading it.

## Optional inference runtime

If a compatible GGUF runtime (for example a `de.kherud.llama`-style binding) is placed on the
classpath by the user, it is subject to **its own** licence, which the user accepts when they
install it. It is not bundled or endorsed by this project.
