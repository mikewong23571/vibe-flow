# impl

Installed task_type bundle source for `preproject_stage3_definition_externalization`.

* `task_type.edn` is the definition artifact source
* prompts live under `prompts/`
* `prepare_run` stays built in, but reads this bundle's declarative config
* `hooks/before_prepare_run` is the only external extension point in this spike
