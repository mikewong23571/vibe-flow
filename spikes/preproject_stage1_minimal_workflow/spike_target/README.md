# spike_target

This directory is both the installation target and the runtime target for preproject_stage1_minimal_workflow.

* toolchain-installed state lives under `.spike-v1/`
* the seed git repository lives under `runtime_repo/`
* each worker run gets its own worktree under `.spike-v1/runs/<run-id>/worktree`
