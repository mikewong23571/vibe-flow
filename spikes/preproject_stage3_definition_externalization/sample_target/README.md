# sample_target

This is the sample git repository used by `preproject_stage3_definition_externalization`.

The repository itself is the workflow target:

* source files live here
* durable workflow state will be installed under `.workflow/state/`
* local runtime state will be installed under `.workflow/local/`
* each run gets a detached worktree under `.workflow/local/runs/<run-id>/worktree`
